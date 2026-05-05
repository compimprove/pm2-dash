package org.compi.pm2dash.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DashboardColors = darkColorScheme(
    primary = Color(0xFF77E0C6),
    onPrimary = Color(0xFF04221C),
    primaryContainer = Color(0xFF123630),
    onPrimaryContainer = Color(0xFFD8FFF5),
    secondary = Color(0xFF8DB3FF),
    onSecondary = Color(0xFF0B1F43),
    secondaryContainer = Color(0xFF183154),
    onSecondaryContainer = Color(0xFFDCE8FF),
    tertiary = Color(0xFFFFC56A),
    onTertiary = Color(0xFF382400),
    tertiaryContainer = Color(0xFF543600),
    onTertiaryContainer = Color(0xFFFFE2B5),
    background = Color(0xFF0B1016),
    onBackground = Color(0xFFE5EDF7),
    surface = Color(0xFF111923),
    onSurface = Color(0xFFDDE7F2),
    surfaceVariant = Color(0xFF182332),
    onSurfaceVariant = Color(0xFF94A8C3),
    outline = Color(0xFF2A3A50),
    outlineVariant = Color(0xFF1D2938),
    error = Color(0xFFFF7A8B),
    onError = Color(0xFF3F0613),
    errorContainer = Color(0xFF5E1020),
    onErrorContainer = Color(0xFFFFD9DE),
)

private val DashboardTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun Pm2DashTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DashboardColors,
        typography = DashboardTypography,
        content = content,
    )
}
