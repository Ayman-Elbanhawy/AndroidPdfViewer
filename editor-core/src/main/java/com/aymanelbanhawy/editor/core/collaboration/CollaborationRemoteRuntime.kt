package com.aymanelbanhawy.editor.core.collaboration

import android.content.Context
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationMode
import com.aymanelbanhawy.editor.core.enterprise.CollaborationBackendMode
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.security.AndroidSecureFileCipher
import com.aymanelbanhawy.editor.core.security.SecureFileCipher
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class CollaborationCredentialStore(
    context: Context,
    private val json: Json,
    private val cipher: SecureFileCipher = AndroidSecureFileCipher(context.applicationContext, "enterprise_pdf_collaboration_tokens"),
) {
    private val root = File(context.applicationContext.filesDir, "secure-collaboration").apply { mkdirs() }

    suspend fun store(alias: String, token: String) = withContext(Dispatchers.IO) {
        cipher.encryptToFile(
            json.encodeToString(CredentialPayload.serializer(), CredentialPayload(token)).toByteArray(StandardCharsets.UTF_8),
            tokenFile(alias),
        )
    }

    suspend fun load(alias: String?): String? = withContext(Dispatchers.IO) {
        if (alias.isNullOrBlank()) return@withContext null
        val file = tokenFile(alias)
        if (!file.exists()) return@withContext null
        runCatching {
            val payload = cipher.decryptFromFile(file).toString(StandardCharsets.UTF_8)
            json.decodeFromString(CredentialPayload.serializer(), payload).token
        }.getOrNull()
    }

    suspend fun clear(alias: String?) = withContext(Dispatchers.IO) {
        if (alias.isNullOrBlank()) return@withContext
        tokenFile(alias).delete()
    }

    private fun tokenFile(alias: String): File = File(root, sha256(alias) + ".bin")

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    @Serializable
    private data class CredentialPayload(val token: String)
}

open class CollaborationRemoteRegistry(
    private val context: Context,
    private val enterpriseAdminRepository: EnterpriseAdminRepository,
    private val credentialStore: CollaborationCredentialStore,
    private val json: Json,
) {
    open suspend fun select(): CollaborationRemoteDataSource {
        val state = enterpriseAdminRepository.loadState()
        enforcePolicy(state)
        return when (state.tenantConfiguration.collaboration.backendMode) {
            CollaborationBackendMode.Disabled -> throw disabledError()
            CollaborationBackendMode.LocalEmulator -> LocalEmulatorCollaborationRemoteDataSource(
                rootDir = File(context.filesDir, "collaboration-emulator"),
                json = json,
            )
            CollaborationBackendMode.RemoteHttp -> HttpCollaborationRemoteDataSource(
                json = json,
                baseUrl = state.tenantConfiguration.collaboration.baseUrl,
                apiPath = state.tenantConfiguration.collaboration.apiPath,
                accessTokenProvider = {
                    val authSession = state.authSession
                    if (state.tenantConfiguration.collaboration.requireEnterpriseAuth && !authSession.isSignedIn) {
                        throw CollaborationRemoteException(
                            RemoteErrorMetadata(
                                code = RemoteErrorCode.Unauthorized,
                                message = "Enterprise sign-in is required for collaboration sync.",
                                retryable = false,
                            ),
                        )
                    }
                    credentialStore.load(authSession.collaborationCredentialAlias)
                },
                connectTimeoutMillis = state.tenantConfiguration.collaboration.connectTimeoutMillis.toInt(),
                readTimeoutMillis = state.tenantConfiguration.collaboration.readTimeoutMillis.toInt(),
                requestTimeoutMillis = state.tenantConfiguration.collaboration.requestTimeoutMillis,
                retryCount = state.tenantConfiguration.collaboration.retryCount,
            )
        }
    }

    private fun enforcePolicy(state: EnterpriseAdminStateModel) {
        if (!state.adminPolicy.allowCollaborationSync) {
            throw disabledError()
        }
        if (state.tenantConfiguration.collaboration.backendMode == CollaborationBackendMode.RemoteHttp) {
            if (!state.adminPolicy.allowExternalSharing && state.adminPolicy.collaborationScope.name == "ExternalGuests") {
                throw CollaborationRemoteException(
                    RemoteErrorMetadata(
                        code = RemoteErrorCode.Forbidden,
                        message = "Tenant policy blocks external collaboration.",
                        retryable = false,
                    ),
                )
            }
            if (state.authSession.mode == AuthenticationMode.Personal && state.tenantConfiguration.collaboration.requireEnterpriseAuth) {
                throw CollaborationRemoteException(
                    RemoteErrorMetadata(
                        code = RemoteErrorCode.Forbidden,
                        message = "Enterprise collaboration requires enterprise mode.",
                        retryable = false,
                    ),
                )
            }
        }
    }

    private fun disabledError(): CollaborationRemoteException {
        return CollaborationRemoteException(
            RemoteErrorMetadata(
                code = RemoteErrorCode.Forbidden,
                message = "Collaboration sync is disabled by policy.",
                retryable = false,
            ),
        )
    }
}

class HttpCollaborationRemoteDataSource(
    private val json: Json,
    private val baseUrl: String,
    private val apiPath: String,
    private val accessTokenProvider: suspend () -> String?,
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val requestTimeoutMillis: Long,
    private val retryCount: Int,
) : CollaborationRemoteDataSource {

    override suspend fun healthCheck(): RemoteServiceHealth {
        val response = execute("GET", buildUrl("health"), null, null)
        return parseJsonResponse(response, RemoteServiceHealth.serializer())
    }

    override suspend fun pull(request: CollaborationPullRequest): CollaborationRemoteSnapshot {
        val query = linkedMapOf(
            "documentKey" to request.documentKey,
            "pageSize" to request.pageSize.toString(),
            "shareLinksPageToken" to request.shareLinksPageToken,
            "reviewThreadsPageToken" to request.reviewThreadsPageToken,
            "activityPageToken" to request.activityPageToken,
            "snapshotsPageToken" to request.snapshotsPageToken,
        ).mapNotNull { (key, value) -> value?.takeIf { it.isNotBlank() }?.let { key to it } }.toMap()
        val response = execute("GET", buildUrl("documents/${urlEncode(request.documentKey)}/artifacts", query), null, null)
        return parseJsonResponse(response, CollaborationRemoteSnapshot.serializer())
    }

    override suspend fun push(request: RemoteMutationRequest): RemoteMutationResult {
        val body = json.encodeToString(RemoteMutationRequest.serializer(), request)
        val response = execute(
            method = "POST",
            url = buildUrl("mutations"),
            body = body,
            extraHeaders = mapOf("Idempotency-Key" to request.idempotencyKey),
        )
        if (response.code == HttpURLConnection.HTTP_CONFLICT) {
            throw CollaborationRemoteException(parseError(response))
        }
        return parseJsonResponse(response, RemoteMutationResult.serializer())
    }

    private suspend fun execute(method: String, url: String, body: String?, extraHeaders: Map<String, String>?): HttpResponse {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt <= retryCount) {
            try {
                return withTimeout(requestTimeoutMillis) {
                    withContext(Dispatchers.IO) {
                        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                            requestMethod = method
                            connectTimeout = connectTimeoutMillis
                            readTimeout = readTimeoutMillis
                            doInput = true
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Content-Type", "application/json")
                            accessTokenProvider()?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
                            extraHeaders?.forEach { (key, value) -> setRequestProperty(key, value) }
                            if (body != null) {
                                doOutput = true
                                outputStream.use { output ->
                                    output.write(body.toByteArray(StandardCharsets.UTF_8))
                                }
                            }
                        }
                        val code = connection.responseCode
                        val responseBody = runCatching {
                            (if (code >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }
                        }.getOrNull()
                        HttpResponse(code = code, body = responseBody, headers = connection.headerFields.filterKeys { it != null })
                    }
                }.also { response ->
                    if (!response.isRetryable()) return response
                    lastError = CollaborationRemoteException(parseError(response))
                }
            } catch (throwable: Throwable) {
                lastError = throwable
                if (!throwable.isRetryableNetwork() || attempt >= retryCount) break
            }
            attempt += 1
            withContext(Dispatchers.IO) { Thread.sleep((1000L shl attempt.coerceAtMost(4)).coerceAtMost(8_000L)) }
        }
        throw when (val error = lastError) {
            is CollaborationRemoteException -> error
            is SocketTimeoutException -> CollaborationRemoteException(
                RemoteErrorMetadata(RemoteErrorCode.Timeout, "Collaboration request timed out.", retryable = true),
            )
            else -> CollaborationRemoteException(
                RemoteErrorMetadata(RemoteErrorCode.Offline, error?.message ?: "Unable to reach collaboration service.", retryable = true),
            )
        }
    }

    private fun buildUrl(path: String, query: Map<String, String> = emptyMap()): String {
        val trimmedBase = baseUrl.trimEnd('/')
        val trimmedPath = apiPath.trim('/').takeIf { it.isNotBlank() }?.let { "/$it" } ?: ""
        val suffix = "/" + path.trimStart('/')
        val queryString = if (query.isEmpty()) "" else query.entries.joinToString(prefix = "?", separator = "&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
        return "$trimmedBase$trimmedPath$suffix$queryString"
    }

    private fun parseError(response: HttpResponse): RemoteErrorMetadata {
        return runCatching {
            if (!response.body.isNullOrBlank()) json.decodeFromString(RemoteErrorMetadata.serializer(), response.body)
            else defaultError(response.code, null)
        }.getOrElse {
            defaultError(response.code, response.body)
        }
    }

    private fun <T> parseJsonResponse(response: HttpResponse, serializer: kotlinx.serialization.KSerializer<T>): T {
        if (response.code !in 200..299) {
            throw CollaborationRemoteException(parseError(response))
        }
        val body = response.body ?: throw CollaborationRemoteException(defaultError(response.code, "Empty response body"))
        return json.decodeFromString(serializer, body)
    }

    private fun defaultError(code: Int, body: String?): RemoteErrorMetadata {
        val mappedCode = when (code) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> RemoteErrorCode.Unauthorized
            HttpURLConnection.HTTP_FORBIDDEN -> RemoteErrorCode.Forbidden
            HttpURLConnection.HTTP_CONFLICT -> RemoteErrorCode.Conflict
            408 -> RemoteErrorCode.Timeout
            429 -> RemoteErrorCode.RateLimited
            in 400..499 -> RemoteErrorCode.InvalidRequest
            in 500..599 -> RemoteErrorCode.ServerError
            else -> RemoteErrorCode.Unknown
        }
        return RemoteErrorMetadata(
            code = mappedCode,
            message = body?.takeIf { it.isNotBlank() } ?: "Collaboration service returned HTTP $code",
            retryable = code == 408 || code == 429 || code >= 500,
            httpStatus = code,
        )
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class HttpResponse(
        val code: Int,
        val body: String?,
        val headers: Map<String, List<String>>,
    ) {
        fun isRetryable(): Boolean = code == 408 || code == 429 || code >= 500
    }
}

class LocalEmulatorCollaborationRemoteDataSource(
    private val rootDir: File,
    private val json: Json,
) : CollaborationRemoteDataSource {

    private val stateFile = File(rootDir, "collaboration-state.json")

    init {
        rootDir.mkdirs()
    }

    override suspend fun healthCheck(): RemoteServiceHealth = withContext(Dispatchers.IO) {
        RemoteServiceHealth(
            isHealthy = true,
            serviceName = "Local Collaboration Emulator",
            serverTimestampEpochMillis = System.currentTimeMillis(),
            supportsPagination = true,
            supportsIdempotency = true,
        )
    }

    override suspend fun pull(request: CollaborationPullRequest): CollaborationRemoteSnapshot = withContext(Dispatchers.IO) {
        val state = readState()
        CollaborationRemoteSnapshot(
            shareLinks = paginate(
                state.shareLinks.filter { it.documentKey == request.documentKey }.sortedByDescending { it.createdAtEpochMillis },
                request.shareLinksPageToken,
                request.pageSize,
            ),
            reviewThreads = paginate(
                state.reviewThreads.filter { it.documentKey == request.documentKey }.sortedByDescending { it.modifiedAtEpochMillis },
                request.reviewThreadsPageToken,
                request.pageSize,
            ),
            activityEvents = paginate(
                state.activityEvents.filter { it.documentKey == request.documentKey }.sortedByDescending { it.createdAtEpochMillis },
                request.activityPageToken,
                request.pageSize,
            ),
            versionSnapshots = paginate(
                state.versionSnapshots.filter { it.documentKey == request.documentKey }.sortedByDescending { it.createdAtEpochMillis },
                request.snapshotsPageToken,
                request.pageSize,
            ),
        )
    }

    override suspend fun push(request: RemoteMutationRequest): RemoteMutationResult = withContext(Dispatchers.IO) {
        val currentState = readState()
        val now = System.currentTimeMillis()
        val result = when (request.payload.artifactType) {
            CollaborationArtifactType.ShareLink -> mutateShareLink(currentState, request, now)
            CollaborationArtifactType.ReviewThread -> mutateThread(currentState, request, now)
            CollaborationArtifactType.ActivityEvent -> mutateActivity(currentState, request, now)
            CollaborationArtifactType.VersionSnapshot -> mutateSnapshot(currentState, request, now)
        }
        writeState(currentState)
        result
    }

    private fun mutateShareLink(state: EmulatorState, request: RemoteMutationRequest, now: Long): RemoteMutationResult {
        val incoming = request.payload.currentJson?.let { json.decodeFromString(ShareLinkModel.serializer(), it) }
        val current = state.shareLinks.firstOrNull { it.id == request.payload.entityId }
        current?.remoteVersion?.let { remoteVersion ->
            if (request.payload.baseRemoteVersion != null && request.payload.baseRemoteVersion != remoteVersion) {
                throw conflict(CollaborationArtifactType.ShareLink, current)
            }
        }
        return if (request.payload.mutationKind == MutationKind.Delete) {
            state.shareLinks.removeAll { it.id == request.payload.entityId }
            RemoteMutationResult(CollaborationArtifactType.ShareLink, request.payload.entityId, deleted = true, remoteVersion = (current?.remoteVersion ?: 0L) + 1L, serverTimestampEpochMillis = now)
        } else {
            val updated = requireNotNull(incoming).copy(
                remoteVersion = (current?.remoteVersion ?: 0L) + 1L,
                serverUpdatedAtEpochMillis = now,
                lastSyncedAtEpochMillis = now,
            )
            state.shareLinks.removeAll { it.id == updated.id }
            state.shareLinks += updated
            RemoteMutationResult(
                artifactType = CollaborationArtifactType.ShareLink,
                entityId = updated.id,
                appliedJson = json.encodeToString(ShareLinkModel.serializer(), updated),
                remoteVersion = updated.remoteVersion,
                serverTimestampEpochMillis = now,
            )
        }
    }

    private fun mutateThread(state: EmulatorState, request: RemoteMutationRequest, now: Long): RemoteMutationResult {
        val incoming = request.payload.currentJson?.let { json.decodeFromString(ReviewThreadModel.serializer(), it) }
        val current = state.reviewThreads.firstOrNull { it.id == request.payload.entityId }
        current?.remoteVersion?.let { remoteVersion ->
            if (request.payload.baseRemoteVersion != null && request.payload.baseRemoteVersion != remoteVersion) {
                throw conflict(CollaborationArtifactType.ReviewThread, current)
            }
        }
        return if (request.payload.mutationKind == MutationKind.Delete) {
            state.reviewThreads.removeAll { it.id == request.payload.entityId }
            RemoteMutationResult(CollaborationArtifactType.ReviewThread, request.payload.entityId, deleted = true, remoteVersion = (current?.remoteVersion ?: 0L) + 1L, serverTimestampEpochMillis = now)
        } else {
            val updated = requireNotNull(incoming).copy(
                remoteVersion = (current?.remoteVersion ?: 0L) + 1L,
                serverUpdatedAtEpochMillis = now,
                lastSyncedAtEpochMillis = now,
            )
            state.reviewThreads.removeAll { it.id == updated.id }
            state.reviewThreads += updated
            RemoteMutationResult(
                artifactType = CollaborationArtifactType.ReviewThread,
                entityId = updated.id,
                appliedJson = json.encodeToString(ReviewThreadModel.serializer(), updated),
                remoteVersion = updated.remoteVersion,
                serverTimestampEpochMillis = now,
            )
        }
    }

    private fun mutateActivity(state: EmulatorState, request: RemoteMutationRequest, now: Long): RemoteMutationResult {
        val event = request.payload.currentJson?.let { json.decodeFromString(ActivityEventModel.serializer(), it) }
            ?: throw CollaborationRemoteException(RemoteErrorMetadata(RemoteErrorCode.InvalidRequest, "Missing activity payload", false))
        val current = state.activityEvents.firstOrNull { it.id == event.id }
        val updated = event.copy(
            remoteVersion = (current?.remoteVersion ?: 0L) + 1L,
            serverUpdatedAtEpochMillis = now,
            lastSyncedAtEpochMillis = now,
        )
        state.activityEvents.removeAll { it.id == updated.id }
        state.activityEvents += updated
        return RemoteMutationResult(
            artifactType = CollaborationArtifactType.ActivityEvent,
            entityId = updated.id,
            appliedJson = json.encodeToString(ActivityEventModel.serializer(), updated),
            remoteVersion = updated.remoteVersion,
            serverTimestampEpochMillis = now,
        )
    }

    private fun mutateSnapshot(state: EmulatorState, request: RemoteMutationRequest, now: Long): RemoteMutationResult {
        val snapshot = request.payload.currentJson?.let { json.decodeFromString(VersionSnapshotModel.serializer(), it) }
            ?: throw CollaborationRemoteException(RemoteErrorMetadata(RemoteErrorCode.InvalidRequest, "Missing snapshot payload", false))
        val current = state.versionSnapshots.firstOrNull { it.id == snapshot.id }
        val updated = snapshot.copy(
            remoteVersion = (current?.remoteVersion ?: 0L) + 1L,
            serverUpdatedAtEpochMillis = now,
            lastSyncedAtEpochMillis = now,
        )
        state.versionSnapshots.removeAll { it.id == updated.id }
        state.versionSnapshots += updated
        return RemoteMutationResult(
            artifactType = CollaborationArtifactType.VersionSnapshot,
            entityId = updated.id,
            appliedJson = json.encodeToString(VersionSnapshotModel.serializer(), updated),
            remoteVersion = updated.remoteVersion,
            serverTimestampEpochMillis = now,
        )
    }

    private fun <T> paginate(items: List<T>, pageToken: String?, pageSize: Int): CollaborationRemotePage<T> {
        val offset = pageToken?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val safePageSize = pageSize.coerceAtLeast(1)
        val slice = items.drop(offset).take(safePageSize)
        val nextToken = (offset + slice.size).takeIf { it < items.size }?.toString()
        return CollaborationRemotePage(slice, nextToken, System.currentTimeMillis())
    }

    private fun conflict(artifactType: CollaborationArtifactType, current: Any): CollaborationRemoteException {
        val conflictJson = when (artifactType) {
            CollaborationArtifactType.ShareLink -> json.encodeToString(ShareLinkModel.serializer(), current as ShareLinkModel)
            CollaborationArtifactType.ReviewThread -> json.encodeToString(ReviewThreadModel.serializer(), current as ReviewThreadModel)
            CollaborationArtifactType.ActivityEvent -> json.encodeToString(ActivityEventModel.serializer(), current as ActivityEventModel)
            CollaborationArtifactType.VersionSnapshot -> json.encodeToString(VersionSnapshotModel.serializer(), current as VersionSnapshotModel)
        }
        return CollaborationRemoteException(
            RemoteErrorMetadata(
                code = RemoteErrorCode.Conflict,
                message = "Remote artifact changed since the local base version.",
                retryable = false,
                conflictRemoteJson = conflictJson,
                serverTimestampEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun readState(): EmulatorState {
        if (!stateFile.exists()) return EmulatorState()
        return runCatching {
            json.decodeFromString(EmulatorState.serializer(), stateFile.readText())
        }.getOrDefault(EmulatorState())
    }

    private fun writeState(state: EmulatorState) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(json.encodeToString(EmulatorState.serializer(), state))
    }

    @Serializable
    private data class EmulatorState(
        val shareLinks: MutableList<ShareLinkModel> = mutableListOf(),
        val reviewThreads: MutableList<ReviewThreadModel> = mutableListOf(),
        val activityEvents: MutableList<ActivityEventModel> = mutableListOf(),
        val versionSnapshots: MutableList<VersionSnapshotModel> = mutableListOf(),
    )
}

private fun Throwable.isRetryableNetwork(): Boolean {
    return this is SocketTimeoutException || this is java.io.IOException
}



