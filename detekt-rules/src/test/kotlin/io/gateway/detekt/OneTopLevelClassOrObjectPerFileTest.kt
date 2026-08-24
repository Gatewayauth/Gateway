package io.gateway.detekt

import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class OneTopLevelClassOrObjectPerFileTest {

    private val rule = OneTopLevelClassOrObjectPerFile()

    @Test
    fun flagsSecondTopLevelType() {
        val findings = rule.lint(
            """
            class A
            class B
            """.trimIndent(),
        )
        assertEquals(1, findings.size, "second top-level class must be flagged")
    }

    @Test
    fun flagsClassPlusObject() {
        val findings = rule.lint(
            """
            class A
            object B
            """.trimIndent(),
        )
        assertEquals(1, findings.size)
    }

    @Test
    fun allowsSingleClassWithTopLevelFunctions() {
        val findings = rule.lint(
            """
            class A
            fun helper() = Unit
            val constant = 1
            """.trimIndent(),
        )
        assertEquals(0, findings.size, "one class plus top-level members is allowed")
    }
}
