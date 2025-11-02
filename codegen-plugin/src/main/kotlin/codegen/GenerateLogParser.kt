package codegen

import com.charleskorn.kaml.Yaml
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.FileSpec
import kotlinx.serialization.decodeFromString
import org.gradle.api.DefaultTask
import schemas.Root
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*


abstract class GenerateLogParser : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputDirectory
    abstract val dataDir: DirectoryProperty

    @TaskAction
    fun generate() {
        // collect and analyse
        val messages = collectIeMessages()
        val schema = analyseSchema(messages)
        if (schema.isEmpty()) {
            logger.warn("No $MARKER blocks found in ${dataDir.get().asFile}; skipping code-gen.")
            return
        }

        // codegen
        FileSpec.builder(pkg, className).apply {
            addDataDirConst(dataDir.get().asFile.absolutePath)
            addEnums(schema)
            addDataClass(schema)
            addParseFunction(schema)
        }.build().writeTo(outputDir.get().asFile)

        logger.lifecycle("Generated $pkg.$className, ${schema.count { it.kind is FieldKind.EnumKind }} enum(s), parser")
    }

    private fun FileSpec.Builder.addDataDirConst(dataDirPath: String) {
        addProperty(
            PropertySpec.builder("DATA_DIR", STRING_CLASSNAME, KModifier.CONST)
                .initializer("%S", dataDirPath)
                .build()
        )
    }

    private fun FileSpec.Builder.addEnums(schema: List<FieldInfo>) {
        schema.mapNotNull { it.kind as? FieldKind.EnumKind }
            .forEach { kind ->
                val enumSpec = TypeSpec.enumBuilder(kind.enumName)
                    .addEnumConstants(kind.values)
                    .build()
                addType(enumSpec)
            }
    }

    private fun FileSpec.Builder.addDataClass(schema: List<FieldInfo>) {
        val ctor = FunSpec.constructorBuilder()
        val props = mutableListOf<PropertySpec>()
        schema.forEach { info ->
            val type = info.kotlinType.copy(nullable = info.nullable)
            ctor.addParameter(info.name, type)
            props += PropertySpec.builder(info.name, type).initializer(info.name).build()
        }
        addType(
            TypeSpec.classBuilder(className)
                .addModifiers(KModifier.DATA)
                .primaryConstructor(ctor.build())
                .addProperties(props)
                .build()
        )
    }

    private fun FileSpec.Builder.addParseFunction(schema: List<FieldInfo>) {
        addFunction(
            FunSpec.builder("parseLogEntry")
                .receiver(STRING_CLASSNAME)
                .returns(ClassName(pkg, className).copy(nullable = true))
                .addKdoc(
                    "Parse a single log line. Returns *null* if it lacks a %L … %L block.",
                    MARKER, MARKER
                )
                .addStatement("val first = indexOf(%S)", MARKER)
                .addStatement("if (first == -1) return null")
                .addStatement("val second = indexOf(%S, first + %L)", MARKER, markerLen)
                .addStatement("if (second == -1 || second <= first) return null")
                .addStatement(
                    "val kv = substring(first + %L, second).trim()" +
                            ".split(%S)" +
                            ".associate { val i = it.indexOf(%S); " +
                            "it.substring(0, i).trim() to it.substring(i + 1).trim() }",
                    markerLen, ";", ":"
                )
                .addStatement(
                    "return %T(%L)",
                    ClassName(pkg, className),
                    schema.joinToString { fieldExpr(it) }
                )
                .build())
    }


    //──────────────────────── inputs: collect IE messages ─────────────────────
    fun collectIeMessages(): List<String> {
        val ieMessages = mutableListOf<String>()
        for (file in dataDir.get().asFile.walkTopDown()) {
            if (!file.isFile) continue
            val text = file.readText()
            if (text.isBlank()) continue
            val root = try {
                Yaml.default.decodeFromString<Root>(text)
            } catch (t: Throwable) {
                logger.error("Failed to parse YAML file: ${file.absolutePath}: ${t.message}")
                continue
            }
            root.logs.values.forEach { logEntries ->
                logEntries.forEach { logEntry ->
                    if (logEntry.diagnosticName == IE_DIAGNOSTIC_NAME) {
                        ieMessages.add(logEntry.message)
                    }
                }
            }
        }
        return ieMessages
    }

    private fun analyseSchema(lines: List<String>): List<FieldInfo> {
        val stats = linkedMapOf<String, FieldStats>()
        for (line in lines) {
            val first = line.indexOf(MARKER)
            val second = if (first != -1) line.indexOf(MARKER, first + MARKER.length) else -1
            if (first == -1 || second == -1 || second <= first) {
                logger.warn("Log message lacks proper $MARKER markers: '$line'")
                continue
            }
            line.substring(first + MARKER.length, second)
                .trim()
                .split(";")
                .forEach { pair ->
                    val idx = pair.indexOf(':'); if (idx == -1) return@forEach
                    val k = pair.substring(0, idx).trim()
                    val v = pair.substring(idx + 1).trim()

                    val st = stats.getOrPut(k) { FieldStats() }
                    when {
                        v == "null" -> st.seenNull = true
                        v.equals("true", ignoreCase = true) || v.equals("false", ignoreCase = true) -> st.seenBoolean =
                            true

                        v.toIntOrNull() != null -> st.seenInt = true
                        v.toDoubleOrNull() != null -> st.seenDouble = true
                        else -> st.uniqueStrings += v
                    }
                }
        }
        return stats.map { (k, s) -> s.toFieldInfo(k) }
    }

    private fun fieldExpr(info: FieldInfo): String = when (val kind = info.kind) {
        is FieldKind.IntKind -> if (info.nullable)
            "kv[\"${info.name}\"]!!.takeUnless { it==\"null\" }?.toInt()"
        else
            "kv[\"${info.name}\"]!!.toInt()"

        is FieldKind.DoubleKind -> if (info.nullable)
            "kv[\"${info.name}\"]!!.takeUnless { it==\"null\" }?.toDouble()"
        else
            "kv[\"${info.name}\"]!!.toDouble()"

        is FieldKind.BooleanKind -> if (info.nullable)
            "kv[\"${info.name}\"]!!.takeUnless { it==\"null\" }?.equals(\"true\", ignoreCase = true)"
        else
            "kv[\"${info.name}\"]!!.equals(\"true\", ignoreCase = true)"

        is FieldKind.EnumKind -> if (info.nullable)
            "kv[\"${info.name}\"]!!.takeUnless { it==\"null\" }?.let { $pkg.${kind.enumName}.valueOf(it) }"
        else
            "$pkg.${kind.enumName}.valueOf(kv[\"${info.name}\"]!!)"

        FieldKind.StringKind -> if (info.nullable)
            "kv[\"${info.name}\"]!!.takeUnless { it==\"null\" }"
        else
            "kv[\"${info.name}\"]!!"
    }

    private class FieldStats {
        var seenNull = false
        var seenBoolean = false
        var seenInt = false
        var seenDouble = false
        val uniqueStrings = mutableSetOf<String>()

        fun toFieldInfo(name: String): FieldInfo {
            val kind: FieldKind = when {
                seenBoolean && uniqueStrings.isEmpty() && !seenInt && !seenDouble -> FieldKind.BooleanKind
                seenInt && !seenDouble && uniqueStrings.isEmpty() -> FieldKind.IntKind
                seenDouble && uniqueStrings.isEmpty() -> FieldKind.DoubleKind
                uniqueStrings.canBeEnum() -> FieldKind.EnumKind(enumName(name), uniqueStrings)
                else -> FieldKind.StringKind
            }
            return FieldInfo(name, kind, nullable = seenNull)
        }

        private fun enumName(field: String): String =
            field.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + "Enum"

        private fun MutableSet<String>.canBeEnum(): Boolean =
            size in 1..10 && all { it.length < 80 && it.matches(Regex("[A-Za-z][_A-Za-z0-9]*")) }
    }

    private data class FieldInfo(
        val name: String,
        val kind: FieldKind,
        val nullable: Boolean,
    ) {
        val kotlinType: TypeName = when (kind) {
            FieldKind.BooleanKind -> BOOLEAN_CLASSNAME
            FieldKind.IntKind -> INT_CLASSNAME
            FieldKind.DoubleKind -> DOUBLE_CLASSNAME
            FieldKind.StringKind -> STRING_CLASSNAME
            is FieldKind.EnumKind -> ClassName("generated", kind.enumName)
        }
    }

    private sealed interface FieldKind {
        object BooleanKind : FieldKind
        object IntKind : FieldKind
        object DoubleKind : FieldKind
        object StringKind : FieldKind
        data class EnumKind(val enumName: String, val values: Set<String>) : FieldKind
    }

    private fun TypeSpec.Builder.addEnumConstants(values: Set<String>): TypeSpec.Builder =
        apply { values.forEach { addEnumConstant(it) } }

    companion object {
        const val IE_DIAGNOSTIC_NAME: String = "IE_DIAGNOSTIC"
        const val MARKER = "KLEKLE"
        const val markerLen = MARKER.length

        const val pkg = "generated"
        const val className = "LogEntry"

        private val STRING_CLASSNAME = ClassName("kotlin", "String")
        private val BOOLEAN_CLASSNAME = ClassName("kotlin", "Boolean")
        private val INT_CLASSNAME = ClassName("kotlin", "Int")
        private val DOUBLE_CLASSNAME = ClassName("kotlin", "Double")
    }
}
