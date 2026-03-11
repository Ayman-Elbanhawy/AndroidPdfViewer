package com.aymanelbanhawy.editor.core.connectors

import android.content.Context
import android.net.Uri
import com.aymanelbanhawy.editor.core.data.ConnectorAccountDao
import com.aymanelbanhawy.editor.core.data.ConnectorAccountEntity
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobDao
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobEntity
import com.aymanelbanhawy.editor.core.data.RemoteDocumentMetadataDao
import com.aymanelbanhawy.editor.core.data.RemoteDocumentMetadataEntity
import com.aymanelbanhawy.editor.core.enterprise.CloudConnector
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.security.AuditEventType
import com.aymanelbanhawy.editor.core.security.AuditTrailEventModel
import com.aymanelbanhawy.editor.core.security.RestrictedAction
import com.aymanelbanhawy.editor.core.security.SecureFileCipher
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface ConnectorRepository {
    suspend fun descriptors(): List<ConnectorDescriptor>
    suspend fun accounts(): List<ConnectorAccountModel>
    suspend fun saveAccount(draft: ConnectorAccountDraft): ConnectorAccountModel
    suspend fun testConnection(accountId: String): ConnectorPolicyDecision
    suspend fun browse(accountId: String, path: String): List<ConnectorItemModel>
    suspend fun openDocument(accountId: String, remotePath: String, displayName: String): OpenDocumentRequest
    suspend fun queueExport(document: DocumentModel, request: ConnectorSaveRequest): ConnectorTransferJobModel
    suspend fun syncPendingTransfers(): Int
    suspend fun transferJobs(): List<ConnectorTransferJobModel>
    suspend fun allowedDestinations(forShare: Boolean): List<ConnectorAccountModel>
    suspend fun cleanupCache(): Int
}

@Serializable
data class ConnectorAccountDraft(
    val connectorType: CloudConnector,
    val displayName: String,
    val baseUrl: String,
    val credentialType: ConnectorCredentialType = ConnectorCredentialType.None,
    val username: String = "",
    val secret: String = "",
    val isEnterpriseManaged: Boolean = false,
)

private interface StorageConnector {
    val descriptor: ConnectorDescriptor
    suspend fun testConnection(account: ConnectorAccountModel, secret: String?): ConnectorPolicyDecision
    suspend fun list(account: ConnectorAccountModel, secret: String?, path: String): List<ConnectorItemModel>
    suspend fun fetch(account: ConnectorAccountModel, secret: String?, remotePath: String, destination: File, metadata: ConnectorFileMetadata?): ConnectorFileMetadata
    suspend fun push(account: ConnectorAccountModel, secret: String?, source: File, remotePath: String, previousMetadata: ConnectorFileMetadata?): ConnectorFileMetadata
}

class ConnectorCredentialStore(
    context: Context,
    private val cipher: SecureFileCipher,
    private val json: Json,
) {
    private val root = File(context.filesDir, "connector-secrets").apply { mkdirs() }

    suspend fun store(alias: String, secret: String) = withContext(Dispatchers.IO) {
        val file = File(root, alias.sha256() + ".bin")
        cipher.encryptToFile(json.encodeToString(SecretPayload.serializer(), SecretPayload(secret)).toByteArray(StandardCharsets.UTF_8), file)
    }

    suspend fun load(alias: String?): String? = withContext(Dispatchers.IO) {
        if (alias.isNullOrBlank()) return@withContext null
        val file = File(root, alias.sha256() + ".bin")
        if (!file.exists()) return@withContext null
        runCatching {
            json.decodeFromString(SecretPayload.serializer(), cipher.decryptFromFile(file).toString(StandardCharsets.UTF_8)).secret
        }.getOrNull()
    }

    suspend fun clear(alias: String?) = withContext(Dispatchers.IO) {
        if (alias.isNullOrBlank()) return@withContext
        File(root, alias.sha256() + ".bin").delete()
    }

    @Serializable
    private data class SecretPayload(val secret: String)
}

class SecureConnectorCache(
    private val context: Context,
    private val cipher: SecureFileCipher,
) {
    private val encryptedDir = File(context.cacheDir, "connector-cache/encrypted").apply { mkdirs() }
    private val tempDir = File(context.cacheDir, "connector-cache/temp").apply { mkdirs() }

    suspend fun stash(file: File): File = withContext(Dispatchers.IO) {
        val encrypted = File(encryptedDir, UUID.randomUUID().toString() + ".bin")
        cipher.encryptToFile(file.readBytes(), encrypted)
        encrypted
    }

    suspend fun materialize(encryptedFile: File, displayName: String): File = withContext(Dispatchers.IO) {
        val target = File(tempDir, UUID.randomUUID().toString() + "_" + displayName)
        target.parentFile?.mkdirs()
        target.writeBytes(cipher.decryptFromFile(encryptedFile))
        target
    }

    suspend fun clearTemp(file: File?) = withContext(Dispatchers.IO) { file?.delete() }

    suspend fun evictOlderThan(thresholdEpochMillis: Long): Int = withContext(Dispatchers.IO) {
        (encryptedDir.listFiles().orEmpty().toList() + tempDir.listFiles().orEmpty().toList())
            .filter { it.lastModified() < thresholdEpochMillis }
            .count { it.delete() }
    }
}

class DefaultConnectorRepository(
    private val context: Context,
    private val accountDao: ConnectorAccountDao,
    private val remoteDocumentMetadataDao: RemoteDocumentMetadataDao,
    private val transferJobDao: ConnectorTransferJobDao,
    private val documentRepository: DocumentRepository,
    private val enterpriseAdminRepository: EnterpriseAdminRepository,
    private val securityRepository: SecurityRepository,
    private val secureFileCipher: SecureFileCipher,
    private val json: Json,
) : ConnectorRepository {

    private val credentialStore = ConnectorCredentialStore(context, secureFileCipher, json)
    private val secureCache = SecureConnectorCache(context, secureFileCipher)
    private val connectors: Map<CloudConnector, StorageConnector> = listOf(
        LocalFilesConnector(),
        WebDavConnector(),
        DocumentProviderConnector(context),
    ).associateBy { it.descriptor.connectorType }

    override suspend fun descriptors(): List<ConnectorDescriptor> = connectors.values.map { it.descriptor }

    override suspend fun accounts(): List<ConnectorAccountModel> {
        val stored = accountDao.all().map { it.toModel() }
        return if (stored.none { it.connectorType == CloudConnector.LocalFiles }) listOf(defaultLocalAccount()) + stored else stored
    }

    override suspend fun saveAccount(draft: ConnectorAccountDraft): ConnectorAccountModel {
        val policy = enforceDestinationPolicy(draft.connectorType, forShare = false)
        if (!policy.allowed) throw IllegalStateException(policy.message)
        val now = System.currentTimeMillis()
        val secretAlias = draft.secret.takeIf { it.isNotBlank() }?.let { "connector-${UUID.randomUUID()}" }
        if (secretAlias != null) credentialStore.store(secretAlias, draft.secret)
        val model = ConnectorAccountModel(
            id = UUID.randomUUID().toString(),
            connectorType = draft.connectorType,
            displayName = draft.displayName.ifBlank { draft.connectorType.name },
            baseUrl = draft.baseUrl,
            credentialType = draft.credentialType,
            username = draft.username,
            secretAlias = secretAlias,
            supportsOpen = true,
            supportsSave = true,
            supportsShare = true,
            supportsImport = true,
            supportsMetadataSync = true,
            supportsResumableTransfer = draft.connectorType == CloudConnector.WebDav,
            isEnterpriseManaged = draft.isEnterpriseManaged,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        accountDao.upsert(model.toEntity())
        return model
    }

    override suspend fun testConnection(accountId: String): ConnectorPolicyDecision {
        val account = resolveAccount(accountId)
        val policy = enforceDestinationPolicy(account.connectorType, forShare = false)
        if (!policy.allowed) return policy
        return resolveConnector(account).testConnection(account, credentialStore.load(account.secretAlias))
    }

    override suspend fun browse(accountId: String, path: String): List<ConnectorItemModel> {
        val account = resolveAccount(accountId)
        val policy = enforceDestinationPolicy(account.connectorType, forShare = false)
        if (!policy.allowed) throw IllegalStateException(policy.message)
        return resolveConnector(account).list(account, credentialStore.load(account.secretAlias), path)
    }

    override suspend fun openDocument(accountId: String, remotePath: String, displayName: String): OpenDocumentRequest {
        val account = resolveAccount(accountId)
        val policy = enforceDestinationPolicy(account.connectorType, forShare = false)
        if (!policy.allowed) throw IllegalStateException(policy.message)
        val tempFile = File(context.cacheDir, "connector-open/${UUID.randomUUID()}_$displayName")
        tempFile.parentFile?.mkdirs()
        val metadata = remoteDocumentMetadataDao.get(remoteKey(account.id, remotePath))?.toModel()
        val updatedMetadata = resolveConnector(account).fetch(account, credentialStore.load(account.secretAlias), remotePath, tempFile, metadata)
        remoteDocumentMetadataDao.upsert(updatedMetadata.toEntity(remoteKey(account.id, remotePath)))
        securityRepository.recordAudit(audit(remotePath, AuditEventType.ConnectorOpened, "Opened from ${account.displayName}"))
        return OpenDocumentRequest.FromFile(tempFile.absolutePath, displayNameOverride = displayName)
    }

    override suspend fun queueExport(document: DocumentModel, request: ConnectorSaveRequest): ConnectorTransferJobModel {
        val account = resolveAccount(request.connectorAccountId)
        val documentPolicy = enforceDocumentPolicy(document, request.destinationMode)
        if (!documentPolicy.allowed) {
            securityRepository.recordAudit(audit(document.documentRef.sourceKey, AuditEventType.ConnectorBlocked, documentPolicy.message ?: "Blocked by policy"))
            throw IllegalStateException(documentPolicy.message)
        }
        val connectorPolicy = enforceDestinationPolicy(account.connectorType, request.destinationMode == SaveDestinationMode.ShareCopy)
        if (!connectorPolicy.allowed) {
            securityRepository.recordAudit(audit(document.documentRef.sourceKey, AuditEventType.ConnectorBlocked, connectorPolicy.message ?: "Blocked destination"))
            throw IllegalStateException(connectorPolicy.message)
        }
        val tempExport = File(context.cacheDir, "connector-export/${UUID.randomUUID()}_${request.displayName}")
        tempExport.parentFile?.mkdirs()
        val exported = documentRepository.saveAs(document, tempExport, request.exportMode)
        val encrypted = secureCache.stash(File(exported.documentRef.workingCopyPath))
        File(exported.documentRef.workingCopyPath).takeIf { it.absolutePath == tempExport.absolutePath }?.delete()
        val now = System.currentTimeMillis()
        val job = ConnectorTransferJobModel(
            id = UUID.randomUUID().toString(),
            connectorAccountId = account.id,
            documentKey = document.documentRef.sourceKey,
            remotePath = request.remotePath,
            localCachePath = encrypted.absolutePath,
            direction = TransferDirection.Upload,
            status = TransferStatus.Pending,
            bytesTransferred = 0,
            totalBytes = encrypted.length(),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        transferJobDao.upsert(job.toEntity())
        return job
    }

    override suspend fun syncPendingTransfers(): Int {
        val pending = transferJobDao.pending()
        var completed = 0
        for (entity in pending) {
            val job = entity.toModel()
            val account = accountDao.get(job.connectorAccountId)?.toModel() ?: if (job.connectorAccountId == LOCAL_ACCOUNT_ID) defaultLocalAccount() else continue
            val connector = resolveConnector(account)
            val encrypted = File(job.localCachePath)
            val tempFile = secureCache.materialize(encrypted, File(job.remotePath).name.ifBlank { "document.pdf" })
            val metadataKey = remoteKey(account.id, job.remotePath)
            val previousMetadata = remoteDocumentMetadataDao.get(metadataKey)?.toModel()
            try {
                transferJobDao.upsert(job.copy(status = TransferStatus.Running, updatedAtEpochMillis = System.currentTimeMillis()).toEntity())
                val uploadedMetadata = connector.push(account, credentialStore.load(account.secretAlias), tempFile, job.remotePath, previousMetadata)
                remoteDocumentMetadataDao.upsert(uploadedMetadata.toEntity(metadataKey))
                transferJobDao.upsert(job.copy(status = TransferStatus.Completed, bytesTransferred = tempFile.length(), totalBytes = tempFile.length(), updatedAtEpochMillis = System.currentTimeMillis()).toEntity())
                securityRepository.recordAudit(audit(job.documentKey, if (account.connectorType == CloudConnector.LocalFiles) AuditEventType.ConnectorSaved else AuditEventType.ConnectorShared, "Transferred to ${account.displayName}", mapOf("remotePath" to job.remotePath)))
                completed += 1
            } catch (error: Throwable) {
                transferJobDao.upsert(job.copy(status = TransferStatus.Failed, attemptCount = job.attemptCount + 1, lastError = error.message, updatedAtEpochMillis = System.currentTimeMillis()).toEntity())
            } finally {
                secureCache.clearTemp(tempFile)
            }
        }
        return completed
    }

    override suspend fun transferJobs(): List<ConnectorTransferJobModel> = transferJobDao.all().map { it.toModel() }

    override suspend fun allowedDestinations(forShare: Boolean): List<ConnectorAccountModel> {
        return accounts().filter { enforceDestinationPolicy(it.connectorType, forShare).allowed }
    }

    override suspend fun cleanupCache(): Int {
        transferJobDao.deleteCompletedBefore(System.currentTimeMillis() - 86_400_000L)
        return secureCache.evictOlderThan(System.currentTimeMillis() - 86_400_000L)
    }

    private suspend fun resolveAccount(accountId: String): ConnectorAccountModel {
        return if (accountId == LOCAL_ACCOUNT_ID) defaultLocalAccount() else requireNotNull(accountDao.get(accountId)?.toModel())
    }

    private fun resolveConnector(account: ConnectorAccountModel): StorageConnector = requireNotNull(connectors[account.connectorType])

    private suspend fun enforceDestinationPolicy(connector: CloudConnector, forShare: Boolean): ConnectorPolicyDecision {
        val enterprise = enterpriseAdminRepository.loadState()
        if (connector !in enterprise.adminPolicy.allowedCloudConnectors && connector != CloudConnector.LocalFiles) {
            return ConnectorPolicyDecision(false, "$connector is not allowed by tenant policy.")
        }
        if (forShare && !enterprise.adminPolicy.allowExternalSharing && connector !in setOf(CloudConnector.LocalFiles, CloudConnector.DocumentProvider)) {
            return ConnectorPolicyDecision(false, "External sharing is blocked by tenant policy.")
        }
        return ConnectorPolicyDecision(true)
    }

    private fun enforceDocumentPolicy(document: DocumentModel, mode: SaveDestinationMode): ConnectorPolicyDecision {
        val restrictedAction = if (mode == SaveDestinationMode.ShareCopy) RestrictedAction.Share else RestrictedAction.Export
        val decision = securityRepository.evaluatePolicy(document.security, restrictedAction)
        return ConnectorPolicyDecision(decision.allowed, decision.message)
    }

    private fun audit(documentKey: String, type: AuditEventType, message: String, metadata: Map<String, String> = emptyMap()): AuditTrailEventModel {
        return AuditTrailEventModel(UUID.randomUUID().toString(), documentKey, type, "local-user", message, System.currentTimeMillis(), metadata)
    }

    private fun remoteKey(accountId: String, path: String): String = "$accountId::$path"

    private fun defaultLocalAccount(): ConnectorAccountModel {
        val now = System.currentTimeMillis()
        return ConnectorAccountModel(LOCAL_ACCOUNT_ID, CloudConnector.LocalFiles, "Local Files", context.filesDir.parentFile?.absolutePath ?: context.filesDir.absolutePath, supportsResumableTransfer = true, createdAtEpochMillis = now, updatedAtEpochMillis = now)
    }

    companion object {
        private const val LOCAL_ACCOUNT_ID = "local-files"
    }
}

private class LocalFilesConnector : StorageConnector {
    override val descriptor = ConnectorDescriptor(CloudConnector.LocalFiles, "Local Files", false, setOf(ConnectorCapability.Open, ConnectorCapability.Import, ConnectorCapability.Save, ConnectorCapability.Share, ConnectorCapability.MetadataSync, ConnectorCapability.BackgroundTransfer, ConnectorCapability.ResumableDownload, ConnectorCapability.ResumableUpload))
    override suspend fun testConnection(account: ConnectorAccountModel, secret: String?) = withContext(Dispatchers.IO) { val root = File(account.baseUrl.ifBlank { "." }); ConnectorPolicyDecision(root.exists() || root.mkdirs(), if (root.exists() || root.mkdirs()) null else "Cannot access local path.") }
    override suspend fun list(account: ConnectorAccountModel, secret: String?, path: String) = withContext(Dispatchers.IO) { File(if (path.isBlank()) account.baseUrl else path).listFiles().orEmpty().sortedBy { it.name.lowercase() }.map { ConnectorItemModel(it.absolutePath, it.name, it.isDirectory, if (it.isFile) it.toMetadata(account.id) else null) } }
    override suspend fun fetch(account: ConnectorAccountModel, secret: String?, remotePath: String, destination: File, metadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) { val source = File(remotePath); source.copyTo(destination, overwrite = true); source.toMetadata(account.id) }
    override suspend fun push(account: ConnectorAccountModel, secret: String?, source: File, remotePath: String, previousMetadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) { val target = File(remotePath); target.parentFile?.mkdirs(); if (target.exists() && previousMetadata?.etag != null && target.toMetadata(account.id).etag != previousMetadata.etag) throw IllegalStateException("Local destination changed since last sync."); source.copyTo(target, overwrite = true); target.toMetadata(account.id) }
}

private class WebDavConnector : StorageConnector {
    override val descriptor = ConnectorDescriptor(CloudConnector.WebDav, "WebDAV", true, setOf(ConnectorCapability.Open, ConnectorCapability.Import, ConnectorCapability.Save, ConnectorCapability.Share, ConnectorCapability.MetadataSync, ConnectorCapability.BackgroundTransfer, ConnectorCapability.ResumableDownload))
    override suspend fun testConnection(account: ConnectorAccountModel, secret: String?) = withContext(Dispatchers.IO) { runCatching { openConnection(account, secret, account.baseUrl, "OPTIONS").responseCode }.fold({ ConnectorPolicyDecision(it in 200..299 || it == 405, if (it in 200..299 || it == 405) null else "HTTP $it") }, { ConnectorPolicyDecision(false, it.message) }) }
    override suspend fun list(account: ConnectorAccountModel, secret: String?, path: String) = withContext(Dispatchers.IO) {
        val connection = openConnection(account, secret, buildUrl(account.baseUrl, path), "PROPFIND")
        connection.setRequestProperty("Depth", "1")
        connection.doOutput = true
        connection.outputStream.use { it.write(ByteArray(0)) }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        Regex("<d:href>(.*?)</d:href>").findAll(body).mapNotNull { match ->
            val href = java.net.URLDecoder.decode(match.groupValues[1], StandardCharsets.UTF_8.name())
            if (href == path || href == "/") null else ConnectorItemModel(href, href.substringAfterLast('/').ifBlank { href }, href.endsWith('/'))
        }.toList()
    }
    override suspend fun fetch(account: ConnectorAccountModel, secret: String?, remotePath: String, destination: File, metadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) {
        val connection = openConnection(account, secret, buildUrl(account.baseUrl, remotePath), "GET")
        val existingBytes = destination.takeIf { it.exists() }?.length() ?: 0L
        if (existingBytes > 0) connection.setRequestProperty("Range", "bytes=$existingBytes-")
        val append = connection.responseCode == HttpURLConnection.HTTP_PARTIAL
        destination.parentFile?.mkdirs()
        if (append) {
            RandomAccessFile(destination, "rw").use { raf ->
                raf.seek(existingBytes)
                connection.inputStream.use { it.copyTo(object : java.io.OutputStream() { override fun write(b: Int) { raf.write(b) }; override fun write(b: ByteArray, off: Int, len: Int) { raf.write(b, off, len) } }) }
            }
        } else {
            destination.outputStream().use { output -> connection.inputStream.use { it.copyTo(output) } }
        }
        metadataFromHeaders(account.id, remotePath, connection, destination.length())
    }
    override suspend fun push(account: ConnectorAccountModel, secret: String?, source: File, remotePath: String, previousMetadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) {
        val head = runCatching { headMetadata(account, secret, remotePath) }.getOrNull()
        if (head?.etag != null && previousMetadata?.etag != null && head.etag != previousMetadata.etag) throw IllegalStateException("Remote destination changed since last sync.")
        val connection = openConnection(account, secret, buildUrl(account.baseUrl, remotePath), "PUT")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/pdf")
        source.inputStream().use { input -> connection.outputStream.use { output -> input.copyTo(output) } }
        val code = connection.responseCode
        if (code !in 200..299 && code !in listOf(201, 204)) throw IllegalStateException("WebDAV upload failed with HTTP $code")
        headMetadata(account, secret, remotePath) ?: metadataFromFileFallback(account.id, remotePath, source)
    }
    private fun headMetadata(account: ConnectorAccountModel, secret: String?, remotePath: String): ConnectorFileMetadata? { val connection = openConnection(account, secret, buildUrl(account.baseUrl, remotePath), "HEAD"); return if (connection.responseCode in 200..299) metadataFromHeaders(account.id, remotePath, connection, connection.getHeaderFieldLong("Content-Length", -1).takeIf { it >= 0 }) else null }
    private fun openConnection(account: ConnectorAccountModel, secret: String?, url: String, method: String): HttpURLConnection { return (URL(url).openConnection() as HttpURLConnection).apply { requestMethod = method; connectTimeout = 15_000; readTimeout = 45_000; setRequestProperty("Accept", "application/json, text/xml, application/xml, */*"); when (account.credentialType) { ConnectorCredentialType.Basic -> if (!secret.isNullOrBlank()) { val token = java.util.Base64.getEncoder().encodeToString("${account.username}:$secret".toByteArray(StandardCharsets.UTF_8)); setRequestProperty("Authorization", "Basic $token") }; ConnectorCredentialType.Bearer -> if (!secret.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $secret"); ConnectorCredentialType.None -> Unit } } }
    private fun buildUrl(baseUrl: String, remotePath: String): String = baseUrl.trimEnd('/') + "/" + remotePath.trimStart('/').replace(" ", "%20")
}

private class DocumentProviderConnector(private val context: Context) : StorageConnector {
    override val descriptor = ConnectorDescriptor(CloudConnector.DocumentProvider, "Enterprise Document Provider", true, setOf(ConnectorCapability.Open, ConnectorCapability.Import, ConnectorCapability.Save, ConnectorCapability.Share, ConnectorCapability.MetadataSync))
    override suspend fun testConnection(account: ConnectorAccountModel, secret: String?) = withContext(Dispatchers.IO) { ConnectorPolicyDecision(context.contentResolver.persistedUriPermissions.any { it.uri.toString() == account.baseUrl }, "Persisted provider permission not granted.") }
    override suspend fun list(account: ConnectorAccountModel, secret: String?, path: String): List<ConnectorItemModel> = emptyList()
    override suspend fun fetch(account: ConnectorAccountModel, secret: String?, remotePath: String, destination: File, metadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) { val uri = Uri.parse(remotePath); destination.parentFile?.mkdirs(); context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { input.copyTo(it) } } ?: throw IllegalStateException("Unable to open document provider stream."); metadata ?: metadataFromFileFallback(account.id, remotePath, destination) }
    override suspend fun push(account: ConnectorAccountModel, secret: String?, source: File, remotePath: String, previousMetadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) { val uri = Uri.parse(remotePath); context.contentResolver.openOutputStream(uri, "wt")?.use { output -> source.inputStream().use { it.copyTo(output) } } ?: throw IllegalStateException("Unable to open document provider output stream."); metadataFromFileFallback(account.id, remotePath, source) }
}

private fun ConnectorAccountEntity.toModel() = ConnectorAccountModel(id, CloudConnector.valueOf(connectorType), displayName, baseUrl, ConnectorCredentialType.valueOf(credentialType), username, secretAlias, supportsOpen, supportsSave, supportsShare, supportsImport, supportsMetadataSync, supportsResumableTransfer, isEnterpriseManaged, createdAtEpochMillis, updatedAtEpochMillis)
private fun ConnectorAccountModel.toEntity() = ConnectorAccountEntity(id, connectorType.name, displayName, baseUrl, credentialType.name, username, secretAlias, supportsOpen, supportsSave, supportsShare, supportsImport, supportsMetadataSync, supportsResumableTransfer, isEnterpriseManaged, createdAtEpochMillis, updatedAtEpochMillis)
private fun ConnectorTransferJobEntity.toModel() = ConnectorTransferJobModel(id, connectorAccountId, documentKey, remotePath, localCachePath, TransferDirection.valueOf(direction), TransferStatus.valueOf(status), bytesTransferred, totalBytes, resumableToken, attemptCount, lastError, createdAtEpochMillis, updatedAtEpochMillis)
private fun ConnectorTransferJobModel.toEntity() = ConnectorTransferJobEntity(id, connectorAccountId, documentKey, remotePath, localCachePath, direction.name, status.name, bytesTransferred, totalBytes, resumableToken, attemptCount, lastError, createdAtEpochMillis, updatedAtEpochMillis)
private fun RemoteDocumentMetadataEntity.toModel() = ConnectorFileMetadata(connectorAccountId, remotePath, displayName, versionId, modifiedAtEpochMillis, etag, checksumSha256, sizeBytes, mimeType)
private fun ConnectorFileMetadata.toEntity(documentKey: String) = RemoteDocumentMetadataEntity(documentKey, connectorAccountId, remotePath, displayName, versionId, modifiedAtEpochMillis, etag, checksumSha256, sizeBytes, mimeType, System.currentTimeMillis())
private fun File.toMetadata(accountId: String) = ConnectorFileMetadata(accountId, absolutePath, name, lastModified().toString(), lastModified(), "${length()}-${lastModified()}", sha256(readBytes()), length())
private fun metadataFromHeaders(accountId: String, remotePath: String, connection: HttpURLConnection, size: Long?) = ConnectorFileMetadata(accountId, remotePath, remotePath.substringAfterLast('/').ifBlank { remotePath }, connection.getHeaderField("x-version-id"), connection.getHeaderFieldDate("Last-Modified", -1).takeIf { it >= 0 }, connection.getHeaderField("ETag")?.trim('"'), connection.getHeaderField("X-Checksum-Sha256"), size, connection.contentType ?: "application/pdf")
private fun metadataFromFileFallback(accountId: String, remotePath: String, file: File) = ConnectorFileMetadata(accountId, remotePath, remotePath.substringAfterLast('/').ifBlank { file.name }, file.lastModified().toString(), file.lastModified(), "${file.length()}-${file.lastModified()}", sha256(file.readBytes()), file.length())
private fun String.sha256(): String = sha256(toByteArray(StandardCharsets.UTF_8))
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

