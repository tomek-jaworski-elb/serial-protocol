package com.jaworski.shipsim.generate;

import com.jaworski.shipsim.codec.Frame;
import com.jaworski.shipsim.codec.FrameEncoder;
import com.jaworski.shipsim.scenario.FaultKind;
import com.jaworski.shipsim.scenario.FaultSpec;
import com.jaworski.shipsim.motion.ShipMotion;
import com.jaworski.shipsim.scenario.MotionParams;
import com.jaworski.shipsim.scenario.Scenario;
import com.jaworski.shipsim.scenario.ShipSpec;
import com.jaworski.shipsim.scenario.TimelineEntry;
import com.jaworski.shipsim.sink.TaggedFrame;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Turns a scenario into ticks. Each tick, every ship first takes the timeline entries that are
 * due, then produces its frame, has any due fault applied to it, and finally moves on by one
 * frame interval. Nothing here is random, so the same scenario always yields the same bytes.
 */
public final class ScenarioRunner implements Iterator<ScenarioRunner.Tick> {

    /** Everything emitted at one instant, ships in scenario order. */
    public record Tick(Duration at, List<TaggedFrame> frames) {
    }

    static final double CRLF_HEADING = 333.8;
    static final String UNKNOWN_PREFIX = "x9";
    private static final int DELIMITER_LENGTH = 2;

    private final Scenario scenario;
    private final List<ShipRun> ships = new ArrayList<>();
    private final FrameEncoder encoder = new FrameEncoder();
    private final double dtSeconds;
    private long tickIndex;

    public ScenarioRunner(Scenario scenario) {
        this.scenario = scenario;
        this.dtSeconds = scenario.rate().toNanos() / 1_000_000_000.0;
        for (ShipSpec spec : scenario.ships()) {
            if (spec.model() == null) {
                throw new IllegalArgumentException("ship " + spec.prefix() + " has no motion parameters");
            }
            ships.add(new ShipRun(spec, spec.model()));
        }
        for (FaultSpec fault : scenario.faults()) {
            ships.stream().filter(s -> s.spec.prefix().equals(fault.ship())).findFirst().orElseThrow().faults.add(fault);
        }
    }

    @Override
    public boolean hasNext() {
        return scenario.duration() == null || at(tickIndex).compareTo(scenario.duration()) < 0;
    }

    @Override
    public Tick next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Duration at = at(tickIndex);
        double seconds = at.toNanos() / 1_000_000_000.0;
        List<TaggedFrame> frames = new ArrayList<>();
        for (ShipRun ship : ships) {
            ship.applyTimeline(at);
            emit(ship, at, seconds, frames);
            ship.motion.step(dtSeconds);
        }
        tickIndex++;
        return new Tick(at, frames);
    }

    private Duration at(long index) {
        return scenario.rate().multipliedBy(index);
    }

    private void emit(ShipRun ship, Duration at, double seconds, List<TaggedFrame> out) {
        String prefix = ship.spec.prefix();
        Frame frame = ship.motion.frame();
        String fault = null;
        int copies = 1;
        boolean hold = false;

        for (FaultSpec due : ship.takeDueFaults(at)) {
            switch (due.kind()) {
                case SILENCE -> ship.silentUntil = at.plus(due.silence());
                case BAD_CRC, CRLF_IN_PAYLOAD, UNKNOWN_PREFIX, GLUE_NEXT, BURST -> {
                    fault = due.kind().yamlName();
                    if (due.kind() == FaultKind.CRLF_IN_PAYLOAD) {
                        frame = frame.withHeading(CRLF_HEADING);
                    } else if (due.kind() == FaultKind.UNKNOWN_PREFIX) {
                        frame = frame.withPrefix(UNKNOWN_PREFIX);
                    } else if (due.kind() == FaultKind.BURST) {
                        copies = due.count();
                    } else if (due.kind() == FaultKind.GLUE_NEXT) {
                        hold = true;
                    }
                }
            }
        }
        if (ship.silentUntil != null && at.compareTo(ship.silentUntil) < 0) {
            return;
        }

        byte[] bytes = encoder.encode(frame);
        if (fault != null && fault.equals(FaultKind.BAD_CRC.yamlName())) {
            bytes[bytes.length - DELIMITER_LENGTH - 1]++;
        }
        if (hold) {
            // The delimiter is lost: this frame waits, without its CR LF, for the ship's next one.
            ship.held = Arrays.copyOf(bytes, bytes.length - DELIMITER_LENGTH);
            return;
        }
        if (ship.held != null) {
            byte[] glued = Arrays.copyOf(ship.held, ship.held.length + bytes.length);
            System.arraycopy(bytes, 0, glued, ship.held.length, bytes.length);
            ship.held = null;
            bytes = glued;
            fault = FaultKind.GLUE_NEXT.yamlName();
        }
        for (int i = 0; i < copies; i++) {
            out.add(new TaggedFrame(bytes, prefix, fault, seconds));
        }
    }

    private static final class ShipRun {
        final ShipSpec spec;
        final ShipMotion motion;
        final Deque<FaultSpec> faults = new ArrayDeque<>();
        int nextTimelineEntry;
        Duration silentUntil;
        byte[] held;

        ShipRun(ShipSpec spec, MotionParams params) {
            this.spec = spec;
            this.motion = new ShipMotion(spec, params);
        }

        void applyTimeline(Duration at) {
            List<TimelineEntry> timeline = spec.timeline();
            while (nextTimelineEntry < timeline.size() && timeline.get(nextTimelineEntry).at().compareTo(at) <= 0) {
                motion.set(timeline.get(nextTimelineEntry).set());
                nextTimelineEntry++;
            }
        }

        List<FaultSpec> takeDueFaults(Duration at) {
            List<FaultSpec> due = new ArrayList<>();
            while (!faults.isEmpty() && faults.peekFirst().at().compareTo(at) <= 0) {
                due.add(faults.pollFirst());
            }
            return due;
        }
    }
}
