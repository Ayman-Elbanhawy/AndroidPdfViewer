package com.aymanelbanhawy.editor.core.ocr

import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import kotlinx.serialization.Serializable

@Serializable
enum class OcrJobStatus {
    Pending,
    Queued,
    Running,
    Completed,
    Failed,
}

data class OcrEngineResult(
    val pageText: String,
    val blocks: List<ExtractedTextBlock>,
    val warningMessage: String? = null,
)

interface OcrEngine {
    suspend fun recognize(imagePath: String, pageIndex: Int): OcrEngineResult
}

class NoOpOcrEngine : OcrEngine {
    override suspend fun recognize(imagePath: String, pageIndex: Int): OcrEngineResult {
        return OcrEngineResult(
            pageText = "",
            blocks = emptyList(),
            warningMessage = "No on-device OCR engine is configured for this build.",
        )
    }
}
