package com.aymanelbanhawy.editor.core.write

import android.content.Context
import android.graphics.BitmapFactory
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

interface PdfWriteEngine {
    suspend fun load(documentRef: PdfDocumentRef): Map<Int, List<PageEditModel>>
    suspend fun persist(document: DocumentModel, destinationPdf: File, exportMode: AnnotationExportMode)
}

class PdfBoxWriteEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" },
) : PdfWriteEngine {

    init {
        PDFBoxResourceLoader.init(context)
    }

    override suspend fun load(documentRef: PdfDocumentRef): Map<Int, List<PageEditModel>> = withContext(ioDispatcher) {
        val sidecar = resolveSidecar(documentRef) ?: return@withContext emptyMap()
        if (!sidecar.exists()) return@withContext emptyMap()
        val payload = json.decodeFromString(PageEditSidecarPayload.serializer(), sidecar.readText())
        payload.editObjects.groupBy { it.pageIndex }
    }

    override suspend fun persist(document: DocumentModel, destinationPdf: File, exportMode: AnnotationExportMode): Unit = withContext(ioDispatcher) {
        applyToPdf(document, destinationPdf)
        if (exportMode == AnnotationExportMode.Editable) {
            val sidecar = File(destinationPdf.absolutePath + ".pageedits.json")
            sidecar.parentFile?.mkdirs()
            val payload = PageEditSidecarPayload(
                documentKey = document.documentRef.sourceKey,
                editObjects = document.pages.flatMap { page -> page.editObjects.map { it.withPage(page.index) } },
                updatedAtEpochMillis = System.currentTimeMillis(),
            )
            sidecar.writeText(json.encodeToString(PageEditSidecarPayload.serializer(), payload))
        } else {
            File(destinationPdf.absolutePath + ".pageedits.json").takeIf { it.exists() }?.delete()
        }
    }

    private fun applyToPdf(document: DocumentModel, destinationPdf: File) {
        if (!destinationPdf.exists()) return
        PDDocument.load(destinationPdf).use { pdDocument ->
            document.pages.forEach { page ->
                if (page.editObjects.isEmpty()) return@forEach
                val pdfPage = pdDocument.getPage(page.index.coerceIn(0, pdDocument.numberOfPages - 1))
                PDPageContentStream(pdDocument, pdfPage, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    page.editObjects.forEach { editObject ->
                        when (editObject) {
                            is TextBoxEditModel -> drawTextEdit(stream, pdfPage.mediaBox.width, pdfPage.mediaBox.height, editObject)
                            is ImageEditModel -> drawImageEdit(pdDocument, stream, pdfPage.mediaBox.width, pdfPage.mediaBox.height, editObject)
                        }
                    }
                }
            }
            pdDocument.save(destinationPdf)
        }
    }

    private fun drawTextEdit(stream: PDPageContentStream, pageWidth: Float, pageHeight: Float, edit: TextBoxEditModel) {
        val bounds = toPdfRect(pageWidth, pageHeight, edit.bounds)
        val font = when (edit.fontFamily) {
            FontFamilyToken.Sans -> PDType1Font.HELVETICA
            FontFamilyToken.Serif -> PDType1Font.TIMES_ROMAN
            FontFamilyToken.Monospace -> PDType1Font.COURIER
        }
        val lines = edit.text.split('\n')
        val lineHeight = edit.fontSizeSp * edit.lineSpacingMultiplier
        val color = parseColor(edit.textColorHex)
        stream.saveGraphicsState()
        stream.setNonStrokingColor(color)
        var currentY = bounds.top - edit.fontSizeSp
        lines.forEach { line ->
            val lineWidth = font.getStringWidth(line) / 1000f * edit.fontSizeSp
            val x = when (edit.alignment) {
                TextAlignment.Start -> bounds.left
                TextAlignment.Center -> bounds.left + ((bounds.width - lineWidth) / 2f)
                TextAlignment.End -> bounds.right - lineWidth
            }
            stream.beginText()
            stream.setFont(font, edit.fontSizeSp)
            stream.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(edit.rotationDegrees.toDouble()), x, currentY))
            stream.showText(line)
            stream.endText()
            currentY -= lineHeight
        }
        stream.restoreGraphicsState()
    }

    private fun drawImageEdit(document: PDDocument, stream: PDPageContentStream, pageWidth: Float, pageHeight: Float, edit: ImageEditModel) {
        val bitmap = BitmapFactory.decodeFile(edit.imagePath) ?: return
        val image = LosslessFactory.createFromImage(document, bitmap)
        val bounds = toPdfRect(pageWidth, pageHeight, edit.bounds)
        stream.saveGraphicsState()
        val matrix = Matrix()
        matrix.translate(bounds.left + bounds.width / 2f, bounds.bottom + bounds.height / 2f)
        matrix.rotate(Math.toRadians(edit.rotationDegrees.toDouble()))
        matrix.translate(-bounds.width / 2f, -bounds.height / 2f)
        stream.transform(matrix)
        stream.drawImage(image, 0f, 0f, bounds.width, bounds.height)
        stream.restoreGraphicsState()
    }

    private fun toPdfRect(pageWidth: Float, pageHeight: Float, rect: com.aymanelbanhawy.editor.core.model.NormalizedRect): PdfRect {
        val left = rect.left * pageWidth
        val width = rect.width * pageWidth
        val height = rect.height * pageHeight
        val bottom = pageHeight - (rect.bottom * pageHeight)
        return PdfRect(left, bottom, width, height)
    }

    private fun parseColor(colorHex: String): FloatArray {
        val color = android.graphics.Color.parseColor(colorHex)
        return floatArrayOf(
            android.graphics.Color.red(color) / 255f,
            android.graphics.Color.green(color) / 255f,
            android.graphics.Color.blue(color) / 255f,
        )
    }

    private fun resolveSidecar(documentRef: PdfDocumentRef): File? {
        val sourceFile = when (documentRef.sourceType) {
            DocumentSourceType.File -> File(documentRef.sourceKey)
            DocumentSourceType.Uri, DocumentSourceType.Asset, DocumentSourceType.Memory -> File(documentRef.workingCopyPath)
        }
        val explicit = File(sourceFile.absolutePath + ".pageedits.json")
        if (explicit.exists()) return explicit
        val working = File(documentRef.workingCopyPath + ".pageedits.json")
        if (working.exists()) return working
        return explicit.takeIf { it.parentFile?.exists() == true } ?: working.takeIf { it.parentFile?.exists() == true }
    }
}

private data class PdfRect(
    val left: Float,
    val bottom: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val top: Float get() = bottom + height
}

@Serializable
private data class PageEditSidecarPayload(
    val documentKey: String,
    val editObjects: List<PageEditModel>,
    val updatedAtEpochMillis: Long,
)



