package com.jaworski.shipsim.generate;

import com.jaworski.shipsim.codec.FrameEncoder;
import com.jaworski.shipsim.generate.ScenarioRunner.Tick;
import com.jaworski.shipsim.scenario.MotionParams;
import com.jaworski.shipsim.scenario.Scenario;
import com.jaworski.shipsim.scenario.ScenarioLoader;
import com.jaworski.shipsim.sink.TaggedFrame;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the shipped {@code scenarios/faults.yaml} and checks every fault's bytes. */
class ScenarioRunnerTest {

    private static final Path SCENARIOS = Path.of("scenarios");
    private static List<TaggedFrame> frames;

    @BeforeAll
    static void runTheFaultScenario() throws IOException {
        frames = run(loadFaults());
    }

    @Test
    void everyShipSendsOnceATickUntilAFaultSaysOtherwise() {
        assertEquals(120, count("b2", null) + count("b2", "crlf-in-payload") + count("b2", "unknown-prefix"));
    }

    @Test
    void badCrcBreaksOnlyTheChecksumByte() {
        TaggedFrame frame = only("w1", "bad-crc");

        assertEquals(10.0, frame.seconds());
        byte[] bytes = frame.bytes();
        assertEquals(27, bytes.length);
        assertNotEquals(FrameEncoder.checksum(bytes, 24), bytes[24], "the checksum must not verify");
        assertEquals(FrameEncoder.CR, bytes[25]);
        assertEquals(FrameEncoder.LF, bytes[26]);
    }

    @Test
    void crlfInPayloadForcesTheDelimiterIntoTheHeadingBytes() {
        TaggedFrame frame = only("b2", "crlf-in-payload");

        assertEquals(20.0, frame.seconds());
        assertEquals(FrameEncoder.CR, frame.bytes()[2]);
        assertEquals(FrameEncoder.LF, frame.bytes()[3]);
        assertEquals(FrameEncoder.checksum(frame.bytes(), 24), frame.bytes()[24], "the checksum still verifies");
    }

    @Test
    void glueNextDeliversTwoFramesAsOneMessageOneTickLater() {
        assertTrue(at("w1", 30.0).isEmpty(), "the glued frame is held back");
        TaggedFrame glued = only("w1", "glue-next");

        assertEquals(31.0, glued.seconds());
        byte[] bytes = glued.bytes();
        assertEquals(27 - 2 + 27, bytes.length);
        assertEquals('w', bytes[0]);
        assertEquals('w', bytes[25], "the second frame starts right after the first one's checksum");
        assertEquals(FrameEncoder.CR, bytes[50]);
        assertEquals(FrameEncoder.LF, bytes[51]);

        TaggedFrame ladyMarie = only("l6", "glue-next");
        assertEquals(36.0, ladyMarie.seconds());
        assertEquals(29 - 2 + 29, ladyMarie.bytes().length);
    }

    @Test
    void unknownPrefixKeepsAValidChecksum() {
        TaggedFrame frame = only("b2", "unknown-prefix");

        assertEquals(40.0, frame.seconds());
        assertEquals('x', frame.bytes()[0]);
        assertEquals('9', frame.bytes()[1]);
        assertEquals(FrameEncoder.checksum(frame.bytes(), 24), frame.bytes()[24]);
    }

    @Test
    void burstRepeatsTheFrameCountTimesInOneTick() {
        List<TaggedFrame> burst = frames.stream().filter(f -> "burst".equals(f.fault())).toList();

        assertEquals(20, burst.size());
        assertTrue(burst.stream().allMatch(f -> f.seconds() == 50.0 && f.ship().equals("w1")));
        assertTrue(burst.stream().allMatch(f -> Arrays.equals(f.bytes(), burst.get(0).bytes())));
    }

    @Test
    void silenceStopsTheFramesButNotTheShip() {
        assertFalse(at("l6", 59.0).isEmpty());
        for (int s = 60; s < 90; s++) {
            assertTrue(at("l6", s).isEmpty(), "l6 must be silent at " + s + " s");
        }
        TaggedFrame resumed = at("l6", 90.0).get(0);
        TaggedFrame before = at("l6", 59.0).get(0);
        assertNull(resumed.fault());
        assertFalse(Arrays.equals(before.bytes(), resumed.bytes()), "the ship moved on while silent");
    }

    @Test
    void theSameScenarioAlwaysYieldsTheSameBytes() throws IOException {
        List<TaggedFrame> again = run(loadFaults());

        assertEquals(frames.size(), again.size());
        for (int i = 0; i < frames.size(); i++) {
            assertArrayEquals(frames.get(i).bytes(), again.get(i).bytes(), "message " + i);
        }
    }

    private static Scenario loadFaults() throws IOException {
        Map<String, MotionParams> params = ScenarioLoader.loadShipParameters(SCENARIOS.resolve("ships.yaml"));
        return ScenarioLoader.load(SCENARIOS.resolve("faults.yaml"), params);
    }

    private static List<TaggedFrame> run(Scenario scenario) {
        List<TaggedFrame> out = new ArrayList<>();
        ScenarioRunner runner = new ScenarioRunner(scenario);
        while (runner.hasNext()) {
            Tick tick = runner.next();
            out.addAll(tick.frames());
        }
        return out;
    }

    private static long count(String ship, String fault) {
        return frames.stream().filter(f -> f.ship().equals(ship) && (fault == null ? f.fault() == null : fault.equals(f.fault()))).count();
    }

    private static TaggedFrame only(String ship, String fault) {
        List<TaggedFrame> matching = frames.stream().filter(f -> f.ship().equals(ship) && fault.equals(f.fault())).toList();
        assertEquals(1, matching.size(), ship + " " + fault);
        return matching.get(0);
    }

    private static List<TaggedFrame> at(String ship, double seconds) {
        return frames.stream().filter(f -> f.ship().equals(ship) && f.seconds() == seconds).toList();
    }
}
