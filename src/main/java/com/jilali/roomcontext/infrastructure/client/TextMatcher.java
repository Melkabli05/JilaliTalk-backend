package com.jilali.roomcontext.infrastructure.client;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/** Case- and accent-insensitive substring matching, tokenized on whitespace. Ported from the
 *  legacy com.jilali.room.TextMatcher (self-contained pure logic, no dependency on any legacy
 *  client/service). */
public final class TextMatcher {

    private static final Pattern COMBINING_DIACRITICS = Pattern.compile("\\p{M}");

    private TextMatcher() {}

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return COMBINING_DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase().trim();
    }

    public static boolean matches(List<String> haystacks, String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String[] tokens = normalizedQuery.split("\\s+");
        List<String> normalizedHaystacks = haystacks.stream()
                .filter(h -> h != null && !h.isBlank())
                .map(TextMatcher::normalize)
                .toList();
        for (String token : tokens) {
            boolean found = false;
            for (String haystack : normalizedHaystacks) {
                if (haystack.contains(token)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
