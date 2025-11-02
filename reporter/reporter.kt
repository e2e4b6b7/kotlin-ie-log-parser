class IEReporter(
    private val source: KtSourceElement?,
    private val context: CheckerContext,
    private val reporter: DiagnosticReporter,
    private val error: KtDiagnosticFactory1<String>,
) {
    operator fun invoke(v: IEData) {
        val dataStr = buildList {
            addAll(serializeData(v))
        }.joinToString("; ")
        val str = "$borderTag $dataStr $borderTag"
        reporter.reportOn(source, error, str, context)
    }

    private val borderTag: String = "KLEKLE"

    private fun serializeData(v: IEData): List<String> = buildList {
        v::class.memberProperties.forEach { property ->
            add("${property.name}: ${property.getter.call(v)}")
        }
    }
}

data class IEData(
    val exprType: String? = null,
    val exposeKind: String? = null,
    val wholeType: String? = null,
    val exposedType: String? = null,
    val hasSuppression: Boolean? = null,
    val reasonInTheSameFile: Boolean? = null,
    val someNumber: Int? = null,
)

object FirMyChecker : _ {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(_) {
        val report = IEReporter(expression.source, context, reporter, FirErrors.MY_IE_ERROR)

        // ...

        report(
            IEData(
                exprType = expression::class.simpleName,
                exposeKind = exposeKind,
                wholeType = renderForDebugging(),
                exposedType = reason.classId.asString(),
                hasSuppression = hasSuppression,
                reasonInTheSameFile = reason.isInTheSameFile()
            )
        )
    }
}
