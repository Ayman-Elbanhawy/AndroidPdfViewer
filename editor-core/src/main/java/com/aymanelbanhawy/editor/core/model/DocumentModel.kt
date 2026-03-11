package com.aymanelbanhawy.editor.core.model

import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import kotlinx.serialization.Serializable

@Serializable
data class DocumentModel(
    val sessionId: String,
    val documentRef: PdfDocumentRef,
    val pages: List<PageModel>,
    val formDocument: FormDocumentModel = FormDocumentModel(),
    val dirtyState: DirtyState = DirtyState(),
    val restoredFromDraft: Boolean = false,
    val lastSavedAtEpochMillis: Long? = null,
) {
    val pageCount: Int
        get() = pages.size
}
