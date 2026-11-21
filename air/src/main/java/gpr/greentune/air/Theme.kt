package gpr.greentune.air

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import gpr.greentune.air.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class AirColors(
    val dark: Boolean,
    val tint: Color,
    val intensity: Float,
    val text: Color,
    val subText: Color,
    val glassTop: Color,
    val glassBottom: Color,
    val glassTint: Color,
    val borderOuter: Color,
    val borderInner: Color,
    val gloss: Color,
    val wallTop: Color,
    val wallBottom: Color,
    val accent: Color,
    val accentDark: Color,
    val danger: Color,
    val ok: Color,
    val warn: Color,
)

fun airColors(dark: Boolean, tint: Color, intensity: Float): AirColors {
    val i = intensity.coerceIn(0f, 1f)
    return if (dark) {
        AirColors(
            dark = true,
            tint = tint,
            intensity = i,
            text = Color(0xFFEFF9FF),
            subText = Color(0xFFA3C1D0),
            glassTop = Color.White.copy(alpha = 0.14f),
            glassBottom = Color.White.copy(alpha = 0.04f),
            glassTint = tint.copy(alpha = 0.05f + 0.12f * i),
            borderOuter = Color.Black.copy(alpha = 0.45f),
            borderInner = Color.White.copy(alpha = 0.26f),
            gloss = Color.White.copy(alpha = 0.17f),
            wallTop = lerp(Color(0xFF061824), tint, 0.12f + 0.14f * i),
            wallBottom = Color(0xFF01070C),
            accent = lerp(tint, Color.White, 0.10f),
            accentDark = lerp(tint, Color.Black, 0.35f),
            danger = Color(0xFFFF6B5E),
            ok = Color(0xFF52E58A),
            warn = Color(0xFFFFC34D),
        )
    } else {
        AirColors(
            dark = false,
            tint = tint,
            intensity = i,
            text = Color(0xFF0B2533),
            subText = Color(0xFF3A5D6E),
            glassTop = Color.White.copy(alpha = 0.66f),
            glassBottom = Color.White.copy(alpha = 0.34f),
            glassTint = tint.copy(alpha = 0.04f + 0.10f * i),
            borderOuter = Color(0xFF0E3A52).copy(alpha = 0.28f),
            borderInner = Color.White.copy(alpha = 0.90f),
            gloss = Color.White.copy(alpha = 0.60f),
            wallTop = lerp(Color(0xFFE6F7FD), tint, 0.10f + 0.18f * i),
            wallBottom = lerp(Color(0xFFBFE6F4), tint, 0.25f + 0.25f * i),
            accent = tint,
            accentDark = lerp(tint, Color.Black, 0.30f),
            danger = Color(0xFFD9372B),
            ok = Color(0xFF16A34A),
            warn = Color(0xFFC98400),
        )
    }
}

val LocalAir = staticCompositionLocalOf { airColors(false, Color(0xFF19A7CE), 0.6f) }

val GlydeSans = FontFamily(
    Font(R.font.glyde_sans_light, FontWeight.Light),
    Font(R.font.glyde_sans_regular, FontWeight.Normal),
    Font(R.font.glyde_sans_regular, FontWeight.Medium),
    Font(R.font.glyde_sans_semibold, FontWeight.SemiBold),
    Font(R.font.glyde_sans_bold, FontWeight.Bold),
)

object Air {
    val colors: AirColors
        @Composable get() = LocalAir.current
}

@Composable
fun AirTheme(theme: String, tint: Color, intensity: Float, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val c = airColors(dark, tint, intensity)
    val scheme = if (dark) {
        darkColorScheme(primary = c.accent, onPrimary = Color.White, surface = Color(0xFF0C1B24), background = Color(0xFF040E14), onSurface = c.text, onBackground = c.text)
    } else {
        lightColorScheme(primary = c.accentDark, onPrimary = Color.White, surface = Color(0xFFF3FBFE), background = Color(0xFFE6F7FD), onSurface = c.text, onBackground = c.text)
    }
    val base = TextStyle(fontFamily = GlydeSans, color = c.text)
    val typography = MaterialTheme.typography.copy(
        bodyLarge = base.copy(fontSize = 16.sp),
        bodyMedium = base.copy(fontSize = 14.sp),
        bodySmall = base.copy(fontSize = 12.sp, color = c.subText),
        titleLarge = base.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        labelMedium = base.copy(fontSize = 13.sp),
        labelSmall = base.copy(fontSize = 11.sp, color = c.subText),
        headlineSmall = base.copy(fontSize = 26.sp, fontWeight = FontWeight.Light),
        headlineMedium = base.copy(fontSize = 30.sp, fontWeight = FontWeight.Light),
    )
    CompositionLocalProvider(LocalAir provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
