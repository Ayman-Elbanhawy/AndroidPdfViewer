package com.aymanelbanhawy.editor.core.collaboration

class CollaborationConflictResolver(
    private val policy: SyncConflictPolicy = SyncConflictPolicy.PreferNewest,
) {
    fun resolveShareLink(local: ShareLinkModel, remote: ShareLinkModel?): ShareLinkModel = when {
        remote == null -> local
        policy == SyncConflictPolicy.PreferLocal -> local
        policy == SyncConflictPolicy.PreferRemote -> remote
        local.createdAtEpochMillis >= remote.createdAtEpochMillis -> local
        else -> remote
    }

    fun resolveThread(local: ReviewThreadModel, remote: ReviewThreadModel?): ReviewThreadModel = when {
        remote == null -> local
        policy == SyncConflictPolicy.PreferLocal -> local
        policy == SyncConflictPolicy.PreferRemote -> remote
        local.modifiedAtEpochMillis >= remote.modifiedAtEpochMillis -> local
        else -> remote
    }

    fun resolutionReason(localUpdatedAt: Long, remoteUpdatedAt: Long): String {
        return when {
            localUpdatedAt == remoteUpdatedAt -> "Identical timestamps"
            localUpdatedAt > remoteUpdatedAt -> "Local change is newer"
            else -> "Remote change is newer"
        }
    }
}
