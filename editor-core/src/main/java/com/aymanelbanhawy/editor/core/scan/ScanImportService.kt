package com.aymanelbanhawy.editor.core.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.core.net.toUri
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.ocr.OcrJobPipeline
import com.aymanelbanhawy.editor.core.ocr.QueuedOcrPage
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

interface ScanImportService {
    suspend fun importImages(imageFiles: List<File>, options: ScanImportOptions): OpenDocumentRequest
}

data class ScanImportOptions(
    val displayName: String = "scan-session.pdf",
    val autoCrop: Boolean = true,
    val deskew: Boolean = true,
    val cleanup: Boolean = true,
)

class DefaultScanImportService(
    private val context: Context,
    private val ocrJobPipeline: OcrJobPipeline,
) : ScanImportService {

    init {
        PDFBoxResourceLoader.init(context)
    }

    override suspend fun importImages(imageFiles: List<File>, options: ScanImportOptions): OpenDocumentRequest {
        require(imageFiles.isNotEmpty()) { "At least one image is required." }
        val processedDir = File(context.cacheDir, "scan-import/${UUID.randomUUID()}").apply { mkdirs() }
        val processedImages = imageFiles.mapIndexedNotNull { index, file ->
            val source = BitmapFactory.decodeFile(file.absolutePath) ?: return@mapIndexedNotNull null
            val prepared = source.prepareForScan(options)
            val output = File(processedDir, "scan_page_${index + 1}.png")
            output.outputStream().use { prepared.compress(Bitmap.CompressFormat.PNG, 100, it) }
            output
        }
        val pdfFile = File(processedDir, options.displayName.ifBlank { "scan-session.pdf" })
        PDDocument().use { document ->
            processedImages.forEach { file ->
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEach
                val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    stream.drawImage(image, 0f, 0f, page.mediaBox.width, page.mediaBox.height)
                }
            }
            document.save(pdfFile)
        }
        ocrJobPipeline.enqueue(
            documentKey = pdfFile.absolutePath,
            jobs = processedImages.mapIndexed { index, file -> QueuedOcrPage(pageIndex = index, imagePath = file.absolutePath) },
        )
        return OpenDocumentRequest.FromFile(pdfFile.absolutePath, displayNameOverride = pdfFile.name)
    }

    private fun Bitmap.prepareForScan(options: ScanImportOptions): Bitmap {
        var current = this
        if (options.autoCrop) current = current.autoCropToContent()
        if (options.deskew) current = current.autoOrient()
        if (options.cleanup) current = current.cleanupForPaper()
        return current
    }

    private fun Bitmap.autoCropToContent(): Bitmap {
        val width = width
        val height = height
        var left = width
        var top = height
        var right = 0
        var bottom = 0
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[(y * width) + x]
                val luminance = ((android.graphics.Color.red(color) * 0.299f) + (android.graphics.Color.green(color) * 0.587f) + (android.graphics.Color.blue(color) * 0.114f)).toInt()
                if (luminance < 248) {
                    left = min(left, x)
                    top = min(top, y)
                    right = max(right, x)
                    bottom = max(bottom, y)
                }
            }
        }
        if (left >= right || top >= bottom) return this
        return Bitmap.createBitmap(this, left.coerceAtLeast(0), top.coerceAtLeast(0), (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
    }

    private fun Bitmap.autoOrient(): Bitmap {
        if (width <= height) return this
        val matrix = Matrix().apply { postRotate(90f) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.cleanupForPaper(): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(this, 0f, 0f, paint)
        val pixels = IntArray(width * height)
        output.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val luma = ((android.graphics.Color.red(color) + android.graphics.Color.green(color) + android.graphics.Color.blue(color)) / 3)
            val channel = if (luma > 180) 255 else 24
            pixels[index] = android.graphics.Color.argb(255, channel, channel, channel)
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
