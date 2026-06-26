package cn.alittlecookie.lutista.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object FilenameExpander {

    private val VAR_PATTERN = Pattern.compile("<([^>]+)>")

    private val ILLEGAL_CHARS = Regex("""[\\/:*?"<>|]""")
    private val CONTROL_CHARS = Regex("""[\u0000-\u001f\u007f]""")

    /**
     * Expand a filename template into a complete filename (no extension).
     *
     * @param template  e.g. "<fname>-<filter>-<yyyy-MM-dd>-<D+1>"
     * @param fname     original filename without path and extension
     * @param filter    filter display name, or "" if no filter
     * @param daily     current <D+1> counter value (already incremented)
     * @param monthly   current <M+1> counter value
     * @param yearly    current <Y+1> counter value
     */
    fun expand(
        template: String,
        fname: String,
        filter: String,
        daily: Int,
        monthly: Int,
        yearly: Int
    ): String {
        val now = Date()
        val matcher = VAR_PATTERN.matcher(template)
        val sb = StringBuffer()

        while (matcher.find()) {
            val token = matcher.group(1)!!
            val replacement = resolveToken(token, now, fname, filter, daily, monthly, yearly)
            matcher.appendReplacement(sb, replacement)
        }
        matcher.appendTail(sb)

        return sanitize(sb.toString())
    }

    private fun resolveToken(
        token: String,
        now: Date,
        fname: String,
        filter: String,
        daily: Int,
        monthly: Int,
        yearly: Int
    ): String {
        return when (token) {
            "fname" -> fname.ifEmpty { "unknown" }
            "filter" -> filter.ifEmpty { "unknown" }
            else -> {
                // D+N / M+N / Y+N counters
                val counterMatch = Regex("^([DMY])\\+(-?\\d+)$").find(token)
                if (counterMatch != null) {
                    val base = when (counterMatch.groupValues[1]) {
                        "D" -> daily
                        "M" -> monthly
                        "Y" -> yearly
                        else -> return "<$token>"
                    }
                    val offset = counterMatch.groupValues[2].toIntOrNull() ?: 1
                    return (base + offset - 1).toString()
                }

                // Date format pattern
                return try {
                    SimpleDateFormat(token, Locale.US).format(now)
                } catch (_: Exception) {
                    "<$token>"
                }
            }
        }
    }

    private fun sanitize(name: String): String {
        var s = name.replace(ILLEGAL_CHARS, "_")
            .replace(CONTROL_CHARS, "")
            .trimEnd('.', ' ')
        if (s.isEmpty()) s = "untitled"
        return s
    }
}
