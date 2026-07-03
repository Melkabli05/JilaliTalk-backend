package com.jilali.room;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Java port of the frontend's {@code createSearchMatcher} (text-search.util.ts): case- and
 * accent-insensitive substring matching, tokenized on whitespace. Kept as static pure functions
 * (no Micronaut bean) so it's trivially unit-testable without a mocking framework, mirroring
 * {@code JilaliGatewayTest}'s pattern of testing extracted pure logic directly.
 */
public final class TextMatcher {

    private static final Pattern COMBINING_DIACRITICS = Pattern.compile("\\p{M}");

    private TextMatcher() {
    }

    /** NFD-decompose, strip combining diacritics, lowercase, trim — mirrors normalizeForSearch(). */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return COMBINING_DIACRITICS.matcher(decomposed).replaceAll("").toLowerCase().trim();
    }

    /**
     * True if every whitespace-separated token of {@code query} is a substring of at least one
     * haystack (case/accent-insensitive). A blank query matches everything.
     */
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
