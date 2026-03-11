package com.aymanelbanhawy.editor.core.collaboration

import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import kotlinx.serialization.Serializable

@Serializable
enum class SharePermission {
    View,
    Comment,
    Edit,
}

@Serializable
data class ShareLinkModel(
    val id: String,
    val documentKey: String,
    val token: String,
    val title: String,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long?,
    val permission: SharePermission,
    val isRevoked: Boolean = false,
) {
    val shareUrl: String
        get() = "https://local.enterprise.pdf/share/$token"
}

@Serializable
data class MentionModel(
    val username: String,
    val displayName: String = username,
)

@Serializable
data class ReviewCommentModel(
    val id: String,
    val threadId: String,
    val author: String,
    val message: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val mentions: List<MentionModel> = emptyList(),
)

@Serializable
enum class ReviewThreadState {
    Open,
    Resolved,
}

@Serializable
data class ReviewThreadModel(
    val id: String,
    val documentKey: String,
    val pageIndex: Int?,
    val anchorBounds: NormalizedRect?,
    val title: String,
    val createdBy: String,
    val createdAtEpochMillis: Long,
    val modifiedAtEpochMillis: Long,
    val state: ReviewThreadState = ReviewThreadState.Open,
    val comments: List<ReviewCommentModel> = emptyList(),
) {
    val latestComment: ReviewCommentModel?
        get() = comments.maxByOrNull { it.modifiedAtEpochMillis }
}

@Serializable
data class ReviewFilterModel(
    val state: ReviewThreadState? = null,
    val mentionedUser: String? = null,
    val query: String = "",
    val pageIndex: Int? = null,
) {
    fun matches(thread: ReviewThreadModel): Boolean {
        if (state != null && thread.state != state) return false
        if (pageIndex != null && thread.pageIndex != pageIndex) return false
        if (mentionedUser != null && thread.comments.none { comment -> comment.mentions.any { it.username.equals(mentionedUser, ignoreCase = true) } }) return false
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return true
        val haystack = buildString {
            append(thread.title)
            append('\n')
            thread.comments.forEach { append(it.message).append('\n') }
        }
        return haystack.contains(trimmedQuery, ignoreCase = true)
    }
}

@Serializable
data class VersionComparisonMetadata(
    val pageCountDelta: Int,
    val annotationDelta: Int,
    val commentDelta: Int,
    val exportedAtEpochMillis: Long?,
)

@Serializable
data class VersionSnapshotModel(
    val id: String,
    val documentKey: String,
    val label: String,
    val createdAtEpochMillis: Long,
    val snapshotPath: String,
    val comparison: VersionComparisonMetadata,
)

@Serializable
enum class ActivityEventType {
    Opened,
    Commented,
    Signed,
    Shared,
    Exported,
}

@Serializable
data class ActivityEventModel(
    val id: String,
    val documentKey: String,
    val type: ActivityEventType,
    val actor: String,
    val summary: String,
    val createdAtEpochMillis: Long,
    val threadId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
enum class SyncOperationType {
    UpsertShareLink,
    UpsertReviewThread,
    RecordActivity,
    CreateSnapshot,
}

@Serializable
enum class SyncOperationState {
    Pending,
    Syncing,
    Completed,
    Failed,
    Conflict,
}

@Serializable
data class SyncOperationModel(
    val id: String,
    val documentKey: String,
    val type: SyncOperationType,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val state: SyncOperationState = SyncOperationState.Pending,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

@Serializable
enum class SyncConflictPolicy {
    PreferNewest,
    PreferLocal,
    PreferRemote,
}

@Serializable
data class SyncConflictModel(
    val operationId: String,
    val documentKey: String,
    val reason: String,
    val resolution: SyncConflictPolicy,
)

@Serializable
data class CollaborationSyncSummary(
    val processedCount: Int = 0,
    val completedCount: Int = 0,
    val conflictCount: Int = 0,
    val failedCount: Int = 0,
)

@Serializable
data class ReviewThreadDraft(
    val title: String = "",
    val message: String = "",
)

@Serializable
data class CollaborationState(
    val shareLinks: List<ShareLinkModel> = emptyList(),
    val reviewThreads: List<ReviewThreadModel> = emptyList(),
    val activityEvents: List<ActivityEventModel> = emptyList(),
    val versionSnapshots: List<VersionSnapshotModel> = emptyList(),
    val activeFilter: ReviewFilterModel = ReviewFilterModel(),
    val pendingSyncCount: Int = 0,
)

interface CollaborationRepository {
    suspend fun shareLinks(documentKey: String): List<ShareLinkModel>
    suspend fun createShareLink(document: DocumentModel, title: String, permission: SharePermission, expiresAtEpochMillis: Long?): ShareLinkModel
    suspend fun reviewThreads(documentKey: String, filter: ReviewFilterModel = ReviewFilterModel()): List<ReviewThreadModel>
    suspend fun addReviewThread(document: DocumentModel, title: String, message: String, pageIndex: Int?, anchorBounds: NormalizedRect?): ReviewThreadModel
    suspend fun addReviewReply(threadId: String, author: String, message: String): ReviewThreadModel
    suspend fun setThreadResolved(threadId: String, resolved: Boolean): ReviewThreadModel?
    suspend fun versionSnapshots(documentKey: String): List<VersionSnapshotModel>
    suspend fun createVersionSnapshot(document: DocumentModel, label: String): VersionSnapshotModel
    suspend fun activityEvents(documentKey: String): List<ActivityEventModel>
    suspend fun recordActivity(event: ActivityEventModel)
    suspend fun pendingSyncOperations(documentKey: String): List<SyncOperationModel>
    suspend fun processSync(documentKey: String): CollaborationSyncSummary
}
