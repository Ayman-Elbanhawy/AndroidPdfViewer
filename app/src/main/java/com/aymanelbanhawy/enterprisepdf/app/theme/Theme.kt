package com.aymanelbanhawy.enterprisepdf.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A5CFF),
    onPrimary = Color(0xFFF8FAFF),
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF03255C),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFF4FFFD),
    secondaryContainer = Color(0xFFC4F3EC),
    onSecondaryContainer = Color(0xFF003732),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFAF7FF),
    tertiaryContainer = Color(0xFFEBDDFF),
    onTertiaryContainer = Color(0xFF2E0A63),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFBFF),
    background = Color(0xFFF4F7FB),
    onBackground = Color(0xFF101623),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE7EDF6),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFF7C8AA5),
    outlineVariant = Color(0xFFC8D2E1),
    scrim = Color(0xFF0F172A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BB6FF),
    onPrimary = Color(0xFF032B75),
    primaryContainer = Color(0xFF0C3F9E),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFF5BE1CF),
    onSecondary = Color(0xFF003A35),
    secondaryContainer = Color(0xFF00524B),
    onSecondaryContainer = Color(0xFFB6FFF4),
    tertiary = Color(0xFFC7A7FF),
    onTertiary = Color(0xFF3D0E87),
    tertiaryContainer = Color(0xFF5B2CB7),
    onTertiaryContainer = Color(0xFFF0E5FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF07111F),
    onBackground = Color(0xFFE8EEF9),
    surface = Color(0xFF0D1726),
    onSurface = Color(0xFFE6EDF8),
    surfaceVariant = Color(0xFF182334),
    onSurfaceVariant = Color(0xFFB6C3D9),
    outline = Color(0xFF8B99B4),
    outlineVariant = Color(0xFF253247),
    scrim = Color(0xFF000000),
)

private val EnterpriseTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 35.sp, lineHeight = 40.sp, letterSpacing = (-0.4).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 31.sp, lineHeight = 36.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.25.sp),
)

private val EnterpriseShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(36.dp),
)

@Composable
fun EnterprisePdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EnterpriseTypography,
        shapes = EnterpriseShapes,
        content = content,
    )
}
