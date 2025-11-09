package security.validation

/**
 * Represents the outcome of a [ValidationScheme] execution.
 *
 * A validation may either **pass**, **fail**, or **encounter an error**.
 * Each non-success result carries contextual information such as the
 * associated [FailStrategy], reason, and any captured exception.
 */
sealed class ValidationResult(
    val failStrategy: FailStrategy? = null,
    val failReason: String? = null,
    val error: Throwable? = null
) {
    /**
     * The validation passed successfully where all conditions were met.
     */
    object Passed : ValidationResult()

    /**
     * The validation failed because one or more conditions did not meet the requirements.
     */
    class Failed(failStrategy: FailStrategy, failReason: String) : ValidationResult(failStrategy, failReason)

    /**
     * The validation could not be performed due to an internal error.
     */
    class Error(failStrategy: FailStrategy, failReason: String, error: Throwable) :
        ValidationResult(failStrategy, failReason, error)
}
