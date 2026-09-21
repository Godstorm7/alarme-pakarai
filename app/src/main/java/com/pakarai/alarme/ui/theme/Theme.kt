package com.pakarai.alarme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pakarai.alarme.R

/**
 * Design system "PakaRai" (ui-ux-pro-max · Dark Mode/OLED):
 * fundo índigo-noturno #0F172A + cards #192134 + acento customizável (cor muda no menu)
 * + CTA índigo #6366F1. Dark-only de propósito (o app acorda você no breu).
 * Tipografia: Russo One (display/títulos, energia) + Inter (corpo, legibilidade).
 */

// ── Fundo estático (OLED índigo-noturno) ──
private val Void = Color(0xFF0F172A)
private val CardColor = Color(0xFF192134)
private val CardRaised = Color(0xFF232F43)
private val Muted = Color(0xFF1F1E27)
private val MutedText = Color(0xFF94A3B8)
private val OutlineColor = Color(0xFF2A3650)
private val ErrorRed = Color(0xFFDC2626)

/** CTA (botões principais/FAB) — índigo da skill, independente do acento do usuário. */
val CtaIndigo = Color(0xFF6366F1)

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
// Russo One: display/títulos (energia de despertar). Inter: corpo (leitura).
private val RussoOne = FontFamily(Font(R.font.russo_one, FontWeight.Normal))
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_black, FontWeight.Black),
)

private val PakaRaiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = RussoOne,
        fontWeight = FontWeight.Normal,
        fontSize = 64.sp,
        letterSpacing = 2.sp,
        lineHeight = 68.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = RussoOne,
        fontWeight = FontWeight.Normal,
        fontSize = 46.sp,
        letterSpacing = 0.5.sp,
        lineHeight = 52.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = RussoOne,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        letterSpacing = 0.2.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = RussoOne,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = RussoOne,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        letterSpacing = 0.6.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.3.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 0.5.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
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
        // tertiary = CTA (índigo), independente do acento
        tertiary = CtaIndigo,
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFF2E2A5E),
        onTertiaryContainer = Color(0xFFC7C2FF),
        background = Void,
        onBackground = Color(0xFFE8EDF4),
        surface = CardColor,
        onSurface = Color(0xFFE8EDF4),
        surfaceVariant = Muted,
        onSurfaceVariant = MutedText,
        outline = OutlineColor,
        outlineVariant = Color(0xFF1E293B),
        error = ErrorRed,
        onError = Color.White,
        errorContainer = Color(0xFF3A1010),
        onErrorContainer = Color(0xFFFFDAD6),
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = PakaRaiTypography,
        shapes = PakaRaiShapes,
    ) {
        // Surface garante o fundo do app E o contentColor padrão claro (onBackground).
        // Sem isso, ícones/textos sem cor explícita ficam pretos no fundo escuro (invisíveis).
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.background,
            contentColor = scheme.onBackground,
            content = content,
        )
    }
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
    val cta = CtaIndigo
}