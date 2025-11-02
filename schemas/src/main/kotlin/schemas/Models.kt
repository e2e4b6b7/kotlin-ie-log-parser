package schemas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Root(
    @SerialName("kotlin.git-branch")
    val branch: String,
    @SerialName("kotlin.git-commit")
    val commit: String,
    @SerialName("kup-builds-with-no-diagnostics-found")
    val buildWithNoDiagnostics: List<String>,
    @SerialName("failed-kup-builds")
    val failedBuilds: List<String>,
    @SerialName("compilation-diagnostics-log")
    val logs: Map<String, List<LogEntry>>,
)

@Serializable
data class LogEntry(
    val location: String,
    @SerialName("name")
    val diagnosticName: String,
    val message: String,
)
