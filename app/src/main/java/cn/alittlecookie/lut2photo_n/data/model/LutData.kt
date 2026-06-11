package cn.alittlecookie.lut2photo_n.data.model

data class LutData(
    val size: Int,
    val data: FloatArray,
    val title: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LutData) return false
        return size == other.size && data.contentEquals(other.data) && title == other.title
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + data.contentHashCode()
        result = 31 * result + title.hashCode()
        return result
    }
}

data class UnifiedLut(
    val size: Int,
    val data: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnifiedLut) return false
        return size == other.size && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + data.contentHashCode()
        return result
    }
}
