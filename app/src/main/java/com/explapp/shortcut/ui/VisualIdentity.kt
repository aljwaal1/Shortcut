package com.explapp.shortcut.ui

import com.explapp.shortcut.tools.ToolSection

data class SectionVisualStyle(
    val accentArgb: Int,
    val softArgb: Int,
)

object VisualIdentity {
    fun forSection(section: ToolSection): SectionVisualStyle = when (section) {
        ToolSection.IMAGES -> SectionVisualStyle(
            accentArgb = 0xFF7657F6.toInt(),
            softArgb = 0xFFEDE8FF.toInt(),
        )
        ToolSection.PDF_FILES -> SectionVisualStyle(
            accentArgb = 0xFFF0526B.toInt(),
            softArgb = 0xFFFFE8EC.toInt(),
        )
        ToolSection.QUICK -> SectionVisualStyle(
            accentArgb = 0xFF00A88F.toInt(),
            softArgb = 0xFFDDF7F1.toInt(),
        )
        ToolSection.AUTOMATION -> SectionVisualStyle(
            accentArgb = 0xFFF39A3F.toInt(),
            softArgb = 0xFFFFEEDC.toInt(),
        )
    }
}
