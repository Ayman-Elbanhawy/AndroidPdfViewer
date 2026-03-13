package com.aymanelbanhawy.enterprisepdf.app.collaboration

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aymanelbanhawy.editor.core.collaboration.*
import com.aymanelbanhawy.editor.core.data.*
import com.aymanelbanhawy.editor.core.enterprise.*
import com.aymanelbanhawy.editor.core.model.*
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollaborationReviewFlowTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
    private lateinit var testContext: Context
    private lateinit var remote: SwitchableCollaborationRemoteDataSource
    private lateinit var repository: CollaborationRepository

    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        testContext = object : ContextWrapper(base) {
            private val filesRoot = File(base.cacheDir, "collaboration-review-flow-test").apply { deleteRecursively(); mkdirs() }
            override fun getFilesDir(): File = filesRoot
            override fun getApplicationContext(): Context = this
        }
        remote = SwitchableCollaborationRemoteDataSource(json)
        repository = buildRepository(testContext, remote, json)
    }

    @Test
    fun addReviewThread_persistsLocallyAndReplaysWhenConnectionReturns() = runBlocking {
        val document = document()
        remote.online = false
        val thread = repository.addReviewThread(document, "Contract review", "@legal please confirm clause 4.", 0, null)
        val offlineSummary = repository.processSync(document.documentRef.sourceKey)
        val queuedAfterOffline = repository.pendingSyncOperations(document.documentRef.sourceKey)

        assertThat(thread.comments).hasSize(1)
        assertThat(thread.comments.single().mentions.map { it.username }).contains("legal")
        assertThat(offlineSummary.failedCount).isAtLeast(1)
        assertThat(queuedAfterOffline.any { it.state == SyncOperationState.Failed }).isTrue()

        remote.online = true
        Thread.sleep(2200L)
        val replaySummary = repository.processSync(document.documentRef.sourceKey)
        val localThreads = repository.reviewThreads(document.documentRef.sourceKey, ReviewFilterModel())
        val localActivity = repository.activityEvents(document.documentRef.sourceKey)
        val queuedAfterReplay = repository.pendingSyncOperations(document.documentRef.sourceKey)

        assertThat(replaySummary.completedCount).isAtLeast(1)
        assertThat(localThreads).hasSize(1)
        assertThat(localThreads.single().title).isEqualTo("Contract review")
        assertThat(localThreads.single().comments.single().message).isEqualTo("@legal please confirm clause 4.")
        assertThat(localActivity.any { it.type == ActivityEventType.Commented }).isTrue()
        assertThat(queuedAfterReplay.none { it.state != SyncOperationState.Completed }).isTrue()
        assertThat(remote.reviewThreadsFor(document.documentRef.sourceKey)).hasSize(1)
    }
}

private fun buildRepository(context: Context, remote: CollaborationRemoteDataSource, json: Json): CollaborationRepository {
    val adminRepository = InstrumentedEnterpriseAdminRepository()
    return DefaultCollaborationRepository(
        context = context,
        shareLinkDao = InstrumentedShareLinkDao(),
        reviewThreadDao = InstrumentedReviewThreadDao(),
        reviewCommentDao = InstrumentedReviewCommentDao(),
        versionSnapshotDao = InstrumentedVersionSnapshotDao(),
        activityEventDao = InstrumentedActivityEventDao(),
        syncQueueDao = InstrumentedSyncQueueDao(),
        remoteRegistry = object : CollaborationRemoteRegistry(context, adminRepository, CollaborationCredentialStore(context, json), json) {
            override suspend fun select(): CollaborationRemoteDataSource = remote
        },
        conflictResolver = CollaborationConflictResolver(),
        enterpriseAdminRepository = adminRepository,
        syncScheduler = object : CollaborationSyncScheduler { override fun schedule(documentKey: String) = Unit },
        json = json,
    )
}

private fun document(): DocumentModel = DocumentModel(
    sessionId = "instrumented-session",
    documentRef = PdfDocumentRef(
        uriString = "file:///tmp/review-flow.pdf",
        displayName = "review-flow.pdf",
        sourceType = DocumentSourceType.File,
        sourceKey = "/tmp/review-flow.pdf",
        workingCopyPath = "/tmp/review-flow.pdf",
    ),
    pages = listOf(PageModel(index = 0, label = "1")),
)
private class SwitchableCollaborationRemoteDataSource(private val json: Json) : CollaborationRemoteDataSource {
    var online: Boolean = true
    private val shareLinks = linkedMapOf<String, ShareLinkModel>()
    private val reviewThreads = linkedMapOf<String, ReviewThreadModel>()
    private val activityEvents = linkedMapOf<String, ActivityEventModel>()
    private val versionSnapshots = linkedMapOf<String, VersionSnapshotModel>()

    override suspend fun healthCheck(): RemoteServiceHealth = RemoteServiceHealth(
        isHealthy = online,
        serviceName = "instrumented",
        serverTimestampEpochMillis = System.currentTimeMillis(),
        supportsPagination = true,
        supportsIdempotency = true,
    )

    override suspend fun pull(request: CollaborationPullRequest): CollaborationRemoteSnapshot {
        requireOnline()
        val now = System.currentTimeMillis()
        return CollaborationRemoteSnapshot(
            shareLinks = CollaborationRemotePage(shareLinks.values.filter { it.documentKey == request.documentKey }, null, now),
            reviewThreads = CollaborationRemotePage(reviewThreads.values.filter { it.documentKey == request.documentKey }, null, now),
            activityEvents = CollaborationRemotePage(activityEvents.values.filter { it.documentKey == request.documentKey }, null, now),
            versionSnapshots = CollaborationRemotePage(versionSnapshots.values.filter { it.documentKey == request.documentKey }, null, now),
        )
    }

    override suspend fun push(request: RemoteMutationRequest): RemoteMutationResult {
        requireOnline()
        val now = System.currentTimeMillis()
        return when (request.payload.artifactType) {
            CollaborationArtifactType.ShareLink -> store(now, request.payload.currentJson, ShareLinkModel.serializer(), shareLinks, CollaborationArtifactType.ShareLink)
            CollaborationArtifactType.ReviewThread -> store(now, request.payload.currentJson, ReviewThreadModel.serializer(), reviewThreads, CollaborationArtifactType.ReviewThread)
            CollaborationArtifactType.ActivityEvent -> store(now, request.payload.currentJson, ActivityEventModel.serializer(), activityEvents, CollaborationArtifactType.ActivityEvent)
            CollaborationArtifactType.VersionSnapshot -> store(now, request.payload.currentJson, VersionSnapshotModel.serializer(), versionSnapshots, CollaborationArtifactType.VersionSnapshot)
        }
    }

    fun reviewThreadsFor(documentKey: String): List<ReviewThreadModel> = reviewThreads.values.filter { it.documentKey == documentKey }

    private fun requireOnline() {
        if (!online) throw CollaborationRemoteException(RemoteErrorMetadata(RemoteErrorCode.Offline, "Instrumentation test remote is offline.", retryable = true))
    }

    private fun <T : Any> store(
        now: Long,
        payload: String?,
        serializer: KSerializer<T>,
        target: MutableMap<String, T>,
        artifactType: CollaborationArtifactType,
    ): RemoteMutationResult {
        val decoded = json.decodeFromString(serializer, requireNotNull(payload))
        val stored = when (decoded) {
            is ShareLinkModel -> decoded.copy(remoteVersion = 1, serverUpdatedAtEpochMillis = now, lastSyncedAtEpochMillis = now) as T
            is ReviewThreadModel -> decoded.copy(remoteVersion = 1, serverUpdatedAtEpochMillis = now, lastSyncedAtEpochMillis = now) as T
            is ActivityEventModel -> decoded.copy(remoteVersion = 1, serverUpdatedAtEpochMillis = now, lastSyncedAtEpochMillis = now) as T
            is VersionSnapshotModel -> decoded.copy(remoteVersion = 1, serverUpdatedAtEpochMillis = now, lastSyncedAtEpochMillis = now) as T
            else -> decoded
        }
        val entityId = when (stored) {
            is ShareLinkModel -> stored.id
            is ReviewThreadModel -> stored.id
            is ActivityEventModel -> stored.id
            is VersionSnapshotModel -> stored.id
            else -> error("Unsupported artifact type")
        }
        target[entityId] = stored
        return RemoteMutationResult(
            artifactType = artifactType,
            entityId = entityId,
            appliedJson = json.encodeToString(serializer, stored),
            remoteVersion = 1,
            serverTimestampEpochMillis = now,
        )
    }
}

private class InstrumentedEnterpriseAdminRepository : EnterpriseAdminRepository {
    private val state = EnterpriseAdminStateModel(
        plan = LicensePlan.Enterprise,
        authSession = AuthSessionModel(mode = AuthenticationMode.Enterprise, provider = AuthenticationProvider.Oidc, isSignedIn = true, displayName = "Ayman"),
        tenantConfiguration = TenantConfigurationModel(collaboration = CollaborationServiceConfig(backendMode = CollaborationBackendMode.RemoteHttp, baseUrl = "https://reviews.internal")),
        adminPolicy = AdminPolicyModel(allowCollaborationSync = true, allowExternalSharing = true),
    )

    override suspend fun loadState(): EnterpriseAdminStateModel = state
    override suspend fun saveState(state: EnterpriseAdminStateModel) = Unit
    override suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel = state
    override suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel = state
    override suspend fun signOut(): EnterpriseAdminStateModel = state
    override suspend fun refreshRemoteState(force: Boolean): EnterpriseAdminStateModel = state
    override suspend fun refreshSessionIfNeeded(): EnterpriseAdminStateModel = state
    override suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel = EntitlementStateModel(LicensePlan.Enterprise, setOf(FeatureFlag.Collaboration))
    override suspend fun queueTelemetry(event: TelemetryEventModel) = Unit
    override suspend fun pendingTelemetry(): List<TelemetryEventModel> = emptyList()
    override suspend fun flushTelemetry(): Int = 0
    override suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File = destination
}
private class InstrumentedShareLinkDao : ShareLinkDao {
    private val items = linkedMapOf<String, ShareLinkEntity>()
    override suspend fun upsert(entity: ShareLinkEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<ShareLinkEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<ShareLinkEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun deleteForDocument(documentKey: String) { items.entries.removeAll { it.value.documentKey == documentKey } }
    override suspend fun deleteById(id: String) { items.remove(id) }
}

private class InstrumentedReviewThreadDao : ReviewThreadDao {
    private val items = linkedMapOf<String, ReviewThreadEntity>()
    override suspend fun upsert(entity: ReviewThreadEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<ReviewThreadEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<ReviewThreadEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun thread(threadId: String): ReviewThreadEntity? = items[threadId]
    override suspend fun deleteForDocument(documentKey: String) { items.entries.removeAll { it.value.documentKey == documentKey } }
    override suspend fun deleteById(threadId: String) { items.remove(threadId) }
}

private class InstrumentedReviewCommentDao : ReviewCommentDao {
    private val items = mutableListOf<ReviewCommentEntity>()
    override suspend fun upsert(entity: ReviewCommentEntity) { items.removeAll { it.id == entity.id }; items += entity }
    override suspend fun upsertAll(entities: List<ReviewCommentEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forThread(threadId: String): List<ReviewCommentEntity> = items.filter { it.threadId == threadId }.sortedBy { it.createdAtEpochMillis }
    override suspend fun deleteForThread(threadId: String) { items.removeAll { it.threadId == threadId } }
    override suspend fun deleteForThreads(threadIds: List<String>) { items.removeAll { it.threadId in threadIds } }
}

private class InstrumentedVersionSnapshotDao : VersionSnapshotDao {
    private val items = linkedMapOf<String, VersionSnapshotEntity>()
    override suspend fun upsert(entity: VersionSnapshotEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<VersionSnapshotEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<VersionSnapshotEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun deleteForDocument(documentKey: String) { items.entries.removeAll { it.value.documentKey == documentKey } }
}

private class InstrumentedActivityEventDao : ActivityEventDao {
    private val items = linkedMapOf<String, ActivityEventEntity>()
    override suspend fun upsert(entity: ActivityEventEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<ActivityEventEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<ActivityEventEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.createdAtEpochMillis }
    override suspend fun deleteForDocument(documentKey: String) { items.entries.removeAll { it.value.documentKey == documentKey } }
}

private class InstrumentedSyncQueueDao : SyncQueueDao {
    private val items = linkedMapOf<String, SyncQueueEntity>()
    override suspend fun upsert(entity: SyncQueueEntity) { items[entity.id] = entity }
    override suspend fun all(): List<SyncQueueEntity> = items.values.toList()
    override suspend fun forDocument(documentKey: String): List<SyncQueueEntity> = items.values.filter { it.documentKey == documentKey }.sortedBy { it.createdAtEpochMillis }
    override suspend fun eligible(documentKey: String, nowEpochMillis: Long): List<SyncQueueEntity> = eligibleAll(nowEpochMillis).filter { it.documentKey == documentKey }
    override suspend fun eligibleAll(nowEpochMillis: Long): List<SyncQueueEntity> = items.values.filter { it.state in listOf(SyncOperationState.Pending.name, SyncOperationState.Failed.name, SyncOperationState.Conflict.name) && it.nextAttemptAtEpochMillis <= nowEpochMillis }.sortedBy { it.createdAtEpochMillis }
}


