package com.ssolstice.camera.manual.compose.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ssolstice.camera.manual.R

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)

val VcrFontFamily = FontFamily(
    Font(R.font.vcr_osd_mono, FontWeight.Normal)
)

val AppTypography = Typography(
    displayLarge = Typography().displayLarge.copy(fontFamily = VcrFontFamily),
    displayMedium = Typography().displayMedium.copy(fontFamily = VcrFontFamily),
    displaySmall = Typography().displaySmall.copy(fontFamily = VcrFontFamily),
    headlineLarge = Typography().headlineLarge.copy(fontFamily = VcrFontFamily),
    headlineMedium = Typography().headlineMedium.copy(fontFamily = VcrFontFamily),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = VcrFontFamily),
    titleLarge = Typography().titleLarge.copy(fontFamily = VcrFontFamily),
    titleMedium = Typography().titleMedium.copy(fontFamily = VcrFontFamily),
    titleSmall = Typography().titleSmall.copy(fontFamily = VcrFontFamily),
    bodyLarge = TextStyle(
        fontFamily = VcrFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = VcrFontFamily),
    bodySmall = Typography().bodySmall.copy(fontFamily = VcrFontFamily),
    labelLarge = Typography().labelLarge.copy(fontFamily = VcrFontFamily),
    labelMedium = Typography().labelMedium.copy(fontFamily = VcrFontFamily),
    labelSmall = Typography().labelSmall.copy(fontFamily = VcrFontFamily)
)