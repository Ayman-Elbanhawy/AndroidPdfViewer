package com.aymanelbanhawy.editor.core.organize

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

interface PageThumbnailRepository {
    suspend fun thumbnailsFor(document: DocumentModel, widthPx: Int = 240): List<ThumbnailDescriptor>
}

class DefaultPageThumbnailRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PageThumbnailRepository {

    override suspend fun thumbnailsFor(document: DocumentModel, widthPx: Int): List<ThumbnailDescriptor> = withContext(ioDispatcher) {
        val cacheDir = File(context.cacheDir, "organize-thumbnails/${document.sessionId}").apply { mkdirs() }
        document.pages.mapIndexed { index, page ->
            val target = File(cacheDir, "page_${index}_${page.rotationDegrees}_${page.contentType.name}.png")
            if (!target.exists()) {
                renderThumbnail(document, page, widthPx, target)
            }
            ThumbnailDescriptor(pageIndex = index, imagePath = target.absolutePath)
        }
    }

    private fun renderThumbnail(document: DocumentModel, page: PageModel, widthPx: Int, target: File) {
        when (page.contentType) {
            PageContentType.Pdf -> renderPdfPage(page, widthPx, target)
            PageContentType.Blank -> renderBlankPage(page, widthPx, target)
            PageContentType.Image -> renderImagePage(page, widthPx, target)
        }
    }

    private fun renderPdfPage(page: PageModel, widthPx: Int, target: File) {
        val sourcePath = page.sourceDocumentPath.ifBlank { return renderBlankPage(page, widthPx, target) }
        val file = File(sourcePath)
        if (!file.exists()) return renderBlankPage(page, widthPx, target)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(pfd).use { renderer ->
            val pageIndex = page.sourcePageIndex.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(pageIndex).use { rendererPage ->
                val scale = widthPx / rendererPage.width.toFloat()
                val height = (rendererPage.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                rendererPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                saveBitmap(bitmap, target)
            }
        }
        pfd.close()
    }

    private fun renderBlankPage(page: PageModel, widthPx: Int, target: File) {
        val aspect = (page.heightPoints / page.widthPoints).coerceAtLeast(1f)
        val heightPx = (widthPx * aspect).toInt().coerceAtLeast(widthPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.LTGRAY
            textSize = 28f
        }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), Paint().apply { style = Paint.Style.STROKE; color = Color.LTGRAY; strokeWidth = 3f })
        canvas.drawText("Blank page", 24f, 48f, paint)
        saveBitmap(bitmap, target)
    }

    private fun renderImagePage(page: PageModel, widthPx: Int, target: File) {
        val imageFile = page.insertedImagePath?.let(::File)
        if (imageFile == null || !imageFile.exists()) {
            renderBlankPage(page, widthPx, target)
            return
        }
        val source = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
        val scale = widthPx / source.width.toFloat()
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createScaledBitmap(source, widthPx, height, true)
        saveBitmap(bitmap, target)
    }

    private fun saveBitmap(bitmap: Bitmap, target: File) {
        FileOutputStream(target).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
