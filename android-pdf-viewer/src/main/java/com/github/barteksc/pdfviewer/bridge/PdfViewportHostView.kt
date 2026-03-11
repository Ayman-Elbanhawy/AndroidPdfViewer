package com.github.barteksc.pdfviewer.bridge

import android.content.Context
import android.widget.FrameLayout
import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.SelectionModel
import com.github.barteksc.pdfviewer.PDFView

internal class PdfViewportHostView(context: Context) : FrameLayout(context) {
    val pdfView = PDFView(context, null)
    private val annotationOverlayView = PdfAnnotationOverlayView(context)
    private val formOverlayView = PdfFormOverlayView(context)
    private val pageEditOverlayView = PdfPageEditOverlayView(context)

    init {
        addView(pdfView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(annotationOverlayView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(formOverlayView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(pageEditOverlayView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        annotationOverlayView.pdfView = pdfView
        formOverlayView.pdfView = pdfView
        pageEditOverlayView.pdfView = pdfView
    }

    fun renderAnnotations(
        pages: List<PageModel>,
        formDocument: FormDocumentModel,
        selection: SelectionModel,
        activeTool: AnnotationTool,
        callbacks: PdfViewportCallbacks,
    ) {
        annotationOverlayView.pages = pages
        annotationOverlayView.selection = selection
        annotationOverlayView.activeTool = activeTool
        annotationOverlayView.callbacks = callbacks

        formOverlayView.formDocument = formDocument
        formOverlayView.selection = selection
        formOverlayView.activeTool = activeTool
        formOverlayView.callbacks = callbacks

        pageEditOverlayView.pages = pages
        pageEditOverlayView.selection = selection
        pageEditOverlayView.activeTool = activeTool
        pageEditOverlayView.callbacks = callbacks
    }
}
