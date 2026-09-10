package com.pakarai.alarme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design system "PakaRai": OLED preto + ciano de madrugada.
 * Dark-only de propósito (o app acorda você no breu).
 */

// ── Cor ──
private val Cyan = Color(0xFF22D3EE)
private val CyanSoft = Color(0xFF38BDF8)
private val BlueAccent = Color(0xFF3B82F6)
private val Void = Color(0xFF0A0E14)
private val CardColor = Color(0xFF10151D)
private val CardRaised = Color(0xFF161D29)
private val Muted = Color(0xFF1E2734)
private val MutedText = Color(0xFFA8B4C6)
private val OutlineColor = Color(0xFF263042)
private val ErrorRed = Color(0xFFFF5252)

private val PakaRaiDarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF03141C),
    primaryContainer = Color(0xFF0A3345),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = BlueAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E3A5F),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = ErrorRed,
    onTertiary = Color.White,
    background = Void,
    onBackground = Color(0xFFF2F4F8),
    surface = CardColor,
    onSurface = Color(0xFFF2F4F8),
    surfaceVariant = Muted,
    onSurfaceVariant = MutedText,
    outline = OutlineColor,
    outlineVariant = Color(0xFF1D2530),
    error = ErrorRed,
    onError = Color.White,
    errorContainer = Color(0xFF3A1010),
    onErrorContainer = Color(0xFFFFDAD6),
)

// ── Tipografia ──
// Sem fontes custom no build (0 risco): Roboto do sistema, pesos fortes + tracking.
private val PakaRaiTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 56.sp,
        letterSpacing = (-1.5).sp,
        lineHeight = 60.sp,
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        letterSpacing = (-1).sp,
        lineHeight = 50.sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 20.sp,
        letterSpacing = 0.4.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.3.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
    ),
)

// ── Forma ──
private val PakaRaiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ── Espaçamento (ritmo 4/8dp) ──
object PakaRaiSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object PakaRaiColors {
    val cyan = Cyan
    val cyanSoft = CyanSoft
    val blue = BlueAccent
    val void = Void
    val card = CardColor
    val cardRaised = CardRaised
    val muted = Muted
    val mutedText = MutedText
    val outline = OutlineColor
    val danger = ErrorRed
}

@Composable
fun AlarmePakaraiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PakaRaiDarkColors,
        typography = PakaRaiTypography,
        shapes = PakaRaiShapes,
        content = content,
    )
}