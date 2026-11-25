package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

private val BaseTypography = Typography()

/**
 * Material 3 Typography enforcing [TextDirection.ContentOrLtr] across all text styles
 * so numbers, prices, signs, dates, percentages, and numeric inputs always render in
 * French/LTR direction even when the UI language is switched to Arabic (RTL),
 * while Arabic words and sentences continue to flow naturally RTL.
 */
val Typography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(textDirection = TextDirection.ContentOrLtr),
    displayMedium = BaseTypography.displayMedium.copy(textDirection = TextDirection.ContentOrLtr),
    displaySmall = BaseTypography.displaySmall.copy(textDirection = TextDirection.ContentOrLtr),
    headlineLarge = BaseTypography.headlineLarge.copy(textDirection = TextDirection.ContentOrLtr),
    headlineMedium = BaseTypography.headlineMedium.copy(textDirection = TextDirection.ContentOrLtr),
    headlineSmall = BaseTypography.headlineSmall.copy(textDirection = TextDirection.ContentOrLtr),
    titleLarge = BaseTypography.titleLarge.copy(textDirection = TextDirection.ContentOrLtr),
    titleMedium = BaseTypography.titleMedium.copy(textDirection = TextDirection.ContentOrLtr),
    titleSmall = BaseTypography.titleSmall.copy(textDirection = TextDirection.ContentOrLtr),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
        textDirection = TextDirection.ContentOrLtr
    ),
    bodyMedium = BaseTypography.bodyMedium.copy(textDirection = TextDirection.ContentOrLtr),
    bodySmall = BaseTypography.bodySmall.copy(textDirection = TextDirection.ContentOrLtr),
    labelLarge = BaseTypography.labelLarge.copy(textDirection = TextDirection.ContentOrLtr),
    labelMedium = BaseTypography.labelMedium.copy(textDirection = TextDirection.ContentOrLtr),
    labelSmall = BaseTypography.labelSmall.copy(textDirection = TextDirection.ContentOrLtr)
)
