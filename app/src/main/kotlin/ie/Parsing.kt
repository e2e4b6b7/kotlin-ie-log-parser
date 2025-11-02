package ie

import com.charleskorn.kaml.Yaml
import generated.DATA_DIR
import generated.LogEntry
import generated.parseLogEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile

/** One payload line (or multi-line entry) together with its project and the raw line. */
data class ProjectEntry(
    val e: LogEntry,
    val project: String,
    val location: String,
) {
    override fun toString(): String = buildString {
        append("project=$project\n")
        append("entry=$e\n")
        append("location:$location\n")
    }
}

/** Aggregated result of parsing a directory tree full of log files. */
data class ParsedLogs(
    val failedProjects: List<String>,
    val noDiagnosticsProjects: List<String>,
    val entries: List<ProjectEntry>,
)

/**
 * Parse every regular file underneath [rootDir].
 */
fun parseAllLogs(rootDir: Path = Path.of(DATA_DIR)): ParsedLogs {
    val failed = mutableListOf<String>()
    val noDiag = mutableListOf<String>()
    val records = mutableListOf<ProjectEntry>()

    Files.walk(rootDir)
        .filter { it.isRegularFile() }
        .forEach { path -> parseFile(path, failed, noDiag, records) }

    return ParsedLogs(failed, noDiag, records)
}

private fun parseFile(
    file: Path,
    failed: MutableList<String>,
    noDiag: MutableList<String>,
    out: MutableList<ProjectEntry>,
) {
    val text = Files.readString(file)
    if (text.isBlank()) return
    val root = Yaml.default.decodeFromString(schemas.Root.serializer(), text)

    failed += root.failedBuilds
    noDiag += root.buildWithNoDiagnostics

    root.logs.forEach { (project, entries) ->
        entries.forEach { e ->
            if (e.diagnosticName != "IE_DIAGNOSTIC") return@forEach
            val parsed = e.message.parseLogEntry() ?: return@forEach
            out += ProjectEntry(parsed, project, e.location)
        }
    }
}
