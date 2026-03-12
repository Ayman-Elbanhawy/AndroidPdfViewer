package com.aymanelbanhawy.enterprisepdf.app.smoke

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aymanelbanhawy.editor.core.EditorCoreContainer
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CriticalWorkflowSmokeTest {
    @Test
    fun openSaveAndDiagnosticsFlow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val container = EditorCoreContainer(context)
        val request = OpenDocumentRequest.FromAsset(assetName = "sample.pdf", displayName = "sample.pdf")

        val opened = runBlocking { container.documentRepository.open(request) }
        assertThat(opened.pageCount).isGreaterThan(0)

        val output = File(context.cacheDir, "smoke-output.pdf")
        val saved = runBlocking {
            container.documentRepository.saveAs(opened, output, AnnotationExportMode.Editable)
        }
        assertThat(saved.documentRef.displayName).contains("smoke-output")
        assertThat(output.exists()).isTrue()

        val diagnostics = runBlocking { container.runtimeDiagnosticsRepository.captureSnapshot(saved) }
        assertThat(diagnostics.lastSaveElapsedMillis).isAtLeast(0L)
    }
}

