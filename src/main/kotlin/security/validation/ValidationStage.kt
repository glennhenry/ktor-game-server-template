package security.validation

/**
 * Represents a single validation stage within a [ValidationScheme].
 *
 * Each stage performs a single logical check (predicate) and may optionally define
 * its own failure handling behavior via [failStrategy] and [failReason].
 *
 * @param name Human-readable label for the stage.
 * @param failStrategy Optional override for how this stage handles failure.
 * @param failReason Optional description of why this validation is required.
 * @param predicate The condition to evaluate; should return `true` if valid.
 */
data class ValidationStage<T>(
    val name: String = "",
    val failStrategy: FailStrategy? = null,
    val failReason: String? = null,
    val predicate: T.() -> Boolean
)
