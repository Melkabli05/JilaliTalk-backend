package com.jilali.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Mirrors the test cases from the frontend's retired snake-to-camel.adapter.spec.ts. */
class SnakeToCamelJsonTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void convertsSnakeCaseKeysRecursively() throws Exception {
        JsonNode input = om.readTree("""
            {"user_id": 42, "head_info": {"head_url": "https://example.com/a.png"}}""");

        JsonNode result = SnakeToCamelJson.convert(input);

        assertEquals(om.readTree("""
            {"userId": 42, "headInfo": {"headUrl": "https://example.com/a.png"}}"""), result);
    }

    @Test
    void preservesLeadingUnderscoreInsteadOfTreatingItAsAWordBoundary() throws Exception {
        JsonNode input = om.readTree("""
            {"_id": "abc123"}""");

        assertEquals(input, SnakeToCamelJson.convert(input));
    }

    @Test
    void mapsOverArraysOfObjects() throws Exception {
        JsonNode input = om.readTree("""
            [{"user_id": 1}, {"user_id": 2}]""");

        JsonNode result = SnakeToCamelJson.convert(input);

        assertEquals(om.readTree("""
            [{"userId": 1}, {"userId": 2}]"""), result);
    }

    @Test
    void leavesScalarValuesUntouched() throws Exception {
        JsonNode input = om.readTree("42");
        assertEquals(input, SnakeToCamelJson.convert(input));
    }

    @Test
    void underscoreFollowedByNonLowercaseIsLeftLiteral() {
        assertEquals("foo_2", SnakeToCamelJson.toCamel("foo_2"));
        assertEquals("foo_Bar", SnakeToCamelJson.toCamel("foo_Bar"));
    }
}
