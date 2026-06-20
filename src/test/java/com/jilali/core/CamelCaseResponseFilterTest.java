package com.jilali.core;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end proof that {@link CamelCaseResponseFilter} rewrites real controller responses —
 * not just the {@link SnakeToCamelJson} unit, but the full pipeline: Micronaut Serde
 * serializes the {@code @JsonProperty(snake_case)}-annotated DTO, then this filter rewrites
 * the resulting JSON tree to camelCase before it reaches the wire.
 */
@MicronautTest
class CamelCaseResponseFilterTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void dtoResponseIsRewrittenToCamelCase() {
        String body = client.toBlocking().retrieve(HttpRequest.GET("/api/test-camel/dto"));

        assertTrue(body.contains("\"userId\":7"), body);
        assertTrue(body.contains("\"nestedInfo\":{\"headUrl\":\"hi\"}"), body);
        assertFalse(body.contains("user_id"), body);
        assertFalse(body.contains("nested_info"), body);
        assertFalse(body.contains("head_url"), body);
    }

    @Test
    void leadingUnderscoreFieldIsPreserved() {
        String body = client.toBlocking().retrieve(HttpRequest.GET("/api/test-camel/dto"));

        assertTrue(body.contains("\"_id\":\"abc123\""), body);
    }

    @Test
    void mapResponseIsRewrittenToCamelCase() {
        String body = client.toBlocking().retrieve(HttpRequest.GET("/api/test-camel/map"));

        assertTrue(body.contains("\"userId\":9"), body);
        assertTrue(body.contains("\"headUrl\":\"https://x\""), body);
    }

    @Test
    void listResponseIsRewrittenElementByElement() {
        String body = client.toBlocking().retrieve(HttpRequest.GET("/api/test-camel/list"));

        assertTrue(body.contains("\"userId\":1"), body);
        assertTrue(body.contains("\"userId\":2"), body);
        assertFalse(body.contains("user_id"), body);
    }

    @Test
    void binaryResponseIsLeftUntouched() {
        byte[] body = client.toBlocking().retrieve(HttpRequest.GET("/api/test-camel/binary"), byte[].class);

        assertTrue(java.util.Arrays.equals(new byte[] {1, 2, 3}, body));
    }
}
