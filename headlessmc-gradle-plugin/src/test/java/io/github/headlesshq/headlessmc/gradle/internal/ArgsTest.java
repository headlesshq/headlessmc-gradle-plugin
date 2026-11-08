package io.github.headlesshq.headlessmc.gradle.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArgsTest {
    @Test
    void quoteEscapesQuotesAndBackslashes() {
        assertEquals("\"simple\"", Args.quote("simple"));
        assertEquals("\"with space\"", Args.quote("with space"));
        assertEquals("\"a\\\"b\"", Args.quote("a\"b"));
        assertEquals("\"C:\\\\Users\\\\me\"", Args.quote("C:\\Users\\me"));
        assertEquals("\"\"", Args.quote(""));
    }

    @Test
    void joinQuotesEveryArgument() {
        assertEquals("\"-Xmx2G\" \"-Dkey=a value\"", Args.join(List.of("-Xmx2G", "-Dkey=a value")));
        assertEquals("", Args.join(List.of()));
    }

    @Test
    void splitOnWhitespace() {
        assertEquals(List.of("launch", "fabric", "1.21.4"), Args.split("  launch fabric\t1.21.4  "));
        assertEquals(List.of(), Args.split(""));
        assertEquals(List.of(), Args.split("   "));
    }

    @Test
    void splitWithQuotes() {
        assertEquals(List.of("launch", "--jvm", "-Xmx2G -Da=b", "1.21"), Args.split("launch --jvm \"-Xmx2G -Da=b\" 1.21"));
        assertEquals(List.of("it's", "a b"), Args.split("it\\'s 'a b'"));
        assertEquals(List.of("a\\b"), Args.split("'a\\b'"));
        assertEquals(List.of(""), Args.split("\"\""));
        assertEquals(List.of("abc"), Args.split("a\"b\"c"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"simple", "with space", "a\"b", "C:\\Users\\me", "", "'single'", "tab\there", "\\"})
    void splitReversesQuote(String arg) {
        assertEquals(List.of(arg), Args.split(Args.quote(arg)));
        assertEquals(List.of(arg, arg), Args.split(Args.join(List.of(arg, arg))));
    }

    @Test
    void splitFailsOnUnclosedQuote() {
        assertThrows(IllegalArgumentException.class, () -> Args.split("\"open"));
        assertThrows(IllegalArgumentException.class, () -> Args.split("'open"));
        assertThrows(IllegalArgumentException.class, () -> Args.split("trailing\\"));
    }

}
