package com.dualnet.optimizer.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF030B18)
val CardBlue = Color(0xFF0A192B)
val Blue = Color(0xFF2679FF)
val Green = Color(0xFF00E88F)
val Cyan = Color(0xFF12C5F7)
val Purple = Color(0xFF7044FF)
val Muted = Color(0xFFACBFDF)
val Outline = Color(0xFF1B344E)
val White = Color(0xFFF3F5FF)
val Yellow = Color(0xFFFFCE4A)

@Composable
fun DualNetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = Blue, secondary = Green, tertiary = Cyan, background = Navy, surface = CardBlue, surfaceVariant = CardBlue, onPrimary = White, onSurface = White, onBackground = White, outline = Outline),
        typography = Typography(
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        ), content = content
    )
}
