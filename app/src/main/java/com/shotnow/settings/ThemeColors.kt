package com.shotnow.settings

data class ThemeColor(
    val id: String,
    val label: String,
    val accent: Int,
    val onAccent: Int,
    val soft: Int,
)

object ThemeColors {
    val Orange = ThemeColor(
        id = "orange",
        label = "焦糖橙",
        accent = 0xFFE97845.toInt(),
        onAccent = 0xFF252525.toInt(),
        soft = 0xFFFBE3D8.toInt(),
    )
    val Teal = ThemeColor(
        id = "teal",
        label = "松石绿",
        accent = 0xFF3F7D6A.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        soft = 0xFFDCEBE5.toInt(),
    )
    val Blue = ThemeColor(
        id = "blue",
        label = "雾蓝",
        accent = 0xFF4F6F9F.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        soft = 0xFFDDE5F1.toInt(),
    )
    val Brick = ThemeColor(
        id = "brick",
        label = "砖红",
        accent = 0xFFA94E4E.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        soft = 0xFFF3DEDE.toInt(),
    )
    val Graphite = ThemeColor(
        id = "graphite",
        label = "石墨",
        accent = 0xFF5F5F5A.toInt(),
        onAccent = 0xFFFFFFFF.toInt(),
        soft = 0xFFE5E4E0.toInt(),
    )

    val all = listOf(Orange, Teal, Blue, Brick, Graphite)
    val default = Orange

    fun byId(id: String?): ThemeColor = all.firstOrNull { it.id == id } ?: default
}
