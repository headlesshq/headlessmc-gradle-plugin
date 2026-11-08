package io.github.headlesshq.headlessmc.gradle.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ArgsTest {
    @Test
    fun quoteEscapesQuotesAndBackslashes() {
        assertEquals("\"simple\"", Args.quote("simple"))
        assertEquals("\"with space\"", Args.quote("with space"))
        assertEquals("\"a\\\"b\"", Args.quote("a\"b"))
        assertEquals("\"C:\\\\Users\\\\me\"", Args.quote("C:\\Users\\me"))
        assertEquals("\"\"", Args.quote(""))
    }

    @Test
    fun joinQuotesEveryArgument() {
        assertEquals("\"-Xmx2G\" \"-Dkey=a value\"", Args.join(listOf("-Xmx2G", "-Dkey=a value")))
        assertEquals("", Args.join(emptyList()))
    }

    @Test
    fun splitOnWhitespace() {
        assertEquals(listOf("launch", "fabric", "1.21.4"), Args.split("  launch fabric\t1.21.4  "))
        assertEquals(emptyList<String>(), Args.split(""))
        assertEquals(emptyList<String>(), Args.split("   "))
    }

    @Test
    fun splitWithQuotes() {
        assertEquals(listOf("launch", "--jvm", "-Xmx2G -Da=b", "1.21"), Args.split("launch --jvm \"-Xmx2G -Da=b\" 1.21"))
        assertEquals(listOf("it's", "a b"), Args.split("it\\'s 'a b'"))
        assertEquals(listOf("a\\b"), Args.split("'a\\b'"))
        assertEquals(listOf(""), Args.split("\"\""))
        assertEquals(listOf("abc"), Args.split("a\"b\"c"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["simple", "with space", "a\"b", "C:\\Users\\me", "", "'single'", "tab\there", "\\"])
    fun splitReversesQuote(arg: String) {
        assertEquals(listOf(arg), Args.split(Args.quote(arg)))
        assertEquals(listOf(arg, arg), Args.split(Args.join(listOf(arg, arg))))
    }

    @Test
    fun splitFailsOnUnclosedQuote() {
        assertThrows<IllegalArgumentException> { Args.split("\"open") }
        assertThrows<IllegalArgumentException> { Args.split("'open") }
        assertThrows<IllegalArgumentException> { Args.split("trailing\\") }
    }

    @Test
    fun quoteForWindowsFollowsTheCRuntimeRules() {
        assertEquals("simple", Args.quoteForWindows("simple"))
        assertEquals("C:\\path\\x", Args.quoteForWindows("C:\\path\\x"))
        assertEquals("\"\"", Args.quoteForWindows(""))
        assertEquals("\"a b\"", Args.quoteForWindows("a b"))
        assertEquals("\"\\\"-Xmx2G\\\" \\\"-Da=b c\\\"\"", Args.quoteForWindows("\"-Xmx2G\" \"-Da=b c\""))
        // backslashes before a quote and at the end are doubled
        assertEquals("\"a\\\\\\\"b\"", Args.quoteForWindows("a\\\"b"))
        assertEquals("\"C:\\Program Files\\\\\"", Args.quoteForWindows("C:\\Program Files\\"))
    }
}
