package com.training.trackplanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF315F8C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7F2),
    onPrimaryContainer = Color(0xFF17324D),
    secondary = Color(0xFF356B61),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9E9E5),
    onSecondaryContainer = Color(0xFF173B35),
    tertiary = Color(0xFF8A5A17),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3E5CC),
    onTertiaryContainer = Color(0xFF402D10),
    background = Color(0xFFF5F6F7),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFECEFF1),
    onSurfaceVariant = Color(0xFF45484D),
    outline = Color(0xFF7A7E84)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C9E8),
    onPrimary = Color(0xFF17324D),
    primaryContainer = Color(0xFF294B6A),
    onPrimaryContainer = Color(0xFFDCE7F2),
    secondary = Color(0xFF9ACCC1),
    onSecondary = Color(0xFF173B35),
    secondaryContainer = Color(0xFF294C45),
    onSecondaryContainer = Color(0xFFD9E9E5),
    tertiary = Color(0xFFE2BE82),
    onTertiary = Color(0xFF402D10),
    background = Color(0xFF101214),
    onBackground = Color(0xFFE4E5E7),
    surface = Color(0xFF181A1D),
    onSurface = Color(0xFFF0F1F2),
    surfaceVariant = Color(0xFF25282C),
    onSurfaceVariant = Color(0xFFC5C8CC),
    outline = Color(0xFF858A90)
)

private val BaseTypography = Typography()

private val AppTypography = BaseTypography.copy(
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodySmall = BaseTypography.bodySmall.copy(lineHeight = 18.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun TrainingTrackPlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
