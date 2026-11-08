package io.github.headlesshq.headlessmc.gradle.dev

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JsonTest {
    @Test
    fun writesPrettyJson() {
        val map = linkedMapOf(
            "schemaVersion" to 1,
            "id" to "example",
            "optional" to false,
            "authors" to listOf("a", "b"),
            "empty" to emptyList<String>(),
            "nested" to mapOf("key" to "value"),
            "emptyMap" to emptyMap<String, String>(),
        )
        assertEquals(
            """
            {
              "schemaVersion": 1,
              "id": "example",
              "optional": false,
              "authors": [
                "a",
                "b"
              ],
              "empty": [],
              "nested": {
                "key": "value"
              },
              "emptyMap": {}
            }

            """.trimIndent(),
            Json.write(map),
        )
    }

    @Test
    fun escapesStrings() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\\u0001\"\n", Json.write("a\"b\\c\nd\te\u0001"))
    }
}
