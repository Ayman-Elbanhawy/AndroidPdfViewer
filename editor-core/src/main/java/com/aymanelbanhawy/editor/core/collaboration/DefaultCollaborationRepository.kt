package com.aymanelbanhawy.editor.core.collaboration

import android.content.Context
import androidx.core.net.toUri
import com.aymanelbanhawy.editor.core.data.ActivityEventDao
import com.aymanelbanhawy.editor.core.data.ActivityEventEntity
import com.aymanelbanhawy.editor.core.data.ReviewCommentDao
import com.aymanelbanhawy.editor.core.data.ReviewCommentEntity
import com.aymanelbanhawy.editor.core.data.ReviewThreadDao
import com.aymanelbanhawy.editor.core.data.ReviewThreadEntity
import com.aymanelbanhawy.editor.core.data.ShareLinkDao
import com.aymanelbanhawy.editor.core.data.ShareLinkEntity
import com.aymanelbanhawy.editor.core.data.SyncQueueDao
import com.aymanelbanhawy.editor.core.data.SyncQueueEntity
import com.aymanelbanhawy.editor.core.data.VersionSnapshotDao
import com.aymanelbanhawy.editor.core.data.VersionSnapshotEntity
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class DefaultCollaborationRepository(
    private val context: Context,
    private val shareLinkDao: ShareLinkDao,
    private val reviewThreadDao: ReviewThreadDao,
    private val reviewCommentDao: ReviewCommentDao,
    private val versionSnapshotDao: VersionSnapshotDao,
    private val activityEventDao: ActivityEventDao,
    private val syncQueueDao: SyncQueueDao,
    private val remoteDataSource: CollaborationRemoteDataSource,
    private val conflictResolver: CollaborationConflictResolver,
    private val json: Json,
) : CollaborationRepository {

    override suspend fun shareLinks(documentKey: String): List<ShareLinkModel> {
        return shareLinkDao.forDocument(documentKey).map { it.toModel() }
    }

    override suspend fun createShareLink(document: DocumentModel, title: String, permission: SharePermission, expiresAtEpochMillis: Long?): ShareLinkModel {
        val now = System.currentTimeMillis()
        val model = ShareLinkModel(
            id = UUID.randomUUID().toString(),
            documentKey = document.documentRef.sourceKey,
            token = UUID.randomUUID().toString().replace("-", ""),
            title = title.ifBlank { document.documentRef.displayName },
            createdBy = "Ayman",
            createdAtEpochMillis = now,
            expiresAtEpochMillis = expiresAtEpochMillis,
            permission = permission,
        )
        shareLinkDao.upsert(model.toEntity())
        enqueueSync(model.documentKey, SyncOperationType.UpsertShareLink, json.encodeToString(ShareLinkModel.serializer(), model))
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = model.documentKey,
                type = ActivityEventType.Shared,
                actor = model.createdBy,
                summary = "Created share link ${model.title}",
                createdAtEpochMillis = now,
                metadata = mapOf("permission" to permission.name, "url" to model.shareUrl),
            ),
        )
        return model
    }

    override suspend fun reviewThreads(documentKey: String, filter: ReviewFilterModel): List<ReviewThreadModel> {
        return reviewThreadDao.forDocument(documentKey).map { entity -> entity.toModel(reviewCommentDao.forThread(entity.id).map { it.toModel() }) }.filter(filter::matches)
    }

    override suspend fun addReviewThread(document: DocumentModel, title: String, message: String, pageIndex: Int?, anchorBounds: NormalizedRect?): ReviewThreadModel {
        val now = System.currentTimeMillis()
        val threadId = UUID.randomUUID().toString()
        val comment = ReviewCommentModel(
            id = UUID.randomUUID().toString(),
            threadId = threadId,
            author = "Ayman",
            message = message,
            createdAtEpochMillis = now,
            modifiedAtEpochMillis = now,
            mentions = parseMentions(message),
        )
        val thread = ReviewThreadModel(
            id = threadId,
            documentKey = document.documentRef.sourceKey,
            pageIndex = pageIndex,
            anchorBounds = anchorBounds,
            title = title.ifBlank { "Review note" },
            createdBy = "Ayman",
            createdAtEpochMillis = now,
            modifiedAtEpochMillis = now,
            comments = listOf(comment),
        )
        reviewThreadDao.upsert(thread.toEntity(json))
        reviewCommentDao.upsert(comment.toEntity(json))
        enqueueSync(thread.documentKey, SyncOperationType.UpsertReviewThread, json.encodeToString(ReviewThreadModel.serializer(), thread))
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = thread.documentKey,
                type = ActivityEventType.Commented,
                actor = comment.author,
                summary = "Started review thread ${thread.title}",
                createdAtEpochMillis = now,
                threadId = thread.id,
                metadata = mapOf("page" to (pageIndex?.plus(1)?.toString() ?: "document")),
            ),
        )
        return thread
    }

    override suspend fun addReviewReply(threadId: String, author: String, message: String): ReviewThreadModel {
        val threadEntity = requireNotNull(reviewThreadDao.thread(threadId))
        val now = System.currentTimeMillis()
        val comment = ReviewCommentModel(
            id = UUID.randomUUID().toString(),
            threadId = threadId,
            author = author,
            message = message,
            createdAtEpochMillis = now,
            modifiedAtEpochMillis = now,
            mentions = parseMentions(message),
        )
        reviewCommentDao.upsert(comment.toEntity(json))
        val updatedThread = threadEntity.copy(modifiedAtEpochMillis = now).toModel(reviewCommentDao.forThread(threadId).map { it.toModel() } + comment)
        reviewThreadDao.upsert(updatedThread.toEntity(json))
        enqueueSync(updatedThread.documentKey, SyncOperationType.UpsertReviewThread, json.encodeToString(ReviewThreadModel.serializer(), updatedThread))
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = updatedThread.documentKey,
                type = ActivityEventType.Commented,
                actor = author,
                summary = "Replied in ${updatedThread.title}",
                createdAtEpochMillis = now,
                threadId = updatedThread.id,
            ),
        )
        return updatedThread
    }

    override suspend fun setThreadResolved(threadId: String, resolved: Boolean): ReviewThreadModel? {
        val entity = reviewThreadDao.thread(threadId) ?: return null
        val comments = reviewCommentDao.forThread(threadId).map { it.toModel() }
        val updated = entity.copy(
            state = if (resolved) ReviewThreadState.Resolved.name else ReviewThreadState.Open.name,
            modifiedAtEpochMillis = System.currentTimeMillis(),
        ).toModel(comments)
        reviewThreadDao.upsert(updated.toEntity(json))
        enqueueSync(updated.documentKey, SyncOperationType.UpsertReviewThread, json.encodeToString(ReviewThreadModel.serializer(), updated))
        return updated
    }

    override suspend fun versionSnapshots(documentKey: String): List<VersionSnapshotModel> {
        return versionSnapshotDao.forDocument(documentKey).map { it.toModel(json) }
    }

    override suspend fun createVersionSnapshot(document: DocumentModel, label: String): VersionSnapshotModel {
        val snapshotDir = File(context.filesDir, "collaboration-snapshots").apply { mkdirs() }
        val sourceFile = File(document.documentRef.workingCopyPath)
        val snapshotFile = File(snapshotDir, "${UUID.randomUUID()}_${sourceFile.name}")
        if (sourceFile.exists()) sourceFile.copyTo(snapshotFile, overwrite = true)
        val previous = versionSnapshots(document.documentRef.sourceKey).firstOrNull()
        val currentCommentCount = reviewThreads(document.documentRef.sourceKey).sumOf { it.comments.size }
        val comparison = VersionComparisonMetadata(
            pageCountDelta = document.pageCount - (previous?.comparison?.pageCountDelta ?: document.pageCount),
            annotationDelta = document.pages.sumOf { it.annotations.size } - (previous?.comparison?.annotationDelta ?: document.pages.sumOf { it.annotations.size }),
            commentDelta = currentCommentCount - (previous?.comparison?.commentDelta ?: currentCommentCount),
            exportedAtEpochMillis = System.currentTimeMillis(),
        )
        val snapshot = VersionSnapshotModel(
            id = UUID.randomUUID().toString(),
            documentKey = document.documentRef.sourceKey,
            label = label.ifBlank { "Snapshot ${System.currentTimeMillis()}" },
            createdAtEpochMillis = System.currentTimeMillis(),
            snapshotPath = snapshotFile.absolutePath,
            comparison = comparison,
        )
        versionSnapshotDao.upsert(snapshot.toEntity(json))
        enqueueSync(snapshot.documentKey, SyncOperationType.CreateSnapshot, json.encodeToString(VersionSnapshotModel.serializer(), snapshot))
        return snapshot
    }

    override suspend fun activityEvents(documentKey: String): List<ActivityEventModel> {
        return activityEventDao.forDocument(documentKey).map { it.toModel(json) }
    }

    override suspend fun recordActivity(event: ActivityEventModel) {
        activityEventDao.upsert(event.toEntity(json))
        enqueueSync(event.documentKey, SyncOperationType.RecordActivity, json.encodeToString(ActivityEventModel.serializer(), event))
    }

    override suspend fun pendingSyncOperations(documentKey: String): List<SyncOperationModel> {
        return syncQueueDao.forDocument(documentKey).map { it.toModel() }.filter { it.state != SyncOperationState.Completed }
    }

    override suspend fun processSync(documentKey: String): CollaborationSyncSummary {
        var processed = 0
        var completed = 0
        var conflicts = 0
        var failed = 0
        syncQueueDao.pending().filter { it.documentKey == documentKey }.forEach { entity ->
            processed += 1
            val syncing = entity.copy(state = SyncOperationState.Syncing.name, updatedAtEpochMillis = System.currentTimeMillis(), attemptCount = entity.attemptCount + 1)
            syncQueueDao.upsert(syncing)
            runCatching {
                when (SyncOperationType.valueOf(entity.type)) {
                    SyncOperationType.UpsertShareLink -> {
                        val local = json.decodeFromString(ShareLinkModel.serializer(), entity.payloadJson)
                        val remote = remoteDataSource.shareLinks(local.documentKey).firstOrNull { it.id == local.id }
                        remoteDataSource.upsertShareLink(conflictResolver.resolveShareLink(local, remote))
                    }
                    SyncOperationType.UpsertReviewThread -> {
                        val local = json.decodeFromString(ReviewThreadModel.serializer(), entity.payloadJson)
                        val remote = remoteDataSource.reviewThreads(local.documentKey).firstOrNull { it.id == local.id }
                        remoteDataSource.upsertReviewThread(conflictResolver.resolveThread(local, remote))
                    }
                    SyncOperationType.RecordActivity -> {
                        remoteDataSource.recordActivity(json.decodeFromString(ActivityEventModel.serializer(), entity.payloadJson))
                    }
                    SyncOperationType.CreateSnapshot -> {
                        remoteDataSource.recordSnapshot(json.decodeFromString(VersionSnapshotModel.serializer(), entity.payloadJson))
                    }
                }
            }.onSuccess {
                completed += 1
                syncQueueDao.upsert(syncing.copy(state = SyncOperationState.Completed.name, updatedAtEpochMillis = System.currentTimeMillis(), lastError = null))
            }.onFailure { error ->
                val remoteNewer = runCatching {
                    when (SyncOperationType.valueOf(entity.type)) {
                        SyncOperationType.UpsertShareLink -> {
                            val local = json.decodeFromString(ShareLinkModel.serializer(), entity.payloadJson)
                            val remote = remoteDataSource.shareLinks(local.documentKey).firstOrNull { it.id == local.id }
                            remote != null && remote.createdAtEpochMillis > local.createdAtEpochMillis
                        }
                        SyncOperationType.UpsertReviewThread -> {
                            val local = json.decodeFromString(ReviewThreadModel.serializer(), entity.payloadJson)
                            val remote = remoteDataSource.reviewThreads(local.documentKey).firstOrNull { it.id == local.id }
                            remote != null && remote.modifiedAtEpochMillis > local.modifiedAtEpochMillis
                        }
                        else -> false
                    }
                }.getOrDefault(false)
                if (remoteNewer) {
                    conflicts += 1
                    syncQueueDao.upsert(syncing.copy(state = SyncOperationState.Conflict.name, lastError = error.message ?: "Conflict", updatedAtEpochMillis = System.currentTimeMillis()))
                } else {
                    failed += 1
                    syncQueueDao.upsert(syncing.copy(state = SyncOperationState.Failed.name, lastError = error.message ?: "Sync failed", updatedAtEpochMillis = System.currentTimeMillis()))
                }
            }
        }
        return CollaborationSyncSummary(processed, completed, conflicts, failed)
    }

    private suspend fun enqueueSync(documentKey: String, type: SyncOperationType, payloadJson: String) {
        val now = System.currentTimeMillis()
        syncQueueDao.upsert(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                documentKey = documentKey,
                type = type.name,
                payloadJson = payloadJson,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                state = SyncOperationState.Pending.name,
                attemptCount = 0,
                lastError = null,
            ),
        )
    }

    private fun parseMentions(message: String): List<MentionModel> {
        return Regex("@([A-Za-z0-9_\\-.]+)").findAll(message).map { MentionModel(it.groupValues[1]) }.distinctBy { it.username }.toList()
    }
}

private fun ShareLinkModel.toEntity(): ShareLinkEntity = ShareLinkEntity(id, documentKey, token, title, createdBy, createdAtEpochMillis, expiresAtEpochMillis, permission.name, isRevoked)
private fun ShareLinkEntity.toModel(): ShareLinkModel = ShareLinkModel(id, documentKey, token, title, createdBy, createdAtEpochMillis, expiresAtEpochMillis, SharePermission.valueOf(permission), isRevoked)

private fun ReviewThreadModel.toEntity(json: Json): ReviewThreadEntity = ReviewThreadEntity(id, documentKey, pageIndex, anchorBounds?.let { json.encodeToString(NormalizedRect.serializer(), it) }, title, createdBy, createdAtEpochMillis, modifiedAtEpochMillis, state.name)
private fun ReviewThreadEntity.toModel(comments: List<ReviewCommentModel>): ReviewThreadModel = ReviewThreadModel(
    id = id,
    documentKey = documentKey,
    pageIndex = pageIndex,
    anchorBounds = anchorBoundsJson?.let { Json { ignoreUnknownKeys = true }.decodeFromString(NormalizedRect.serializer(), it) },
    title = title,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis,
    modifiedAtEpochMillis = modifiedAtEpochMillis,
    state = ReviewThreadState.valueOf(state),
    comments = comments,
)

private fun ReviewCommentModel.toEntity(json: Json): ReviewCommentEntity = ReviewCommentEntity(id, threadId, author, message, createdAtEpochMillis, modifiedAtEpochMillis, json.encodeToString(ListSerializer(MentionModel.serializer()), mentions))
private fun ReviewCommentEntity.toModel(): ReviewCommentModel = ReviewCommentModel(id, threadId, author, message, createdAtEpochMillis, modifiedAtEpochMillis, Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(MentionModel.serializer()), mentionsJson))

private fun VersionSnapshotModel.toEntity(json: Json): VersionSnapshotEntity = VersionSnapshotEntity(id, documentKey, label, createdAtEpochMillis, snapshotPath, json.encodeToString(VersionComparisonMetadata.serializer(), comparison))
private fun VersionSnapshotEntity.toModel(json: Json): VersionSnapshotModel = VersionSnapshotModel(id, documentKey, label, createdAtEpochMillis, snapshotPath, json.decodeFromString(VersionComparisonMetadata.serializer(), comparisonJson))

private fun ActivityEventModel.toEntity(json: Json): ActivityEventEntity = ActivityEventEntity(id, documentKey, type.name, actor, summary, createdAtEpochMillis, threadId, json.encodeToString(MapSerializer(String.serializer(), String.serializer()), metadata))
private fun ActivityEventEntity.toModel(json: Json): ActivityEventModel = ActivityEventModel(id, documentKey, ActivityEventType.valueOf(type), actor, summary, createdAtEpochMillis, threadId, json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), metadataJson))

private fun SyncQueueEntity.toModel(): SyncOperationModel = SyncOperationModel(id, documentKey, SyncOperationType.valueOf(type), payloadJson, createdAtEpochMillis, updatedAtEpochMillis, SyncOperationState.valueOf(state), attemptCount, lastError)

