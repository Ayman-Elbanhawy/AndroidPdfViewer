package com.aymanelbanhawy.enterprisepdf.app.diagnostics

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aymanelbanhawy.editor.core.EditorCoreContainer
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeDocumentOpenBenchmarkTest {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchmarkDocumentOpen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = EditorCoreContainer(context)
        val pdf = File(context.cacheDir, "benchmark-large.pdf")
        if (!pdf.exists()) {
            createLargePdf(pdf)
        }

        benchmarkRule.measureRepeated {
            runBlocking {
                container.documentRepository.open(
                    OpenDocumentRequest.FromFile(
                        absolutePath = pdf.absolutePath,
                        displayNameOverride = pdf.name,
                    ),
                )
            }
        }
    }

    private fun createLargePdf(destination: File) {
        val paint = Paint().apply { textSize = 14f }
        val document = PdfDocument()
        try {
            repeat(60) { index ->
                val pageInfo = PdfDocument.PageInfo.Builder(612, 792, index + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawText("Benchmark page ${index + 1}", 48f, 96f, paint)
                document.finishPage(page)
            }
            destination.outputStream().use { output -> document.writeTo(output) }
        } finally {
            document.close()
        }
    }
}
