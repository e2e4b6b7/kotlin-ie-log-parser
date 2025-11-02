package codegen

import org.gradle.api.*
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.*

abstract class LogParserCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 1) user-visible extension
        val ext = project.extensions.create(
            "codegen",
            LogParserCodegenExtension::class.java
        )

        // 2) generation task wired into Kotlin compilation
        val generate = project.tasks.register<GenerateLogParser>("generateLogParser") {
            outputDir.set(project.layout.buildDirectory.dir("generated/sources/logparser/kotlin"))
            dataDir.set(ext.dataDir)
        }

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.tasks.named("compileKotlin") {
                dependsOn(generate)
            }
            project.extensions.getByType<SourceSetContainer>()
                .named("main") { java.srcDir(generate.map { it.outputDir }) }
        }
    }
}

abstract class LogParserCodegenExtension {
    abstract val dataDir: DirectoryProperty
}
