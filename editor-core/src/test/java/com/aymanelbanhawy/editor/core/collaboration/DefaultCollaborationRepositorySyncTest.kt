package com.aymanelbanhawy.editor.core.collaboration

import android.content.ContextWrapper
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
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class DefaultCollaborationRepositorySyncTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
    private val testContext = object : ContextWrapper(null) {
        private val filesRoot = File(System.getProperty("java.io.tmpdir"), "collaboration-sync-test").apply { mkdirs() }
        override fun getFilesDir(): File = filesRoot
    }

    @Test
    fun processSyncCompletesQueuedShareLink() = runTest {
        val remote = InMemoryCollaborationRemoteDataSource()
        val repository = DefaultCollaborationRepository(
            context = testContext,
            shareLinkDao = FakeShareLinkDao(),
            reviewThreadDao = FakeReviewThreadDao(),
            reviewCommentDao = FakeReviewCommentDao(),
            versionSnapshotDao = FakeVersionSnapshotDao(),
            activityEventDao = FakeActivityEventDao(),
            syncQueueDao = FakeSyncQueueDao(),
            remoteDataSource = remote,
            conflictResolver = CollaborationConflictResolver(),
            json = json,
        )

        repository.createShareLink(document(), "Team review", SharePermission.Comment, null)
        val summary = repository.processSync(document().documentRef.sourceKey)

        assertThat(summary.completedCount).isEqualTo(2)
        assertThat(remote.shareLinks(document().documentRef.sourceKey)).hasSize(1)
        assertThat(remote.activity(document().documentRef.sourceKey)).hasSize(1)
    }

    private fun document(): DocumentModel {
        return DocumentModel(
            sessionId = "session",
            documentRef = PdfDocumentRef(
                uriString = "file:///tmp/review.pdf",
                displayName = "review.pdf",
                sourceType = DocumentSourceType.File,
                sourceKey = "/tmp/review.pdf",
                workingCopyPath = "/tmp/review.pdf",
            ),
            pages = listOf(PageModel(index = 0, label = "1")),
        )
    }
}

private class FakeShareLinkDao : ShareLinkDao {
    private val items = linkedMapOf<String, ShareLinkEntity>()
    override suspend fun upsert(entity: ShareLinkEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<ShareLinkEntity> = items.values.filter { it.documentKey == documentKey }
}

private class FakeReviewThreadDao : ReviewThreadDao {
    private val items = linkedMapOf<String, ReviewThreadEntity>()
    override suspend fun upsert(entity: ReviewThreadEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<ReviewThreadEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun thread(threadId: String): ReviewThreadEntity? = items[threadId]
}

private class FakeReviewCommentDao : ReviewCommentDao {
    private val items = mutableListOf<ReviewCommentEntity>()
    override suspend fun upsert(entity: ReviewCommentEntity) { items.removeAll { it.id == entity.id }; items += entity }
    override suspend fun upsertAll(entities: List<ReviewCommentEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forThread(threadId: String): List<ReviewCommentEntity> = items.filter { it.threadId == threadId }
}

private class FakeVersionSnapshotDao : VersionSnapshotDao {
    private val items = linkedMapOf<String, VersionSnapshotEntity>()
    override suspend fun upsert(entity: VersionSnapshotEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<VersionSnapshotEntity> = items.values.filter { it.documentKey == documentKey }
}

private class FakeActivityEventDao : ActivityEventDao {
    private val items = linkedMapOf<String, ActivityEventEntity>()
    override suspend fun upsert(entity: ActivityEventEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<ActivityEventEntity> = items.values.filter { it.documentKey == documentKey }
}

private class FakeSyncQueueDao : SyncQueueDao {
    private val items = linkedMapOf<String, SyncQueueEntity>()
    override suspend fun upsert(entity: SyncQueueEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<SyncQueueEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun pending(): List<SyncQueueEntity> = items.values.filter { it.state != SyncOperationState.Completed.name }
}
