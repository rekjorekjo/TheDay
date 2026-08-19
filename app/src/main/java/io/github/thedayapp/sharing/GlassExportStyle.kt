package io.github.thedayapp.sharing

/**
 * Flutter Liquid Glass 在原生导出渲染中的样式参数。非空时使用 Glass 视觉而非 Classic 装饰模板。
 */
data class GlassExportStyle(
    val primary: Int,
    val secondary: Int,
    val tertiary: Int,
    val accent: Int,
    val clarity: Int,
    val isDark: Boolean,
    val backgroundPhase: Float = 0.18f,
    val backgroundMode: String = "FLOW",
    val backgroundTexture: String = "DIAGONAL",
) {
    val clarityFraction: Float
        get() = clarity.coerceIn(0, 100) / 100f

    val glassFade: Float
        get() = 1f - clarityFraction

    val surfaceFillAlpha: Int
        get() {
            val base = if (isDark) 0.30f else 0.48f
            return (base * glassFade * glassFade * 255f).toInt().coerceIn(0, 255)
        }

    val borderAlpha: Int
        get() {
            val alpha = 0.28f + ((0.24f - 0.28f) * clarityFraction)
            return (alpha * 255f).toInt().coerceIn(0, 255)
        }

    val shadowAlpha: Int
        get() = (0.22f * glassFade * glassFade * 255f).toInt().coerceIn(0, 255)
}
