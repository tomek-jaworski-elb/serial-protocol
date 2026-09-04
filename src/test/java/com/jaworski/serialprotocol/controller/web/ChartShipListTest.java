package com.jaworski.serialprotocol.controller.web;

import com.jaworski.serialprotocol.dto.Models;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Markup of the ship list on {@code /chart}: the per-ship led indicator and packet counter.
 *
 * <p>{@code MapControllerTest.test_ChartEndpoint} asserts only the status and content type, so
 * nothing here was covered. That is how the packet counter shipped a whole release carrying
 * {@code class="visually-hidden"} — present, updated on every message, and invisible.</p>
 *
 * <p>Every test starts from {@link #shipListRows} so it fails loudly on an empty list instead
 * of passing vacuously: {@code th:each} over no ships executes its body zero times, and an
 * assertion looking for absence would be satisfied by a page that renders nothing at all.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ChartShipListTest {

    /** One {@code <li class="ship-row">} up to the closing tag. */
    private static final Pattern SHIP_ROW =
            Pattern.compile("<li[^>]*class=\"ship-row\".*?</li>", Pattern.DOTALL);

    /**
     * The counter element on its own. The assertions about what the counter must NOT carry have
     * to be aimed at it and nothing else: run against the whole row they would also fail for the
     * visually-hidden label in front of it or a live region on the course readout beside it.
     *
     * <p>The class attribute is matched on a word boundary rather than as the literal
     * {@code class="rx-count"}. Pinned to the literal, the exact regression these tests are named
     * for — {@code class="rx-count visually-hidden"} — stops matching the pattern altogether, so
     * the run still goes red but on "no counter in the row at all" instead of on the assertion
     * that describes the defect. It was blind to any other hiding class, {@code d-none} included,
     * for the same reason.</p>
     */
    private static final Pattern COUNTER_SPAN = Pattern.compile(
            "<span[^>]*class=\"[^\"]*\\brx-count\\b[^\"]*\"[^>]*>[^<]*</span>");

    /** The two id shapes the indicator depends on, as rendered into the page. */
    private static final Pattern RENDERED_INDICATOR_ID =
            Pattern.compile("id=\"(led\\d+|rs_model\\d+_no)\"");

    /**
     * The same ids as {@code modelsConfig} keys. The lookbehind is load-bearing: without it
     * {@code led:} also matches inside a key such as {@code scaled:} or {@code enabled:}, which
     * would inflate the count and fail this test over a change that never touched an id.
     */
    private static final Pattern SCRIPT_INDICATOR_ID = Pattern.compile(
            "(?<![A-Za-z0-9_$])(?:led|rsField):\\s*\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    /**
     * The rendered page, fetched once per test method. JUnit builds a fresh instance per method,
     * so this needs no resetting.
     *
     * <p>Without it every helper issued its own request -- and because counterSpanOf() goes
     * through rowFor(), a single six-ship loop rendered the page eighteen times. Worse than the
     * cost: two assertions inside one iteration were then made against two different responses,
     * so any per-request variation would surface as a contradiction between them.</p>
     */
    private String renderedChart;

    @Test
    void everyShipGetsItsOwnRow() throws Exception {
        List<String> rows = shipListRows();

        for (Models ship : Models.values()) {
            assertThat(rows)
                    .as("a row per ship, mentioning %s", ship.getName())
                    .anyMatch(row -> row.contains(ship.getName()));
        }
        assertThat(rows).hasSize(Models.values().length);
    }

    /**
     * The regression this page shipped with. Bootstrap's {@code .visually-hidden} hides content
     * with {@code clip: rect(0,0,0,0) !important}, which cannot be undone from another rule — the
     * element keeps its full measured width, so it reads as correct to everything but the eye.
     * The only fix is the class being absent, which is exactly what this asserts.
     */
    @Test
    void theCounterIsNotHiddenFromSight() throws Exception {
        for (Models ship : Models.values()) {
            assertThat(counterSpanOf(ship))
                    .as("%s counter must be visible", ship.getName())
                    .containsPattern("class=\"[^\"]*\\brx-count\\b")
                    .doesNotContain("visually-hidden");
        }
    }

    @Test
    void theCounterStartsAtThreeZerosAndIsDescribed() throws Exception {
        for (Models ship : Models.values()) {
            String row = rowFor(ship);
            // Zero-padded, because .rx-count reserves a fixed 3ch box; ShipCounter wraps at 999.
            assertThat(counterSpanOf(ship))
                    .as("%s starts at 000", ship.getName())
                    .endsWith(">000</span>");
            // The number is named by visually-hidden text in FRONT of the counter, not by the
            // counter's own title: a title on a span that already has text becomes the accessible
            // description, which readers skip at default verbosity, and never shows on touch.
            assertThat(row)
                    .as("%s counter is named for a screen reader", ship.getName())
                    .containsPattern("class=\"visually-hidden\"[^>]*>Packets received from "
                            + ship.getName() + ":</span>");
            assertThat(row)
                    .as("%s counter keeps its hover title", ship.getName())
                    .contains("title=\"Packets received from " + ship.getName() + "\"");
        }
    }

    /**
     * At roughly one message per second per ship, announcing the counter would mean six new
     * numbers every second in a screen reader. It is not a live region today; this keeps it
     * from quietly becoming one.
     */
    @Test
    void theCounterIsNotALiveRegion() throws Exception {
        for (Models ship : Models.values()) {
            assertThat(counterSpanOf(ship))
                    .as("%s counter must not announce itself", ship.getName())
                    .doesNotContain("aria-live");
        }
    }

    @Test
    void everyRowHasALedDot() throws Exception {
        for (Models ship : Models.values()) {
            assertThat(rowFor(ship))
                    .as("%s led dot, with the class that carries every bit of its styling",
                            ship.getName())
                    .containsPattern("id=\"led" + ship.getId() + "\"[^>]*class=\"led-dot\"");
        }
    }

    /**
     * The Java/JavaScript contract, read out of the JavaScript itself rather than restated here.
     *
     * <p>{@code chart-script.js} finds both elements with {@code getElementById}. A miss is a
     * plain {@code return} — no console error, no failed request, nothing to notice — so an id
     * renamed on either side silently stops the indicator from ever lighting. Parsing the real
     * {@code modelsConfig} means this test cannot drift out of step with it.</p>
     */
    @Test
    void theRenderedIdsAreExactlyTheOnesTheScriptLooksUp() throws Exception {
        Set<String> inScript = allMatches(SCRIPT_INDICATOR_ID, scriptSource());
        Set<String> inHtml = allMatches(RENDERED_INDICATOR_ID, chartHtml());

        assertThat(inScript)
                .as("two ids per ship parsed out of modelsConfig")
                .hasSize(Models.values().length * 2);
        // Set equality, not containment, and so in both directions: an id the script wants but
        // the page never renders leaves an indicator that can never light, while one the page
        // renders but the script never looks up is an element nothing will ever update. Checking
        // a single direction catches only one of those two identical-looking silent failures.
        assertThat(inHtml).isEqualTo(inScript);
    }

    // --- helpers ---

    private static String scriptSource() throws Exception {
        return new ClassPathResource("static/js/chart-script.js")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    private static Set<String> allMatches(Pattern pattern, String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private String chartHtml() throws Exception {
        if (renderedChart == null) {
            // Status asserted here rather than left implicit: a redirect or a 500 has an empty
            // body, which every assertion in this class would otherwise report as "no ship rows".
            renderedChart = mockMvc.perform(get("/chart"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
        }
        return renderedChart;
    }

    /**
     * A {@code List}, deliberately: the rows are an ordered sequence whose entries could
     * legitimately coincide. Collected into a Set, two identical rows would silently become one
     * and the size assertion below would fail for a reason its message does not mention.
     */
    private List<String> shipListRows() throws Exception {
        List<String> rows = new ArrayList<>();
        Matcher matcher = SHIP_ROW.matcher(chartHtml());
        while (matcher.find()) {
            rows.add(matcher.group());
        }
        assertThat(rows)
                .as("no ship rows means every other assertion here would pass on nothing")
                .hasSize(Models.values().length);
        return rows;
    }

    private String counterSpanOf(Models ship) throws Exception {
        Matcher matcher = COUNTER_SPAN.matcher(rowFor(ship));
        assertThat(matcher.find())
                .as("%s row must contain a counter at all", ship.getName())
                .isTrue();
        return matcher.group();
    }

    private String rowFor(Models ship) throws Exception {
        return shipListRows().stream()
                .filter(row -> row.contains(ship.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + ship.getName()));
    }
}
