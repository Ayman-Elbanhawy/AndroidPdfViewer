package com.aymanelbanhawy.editor.core.runtime

import kotlinx.serialization.Serializable

@Serializable
enum class RuntimeLogLevel {
    Debug,
    Info,
    Warn,
    Error,
}

@Serializable
enum class RuntimeEventCategory {
    Startup,
    DocumentOpen,
    Save,
    Indexing,
    Ocr,
    Sync,
    Recovery,
    Cache,
    Provider,
    Failure,
}

@Serializable
data class RuntimeBreadcrumbModel(
    val id: String,
    val category: RuntimeEventCategory,
    val level: RuntimeLogLevel,
    val eventName: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap(),
    val createdAtEpochMillis: Long,
)

data class CacheDiagnosticsModel(
    val thumbnailFileCount: Int = 0,
    val thumbnailBytes: Long = 0,
    val connectorCacheFileCount: Int = 0,
    val connectorCacheBytes: Long = 0,
    val exportCacheFileCount: Int = 0,
    val exportCacheBytes: Long = 0,
)

data class QueueDiagnosticsModel(
    val pendingOcrJobs: Int = 0,
    val runningOcrJobs: Int = 0,
    val failedOcrJobs: Int = 0,
    val pendingSyncOperations: Int = 0,
    val connectorTransferJobs: Int = 0,
)

data class ProviderHealthModel(
    val name: String,
    val status: String,
    val detail: String,
)

data class RuntimeDiagnosticsSnapshot(
    val startupElapsedMillis: Long = 0,
    val lastDocumentOpenElapsedMillis: Long = 0,
    val lastSaveElapsedMillis: Long = 0,
    val cache: CacheDiagnosticsModel = CacheDiagnosticsModel(),
    val queues: QueueDiagnosticsModel = QueueDiagnosticsModel(),
    val providerHealth: List<ProviderHealthModel> = emptyList(),
    val recentBreadcrumbs: List<RuntimeBreadcrumbModel> = emptyList(),
    val recentFailures: List<RuntimeBreadcrumbModel> = emptyList(),
)

data class StartupRepairResult(
    val repairedDraftCount: Int = 0,
    val recoveredSaveCount: Int = 0,
    val resumedOcrCount: Int = 0,
    val resumedSyncCount: Int = 0,
    val quarantinedSidecarCount: Int = 0,
)
