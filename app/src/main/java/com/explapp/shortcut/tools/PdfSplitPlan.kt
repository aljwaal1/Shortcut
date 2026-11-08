package com.explapp.shortcut.tools

data class PdfPageRange(val firstPage: Int, val lastPage: Int, val name: String)

/** UI page numbers are 1-based; the PdfBox API receives zero-based indices. */
object PdfSplitPlan {
    fun pages(pageCount: Int): List<PdfPageRange> {
        require(pageCount > 0) { "PDF has no pages" }
        val digits = maxOf(3, pageCount.toString().length)
        return (1..pageCount).map { page ->
            PdfPageRange(page, page, "Page_${page.toString().padStart(digits, '0')}.pdf")
        }
    }

    fun chapters(pageCount: Int, starts: List<Int>): List<PdfPageRange> {
        require(pageCount > 0) { "PDF has no pages" }
        val positions = (starts.filter { it in 1..pageCount } + 1).distinct().sorted()
        val digits = maxOf(2, positions.size.toString().length)
        return positions.mapIndexed { index, start ->
            val end = if (index + 1 < positions.size) positions[index + 1] - 1 else pageCount
            PdfPageRange(start, end, "Chapter_${(index + 1).toString().padStart(digits, '0')}.pdf")
        }
    }

    /** Comma or newline separated inclusive ranges, e.g. 1-12, 13-25. */
    fun parseRanges(input: String, pageCount: Int): List<PdfPageRange> {
        require(pageCount > 0) { "PDF has no pages" }
        val tokens = input.split(',', '\n', ';').map(String::trim).filter(String::isNotEmpty)
        require(tokens.isNotEmpty()) { "Enter at least one range" }
        val ranges = tokens.map { token ->
            val match = Regex("^(\\d+)\\s*[-–]\\s*(\\d+)$").matchEntire(token)
                ?: throw IllegalArgumentException("Invalid range: $token")
            val first = match.groupValues[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid page number")
            val last = match.groupValues[2].toIntOrNull() ?: throw IllegalArgumentException("Invalid page number")
            require(first in 1..pageCount && last in first..pageCount) { "Range outside PDF: $token" }
            first to last
        }
        require(ranges.first().first == 1 && ranges.last().second == pageCount) { "Ranges must cover every page" }
        require(ranges.zipWithNext().all { (a, b) -> a.second + 1 == b.first }) {
            "Ranges must be consecutive, with no overlaps or gaps"
        }
        val digits = maxOf(2, ranges.size.toString().length)
        return ranges.mapIndexed { index, range ->
            PdfPageRange(range.first, range.second, "Chapter_${(index + 1).toString().padStart(digits, '0')}.pdf")
        }
    }
}
