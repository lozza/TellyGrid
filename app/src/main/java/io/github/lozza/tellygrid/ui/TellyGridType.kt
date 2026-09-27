package io.github.lozza.tellygrid.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import io.github.lozza.tellygrid.R

/**
 * Plus Jakarta Sans (SIL Open Font License, see docs/PlusJakartaSans-OFL.txt), chosen for a
 * broadcaster-style look. One variable font file covers weights 200 to 800.
 */
@OptIn(ExperimentalTextApi::class)
val AppFont = FontFamily(
    listOf(300, 400, 500, 600, 700, 800).map { weight ->
        Font(
            R.font.plus_jakarta_sans,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

// Line height relative to each text's own size: Material's fixed 24sp line height clipped
// two-line titles in the guide's fixed-height cells with this font's taller glyphs.
private fun TextStyle.appFont() = copy(fontFamily = AppFont, lineHeight = 1.2.em)

val TellyGridTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.appFont(), displayMedium = displayMedium.appFont(), displaySmall = displaySmall.appFont(),
        headlineLarge = headlineLarge.appFont(), headlineMedium = headlineMedium.appFont(), headlineSmall = headlineSmall.appFont(),
        titleLarge = titleLarge.appFont(), titleMedium = titleMedium.appFont(), titleSmall = titleSmall.appFont(),
        bodyLarge = bodyLarge.appFont(), bodyMedium = bodyMedium.appFont(), bodySmall = bodySmall.appFont(),
        labelLarge = labelLarge.appFont(), labelMedium = labelMedium.appFont(), labelSmall = labelSmall.appFont(),
    )
}
