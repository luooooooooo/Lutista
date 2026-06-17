package cn.alittlecookie.lutista.watermark.model

enum class Anchor { TL, TC, TR, ML, MC, MR, BL, BC, BR }

enum class ContainerMode { LINEAR_H, LINEAR_V, FREE }

enum class MainAxisAlign { START, CENTER, END, SPACE_BETWEEN, SPACE_EVENLY }

enum class CrossAxisAlign { START, CENTER, END, STRETCH }

enum class SizeMode { FIXED, WRAP_CONTENT }

enum class TextAlign { LEFT, CENTER, RIGHT }

enum class FontWeight { NORMAL, BOLD }

enum class OutputFormat { JPG, PNG }

data class Margin(val top: Float, val right: Float, val bottom: Float, val left: Float) {
    companion object {
        val ZERO = Margin(0f, 0f, 0f, 0f)
        fun all(value: Float) = Margin(value, value, value, value)
    }
}

data class Padding(val top: Float, val right: Float, val bottom: Float, val left: Float) {
    companion object {
        val ZERO = Padding(0f, 0f, 0f, 0f)
        fun all(value: Float) = Padding(value, value, value, value)
    }
}

data class BorderWidth(val top: Float, val right: Float, val bottom: Float, val left: Float) {
    companion object {
        val ZERO = BorderWidth(0f, 0f, 0f, 0f)
        fun all(value: Float) = BorderWidth(value, value, value, value)
    }
}
