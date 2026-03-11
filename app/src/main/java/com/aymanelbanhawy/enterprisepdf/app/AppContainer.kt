package com.aymanelbanhawy.enterprisepdf.app

import android.content.Context
import com.aymanelbanhawy.editor.core.EditorCoreContainer
import com.aymanelbanhawy.editor.core.forms.FormSupportRepository
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.organize.PageThumbnailRepository
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.session.EditorSession

class AppContainer(
    private val editorCoreContainer: EditorCoreContainer,
) {
    val appContext: Context get() = editorCoreContainer.appContext
    val documentRepository: DocumentRepository get() = editorCoreContainer.documentRepository
    val pageThumbnailRepository: PageThumbnailRepository get() = editorCoreContainer.pageThumbnailRepository
    val formSupportRepository: FormSupportRepository get() = editorCoreContainer.formSupportRepository

    fun createSession(): EditorSession = editorCoreContainer.newSession()

    fun seedDocumentRequest(): OpenDocumentRequest {
        return OpenDocumentRequest.FromAsset(
            assetName = "sample.pdf",
            displayName = "sample.pdf",
        )
    }
}
