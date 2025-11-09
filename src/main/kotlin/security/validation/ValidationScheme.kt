package security.validation

import utils.logging.Logger

/**
 * Defines a validation scheme composed of one or more validation stages.
 *
 * This class provides a DSL-style API for validating domain logic (such as player data).
 * Each validation step is expressed using [require], allowing multiple rules
 * to be chained together before running the final [validate] check.
 *
 * Example:
 * ```kotlin
 * ValidationScheme("BuildingCreate") { playerService }
 *     .validateFor(playerId)
 *     .require("Resource Check") { getResources() >= 100 }
 *     .require("XP Check") { getXP() >= 20 }
 *     .validate(failStrategy = FailStrategy.Disconnect)
 * ```
 *
 * - Validation stops at the first failed [require] stage.
 * - The fail strategy and reason are determined by:
 *   1. The stage's own `failStrategy` and `failReason` if defined.
 *   2. The global ones passed to [validate].
 *   3. Defaults: `FailStrategy.Cancel` and reason `"[Not specified]"`.
 *
 * @param schemeName A readable alias or identifier for this validation scheme (e.g., `"BuildingCreate"`).
 * @param factory A factory lambda that provides the execution context (e.g., `PlayerServices`).
 * @param T The type of the validation context provided by [factory].
 */
class ValidationScheme<T>(private val schemeName: String, private val factory: () -> T) {
    private var target: String = "[Undefined]"
    private val stages = mutableListOf<ValidationStage<T>>()

    /**
     * Defines the logical target of this validation (e.g., a player ID, username).
     *
     * Used primarily for logging and debugging. If not specified,
     * defaults to `"[Undefined]"`.
     */
    fun validateFor(target: String) = apply { this.target = target }

    /**
     * Adds a validation stage to the scheme.
     *
     * Each stage defines a named predicate that must evaluate to `true`.
     * If it fails, the associated [FailStrategy] and reason will be used
     * when reporting or handling the failure.
     *
     * @param stageName Descriptive name for this stage (e.g., `"XP Check"`).
     * @param failStrategy Optional strategy that determines how failure is handled.
     * @param failReason Optional reason string describing why the validation exists.
     * @param predicate The actual validation check to run.
     */
    fun require(
        stageName: String,
        failStrategy: FailStrategy = FailStrategy.Cancel,
        failReason: String = "[Not specified]",
        predicate: T.() -> Boolean
    ) = apply {
        stages.add(
            ValidationStage(stageName, failStrategy, failReason, predicate)
        )
    }

    /**
     * Executes all registered validation stages sequentially.
     *
     * Stops immediately when the first validation fails.
     *
     * The [ValidationResult] will be one of:
     * - [ValidationResult.Passed] = All checks succeeded.
     * - [ValidationResult.Failed] = Failed at a particular stage.
     * - [ValidationResult.Error] = Exception occurred during validation.
     *
     * The effective [FailStrategy] and reason follow this priority:
     * 1. Stage-level values (if set in [require]).
     * 2. Values passed to this method.
     * 3. Default fallbacks (`Cancel`, "[Not specified]").
     *
     * @param failStrategy Default failure handling strategy.
     * @param failReason Default reason for failure if not overridden.
     * @return A [ValidationResult] describing the outcome.
     */
    fun validate(
        failStrategy: FailStrategy = FailStrategy.Cancel,
        failReason: String = "[Not specified]"
    ): ValidationResult {
        val instance = factory()

        for ((index, stage) in stages.withIndex()) {
            val name = if (stage.name.isEmpty()) "stage-$index" else "stage-$index: ${stage.name}"
            val strategy = stage.failStrategy ?: failStrategy
            val reason = stage.failReason ?: failReason

            val passed = try {
                stage.predicate(instance)
            } catch (e: Exception) {
                Logger.error { "Error during validation check of '$schemeName' ($name) for target=$target" }
                return ValidationResult.Error(strategy, reason, e)
            }

            if (!passed) {
                return ValidationResult.Failed(strategy, reason)
            }
        }

        return ValidationResult.Passed
    }
}

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
