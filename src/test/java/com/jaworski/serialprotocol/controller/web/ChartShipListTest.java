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

    /**
     * The follow toggle exists, is a real button, and starts unavailable.
     *
     * <p>A ship that has never reported has no position to aim a camera at, so the control is
     * born unavailable and turns available on the first message. It carries {@code aria-disabled}
     * rather than {@code disabled} so the keyboard can reach it throughout; the consequence --
     * that it does not block clicks -- is handled in {@code chart-script.js}, and asserting the
     * attribute here is what keeps the two halves of that bargain visible together.</p>
     */
    @Test
    void everyRowHasAFollowToggleThatStartsUnavailable() throws Exception {
        for (Models ship : Models.values()) {
            assertThat(rowFor(ship))
                    .as("%s follow toggle", ship.getName())
                    .containsPattern("<button[^>]*class=\"follow-toggle\"")
                    .containsPattern("id=\"follow" + ship.getId() + "\"")
                    .contains("type=\"button\"")
                    .contains("aria-pressed=\"false\"")
                    .contains("aria-disabled=\"true\"");
        }
    }

    /**
     * The toggle names itself, and the name does not move.
     *
     * <p>The icon has no text, so without a label the control announces as "button". The label
     * sits on the button and stays constant -- pressed state travels in {@code aria-pressed}, not
     * in the wording. This is also the reason the toggle is its own element instead of the whole
     * row being the control: the row holds a packet counter that changes about once a second, and
     * a button wrapping it would have an accessible name that changed with it.</p>
     */
    @Test
    void theFollowToggleIsNamedAfterItsShip() throws Exception {
        for (Models ship : Models.values()) {
            assertThat(rowFor(ship))
                    .as("%s follow toggle names its ship", ship.getName())
                    .contains("aria-label=\"Follow " + ship.getName() + "\"");
        }
    }

    /**
     * The toggle comes before the colour chip.
     *
     * <p>Order is the whole point of the placement: first in the row, the toggles line up in a
     * column down the left edge while the LED dots keep theirs down the right, and neither
     * indicator has to move. Put the toggle at the other end and the dots stop being scannable
     * without looking straight at them, which is what they are for.</p>
     */
    @Test
    void theFollowToggleComesFirstInTheRow() throws Exception {
        for (Models ship : Models.values()) {
            String row = rowFor(ship);
            assertThat(row.indexOf("follow-toggle"))
                    .as("%s toggle precedes the colour chip", ship.getName())
                    .isLessThan(row.indexOf("ship-chip"));
        }
    }

    /**
     * The orientation button ships hidden and names itself.
     *
     * <p>It only means something while a ship is followed, so the page renders it hidden and
     * {@code chart-script.js} reveals it. Asserting the initial {@code hidden} here is what stops
     * a control that says COG from sitting over a chart that is plainly north-up.</p>
     *
     * <p>Its visible text is the orientation in force, which the script rewrites; the accessible
     * name spells the mode out in words, because HDG and COG are jargon a screen reader would
     * otherwise spell letter by letter.</p>
     */
    @Test
    void theOrientationButtonStartsHidden() throws Exception {
        assertThat(chartHtml())
                .as("orientation button, hidden until a ship is followed")
                .containsPattern("<button[^>]*id=\"orientationBtn\"")
                .containsPattern("id=\"orientationBtn\"(?s).{0,200}?hidden")
                .containsPattern("id=\"orientationBtn\"(?s).{0,200}?aria-label=");
    }

    /**
     * The compass ships hidden and is a control, not a picture.
     *
     * <p>Every mapping service checked hides its compass while the chart is north-up and makes it
     * a button meaning "reset bearing to north". North here means not following, so the button has
     * to exist and has to start hidden — a compass sitting over a plainly north-up chart would be
     * pointing at something nobody asked about.</p>
     */
    @Test
    void theCompassStartsHiddenAndIsAButton() throws Exception {
        assertThat(chartHtml())
                .as("compass button, hidden until the chart is rotated")
                .containsPattern("<button[^>]*id=\"compassBtn\"")
                .containsPattern("id=\"compassBtn\"(?s).{0,200}?hidden")
                .contains("aria-label=\"Reset map to north\"")
                .containsPattern("id=\"compassNeedle\"")
                .containsPattern("id=\"compassLabel\"");
    }

    /**
     * The chip's status sits OUTSIDE its button, and this is the assertion that says why.
     *
     * <p>The button leaves the mode and carries a label that never moves; the age of the last
     * message is a status, and putting it inside the control would give that control an accessible
     * name that changed every few seconds. That is the same objection which kept the ship row from
     * becoming a button, and it is invisible to the eye — the page looks identical either way — so
     * nothing but a test holds the two apart.</p>
     */
    @Test
    void theChipStatusIsNotInsideTheChipButton() throws Exception {
        String html = chartHtml();
        assertThat(html)
                .as("follow chip, hidden until a ship is followed")
                .containsPattern("id=\"followChip\"(?s).{0,120}?hidden")
                .containsPattern("<button[^>]*id=\"followChipBtn\"")
                .containsPattern("id=\"followChipStatus\"");

        int buttonStart = html.indexOf("id=\"followChipBtn\"");
        int buttonEnd = html.indexOf("</button>", buttonStart);
        int status = html.indexOf("id=\"followChipStatus\"");
        assertThat(status)
                .as("the age of the last message must not live inside the button that leaves the mode")
                .isGreaterThan(buttonEnd);
    }

    /**
     * Neither half of the chip announces itself.
     *
     * <p>At about one message a second a live region on the age would be an endless stream of
     * announcements — the trap the packet counter beside it already documents. Only crossing the
     * silence threshold is worth saying out loud, and that is a state change, not a ticking
     * number.</p>
     */
    @Test
    void theChipIsNotALiveRegion() throws Exception {
        String html = chartHtml();
        int chipStart = html.indexOf("id=\"followChip\"");
        int chipEnd = html.indexOf("</div>", html.indexOf("id=\"followChipStatus\""));
        assertThat(html.substring(chipStart, chipEnd))
                .as("chip must not announce every message")
                .doesNotContain("aria-live");
    }

    /**
     * The one thing that does announce itself lives outside the chip.
     *
     * <p>The chip may not be a live region — at about a message a second the age beside the button
     * would be an endless stream of announcements — but a followed ship going quiet for a minute is
     * worth saying once. That announcement therefore gets an element of its own, outside the chip,
     * written only when the state flips in either direction.</p>
     *
     * <p>Asserting it is <em>after</em> the chip is what keeps the two rules from colliding: moved
     * inside, it would make the whole chip live and the sibling test would go red for a reason that
     * has nothing to do with what broke.</p>
     */
    @Test
    void theSilenceAnnouncementIsALiveRegionOutsideTheChip() throws Exception {
        String html = chartHtml();
        assertThat(html)
                .as("silence announcement, hidden from sight but not from readers")
                .containsPattern("id=\"followAlert\"(?s).{0,120}?aria-live=\"polite\"")
                .containsPattern("class=\"[^\"]*visually-hidden[^\"]*\"[^>]*id=\"followAlert\"");

        int chipEnd = html.indexOf("</div>", html.indexOf("id=\"followChipStatus\""));
        assertThat(html.indexOf("id=\"followAlert\""))
                .as("it must sit outside the chip, or the chip becomes a live region")
                .isGreaterThan(chipEnd);
    }

    /**
     * The chip overlays the map, at every width.
     *
     * <p>It is positioned against {@code .map-pane}, the same box the zoom controls anchor to. That
     * placement is load-bearing on a phone: engaging the mode closes the ship list sheet, so the
     * chip becomes the only visible way back out. Rendered outside the pane it would scroll away
     * with the page and leave the mode with no exit on the one layout where the toggles are
     * hidden.</p>
     */
    @Test
    void theChipIsInsideTheMapPane() throws Exception {
        String html = chartHtml();
        int paneStart = html.indexOf("class=\"map-pane\"");
        int chip = html.indexOf("id=\"followChip\"");
        int listPanel = html.indexOf("id=\"shipListPanel\"");

        assertThat(paneStart).as("map pane").isNotNegative();
        assertThat(chip)
                .as("the chip must be rendered inside the map pane, after it opens")
                .isGreaterThan(paneStart);
        assertThat(chip)
                .as("and before the ship list, which is a sibling of the pane, not its content")
                .isLessThan(listPanel);
    }

    /**
     * The hover tooltip is configured to escape the panel that would clip it.
     *
     * <p>Bootstrap inserts a tooltip next to its trigger unless told otherwise. The trigger here
     * lives inside the ship list, which on mobile is an {@code overflow-y: auto} sheet — so
     * without {@code data-bs-container="body"} the tooltip is cut off at the sheet's top edge,
     * exactly where the first row is. Nothing about the markup looks wrong when it is missing and
     * nothing at all is wrong on desktop, which is why it is asserted rather than trusted.</p>
     *
     * <p>{@code data-bs-title}, not {@code title}: the native tooltip would otherwise flash under
     * the cursor before Bootstrap replaced it. The text starts at the unavailable wording because
     * that is what is true when the page is served — no ship has reported yet.</p>
     */
    @Test
    void theFollowTooltipIsPlacedAboveAndEscapesTheScrollingPanel() throws Exception {
        for (Models ship : Models.values()) {
            assertThat(rowFor(ship))
                    .as("%s follow tooltip", ship.getName())
                    .contains("data-bs-placement=\"top\"")
                    .contains("data-bs-container=\"body\"")
                    .contains("data-bs-title=\"" + ship.getName() + " has not reported yet\"");
            assertThat(rowFor(ship))
                    .as("%s must not carry a native title that would flash first", ship.getName())
                    .doesNotContain("title=\"Follow");
        }
    }

    /**
     * A CALL to the hull geometry function, with its arguments.
     *
     * <p>The lookbehind is load-bearing, exactly as in {@link #SCRIPT_INDICATOR_ID}: without it the
     * function's own declaration matches too, and its parameter list is
     * {@code (x, y, scale, angle, yy, xx, pp)} — no {@code cfg.scale} in sight — so the test fails
     * on the one line that could never be wrong.</p>
     */
    private static final Pattern HULL_VERTEX_CALL =
            Pattern.compile("(?<!function )computeShipVerticesForKonva\\(([^)]*)\\)", Pattern.DOTALL);

    /**
     * The silhouette must keep its size relative to the chart at every zoom.
     *
     * <p>{@code docs/chart-map-view.md} calls this a functional requirement — "Do not add any
     * zoom-dependent scaling here" — and the temptation to break it sits four lines away: the LOD
     * arrow beside the hull is deliberately given a CONSTANT ON-SCREEN size with
     * {@code SHIP_ARROW_SCREEN_PX / konvaStage.scaleX()}. Copying that idiom one step too far, to
     * keep a ship visible when zoomed out, would make hulls grow and shrink against the chart they
     * sit on. Nothing else would notice: Java cannot run Konva, and a screenshot at any single zoom
     * looks identical either way.</p>
     *
     * <p><b>This is a proxy and should be read as one.</b> It guards the shape of the code, not the
     * rendered ratio — it asserts that hull geometry is built from the constant {@code cfg.scale}
     * and never from the live stage scale. The behavioural check (hull length against a fixed chart
     * distance at 0.5×, 1×, 3×, 6× and 12×, ratio constant to five decimals) can only be done in a
     * browser, and was.</p>
     */
    @Test
    void theHullIsSizedFromTheChartAndNotFromTheZoom() throws Exception {
        String script = scriptSource();

        Matcher call = HULL_VERTEX_CALL.matcher(script);
        int calls = 0;
        while (call.find()) {
            calls++;
            String args = call.group(1).replaceAll("\\s+", " ").trim();
            assertThat(args)
                    .as("hull geometry must be built from the constant cfg.scale: %s", args)
                    .contains("cfg.scale");
            assertThat(args)
                    .as("hull geometry must not depend on the live zoom: %s", args)
                    .doesNotContain("scaleX()");
        }
        assertThat(calls)
                .as("the hull geometry function should still be called; has it been renamed?")
                .isGreaterThanOrEqualTo(2);

        int bodyStart = script.indexOf("function computeShipVerticesForKonva");
        assertThat(bodyStart).as("hull geometry function").isNotNegative();
        String body = script.substring(bodyStart, script.indexOf("\n}", bodyStart));
        assertThat(body)
                .as("the hull geometry function itself must know nothing about the stage")
                .doesNotContain("konvaStage");
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
        Set<String> inScript = MarkupSupport.allMatches(SCRIPT_INDICATOR_ID, scriptSource());
        Set<String> inHtml = MarkupSupport.allMatches(RENDERED_INDICATOR_ID, chartHtml());

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
