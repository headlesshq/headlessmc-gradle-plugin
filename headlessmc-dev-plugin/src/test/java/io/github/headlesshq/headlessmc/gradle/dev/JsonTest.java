package io.github.headlesshq.headlessmc.gradle.dev;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTest {
    @Test
    void writesPrettyJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", 1);
        map.put("id", "example");
        map.put("optional", false);
        map.put("authors", List.of("a", "b"));
        map.put("empty", List.of());
        map.put("nested", Map.of("key", "value"));
        map.put("emptyMap", Map.of());
        assertEquals("{\n"
            + "  \"schemaVersion\": 1,\n"
            + "  \"id\": \"example\",\n"
            + "  \"optional\": false,\n"
            + "  \"authors\": [\n"
            + "    \"a\",\n"
            + "    \"b\"\n"
            + "  ],\n"
            + "  \"empty\": [],\n"
            + "  \"nested\": {\n"
            + "    \"key\": \"value\"\n"
            + "  },\n"
            + "  \"emptyMap\": {}\n"
            + "}\n", Json.write(map));
    }

    @Test
    void escapesStrings() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\\u0001\"\n", Json.write("a\"b\\c\nd\te\u0001"));
    }

}
