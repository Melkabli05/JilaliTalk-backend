package com.jilali.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;

/**
 * Rewrites every JSON response body under {@code /api/**} from the upstream Jilali's
 * snake_case wire format to camelCase, so the Angular frontend receives idiomatic JSON
 * directly and needs no client-side adapter.
 * <p>
 * DTOs keep their {@code @JsonProperty(snake_case)} annotations — those stay correct (and
 * necessary) for deserializing the upstream response in {@link com.jilali.client.JilaliClient}.
 * This filter only rewrites what we send back out our own door, by re-deriving the JSON tree
 * from the already-built response body and renaming its keys — the body object itself
 * (a record, Map, or List) is left untouched.
 */
@ServerFilter(ServerFilter.MATCH_ALL_PATTERN)
public class CamelCaseResponseFilter {

    private final ObjectMapper om;

    public CamelCaseResponseFilter(ObjectMapper om) {
        this.om = om;
    }

    @ResponseFilter
    public void camelCase(MutableHttpResponse<?> response) {
        Object body = response.body();
        if (body == null || body instanceof byte[] || body instanceof JsonNode) {
            return;
        }
        if (!isJson(response)) {
            return;
        }
        JsonNode tree = om.valueToTree(body);
        response.body(SnakeToCamelJson.convert(tree));
    }

    private boolean isJson(HttpResponse<?> response) {
        return response.getContentType()
            .map(MediaType::getExtension)
            .map(ext -> ext.equalsIgnoreCase("json"))
            .orElse(true); // Micronaut defaults an unset content type to application/json on send.
    }
}
