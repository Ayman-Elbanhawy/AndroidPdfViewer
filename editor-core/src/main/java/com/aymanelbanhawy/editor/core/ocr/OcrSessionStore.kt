package com.aymanelbanhawy.editor.core.ocr

import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class OcrSessionStore(
    private val json: Json,
) {
    fun load(documentRef: PdfDocumentRef): OcrDocumentPayload? {
        val sidecar = resolveExistingSidecar(documentRef) ?: return null
        return runCatching {
            json.decodeFromString(OcrDocumentPayload.serializer(), sidecar.readText())
        }.getOrNull()
    }

    fun persistPage(documentKey: String, settings: OcrSettingsModel, page: OcrPageContent) {
        val sidecar = sidecarFileForPath(documentKey) ?: return
        sidecar.parentFile?.mkdirs()
        val current = if (sidecar.exists()) {
            runCatching { json.decodeFromString(OcrDocumentPayload.serializer(), sidecar.readText()) }.getOrNull()
        } else {
            null
        }
        val pages = (current?.pages.orEmpty().filterNot { it.pageIndex == page.pageIndex } + page).sortedBy { it.pageIndex }
        val payload = OcrDocumentPayload(
            documentKey = documentKey,
            settings = settings,
            pages = pages,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
        sidecar.writeText(json.encodeToString(OcrDocumentPayload.serializer(), payload))
    }

    fun copySidecar(documentRef: PdfDocumentRef, destinationPdf: File, settings: OcrSettingsModel? = null) {
        val payload = load(documentRef) ?: return
        val effective = if (settings != null) payload.copy(settings = settings, updatedAtEpochMillis = System.currentTimeMillis()) else payload
        destinationPdf.parentFile?.mkdirs()
        File(destinationPdf.absolutePath + SIDE_CAR_SUFFIX).writeText(json.encodeToString(OcrDocumentPayload.serializer(), effective.copy(documentKey = destinationPdf.absolutePath)))
    }

    private fun resolveExistingSidecar(documentRef: PdfDocumentRef): File? {
        val candidates = buildList {
            when (documentRef.sourceType) {
                DocumentSourceType.File -> add(File(documentRef.sourceKey + SIDE_CAR_SUFFIX))
                else -> Unit
            }
            add(File(documentRef.workingCopyPath + SIDE_CAR_SUFFIX))
        }
        return candidates.firstOrNull { it.exists() }
    }

    private fun sidecarFileForPath(documentKey: String): File? {
        return if (documentKey.contains("://") && !File(documentKey).exists()) {
            null
        } else {
            File(documentKey + SIDE_CAR_SUFFIX)
        }
    }

    companion object {
        const val SIDE_CAR_SUFFIX: String = ".ocr.json"
    }
}

@Serializable
data class OcrDocumentPayload(
    val documentKey: String,
    val settings: OcrSettingsModel,
    val pages: List<OcrPageContent>,
    val updatedAtEpochMillis: Long,
)