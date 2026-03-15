package com.aymanelbanhawy.enterprisepdf.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorViewModel
import com.aymanelbanhawy.enterprisepdf.app.theme.EnterprisePdfTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: EditorViewModel by viewModels {
        val container = (application as EnterprisePdfApplication).appContainer
        EditorViewModel.factory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnterprisePdfTheme {
                EnterprisePdfAppFrame {
                    MainActivityContent(
                        viewModel = viewModel,
                        onShareDocument = ::shareDocument,
                        onShareText = ::shareText,
                    )
                }
            }
        }
    }

    private fun shareDocument(event: EditorSessionEvent.ShareDocument) {
        val sharedUri = if (event.document.documentRef.uri.scheme == "file") {
            val file = File(requireNotNull(event.document.documentRef.uri.path))
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } else {
            event.document.documentRef.uri
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, sharedUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, event.document.documentRef.displayName))
    }

    private fun shareText(event: EditorSessionEvent.ShareText) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, event.text)
        }
        startActivity(Intent.createChooser(sendIntent, event.title))
    }
}

@Composable
private fun EnterprisePdfAppFrame(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            scheme.primaryContainer.copy(alpha = 0.2f),
            scheme.background,
            scheme.surfaceVariant.copy(alpha = 0.82f),
        ),
    )
    val glowBrush = Brush.radialGradient(
        colors = listOf(
            scheme.tertiaryContainer.copy(alpha = 0.24f),
            Color.Transparent,
        ),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .background(glowBrush, RoundedCornerShape(34.dp)),
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.surface.copy(alpha = 0.74f),
            contentColor = scheme.onBackground,
            shape = RoundedCornerShape(34.dp),
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
        ) {
            content()
        }
    }
}
