package com.aymanelbanhawy.editor.core.forms

import com.aymanelbanhawy.editor.core.model.NormalizedPoint
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import kotlinx.serialization.Serializable

@Serializable
enum class FormFieldType {
    Text,
    MultilineText,
    Checkbox,
    RadioGroup,
    Dropdown,
    Date,
    Signature,
}

@Serializable
enum class SignatureVerificationStatus {
    Unsigned,
    Signed,
    Verified,
    Invalid,
}

@Serializable
enum class SignatureKind {
    Signature,
    Initials,
}

@Serializable
data class FormFieldOption(
    val label: String,
    val value: String,
)

@Serializable
sealed interface FormFieldValue {
    @Serializable
    data class Text(val text: String = "") : FormFieldValue

    @Serializable
    data class BooleanValue(val checked: Boolean = false) : FormFieldValue

    @Serializable
    data class Choice(val selected: String = "") : FormFieldValue

    @Serializable
    data class SignatureValue(
        val savedSignatureId: String = "",
        val signerName: String = "",
        val signedAtEpochMillis: Long = 0L,
        val status: SignatureVerificationStatus = SignatureVerificationStatus.Unsigned,
        val imagePath: String? = null,
        val kind: SignatureKind = SignatureKind.Signature,
    ) : FormFieldValue
}

@Serializable
data class FormFieldModel(
    val name: String,
    val label: String,
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val type: FormFieldType,
    val required: Boolean = false,
    val options: List<FormFieldOption> = emptyList(),
    val value: FormFieldValue,
    val placeholder: String = "",
    val maxLength: Int? = null,
    val readOnly: Boolean = false,
    val exportValue: String = "",
    val helperText: String = "",
    val signatureStatus: SignatureVerificationStatus = SignatureVerificationStatus.Unsigned,
)

@Serializable
data class FormDocumentModel(
    val fields: List<FormFieldModel> = emptyList(),
) {
    fun updateField(updated: FormFieldModel): FormDocumentModel = copy(
        fields = fields.map { field -> if (field.name == updated.name) updated else field },
    )

    fun field(name: String): FormFieldModel? = fields.firstOrNull { it.name == name }
}

@Serializable
enum class ValidationSeverity {
    Info,
    Warning,
    Error,
}

@Serializable
data class FormValidationIssue(
    val fieldName: String,
    val message: String,
    val severity: ValidationSeverity,
)

@Serializable
data class FormValidationSummary(
    val issues: List<FormValidationIssue> = emptyList(),
) {
    val isValid: Boolean get() = issues.none { it.severity == ValidationSeverity.Error }
    fun issueFor(fieldName: String): FormValidationIssue? = issues.firstOrNull { it.fieldName == fieldName }
}

@Serializable
data class SignatureStroke(
    val points: List<NormalizedPoint>,
)

@Serializable
data class SignatureCapture(
    val strokes: List<SignatureStroke>,
    val width: Float,
    val height: Float,
)

@Serializable
data class SavedSignatureModel(
    val id: String,
    val name: String,
    val kind: SignatureKind,
    val imagePath: String,
    val createdAtEpochMillis: Long,
)

@Serializable
data class FormProfileModel(
    val id: String,
    val name: String,
    val values: Map<String, FormFieldValue>,
    val createdAtEpochMillis: Long,
)
