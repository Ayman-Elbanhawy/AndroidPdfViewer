package com.aymanelbanhawy.editor.core.collaboration

import com.aymanelbanhawy.editor.core.model.DocumentModel

interface CollaborationRemoteDataSource {
    suspend fun upsertShareLink(model: ShareLinkModel): ShareLinkModel
    suspend fun upsertReviewThread(model: ReviewThreadModel): ReviewThreadModel
    suspend fun recordActivity(event: ActivityEventModel): ActivityEventModel
    suspend fun recordSnapshot(snapshot: VersionSnapshotModel): VersionSnapshotModel
    suspend fun shareLinks(documentKey: String): List<ShareLinkModel>
    suspend fun reviewThreads(documentKey: String): List<ReviewThreadModel>
    suspend fun activity(documentKey: String): List<ActivityEventModel>
    suspend fun snapshots(documentKey: String): List<VersionSnapshotModel>
}

class InMemoryCollaborationRemoteDataSource : CollaborationRemoteDataSource {
    private val shareLinks = linkedMapOf<String, ShareLinkModel>()
    private val threads = linkedMapOf<String, ReviewThreadModel>()
    private val activity = linkedMapOf<String, ActivityEventModel>()
    private val snapshots = linkedMapOf<String, VersionSnapshotModel>()

    override suspend fun upsertShareLink(model: ShareLinkModel): ShareLinkModel {
        shareLinks[model.id] = model
        return model
    }

    override suspend fun upsertReviewThread(model: ReviewThreadModel): ReviewThreadModel {
        threads[model.id] = model
        return model
    }

    override suspend fun recordActivity(event: ActivityEventModel): ActivityEventModel {
        activity[event.id] = event
        return event
    }

    override suspend fun recordSnapshot(snapshot: VersionSnapshotModel): VersionSnapshotModel {
        snapshots[snapshot.id] = snapshot
        return snapshot
    }

    override suspend fun shareLinks(documentKey: String): List<ShareLinkModel> = shareLinks.values.filter { it.documentKey == documentKey }.sortedByDescending { it.createdAtEpochMillis }
    override suspend fun reviewThreads(documentKey: String): List<ReviewThreadModel> = threads.values.filter { it.documentKey == documentKey }.sortedByDescending { it.modifiedAtEpochMillis }
    override suspend fun activity(documentKey: String): List<ActivityEventModel> = activity.values.filter { it.documentKey == documentKey }.sortedByDescending { it.createdAtEpochMillis }
    override suspend fun snapshots(documentKey: String): List<VersionSnapshotModel> = snapshots.values.filter { it.documentKey == documentKey }.sortedByDescending { it.createdAtEpochMillis }
}
