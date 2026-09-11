package com.jaworski.serialprotocol.controller.web;

import org.springframework.core.io.ClassPathResource;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared helpers for the tests that assert on rendered markup.
 *
 * <p>Both {@link ChartShipListTest} and {@link TracksPageTest} pull apart HTML that Thymeleaf
 * produced, and both had grown their own copy of the same two operations. They are pure functions
 * over a string, so there is nothing per-test about them and no reason for two versions to drift.</p>
 */
final class MarkupSupport {

    private MarkupSupport() {
    }

    /**
     * Every capturing group that matched anywhere in {@code text}, in the order found.
     *
     * <p>All groups, not just the first: a pattern that has to accept an attribute in either order
     * ends up with one alternative per order, and only one of them is non-null per match.</p>
     */
    static Set<String> allMatches(Pattern pattern, String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            for (int group = 1; group <= matcher.groupCount(); group++) {
                if (matcher.group(group) != null) {
                    found.add(matcher.group(group));
                }
            }
        }
        return found;
    }

    /**
     * Whether a browser asking for this absolute path would be served a file.
     *
     * <p>The check the server itself never performs: a missing static asset is a 404 the
     * application does not see and a {@code ReferenceError} in the browser, which is how one
     * absent file silently took an entire inline script with it on the tracks page.</p>
     */
    static boolean staticAssetExists(String absolutePath) {
        String path = absolutePath.startsWith("/") ? absolutePath.substring(1) : absolutePath;
        return new ClassPathResource("static/" + path).exists();
    }
}
