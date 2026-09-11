package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.Models;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The tracks page, guarded against the three faults that between them stopped it drawing anything.
 *
 * <p>All three were invisible from the server's side — the page returned 200 and looked complete —
 * and invisible from the code's side too, because each failure was in a different layer: a script
 * tag pointing at a file nobody had shipped, a JavaScript object defined only in a script this page
 * does not load, and two form values the server could not parse. Only the browser saw any of it,
 * and only in its console.</p>
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
     * The test that would have caught it: every asset the page references has to exist.
     *
     * <p>This page asked for {@code /js/common/enums.js} and {@code /js/nouislider/nouislider.min.js}
     * and neither had ever been in the repository — the second one is real, but it lives under
     * {@code /nouislider/} and only the stylesheet was ever committed. A missing script is a 404 the
     * server never sees and a {@code ReferenceError} the moment anything touches what it should have
     * defined, which is how one absent file silently took the entire inline script with it.</p>
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

    /**
     * The time range the form submits must be parseable as {@code HH:mm}.
     *
     * <p>{@code TrackService} parses these with that pattern and, when the parse fails, its filter
     * rejects every log item — so the values 20 and 80 the page used to submit emptied the result
     * set on the server before the browser was ever involved. The page then reported "No data for
     * selected models", which reads like an answer rather than a defect.</p>
     */
    @Test
    void theSubmittedTimeRangeIsParseable() throws Exception {
        String html = tracksHtml();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

        for (String field : new String[]{"minValue", "maxValue"}) {
            Matcher matcher = Pattern.compile("name=\"" + field + "\"[^>]*value=\"([^\"]*)\"").matcher(html);
            assertThat(matcher.find()).as("%s input", field).isTrue();
            String value = matcher.group(1);

            // 24:00 is not a LocalTime; TrackService turns it into 23:59 before parsing, so accept
            // exactly that sentinel and nothing else loose.
            if ("24:00".equals(value)) {
                continue;
            }
            try {
                LocalTime.parse(value, formatter);
            } catch (DateTimeParseException e) {
                throw new AssertionError(field + " must be HH:mm or the sentinel 24:00, but was: " + value, e);
            }
        }
    }

    /**
     * The ship palette reaches the page from the enum that owns it.
     *
     * <p>The drawing loop used to call {@code ModelsOfShips.getColorFromId()}, an object defined
     * only inside {@code chart-script.js} — which this page does not load. Inlining the map from
     * {@link Models} keeps one definition of the palette instead of a second copy that could drift
     * away from the first without anything noticing.</p>
     */
    @Test
    void theShipPaletteIsInlinedForEveryModel() throws Exception {
        String html = tracksHtml();
        // The CALL, not the bare name: the script keeps a comment explaining what it used to reach
        // for and why that failed, and that explanation is worth more than a stricter assertion.
        assertThat(html)
                .as("the page must not call into the chart page's script objects")
                .doesNotContain("ModelsOfShips.");

        for (Models ship : Models.values()) {
            assertThat(html)
                    .as("%s colour, inlined from the Models enum", ship.getName())
                    .contains("\"" + ship.getId() + "\":\"" + ship.getColor() + "\"");
        }
    }
}
