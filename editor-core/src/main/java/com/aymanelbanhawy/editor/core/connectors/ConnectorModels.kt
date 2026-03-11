package com.aymanelbanhawy.editor.core.connectors

import com.aymanelbanhawy.editor.core.enterprise.CloudConnector
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import kotlinx.serialization.Serializable

@Serializable
enum class ConnectorCapability {
    Open,
    Import,
    Save,
    Share,
    MetadataSync,
    BackgroundTransfer,
    ResumableDownload,
    ResumableUpload,
}

@Serializable
enum class ConnectorCredentialType {
    None,
    Basic,
    Bearer,
}

@Serializable
enum class TransferDirection {
    Download,
    Upload,
}

@Serializable
enum class TransferStatus {
    Pending,
    Running,
    Paused,
    Completed,
    Failed,
}

@Serializable
enum class SaveDestinationMode {
    SaveCopy,
    ShareCopy,
}

@Serializable
data class ConnectorAccountModel(
    val id: String,
    val connectorType: CloudConnector,
    val displayName: String,
    val baseUrl: String,
    val credentialType: ConnectorCredentialType = ConnectorCredentialType.None,
    val username: String = "",
    val secretAlias: String? = null,
    val supportsOpen: Boolean = true,
    val supportsSave: Boolean = true,
    val supportsShare: Boolean = true,
    val supportsImport: Boolean = true,
    val supportsMetadataSync: Boolean = true,
    val supportsResumableTransfer: Boolean = false,
    val isEnterpriseManaged: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class ConnectorDescriptor(
    val connectorType: CloudConnector,
    val title: String,
    val supportsConfiguration: Boolean,
    val capabilities: Set<ConnectorCapability>,
)

@Serializable
data class ConnectorFileMetadata(
    val connectorAccountId: String,
    val remotePath: String,
    val displayName: String,
    val versionId: String? = null,
    val modifiedAtEpochMillis: Long? = null,
    val etag: String? = null,
    val checksumSha256: String? = null,
    val sizeBytes: Long? = null,
    val mimeType: String = "application/pdf",
)

@Serializable
data class ConnectorItemModel(
    val path: String,
    val displayName: String,
    val isDirectory: Boolean,
    val metadata: ConnectorFileMetadata? = null,
)

@Serializable
data class ConnectorPolicyDecision(
    val allowed: Boolean,
    val message: String? = null,
)

@Serializable
data class ConnectorSaveRequest(
    val connectorAccountId: String,
    val remotePath: String,
    val displayName: String,
    val exportMode: AnnotationExportMode,
    val destinationMode: SaveDestinationMode,
    val overwrite: Boolean = false,
)

@Serializable
data class ConnectorTransferJobModel(
    val id: String,
    val connectorAccountId: String,
    val documentKey: String,
    val remotePath: String,
    val localCachePath: String,
    val direction: TransferDirection,
    val status: TransferStatus,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val resumableToken: String? = null,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Serializable
data class ConnectorExportResult(
    val metadata: ConnectorFileMetadata,
    val localWorkingPath: String,
    val blockedReason: String? = null,
)
