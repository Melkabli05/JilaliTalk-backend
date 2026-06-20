package com.jilali.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Recursively renames JSON object keys from snake_case to camelCase. A faithful port of the
 * frontend's retired {@code snake-to-camel.adapter.ts}: moved here so the BFF — the boundary
 * that actually owns the upstream wire format — does this translation once, instead of every
 * frontend consumer redoing it per response.
 */
public final class SnakeToCamelJson {

    private SnakeToCamelJson() {
    }

    public static JsonNode convert(JsonNode node) {
        if (node == null || !(node.isArray() || node.isObject())) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode(node.size());
            for (JsonNode element : node) {
                result.add(convert(element));
            }
            return result;
        }
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            result.set(toCamel(entry.getKey()), convert(entry.getValue()));
        }
        return result;
    }

    /** Preserves leading underscores (e.g. {@code _id}) — a naming convention, not a word boundary. */
    static String toCamel(String key) {
        int leading = 0;
        while (leading < key.length() && key.charAt(leading) == '_') {
            leading++;
        }
        String prefix = key.substring(0, leading);
        String rest = key.substring(leading);

        StringBuilder camelCased = new StringBuilder(rest.length());
        int i = 0;
        while (i < rest.length()) {
            char c = rest.charAt(i);
            char next = i + 1 < rest.length() ? rest.charAt(i + 1) : '\0';
            if (c == '_' && next >= 'a' && next <= 'z') {
                camelCased.append(Character.toUpperCase(next));
                i += 2;
            } else {
                camelCased.append(c);
                i++;
            }
        }
        return prefix + camelCased;
    }
}
