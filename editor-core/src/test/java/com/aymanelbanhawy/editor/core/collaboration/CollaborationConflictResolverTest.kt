package com.aymanelbanhawy.editor.core.collaboration

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CollaborationConflictResolverTest {
    @Test
    fun preferNewestChoosesRemoteWhenRemoteIsNewer() {
        val resolver = CollaborationConflictResolver(SyncConflictPolicy.PreferNewest)
        val local = ShareLinkModel("local", "doc", "token", "Local", "Ayman", 10L, null, SharePermission.Comment)
        val remote = local.copy(title = "Remote", createdAtEpochMillis = 20L)

        val resolved = resolver.resolveShareLink(local, remote)

        assertThat(resolved.title).isEqualTo("Remote")
    }

    @Test
    fun preferLocalKeepsLocalThread() {
        val resolver = CollaborationConflictResolver(SyncConflictPolicy.PreferLocal)
        val local = ReviewThreadModel("thread", "doc", 0, null, "Local", "Ayman", 10L, 30L)
        val remote = local.copy(title = "Remote", modifiedAtEpochMillis = 40L)

        val resolved = resolver.resolveThread(local, remote)

        assertThat(resolved.title).isEqualTo("Local")
    }
}
