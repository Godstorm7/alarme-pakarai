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
 * Design system "PakaRai": OLED preto + acento customizável (cor muda no menu).
 * Dark-only de propósito (o app acorda você no breu).
 */

// ── Fundo estático (OLẸD) ──
private val Void = Color(0xFF0A0E14)
private val CardColor = Color(0xFF10151D)
private val CardRaised = Color(0xFF161D29)
private val Muted = Color(0xFF1E2734)
private val MutedText = Color(0xFFA8B4C6)
private val OutlineColor = Color(0xFF263042)
private val ErrorRed = Color(0xFFFF5252)

/** Uma cor de acento. O resto do fundo não muda — só o "verde" do app. */
data class PakaRaiAccent(
    val id: String,
    val label: String,
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
)

val PakaRaiAccents = listOf(
    PakaRaiAccent(
        id = "amber",
        label = "Âmbar",
        color = Color(0xFFFFB020),
        onColor = Color(0xFF1A0F00),
        container = Color(0xFF3A2A08),
        onContainer = Color(0xFFFFE4A3),
        secondary = Color(0xFFF59E0B),
        secondaryContainer = Color(0xFF3A2F10),
        onSecondaryContainer = Color(0xFFFDE68A),
    ),
    PakaRaiAccent(
        id = "emerald",
        label = "Esmeralda",
        color = Color(0xFF34D399),
        onColor = Color(0xFF022015),
        container = Color(0xFF0A3B2D),
        onContainer = Color(0xFFA7F3D0),
        secondary = Color(0xFF10B981),
        secondaryContainer = Color(0xFF0B3528),
        onSecondaryContainer = Color(0xFFA7F3D0),
    ),
    PakaRaiAccent(
        id = "violet",
        label = "Violeta",
        color = Color(0xFFA78BFA),
        onColor = Color(0xFF140A2E),
        container = Color(0xFF2A1A4E),
        onContainer = Color(0xFFDDD6FE),
        secondary = Color(0xFF8B5CF6),
        secondaryContainer = Color(0xFF241447),
        onSecondaryContainer = Color(0xFFDDD6FE),
    ),
    PakaRaiAccent(
        id = "coral",
        label = "Coral",
        color = Color(0xFFFB7185),
        onColor = Color(0xFF22030A),
        container = Color(0xFF3B0F1A),
        onContainer = Color(0xFFFECDD3),
        secondary = Color(0xFFF43F5E),
        secondaryContainer = Color(0xFF3A0F1A),
        onSecondaryContainer = Color(0xFFFECDD3),
    ),
    PakaRaiAccent(
        id = "gold",
        label = "Ouro",
        color = Color(0xFFFACC15),
        onColor = Color(0xFF1F1700),
        container = Color(0xFF382A06),
        onContainer = Color(0xFFFEF08A),
        secondary = Color(0xFFEAB308),
        secondaryContainer = Color(0xFF3A2D08),
        onSecondaryContainer = Color(0xFFFEF08A),
    ),
    PakaRaiAccent(
        id = "cyan",
        label = "Ciano",
        color = Color(0xFF22D3EE),
        onColor = Color(0xFF03141C),
        container = Color(0xFF0A3345),
        onContainer = Color(0xFFA5F3FC),
        secondary = Color(0xFF3B82F6),
        secondaryContainer = Color(0xFF1E3A5F),
        onSecondaryContainer = Color(0xFFBFDBFE),
    ),
)

fun accentPalette(id: String?): PakaRaiAccent =
    PakaRaiAccents.firstOrNull { it.id == id } ?: PakaRaiAccents.first()

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

@Composable
fun AlarmePakaraiTheme(
    accentId: String? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val accent = accentPalette(accentId)
    val scheme = darkColorScheme(
        primary = accent.color,
        onPrimary = accent.onColor,
        primaryContainer = accent.container,
        onPrimaryContainer = accent.onContainer,
        secondary = accent.secondary,
        onSecondary = accent.onColor,
        secondaryContainer = accent.secondaryContainer,
        onSecondaryContainer = accent.onSecondaryContainer,
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
    MaterialTheme(
        colorScheme = scheme,
        typography = PakaRaiTypography,
        shapes = PakaRaiShapes,
        content = content,
    )
}

/** Referência estática para quem precisa da cor fora do Compose (círculos do menu etc). */
object PakaRaiColors {
    val void = Void
    val card = CardColor
    val cardRaised = CardRaised
    val muted = Muted
    val mutedText = MutedText
    val outline = OutlineColor
    val danger = ErrorRed
}