package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.jaworski.serialprotocol.dto.Models;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
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

    /** The desktop bar and the mobile bar each list Tracks right after Chart; on /tracks it is the active one. */
    @Test
    void theMenuLinksToTracksRightAfterChartInBothVariants() throws Exception {
        Pattern pair = Pattern.compile("href=\"/chart\"[^>]*>Chart</a>\\s*<a[^>]*href=\"/tracks\"[^>]*>Tracks</a>");
        String home = mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertThat(pair.matcher(home).results().count()).as("desktop and mobile variants").isEqualTo(2);
        assertThat(Pattern.compile("class=\"fb-link active\"[^>]*href=\"/tracks\"").matcher(tracksHtml()).results().count())
                .as("Tracks is active on its own page, in both variants").isEqualTo(2);
    }

    /** One checkbox row per ship, from Models: id, name and colour, none ticked. */
    @Test
    void everyShipIsRenderedFromModelsWithItsColour() throws Exception {
        String html = tracksHtml();
        for (Models ship : Models.values()) {
            assertThat(html).contains("name=\"ship\" value=\"" + ship.getId() + "\"")
                    .contains("--ship-color: " + ship.getColor())
                    .contains(">" + ship.getName() + "<")
                    .contains("data-ship-note=\"" + ship.getId() + "\"");
        }
        assertThat(html).doesNotContain("checked=\"checked\" name=\"ship\"");
    }

    /** The help dialog, its opener and the driven buttons' keys (the script turns them into aria-keyshortcuts). */
    @Test
    void theKeyboardShortcutsDialogAndTheKeysOfTheDrivenButtonsArePresent() throws Exception {
        String html = tracksHtml();
        assertThat(html).contains("id=\"shortcutsDialog\"", "id=\"shortcutsBtn\"", "id=\"shortcutsOn\"");
        assertThat(html).containsPattern("id=\"playBtn\"[^>]*data-keys=\"Space K\"")
                .containsPattern("id=\"printFrameBtn\"[^>]*data-keys=\"F\"");
    }

    /** A hidden, unnamed field per ship (it is never submitted), tied to the ship's checkbox. */
    @Test
    void everyShipHasAHiddenCaptainFieldAndThePrintFormCarriesTheCaptains() throws Exception {
        String html = tracksHtml();
        for (Models ship : Models.values()) {
            assertThat(html).containsPattern("aria-controls=\"captain-" + ship.getId() + "\"[^>]*aria-expanded=\"false\"")
                    .containsPattern("<input[^>]*id=\"captain-" + ship.getId() + "\"[^>]*aria-label=\"Captain of "
                            + Pattern.quote(ship.getName()) + "\"[^>]*maxlength=\"120\"");
        }
        assertThat(html).doesNotContainPattern("<input[^>]*id=\"captain-[^>]*name=")
                .containsPattern("<input[^>]*\\shidden[^>]*id=\"captain-1\"")
                .contains("<input type=\"hidden\" name=\"captains\">");
    }

    @Test
    void theFormControlsArePresent() throws Exception {
        String html = tracksHtml();
        assertThat(html).contains("id=\"fromInput\"", "id=\"toInput\"", "id=\"rangeError\"", "id=\"tracksMessage\"",
                "id=\"refreshBtn\"", "id=\"clearBtn\"", "id=\"printBtn\"",
                "id=\"periodSlider\"", "id=\"showSilhouettes\"", "id=\"timeLabels\"", "id=\"gapInput\"",
                "id=\"printFrameBtn\"", "id=\"removeFrameBtn\"", "id=\"konvaContainer\"",
                "id=\"zoomInBtn\"", "id=\"drawHint\"", "id=\"i-printer\"", "aria-label=\"Zoom in\"", "id=\"zoomOutBtn\"", "id=\"zoomResetBtn\"", "id=\"scaleBarLine\"",
                "id=\"playBtn\"", "id=\"stopBtn\"", "id=\"resetBtn\"", "id=\"speedSlider\"", "id=\"replayScrubber\"");
    }

    /** The page needs the zone, the gap and the server's clock at render time. */
    @Test
    void theModelAttributesAreRendered() throws Exception {
        String html = tracksHtml();
        assertThat(html).contains("Times in <span>Europe/Warsaw</span>");
        assertThat(html).containsPattern("data-now=\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\"");
        assertThat(html).containsPattern("id=\"gapInput\"[^>]*value=\"10\"");
    }

    /** Guard: the shared pinch has to be defined before the page script that calls it, on both pages. */
    @Test
    void theSharedPinchScriptLoadsBeforeThePageScript() throws Exception {
        String chart = mockMvc.perform(get("/chart")).andReturn().getResponse().getContentAsString();
        assertThat(tracksHtml().indexOf("/js/common/stage-pinch.js")).as("/tracks").isPositive()
                .isLessThan(tracksHtml().indexOf("/js/tracks-script.js"));
        assertThat(chart.indexOf("/js/common/stage-pinch.js")).as("/chart").isPositive()
                .isLessThan(chart.indexOf("/js/chart-script.js"));
        assertThat(MarkupSupport.staticSource("js/tracks-script.js")).as("/tracks binds the pinch")
                .containsPattern("bindStagePinch\\(\\s*konvaStage\\s*,");
    }

    /** Guard: the stage follows its box through a ResizeObserver, not the window, or a rotation leaves it stale. */
    @Test
    void theStageFollowsItsBoxNotTheWindow() throws Exception {
        String script = MarkupSupport.staticSource("js/tracks-script.js");
        assertThat(script).doesNotContainPattern("window\\s*\\.addEventListener\\(\\s*'resize'")
                .containsPattern("new\\s+ResizeObserver\\([\\s\\S]*?\\)\\s*\\.observe\\(\\s*container\\s*\\)");
    }

    /**
     * Guard: a hidden button (Print frame on touch) must not be pressed by its key; Refresh scrolls its result into
     * view, the replay bar stopping above the bottom nav; a scrub ends only for the finger that started it, a second
     * finger cannot take it over, and a scrub released at the end does not restart the replay.
     */
    @Test
    void aHiddenButtonIsNotPressedAndTheResultIsRevealed() throws Exception {
        String script = MarkupSupport.staticSource("js/tracks-script.js");
        assertThat(script).containsPattern("const\\s+press\\s*=[^\\n]*getClientRects\\(\\)");
        int start = script.indexOf("async function refresh");
        assertThat(start).as("refresh").isNotNegative();
        assertThat(script.substring(start, script.indexOf("\n}", start))).contains("revealResult()");
        assertThat(MarkupSupport.staticSource("css/tracks.css")).containsPattern("scroll-margin-bottom:\\s*var\\(--stack-chrome\\)");
        assertThat(script).containsPattern("e\\.pointerId\\s*!==\\s*scrubPointer")
                .containsPattern("if\\s*\\(\\s*scrubPointer\\s*!==\\s*null\\s*\\)\\s*return")
                .containsPattern("wasPlaying\\s*&&\\s*clockT\\s*<\\s*replayEnd");
    }

    @Test
    void theSkipButtonsAreRendered() throws Exception {
        assertThat(tracksHtml()).containsPattern("id=\"backBtn\"[^>]*>\\s*-10 s\\s*<")
                .containsPattern("id=\"forwardBtn\"[^>]*>\\s*\\+10 s\\s*<");
    }

    /** Guard: the script and the stylesheet must agree on when the panel sits beside the map. */
    @Test
    void theSideLayoutMediaQueryIsTheSameInScriptAndStylesheet() throws Exception {
        Matcher js = Pattern.compile("matchMedia\\('([^']+)'\\)")
                .matcher(MarkupSupport.staticSource("js/tracks-script.js"));
        assertThat(js.find()).as("matchMedia literal in tracks-script.js").isTrue();
        Matcher css = Pattern.compile("@media\\s+([^{]+?)\\s*\\{")
                .matcher(MarkupSupport.staticSource("css/tracks.css"));
        assertThat(css.find()).as("first @media in tracks.css").isTrue();
        assertThat(css.group(1)).isEqualTo(js.group(1));
    }

    /** Guard (hull geometry): hulls are sized by hull.scale, never a stage scale; the fixed-size marker is replay-only. */
    @Test
    void hullsUseTheirOwnScaleAndOnlyTheReplayDrawsTheMarker() throws Exception {
        String draw = MarkupSupport.staticSource("js/tracks-draw.js");
        int hulls = draw.indexOf("function hullsSceneFunc");
        int silhouettes = draw.indexOf("function silhouettesSceneFunc");
        assertThat(hulls).isNotNegative();
        assertThat(silhouettes).isNotNegative();
        String hullsBody = draw.substring(hulls, draw.indexOf("\n}", hulls));
        String silhouettesBody = draw.substring(silhouettes, draw.indexOf("\n}", silhouettes));
        assertThat(hullsBody).containsPattern("computeShipVerticesForKonva\\([^)]*\\bhull\\.scale\\b")
                .as("the live zoom (absScale) must not reach the hull geometry")
                .doesNotContainPattern("computeShipVerticesForKonva\\([^;]*absScale")
                .contains("markerRadius");
        assertThat(silhouettesBody).doesNotContain("markerRadius");
    }
}
