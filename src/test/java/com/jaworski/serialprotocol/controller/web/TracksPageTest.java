package com.jaworski.serialprotocol.controller.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.jaworski.serialprotocol.dto.Models;

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
}
