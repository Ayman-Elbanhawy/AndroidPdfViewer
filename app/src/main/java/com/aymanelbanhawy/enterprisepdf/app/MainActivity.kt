package com.aymanelbanhawy.enterprisepdf.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorScreen
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorViewModel
import com.aymanelbanhawy.enterprisepdf.app.organize.OrganizePagesScreen
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
                Surface(modifier = Modifier) {
                    val state = viewModel.uiState.collectAsStateWithLifecycle().value
                    val mergeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> -> viewModel.mergeDocuments(uris) }
                    val organizeImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let(viewModel::insertImagePage) }
                    val editImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let(viewModel::addImageEdit) }
                    val replaceEditImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let(viewModel::replaceSelectedImage) }
                    val profileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let(viewModel::importFormProfile) }
                    val scanImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> -> viewModel.importScanImages(uris) }

                    if (state.organizeVisible) {
                        OrganizePagesScreen(
                            state = state,
                            events = viewModel.events,
                            onBack = viewModel::showEditor,
                            onSelectPage = viewModel::selectPage,
                            onMovePage = viewModel::movePage,
                            onRotateSelected = viewModel::rotateSelectedPages,
                            onDeleteSelected = viewModel::deleteSelectedPages,
                            onDuplicateSelected = viewModel::duplicateSelectedPages,
                            onExtractSelected = viewModel::extractSelectedPages,
                            onInsertBlankPage = viewModel::insertBlankPage,
                            onPickImagePage = { organizeImageLauncher.launch("image/*") },
                            onPickMergePdfs = { mergeLauncher.launch(arrayOf("application/pdf")) },
                            onUpdateSplitRange = viewModel::updateSplitRangeExpression,
                            onSplitByRange = viewModel::splitByRange,
                            onSplitOddPages = viewModel::splitOddPages,
                            onSplitEvenPages = viewModel::splitEvenPages,
                            onSplitSelectedPages = viewModel::splitSelectedPages,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                        )
                    } else {
                        EditorScreen(
                            state = state,
                            events = viewModel.events,
                            onActionSelected = viewModel::onActionSelected,
                            onDocumentLoaded = viewModel::onDocumentLoaded,
                            onPageChanged = viewModel::onPageChanged,
                            onToolSelected = viewModel::onToolSelected,
                            onAnnotationCreated = viewModel::onAnnotationCreated,
                            onAnnotationUpdated = viewModel::onAnnotationUpdated,
                            onAnnotationSelectionChanged = viewModel::onAnnotationSelectionChanged,
                            onFormFieldTapped = viewModel::onFormFieldTapped,
                            onPageEditSelectionChanged = viewModel::onPageEditSelectionChanged,
                            onPageEditUpdated = viewModel::onPageEditUpdated,
                            onSidebarToggle = viewModel::toggleAnnotationSidebar,
                            onDeleteSelected = viewModel::deleteSelectedAnnotation,
                            onDuplicateSelected = viewModel::duplicateSelectedAnnotation,
                            onRecolorSelected = viewModel::recolorSelectedAnnotation,
                            onSelectAnnotation = viewModel::selectAnnotation,
                            onSelectFormField = viewModel::selectFormField,
                            onTextFieldChanged = viewModel::updateTextField,
                            onBooleanFieldChanged = viewModel::toggleBooleanField,
                            onChoiceFieldChanged = viewModel::updateChoiceField,
                            onSaveFormProfile = viewModel::saveFormProfile,
                            onApplyFormProfile = viewModel::applyFormProfile,
                            onExportFormData = viewModel::exportCurrentFormData,
                            onImportProfile = { profileLauncher.launch("application/json") },
                            onOpenSignatureCapture = viewModel::openSignatureCapture,
                            onApplySavedSignature = viewModel::applySavedSignature,
                            onDismissSignatureCapture = viewModel::dismissSignatureCapture,
                            onSaveSignatureCapture = viewModel::saveSignatureAndApply,
                            onAddTextBox = viewModel::addTextBox,
                            onAddImage = { editImageLauncher.launch("image/*") },
                            onSelectEditObject = viewModel::selectPageEdit,
                            onDeleteSelectedEdit = viewModel::deleteSelectedEdit,
                            onDuplicateSelectedEdit = viewModel::duplicateSelectedEdit,
                            onReplaceSelectedImage = { replaceEditImageLauncher.launch("image/*") },
                            onSelectedEditTextChanged = viewModel::updateSelectedTextContent,
                            onSelectedEditFontFamilyChanged = { viewModel.updateSelectedTextStyle(fontFamily = it) },
                            onSelectedEditFontSizeChanged = { viewModel.updateSelectedTextStyle(fontSizeSp = it) },
                            onSelectedEditColorChanged = { viewModel.updateSelectedTextStyle(textColorHex = it) },
                            onSelectedEditAlignmentChanged = { viewModel.updateSelectedTextStyle(alignment = it) },
                            onSelectedEditLineSpacingChanged = { viewModel.updateSelectedTextStyle(lineSpacingMultiplier = it) },
                            onSelectedEditOpacityChanged = viewModel::updateSelectedEditOpacity,
                            onSelectedEditRotationChanged = viewModel::updateSelectedEditRotation,
                            onSearchQueryChanged = viewModel::updateSearchQuery,
                            onSearch = viewModel::performSearch,
                            onAssistantPromptChanged = viewModel::updateAssistantPrompt,
                            onAskPdf = viewModel::askPdf,
                            onSummarizeDocumentWithAi = viewModel::summarizeDocumentWithAi,
                            onSummarizePageWithAi = viewModel::summarizeCurrentPageWithAi,
                            onExtractActionItemsWithAi = viewModel::extractActionItemsWithAi,
                            onExplainSelectionWithAi = viewModel::explainSelectionWithAi,
                            onSemanticSearchWithAi = viewModel::runAiSemanticSearch,
                            onAssistantPrivacyModeChanged = viewModel::updateAssistantPrivacyMode,
                            onAssistantProviderDraftChanged = viewModel::updateAssistantProviderDraft,
                            onSaveAssistantProvider = viewModel::saveAssistantProvider,
                            onRefreshAssistantProviders = viewModel::refreshAssistantProviders,
                            onTestAssistantConnection = viewModel::testAssistantConnection,
                            onCancelAssistantRequest = viewModel::cancelAssistantRequest,
                            onOpenAssistantCitation = viewModel::openAssistantCitation,
                            onNextSearchHit = viewModel::nextSearchHit,
                            onPreviousSearchHit = viewModel::previousSearchHit,
                            onSelectSearchHit = viewModel::selectSearchHit,
                            onUseRecentSearch = { query -> viewModel.updateSearchQuery(query); viewModel.performSearch(query) },
                            onOpenOutlineItem = viewModel::openOutlineItem,
                            onCopySelectedText = viewModel::copySelectedText,
                            onShareSelectedText = viewModel::shareSelectedText,
                            onShowScanImport = viewModel::showScanImportDialog,
                            onDismissScanImport = viewModel::dismissScanImportDialog,
                            onScanImportOptionsChanged = viewModel::updateScanImportOptions,
                            onPickScanImages = { scanImagesLauncher.launch("image/*") },
                            onCreateShareLink = viewModel::createShareLink,
                            onCreateSnapshot = viewModel::createVersionSnapshot,
                            onSyncNow = viewModel::syncCollaboration,
                            onReviewFilterChanged = viewModel::updateReviewFilter,
                            onAddReviewThread = viewModel::addReviewThread,
                            onAddReviewReply = viewModel::addReviewReply,
                            onToggleThreadResolved = viewModel::toggleThreadResolved,
                            onConfigureAppLock = viewModel::configureAppLock,
                            onUnlockWithPin = viewModel::unlockWithPin,
                            onUnlockWithBiometric = viewModel::unlockWithBiometric,
                            onLockNow = viewModel::lockNow,
                            onUpdatePermissions = viewModel::updateDocumentPermissions,
                            onUpdateTenantPolicy = viewModel::updateTenantPolicy,
                            onUpdatePasswordProtection = viewModel::updatePasswordProtection,
                            onUpdateWatermark = viewModel::updateWatermark,
                            onUpdateMetadataScrub = viewModel::updateMetadataScrub,
                            onInspectSecurity = viewModel::generateInspectionReport,
                            onMarkRedaction = viewModel::markSelectedTextForRedaction,
                            onPreviewRedactions = viewModel::setRedactionPreview,
                            onApplyRedactions = viewModel::applyRedactions,
                            onRemoveRedaction = viewModel::removeRedactionMark,
                            onExportAuditTrail = viewModel::exportAuditTrail,
                            onSignInPersonal = viewModel::signInPersonal,
                            onSignInEnterprise = viewModel::signInEnterprise,
                            onSignOutEnterprise = viewModel::signOutEnterprise,
                            onSetEnterprisePlan = viewModel::setEnterprisePlan,
                            onUpdateEnterprisePrivacy = viewModel::updateEnterprisePrivacy,
                            onUpdateEnterprisePolicy = viewModel::updateEnterprisePolicy,
                            onGenerateDiagnosticsBundle = viewModel::generateDiagnosticsBundle,
                            onRefreshEnterpriseRemote = viewModel::refreshEnterpriseRemoteState,
                            onFlushEnterpriseTelemetry = viewModel::flushEnterpriseTelemetry,
                            onSaveConnectorAccount = viewModel::saveConnectorAccount,
                            onTestConnectorConnection = viewModel::testConnectorConnection,
                            onOpenConnectorDocument = viewModel::openDocumentFromConnector,
                            onSyncConnectorTransfers = viewModel::syncConnectorTransfers,
                            onCleanupConnectorCache = viewModel::cleanupConnectorCache,
                            onSaveToConnectorEditable = viewModel::saveToConnectorEditable,
                            onSaveToConnectorFlattened = viewModel::saveToConnectorFlattened,
                            onShareToConnectorEditable = viewModel::shareToConnectorEditable,
                            onDismissConnectorExportDialog = viewModel::dismissConnectorExportDialog,
                            onConnectorExportAccountChanged = viewModel::updateConnectorExportAccount,
                            onConnectorExportRemotePathChanged = viewModel::updateConnectorExportRemotePath,
                            onConnectorExportDisplayNameChanged = viewModel::updateConnectorExportDisplayName,
                            onSubmitConnectorExport = viewModel::submitConnectorExport,
                            onRotatePage = viewModel::rotateCurrentPage,
                            onReorderPages = viewModel::showOrganize,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            onSaveEditable = viewModel::saveEditable,
                            onSaveFlattened = viewModel::saveFlattened,
                            onSaveAsEditable = viewModel::saveAsEditable,
                            onSaveAsFlattened = viewModel::saveAsFlattened,
                            onShareRequested = ::shareDocument,
                            onShareTextRequested = ::shareText,
                        )
                    }
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







