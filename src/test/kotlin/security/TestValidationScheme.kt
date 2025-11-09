package security

import security.validation.FailStrategy
import security.validation.ValidationResult
import security.validation.ValidationScheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class TestValidationScheme {
    @Test
    fun `test validation pass`() {
        val result = ValidationScheme("example") { "HelloWorld Ktor" }
            .require("Contains space") { contains(" ") }
            .require("Contains 'Ktor'") { contains("Ktor") }
            .validate()
        assertIs<ValidationResult.Passed>(result)
    }

    @Test
    fun `test validation fail at non-last stage with default strategy`() {
        val result = ValidationScheme("example") { "Hello Ktor" }
            .require("Contains space") { contains(" ") }
            .require("Contains 'World' FAILED") { contains("World") }
            .require("Contains 'Ktor'") { contains("Ktor") }
            .validate()

        assertIs<ValidationResult.Failed>(result)
        assertNotNull(result.failStrategy)
        assertEquals(result.failStrategy, FailStrategy.Cancel)
    }

    @Test
    fun `test validation fail at non-last stage with specified strategy`() {
        val result = ValidationScheme("example") { "Hello Ktor" }
            .require("Contains space") { contains(" ") }
            .require("Contains 'World' FAILED", failStrategy = FailStrategy.Disconnect) { contains("World") }
            .require("Contains 'Ktor'") { contains("Ktor") }
            .validate()

        assertIs<ValidationResult.Failed>(result)
        assertNotNull(result.failStrategy)
        assertEquals(result.failStrategy, FailStrategy.Disconnect)
    }

    @Test
    fun `test validation throw error return error result`() {
        val result = ValidationScheme("example") { "Hello Ktor" }
            .require("Contains space") { contains(" ") }
            .require("Contains 'World' ERROR") { throw Exception() }
            .require("Contains 'Ktor'") { contains("Ktor") }
            .validate()

        assertIs<ValidationResult.Error>(result)
    }
}
