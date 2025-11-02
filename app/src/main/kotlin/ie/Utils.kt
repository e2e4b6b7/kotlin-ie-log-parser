package ie

/**
 * Pretty–prints a 2-D table of values.  Column widths are chosen automatically.
 *
 * @param rows     the table body (all rows should have the same length; extra
 *                 cells are ignored, missing cells printed as empty string)
 * @param headers  optional header row; if present it is rendered above a line
 *                 of dashes.
 */
fun printTable(rows: List<List<Any?>>, headers: List<String>? = null) {
    if (rows.isEmpty() && headers.isNullOrEmpty()) {
        println("<empty table>")
        return
    }

    val cols = maxOf(headers?.size ?: 0, rows.maxOfOrNull { it.size } ?: 0)
    if (cols == 0) return

    // compute column widths
    val widths = IntArray(cols) { 0 }
    fun String.updateWidth(i: Int) {
        widths[i] = maxOf(widths[i], length)
    }

    headers?.forEachIndexed { i, h -> h.updateWidth(i) }
    rows.forEach { row -> row.forEachIndexed { i, cell -> cell.toString().updateWidth(i) } }

    fun String.pad(i: Int) = padEnd(widths[i])
    fun renderRow(row: List<String>) =
        row.mapIndexed { i, cell -> cell.pad(i) }.joinToString(" | ")

    headers?.let {
        println(renderRow(it))
        println(widths.joinToString("-+-") { "-".repeat(it) })
    }

    rows.forEach { r -> println(renderRow(r.map { it.toString() })) }
}

/**
 * Groups [items] by [selector], skips `null`s, counts occurrences,
 * sorts by descending count, then prints a two-column table.
 *
 * Example:
 * ```kotlin
 * printGroupingTable(entries) { it.project }       // default header = "Value"
 * printGroupingTable(entries, header = "Project") { it.project }
 * ```
 */
fun <T, V : Any> List<T>.printGroupingTable(
    header: String = "Value",
    selector: (T) -> V?,
) {
    if (isEmpty()) return

    val counts = mapNotNull(selector)
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<V, Int>> { it.value }.thenBy { it.key.toString() })

    val rows = counts.map { listOf(it.key, it.value) }
    printTable(rows, listOf(header, "Count"))
}

/**
 * Prints high-level statistics for a [ParsedLogs] object:
 *   • lists projects that failed to compile or lacked diagnostics
 *   • shows a project-frequency table for all interesting entries
 */
fun ParsedLogs.printGeneralInfo() {
    if (failedProjects.isNotEmpty()) {
        println("❌ Projects that did **not compile successfully**:")
        failedProjects.forEach { println("   • $it") }
        println()
    }

    if (noDiagnosticsProjects.isNotEmpty()) {
        println("⚠️  Projects with **no diagnostics collected**:")
        noDiagnosticsProjects.forEach { println("   • $it") }
        println()
    }

    println("📊 Entries per project:")
    entries.printGroupingTable(header = "Project") { it.project }
}
