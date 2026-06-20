package com.jilali.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Map;

/**
 * Test-only routes for {@link CamelCaseResponseFilterTest}. Lives under {@code /api} so the
 * filter (scoped by {@link CamelCaseResponseFilter}) actually applies to it, mirroring real
 * controllers that return upstream-shaped, snake_case-annotated DTOs.
 */
@Controller("/api/test-camel")
public class CamelCaseTestController {

    @Get("/dto")
    public Dto dto() {
        return new Dto(7L, new Nested("hi"), "abc123");
    }

    @Get("/map")
    public Map<String, Object> map() {
        return Map.of("user_id", 9, "head_url", "https://x");
    }

    @Get("/list")
    public List<Dto> list() {
        return List.of(new Dto(1L, new Nested("a"), "x"), new Dto(2L, new Nested("b"), "y"));
    }

    @Get(value = "/binary", produces = MediaType.APPLICATION_OCTET_STREAM)
    public HttpResponse<byte[]> binary() {
        return HttpResponse.ok(new byte[] {1, 2, 3}).contentType("bin/cc2018");
    }

    @Serdeable
    public record Dto(
        @JsonProperty("user_id") long userId,
        @JsonProperty("nested_info") Nested nestedInfo,
        @JsonProperty("_id") String id) {
    }

    @Serdeable
    public record Nested(@JsonProperty("head_url") String headUrl) {
    }
}
