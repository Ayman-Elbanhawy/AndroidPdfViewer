package com.aymanelbanhawy.editor.core.ocr

import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import kotlinx.serialization.json.Json
import java.io.File

class OcrSessionStore(
    private val json: Json,
) {
    fun load(documentRef: PdfDocumentRef): OcrDocumentPayload? {
        val sidecar = resolveExistingSidecar(documentRef) ?: return null
        return decodeSidecar(sidecar)
    }

    fun loadForDocumentKey(documentKey: String): OcrDocumentPayload? {
        val sidecar = sidecarFileForPath(documentKey) ?: return null
        return sidecar.takeIf { it.exists() }?.let(::decodeSidecar)
    }

    fun mergePage(documentKey: String, settings: OcrSettingsModel, page: OcrPageContent): OcrDocumentPayload {
        val sidecar = sidecarFileForPath(documentKey)
        val current = sidecar?.takeIf { it.exists() }?.let(::decodeSidecar)
        val pages = (current?.pages.orEmpty().filterNot { it.pageIndex == page.pageIndex } + page).sortedBy { it.pageIndex }
        return OcrDocumentPayload(
            documentKey = documentKey,
            settings = settings,
            pages = pages,
            updatedAtEpochMillis = System.currentTimeMillis(),
        )
    }

    fun persistPayload(payload: OcrDocumentPayload) {
        val sidecar = sidecarFileForPath(payload.documentKey) ?: return
        sidecar.parentFile?.mkdirs()
        sidecar.writeText(json.encodeToString(OcrDocumentPayload.serializer(), payload))
    }

    fun persistPage(documentKey: String, settings: OcrSettingsModel, page: OcrPageContent) {
        persistPayload(mergePage(documentKey, settings, page))
    }

    fun copySidecar(documentRef: PdfDocumentRef, destinationPdf: File, settings: OcrSettingsModel? = null) {
        val payload = load(documentRef) ?: return
        val effective = if (settings != null) {
            payload.copy(settings = settings, updatedAtEpochMillis = System.currentTimeMillis())
        } else {
            payload
        }
        persistPayload(effective.copy(documentKey = destinationPdf.absolutePath))
    }

    fun deleteSidecar(destinationPdf: File) {
        File(destinationPdf.absolutePath + SIDE_CAR_SUFFIX).delete()
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

    private fun decodeSidecar(file: File): OcrDocumentPayload? {
        return runCatching {
            json.decodeFromString(OcrDocumentPayload.serializer(), file.readText())
        }.getOrNull()
    }

    companion object {
        const val SIDE_CAR_SUFFIX: String = ".ocr.json"
    }
}

@kotlinx.serialization.Serializable
data class OcrDocumentPayload(
    val documentKey: String,
    val settings: OcrSettingsModel,
    val pages: List<OcrPageContent>,
    val updatedAtEpochMillis: Long,
)
