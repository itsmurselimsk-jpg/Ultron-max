package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val UltronDarkColorScheme = darkColorScheme(
    primary = CrimsonCore,
    onPrimary = TextPrimary,
    primaryContainer = CrimsonDarkContainer,
    onPrimaryContainer = TextPrimary,
    secondary = CyberCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = ElectricBlueContainer,
    onSecondaryContainer = TextPrimary,
    tertiary = UltravioletPurple,
    onTertiary = TextPrimary,
    tertiaryContainer = PurpleContainer,
    onTertiaryContainer = TextPrimary,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = CharcoalDeep,
    onSurface = TextPrimary,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = CharcoalDeep,
    surfaceContainer = CharcoalElevated,
    surfaceContainerHigh = CharcoalPanel,
    outline = HairlineBorder,
    outlineVariant = CharcoalPanel,
    error = CrimsonLaser,
    onError = TextPrimary,
    errorContainer = CrimsonDarkContainer,
    onErrorContainer = TextPrimary
)

private val UltronShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun UltronOmegaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = UltronDarkColorScheme,
        typography = UltronTypography,
        shapes = UltronShapes,
        content = content
    )
}
