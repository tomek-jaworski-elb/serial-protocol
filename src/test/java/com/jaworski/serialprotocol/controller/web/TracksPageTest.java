package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The tracks page: a missing script or stylesheet is a 404 the server never sees, so the test
 * checks every asset the rendered page asks for.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class TracksPageTest {

    /** Every {@code src} the rendered page asks the browser to load. */
    private static final Pattern SCRIPT_SRC = Pattern.compile("<script[^>]*\\ssrc=\"([^\"]+)\"");

    /** Every stylesheet it asks for, matched the same way. */
    private static final Pattern STYLESHEET_HREF =
            Pattern.compile("<link[^>]*rel=\"stylesheet\"[^>]*href=\"([^\"]+)\"|<link[^>]*href=\"([^\"]+)\"[^>]*rel=\"stylesheet\"");

    @Autowired
    private MockMvc mockMvc;

    private String tracksHtml;

    private String tracksHtml() throws Exception {
        if (tracksHtml == null) {
            tracksHtml = mockMvc.perform(get("/tracks"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
        }
        return tracksHtml;
    }

    /**
     * Every asset the page references has to exist: this page once asked for two scripts that had
     * never been committed, and the inline script died on the first thing it touched from them.
     */
    @Test
    void everyAssetThePageAsksForExists() throws Exception {
        String html = tracksHtml();
        Set<String> assets = new LinkedHashSet<>(MarkupSupport.allMatches(SCRIPT_SRC, html));
        assets.addAll(MarkupSupport.allMatches(STYLESHEET_HREF, html));

        assertThat(assets).as("the page must load something").isNotEmpty();
        for (String asset : assets) {
            if (asset.startsWith("http://") || asset.startsWith("https://") || asset.startsWith("data:")) {
                continue;
            }
            assertThat(MarkupSupport.staticAssetExists(asset))
                    .as("%s is referenced by /tracks but is not in static/", asset)
                    .isTrue();
        }
    }
}
