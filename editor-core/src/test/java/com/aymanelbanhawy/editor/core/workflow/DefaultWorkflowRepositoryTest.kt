package com.aymanelbanhawy.editor.core.workflow

import android.content.Context
import android.content.ContextWrapper
import com.aymanelbanhawy.editor.core.data.ActivityEventDao
import com.aymanelbanhawy.editor.core.data.ActivityEventEntity
import com.aymanelbanhawy.editor.core.data.CompareReportDao
import com.aymanelbanhawy.editor.core.data.CompareReportEntity
import com.aymanelbanhawy.editor.core.data.FormTemplateDao
import com.aymanelbanhawy.editor.core.data.FormTemplateEntity
import com.aymanelbanhawy.editor.core.data.WorkflowRequestDao
import com.aymanelbanhawy.editor.core.data.WorkflowRequestEntity
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationMode
import com.aymanelbanhawy.editor.core.enterprise.AuthSessionModel
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.aymanelbanhawy.editor.core.forms.FormDocumentModel
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.PageContentType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DefaultWorkflowRepositoryTest {
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        private val root = File(System.getProperty("java.io.tmpdir"), "workflow-repository-test").apply { mkdirs() }
        override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }
        override fun getApplicationContext(): Context = this
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }

    init {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun compareDocuments_generatesReviewableChangeList() = runBlocking {
        val repository = repository()
        val baseline = createPdf("baseline-${System.nanoTime()}.pdf", "alpha")
        val compared = createPdf("compared-${System.nanoTime()}.pdf", "alpha beta")

        val report = repository.compareDocuments(document(baseline), ref(compared))

        assertThat(report.summary.changedPages).isEqualTo(1)
        assertThat(report.pageChanges).hasSize(1)
        assertThat(report.pageChanges.single().markers).isNotEmpty()
        assertThat(repository.compareReports(document(baseline).documentRef.sourceKey)).hasSize(1)
    }

    @Test
    fun createFormRequest_roundTripsSubmissionLifecycle() = runBlocking {
        val repository = repository()
        val document = document(createPdf("form-${System.nanoTime()}.pdf", "form"))
        val template = repository.createFormTemplate(document, "Vendor Intake")
        val request = repository.createFormRequest(
            document = document,
            templateId = template.id,
            title = "Collect vendor details",
            recipients = listOf(WorkflowRecipientModel("person@tenant.com", "Person", WorkflowRecipientRole.Submitter, 1)),
            reminderIntervalDays = 2,
            expiresAtEpochMillis = System.currentTimeMillis() + 86_400_000L,
        )

        val updated = repository.updateRequestResponse(
            request.id,
            WorkflowResponseModel(
                recipientEmail = "person@tenant.com",
                status = WorkflowRequestStatus.Completed,
                actedAtEpochMillis = System.currentTimeMillis(),
                fieldValues = mapOf("vendorName" to "Acme"),
            ),
        )

        assertThat(updated?.status).isEqualTo(WorkflowRequestStatus.Completed)
        assertThat(updated?.submissions).hasSize(1)
        assertThat(repository.workflowRequests(document.documentRef.sourceKey)).hasSize(1)
    }

    @Test
    fun createSignatureRequest_blocksExternalRecipientsWhenPolicyDisallows() = runBlocking {
        val repository = repository(
            state = EnterpriseAdminStateModel(
                authSession = AuthSessionModel(mode = AuthenticationMode.Enterprise, isSignedIn = true, email = "owner@tenant.com", displayName = "Owner"),
                tenantConfiguration = TenantConfigurationModel(domain = "tenant.com"),
                adminPolicy = AdminPolicyModel(allowExternalSharing = false),
                plan = LicensePlan.Enterprise,
            ),
        )
        val document = document(createPdf("signature-${System.nanoTime()}.pdf", "sign"))

        val failure = runCatching {
            repository.createSignatureRequest(
                document = document,
                title = "Sign contract",
                recipients = listOf(WorkflowRecipientModel("outside@example.com", "Outside", WorkflowRecipientRole.Signer, 1)),
                reminderIntervalDays = 1,
                expiresAtEpochMillis = System.currentTimeMillis() + 86_400_000L,
            )
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SecurityException::class.java)
    }

    private fun repository(state: EnterpriseAdminStateModel = EnterpriseAdminStateModel()): WorkflowRepository {
        return DefaultWorkflowRepository(
            context = context,
            compareReportDao = RecordingCompareReportDao(),
            formTemplateDao = RecordingFormTemplateDao(),
            workflowRequestDao = RecordingWorkflowRequestDao(),
            activityEventDao = RecordingActivityEventDao(),
            enterpriseAdminRepository = RecordingEnterpriseAdminRepository(state),
            extractionService = PdfBoxTextExtractionService(),
            json = json,
        )
    }

    private fun document(file: File): DocumentModel {
        return DocumentModel(
            sessionId = "session-${file.nameWithoutExtension}",
            documentRef = ref(file),
            pages = listOf(
                PageModel(
                    index = 0,
                    label = "1",
                    contentType = PageContentType.Pdf,
                    sourceDocumentPath = file.absolutePath,
                    sourcePageIndex = 0,
                    widthPoints = PDRectangle.LETTER.width,
                    heightPoints = PDRectangle.LETTER.height,
                ),
            ),
            formDocument = FormDocumentModel(
                fields = listOf(
                    FormFieldModel(
                        name = "vendorName",
                        label = "Vendor name",
                        pageIndex = 0,
                        bounds = NormalizedRect(0.1f, 0.1f, 0.4f, 0.2f),
                        type = FormFieldType.Text,
                        required = true,
                        value = FormFieldValue.Text(""),
                    ),
                    FormFieldModel(
                        name = "signHere",
                        label = "Sign here",
                        pageIndex = 0,
                        bounds = NormalizedRect(0.5f, 0.6f, 0.8f, 0.75f),
                        type = FormFieldType.Signature,
                        required = true,
                        value = FormFieldValue.SignatureValue(signerName = ""),
                    ),
                ),
            ),
        )
    }

    private fun ref(file: File): PdfDocumentRef = PdfDocumentRef(
        uriString = file.toURI().toString(),
        displayName = file.name,
        sourceType = DocumentSourceType.File,
        sourceKey = file.absolutePath,
        workingCopyPath = file.absolutePath,
    )

    private fun createPdf(name: String, text: String): File {
        val file = File(context.filesDir, name)
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA_BOLD, 18f)
                stream.newLineAtOffset(72f, 700f)
                stream.showText(text)
                stream.endText()
            }
            document.save(file)
        }
        return file
    }
}

private class RecordingCompareReportDao : CompareReportDao {
    private val items = linkedMapOf<String, CompareReportEntity>()
    override suspend fun upsert(entity: CompareReportEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<CompareReportEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.createdAtEpochMillis }
}

private class RecordingFormTemplateDao : FormTemplateDao {
    private val items = linkedMapOf<String, FormTemplateEntity>()
    override suspend fun upsert(entity: FormTemplateEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<FormTemplateEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.updatedAtEpochMillis }
    override suspend fun byId(templateId: String): FormTemplateEntity? = items[templateId]
}

private class RecordingWorkflowRequestDao : WorkflowRequestDao {
    private val items = linkedMapOf<String, WorkflowRequestEntity>()
    override suspend fun upsert(entity: WorkflowRequestEntity) { items[entity.id] = entity }
    override suspend fun forDocument(documentKey: String): List<WorkflowRequestEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.updatedAtEpochMillis }
    override suspend fun byId(requestId: String): WorkflowRequestEntity? = items[requestId]
}

private class RecordingActivityEventDao : ActivityEventDao {
    private val items = linkedMapOf<String, ActivityEventEntity>()
    override suspend fun upsert(entity: ActivityEventEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<ActivityEventEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<ActivityEventEntity> = items.values.filter { it.documentKey == documentKey }.sortedByDescending { it.createdAtEpochMillis }
    override suspend fun deleteForDocument(documentKey: String) { items.entries.removeIf { it.value.documentKey == documentKey } }
}

private class RecordingEnterpriseAdminRepository(
    private var state: EnterpriseAdminStateModel,
) : EnterpriseAdminRepository {
    override suspend fun loadState(): EnterpriseAdminStateModel = state
    override suspend fun saveState(state: EnterpriseAdminStateModel) { this.state = state }
    override suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel = state
    override suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel = state
    override suspend fun signOut(): EnterpriseAdminStateModel = state
    override suspend fun refreshRemoteState(force: Boolean): EnterpriseAdminStateModel = state
    override suspend fun refreshSessionIfNeeded(): EnterpriseAdminStateModel = state
    override suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel = EntitlementStateModel(state.plan, emptySet())
    override suspend fun queueTelemetry(event: TelemetryEventModel) = Unit
    override suspend fun pendingTelemetry(): List<TelemetryEventModel> = emptyList()
    override suspend fun flushTelemetry(): Int = 0
    override suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File {
        destination.parentFile?.mkdirs()
        destination.writeText(appSummary.toString())
        return destination
    }
}

