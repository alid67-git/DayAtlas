package com.dayatlas.app

/**
 * Splits a help_*.txt resource (title line, intro paragraph, then
 * blank-line-separated sections) into a tappable table of contents.
 * Section headings are detected structurally - a short, unpunctuated
 * paragraph - so this works the same across all 8 languages without any
 * per-language markup in the raw text files.
 */
object HelpContent {
    data class Section(val heading: String, val body: String)
    data class Parsed(val title: String, val intro: String, val sections: List<Section>, val footer: String)

    private const val HEADING_MAX_LENGTH = 60
    private val HEADING_DISQUALIFYING_END_CHARS = charArrayOf(
        '.', ',', '?', '!', ':', ';', '،', '؟', '。', '！', '，', '：',
    )

    private fun looksLikeHeading(block: String): Boolean =
        block.length <= HEADING_MAX_LENGTH && block.last() !in HEADING_DISQUALIFYING_END_CHARS

    fun parse(raw: String): Parsed {
        val blocks = raw.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (blocks.isEmpty()) return Parsed(title = "", intro = "", sections = emptyList(), footer = "")

        val title = blocks[0]
        val headingIndices = blocks.indices.filter { it > 0 && looksLikeHeading(blocks[it]) }
        val introEnd = headingIndices.firstOrNull() ?: blocks.size
        val intro = blocks.subList(1, introEnd).joinToString("\n\n")

        val sections = headingIndices.mapIndexed { pos, index ->
            val bodyStart = index + 1
            val bodyEnd = headingIndices.getOrNull(pos + 1) ?: blocks.size
            Section(heading = blocks[index], body = blocks.subList(bodyStart, bodyEnd).joinToString("\n\n"))
        }.toMutableList()

        // The last block (a "Developed by: ..." credit line) is short and
        // unpunctuated too, so it is always detected as a trailing heading
        // with an empty body - pull it out as the footer instead.
        val footer = if (sections.isNotEmpty() && sections.last().body.isEmpty()) {
            sections.removeAt(sections.size - 1).heading
        } else {
            ""
        }

        return Parsed(title = title, intro = intro, sections = sections, footer = footer)
    }
}
