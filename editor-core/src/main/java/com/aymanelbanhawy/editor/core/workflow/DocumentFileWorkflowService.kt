package com.aymanelbanhawy.editor.core.workflow

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.scan.ScanImportOptions
import com.aymanelbanhawy.editor.core.scan.ScanImportService
import com.aymanelbanhawy.editor.core.search.IndexedPageContent
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.aymanelbanhawy.editor.core.search.SearchContentSource
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DocumentFileWorkflowService(
    private val context: Context,
    private val extractionService: PdfBoxTextExtractionService,
    private val ocrSessionStore: OcrSessionStore,
    private val documentRepository: DocumentRepository,
    private val scanImportService: ScanImportService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun exportDocumentAsText(document: DocumentModel, destination: File): ExportBundleResult = withContext(ioDispatcher) {
        val pages = extractIndexedContent(document)
        destination.parentFile?.mkdirs()
        val body = buildString {
            appendLine(document.documentRef.displayName)
            appendLine()
            pages.forEachIndexed { index, page ->
                appendLine("Page ${index + 1}")
                appendLine(page.pageText.ifBlank { "(No text detected)" })
                if (index != pages.lastIndex) appendLine()
            }
        }.trim()
        destination.writeText(body)
        ExportBundleResult(
            title = "Text Export",
            artifacts = listOf(
                ExportArtifactModel(
                    path = destination.absolutePath,
                    displayName = destination.name,
                    mimeType = "text/plain",
                ),
            ),
        )
    }

    suspend fun exportDocumentAsMarkdown(document: DocumentModel, destination: File): ExportBundleResult = withContext(ioDispatcher) {
        val pages = extractIndexedContent(document)
        destination.parentFile?.mkdirs()
        val body = buildString {
            appendLine("# ${document.documentRef.displayName}")
            appendLine()
            pages.forEachIndexed { index, page ->
                appendLine("## Page ${index + 1}")
                appendLine()
                val blocks = page.blocks.takeIf { it.isNotEmpty() } ?: listOf()
                if (blocks.isEmpty()) {
                    appendLine(page.pageText.ifBlank { "_No text detected._" })
                } else {
                    blocks.forEach { block ->
                        appendLine(block.text.trim())
                        appendLine()
                    }
                }
            }
        }.trim()
        destination.writeText(body)
        ExportBundleResult(
            title = "Markdown Export",
            artifacts = listOf(
                ExportArtifactModel(
                    path = destination.absolutePath,
                    displayName = destination.name,
                    mimeType = "text/markdown",
                ),
            ),
        )
    }

    suspend fun exportDocumentAsImages(
        document: DocumentModel,
        outputDirectory: File,
        format: ExportImageFormat,
    ): ExportBundleResult = withContext(ioDispatcher) {
        val sourceFile = File(document.documentRef.workingCopyPath)
        require(sourceFile.exists()) { "Working copy is missing for image export." }
        outputDirectory.mkdirs()
        val artifacts = mutableListOf<ExportArtifactModel>()
        ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            (page.width * 2f).toInt().coerceAtLeast(1),
                            (page.height * 2f).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val extension = if (format == ExportImageFormat.Png) "png" else "jpg"
                        val file = File(outputDirectory, "${document.documentRef.displayName.removeSuffix(".pdf")}_page_${pageIndex + 1}.$extension")
                        file.outputStream().use { output ->
                            bitmap.compress(
                                if (format == ExportImageFormat.Png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                                if (format == ExportImageFormat.Png) 100 else 88,
                                output,
                            )
                        }
                        bitmap.recycle()
                        artifacts += ExportArtifactModel(
                            path = file.absolutePath,
                            displayName = file.name,
                            mimeType = if (format == ExportImageFormat.Png) "image/png" else "image/jpeg",
                            pageIndex = pageIndex,
                        )
                    }
                }
            }
        }
        ExportBundleResult(title = "Image Export", artifacts = artifacts)
    }

    suspend fun createPdfFromImages(imageFiles: List<File>, displayName: String): CreatedPdfResult = withContext(ioDispatcher) {
        val request = scanImportService.importImages(
            imageFiles = imageFiles,
            options = ScanImportOptions(displayName = displayName),
        )
        CreatedPdfResult(request = request, sourceImageCount = imageFiles.size)
    }

    suspend fun optimizeDocument(
        document: DocumentModel,
        destination: File,
        preset: PdfOptimizationPreset,
    ): OptimizationResult = withContext(ioDispatcher) {
        destination.parentFile?.mkdirs()
        val original = File(document.documentRef.workingCopyPath)
        val originalSize = original.takeIf { it.exists() }?.length() ?: 0L
        val mode = when (preset) {
            PdfOptimizationPreset.Light -> AnnotationExportMode.Editable
            PdfOptimizationPreset.Balanced -> AnnotationExportMode.Flatten
            PdfOptimizationPreset.Strong -> AnnotationExportMode.Flatten
        }
        documentRepository.saveAs(document, destination, mode)
        OptimizationResult(
            destination = destination,
            preset = preset,
            originalSizeBytes = originalSize,
            optimizedSizeBytes = destination.length(),
        )
    }

    private suspend fun extractIndexedContent(document: DocumentModel): List<IndexedPageContent> {
        val extractedPages = extractionService.extract(document.documentRef).associateBy { it.pageIndex }
        val ocrPages = ocrSessionStore.load(document.documentRef)?.pages.orEmpty().associateBy { it.pageIndex }
        val maxIndex = maxOf(
            extractedPages.keys.maxOrNull() ?: -1,
            ocrPages.keys.maxOrNull() ?: -1,
            document.pages.lastIndex,
        )
        if (maxIndex < 0) {
            return emptyList()
        }
        return (0..maxIndex).map { pageIndex ->
            val extracted = extractedPages[pageIndex]
            val ocr = ocrPages[pageIndex]
            when {
                extracted != null && extracted.pageText.isNotBlank() -> extracted
                ocr != null -> IndexedPageContent(
                    pageIndex = pageIndex,
                    pageText = ocr.text.ifBlank { ocr.blocks.joinToString("\n") { it.text } },
                    blocks = ocr.flattenedSearchBlocks(),
                    source = SearchContentSource.Ocr,
                )
                extracted != null -> extracted
                else -> IndexedPageContent(
                    pageIndex = pageIndex,
                    pageText = "",
                    blocks = emptyList(),
                    source = SearchContentSource.EmbeddedText,
                )
            }
        }
    }
}
