package devtools.cmd

/**
 * Serves as a common base class for command implementations.
 *
 * This base class verifies that each command:
 * - Has [ArgumentInfo] entry for each field in the command’s argument type [T].
 * - Does not provide extraneous argument information.
 * - Optional arguments declare a valid default value.
 *
 * Extending this class helps catch configuration errors early during initialization.
 *
 * @param T The argument type accepted by this command.
 */
abstract class BaseCommand<T> : Command<T> {
    init {
        val serialDesc = serializer.descriptor
        val fieldNames = (0 until serialDesc.elementsCount).map { serialDesc.getElementName(it) }.toSet()

        for (name in fieldNames) {
            // Ensure each field's info are provided
            val info = argInfo[name]
                ?: throw IllegalArgumentException(
                    "Command '$name' is missing ArgumentInfo for field '$name'"
                )

            // Ensure optional field specify a default value
            if (!info.required && info.defaultValue == null)
                throw IllegalArgumentException(
                    "Optional argument '$name' in command '${this.name}' must specify a default value."
                )
        }

        // Ensure argInfo does not have extra entries
        for (extra in argInfo.keys - fieldNames) {
            throw IllegalArgumentException(
                "Command '${this.name}' has extraneous ArgumentInfo for unknown field '$extra'"
            )
        }
    }
}
