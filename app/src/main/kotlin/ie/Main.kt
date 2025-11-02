package ie

fun main() {
    val parsed = parseAllLogs()
    parsed.printGeneralInfo()
    println("Total IE entries: ${parsed.entries.size}")
}
