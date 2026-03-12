package com.aymanelbanhawy.editor.core.runtime

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.aymanelbanhawy.editor.core.data.ConnectorAccountDao
import com.aymanelbanhawy.editor.core.data.ConnectorAccountEntity
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobDao
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobEntity
import com.aymanelbanhawy.editor.core.data.DraftDao
import com.aymanelbanhawy.editor.core.data.DraftEntity
import com.aymanelbanhawy.editor.core.data.OcrJobDao
import com.aymanelbanhawy.editor.core.data.OcrJobEntity
import com.aymanelbanhawy.editor.core.data.RuntimeBreadcrumbDao
import com.aymanelbanhawy.editor.core.data.RuntimeBreadcrumbEntity
import com.aymanelbanhawy.editor.core.data.SyncQueueDao
import com.aymanelbanhawy.editor.core.data.SyncQueueEntity
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class DefaultRuntimeDiagnosticsRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }

    @Test
    fun runStartupRepair_recoversTempAndQuarantinesCorruptDraft() = runTest {
        val root = File("build/runtime-test/${UUID.randomUUID()}").apply { mkdirs() }
        val context = TestContext(root)
        val draftsDir = File(context.filesDir, "drafts").apply { mkdirs() }
        val workingDir = File(context.filesDir, "working-documents").apply { mkdirs() }
        File(draftsDir, "broken.json").writeText("not-json")
        File(workingDir, "contract.pdf.tmp").writeText("pdf-bytes")
        File(workingDir, "contract.pdf.saving.lock").writeText("locked")

        val repository = DefaultRuntimeDiagnosticsRepository(
            context = context,
            breadcrumbDao = TestRuntimeBreadcrumbDao(),
            draftDao = TestDraftDao(),
            ocrJobDao = TestOcrJobDao(),
            syncQueueDao = TestSyncQueueDao(),
            connectorTransferJobDao = TestConnectorTransferJobDao(),
            connectorAccountDao = TestConnectorAccountDao(),
            json = json,
        )

        val repair = repository.runStartupRepair()

        assertThat(repair.repairedDraftCount).isEqualTo(1)
        assertThat(repair.recoveredSaveCount).isAtLeast(1)
        assertThat(File(workingDir, "contract.pdf").exists()).isTrue()
        assertThat(File(draftsDir, "broken.json.corrupt").exists()).isTrue()
    }
}

private class TestContext(root: File) : ContextWrapper(Application()) {
    private val cache = File(root, "cache").apply { mkdirs() }
    private val files = File(root, "files").apply { mkdirs() }
    override fun getCacheDir(): File = cache
    override fun getFilesDir(): File = files
    override fun getApplicationContext(): Context = this
}

private class TestRuntimeBreadcrumbDao : RuntimeBreadcrumbDao {
    private val items = mutableListOf<RuntimeBreadcrumbEntity>()
    override suspend fun upsert(entity: RuntimeBreadcrumbEntity) { items.removeAll { it.id == entity.id }; items += entity }
    override suspend fun recent(limit: Int): List<RuntimeBreadcrumbEntity> = items.sortedByDescending { it.createdAtEpochMillis }.take(limit)
    override suspend fun recentFailures(limit: Int): List<RuntimeBreadcrumbEntity> = items.filter { it.level == "Error" || it.category == "Failure" }.take(limit)
    override suspend fun trimOlderThan(thresholdEpochMillis: Long) { items.removeAll { it.createdAtEpochMillis < thresholdEpochMillis } }
}

private class TestDraftDao : DraftDao {
    override suspend fun upsert(entity: DraftEntity) = Unit
    override suspend fun getLatestForSource(sourceKey: String): DraftEntity? = null
    override suspend fun deleteBySource(sourceKey: String) = Unit
    override suspend fun deleteBySession(sessionId: String) = Unit
}

private class TestOcrJobDao : OcrJobDao {
    override suspend fun upsert(entity: OcrJobEntity) = Unit
    override suspend fun upsertAll(entities: List<OcrJobEntity>) = Unit
    override suspend fun job(id: String): OcrJobEntity? = null
    override suspend fun all(): List<OcrJobEntity> = emptyList()
    override suspend fun jobsForDocument(documentKey: String): List<OcrJobEntity> = emptyList()
    override fun observeJobsForDocument(documentKey: String): Flow<List<OcrJobEntity>> = flowOf(emptyList())
    override suspend fun jobForPage(documentKey: String, pageIndex: Int): OcrJobEntity? = null
    override suspend fun pendingOrResumable(documentKey: String, staleBeforeEpochMillis: Long, limit: Int): List<OcrJobEntity> = emptyList()
}

private class TestSyncQueueDao : SyncQueueDao {
    override suspend fun upsert(entity: SyncQueueEntity) = Unit
    override suspend fun all(): List<SyncQueueEntity> = emptyList()
    override suspend fun forDocument(documentKey: String): List<SyncQueueEntity> = emptyList()
    override suspend fun eligible(documentKey: String, nowEpochMillis: Long): List<SyncQueueEntity> = emptyList()
    override suspend fun eligibleAll(nowEpochMillis: Long): List<SyncQueueEntity> = emptyList()
}

private class TestConnectorTransferJobDao : ConnectorTransferJobDao {
    override suspend fun upsert(entity: ConnectorTransferJobEntity) = Unit
    override suspend fun get(id: String): ConnectorTransferJobEntity? = null
    override suspend fun pending(): List<ConnectorTransferJobEntity> = emptyList()
    override suspend fun all(): List<ConnectorTransferJobEntity> = emptyList()
    override suspend fun deleteById(id: String) = Unit
    override suspend fun deleteCompletedBefore(thresholdEpochMillis: Long) = Unit
}

private class TestConnectorAccountDao : ConnectorAccountDao {
    override suspend fun upsert(entity: ConnectorAccountEntity) = Unit
    override suspend fun all(): List<ConnectorAccountEntity> = emptyList()
    override suspend fun get(id: String): ConnectorAccountEntity? = null
    override suspend fun deleteById(id: String) = Unit
}


