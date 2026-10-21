package com.jaworski.shipsim.generate;

import com.jaworski.shipsim.codec.Frame;
import com.jaworski.shipsim.codec.FrameEncoder;
import com.jaworski.shipsim.motion.ShipMotion;
import com.jaworski.shipsim.scenario.FaultKind;
import com.jaworski.shipsim.scenario.FaultSpec;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Turns a scenario into ticks. Each tick, every ship first takes the timeline entries that are
 * due, then produces its frame, has any due fault applied to it, and finally moves on by one
 * frame interval. Nothing here is random, so the same scenario always yields the same bytes.
 *
 * <p>Rules beyond the fault table: a fault due while its ship is silent stays queued and fires
 * on the first frame after the silence (a held {@code glue-next} frame waits the same way); a
 * {@code glue-next} on the last tick of a run has nothing to glue to, so the frame goes out as
 * it is; several faults due on one tick all apply, and the message is tagged with their names
 * joined by {@code +}; a {@code glue-next} that arrives while a frame is already held adds to
 * what is held, so three frames can arrive as one message.</p>
 */
public final class ScenarioRunner implements Iterator<ScenarioRunner.Tick> {

    /** Everything emitted at one instant, ships in scenario order. */
    public record Tick(Duration at, List<TaggedFrame> frames) {
    }

    static final double CRLF_HEADING = 333.8;
    static final String UNKNOWN_PREFIX = "x9";
    static final String FAULT_SEPARATOR = "+";
    private static final int DELIMITER_LENGTH = 2;

    private final Scenario scenario;
    private final List<ShipRun> ships = new ArrayList<>();
    private final FrameEncoder encoder = new FrameEncoder();
    private final double dtSeconds;
    private long tickIndex;

    public ScenarioRunner(Scenario scenario) {
        this.scenario = scenario;
        this.dtSeconds = scenario.rate().toNanos() / 1_000_000_000.0;
        Map<String, ShipRun> byPrefix = new LinkedHashMap<>();
        for (ShipSpec spec : scenario.ships()) {
            if (spec.model() == null) {
                throw new IllegalArgumentException("ship " + spec.prefix() + " has no motion parameters");
            }
            ShipRun run = new ShipRun(spec, spec.model());
            ships.add(run);
            byPrefix.put(spec.prefix(), run);
        }
        for (FaultSpec fault : scenario.faults()) {
            ShipRun run = byPrefix.get(fault.ship());
            if (run == null) {
                throw new IllegalArgumentException("fault at " + fault.at() + " names ship " + fault.ship()
                        + ", which is not in the scenario");
            }
            run.faults.add(fault);
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
        boolean lastTick = scenario.duration() != null && at(tickIndex + 1).compareTo(scenario.duration()) >= 0;
        List<TaggedFrame> frames = new ArrayList<>();
        for (ShipRun ship : ships) {
            ship.applyTimeline(at);
            emit(ship, at, seconds, lastTick, frames);
            ship.motion.step(dtSeconds);
        }
        tickIndex++;
        return new Tick(at, frames);
    }

    private Duration at(long index) {
        return scenario.rate().multipliedBy(index);
    }

    private void emit(ShipRun ship, Duration at, double seconds, boolean lastTick, List<TaggedFrame> out) {
        if (ship.isSilentAt(at)) {
            return;
        }
        List<FaultSpec> due = ship.takeDueFaults(at);
        int silence = indexOf(due, FaultKind.SILENCE);
        if (silence >= 0) {
            ship.silentUntil = at.plus(due.get(silence).silence());
            due.remove(silence);
            ship.requeue(due);
            return;
        }

        Frame frame = ship.motion.frame();
        Set<String> applied = new LinkedHashSet<>();
        int copies = 1;
        boolean hold = false;
        boolean badCrc = false;
        for (FaultSpec f : due) {
            switch (f.kind()) {
                case CRLF_IN_PAYLOAD -> frame = frame.withHeading(CRLF_HEADING);
                case UNKNOWN_PREFIX -> frame = frame.withPrefix(UNKNOWN_PREFIX);
                case BURST -> copies = f.count();
                case BAD_CRC -> badCrc = true;
                case GLUE_NEXT -> hold = !lastTick;
                case SILENCE -> {
                    // Handled above: a silence never reaches this loop.
                }
            }
            if (f.kind() != FaultKind.GLUE_NEXT || hold) {
                applied.add(f.kind().yamlName());
            }
        }

        byte[] bytes = encoder.encode(frame);
        if (badCrc) {
            bytes[bytes.length - DELIMITER_LENGTH - 1]++;
        }
        if (hold) {
            // The delimiter is lost: this frame waits, without its CR LF, for the ship's next one.
            ship.hold(Arrays.copyOf(bytes, bytes.length - DELIMITER_LENGTH), applied);
            return;
        }
        if (ship.held != null) {
            bytes = concat(ship.held, bytes);
            Set<String> all = new LinkedHashSet<>(ship.heldFaults);
            all.addAll(applied);
            applied = all;
            ship.held = null;
            ship.heldFaults.clear();
        }
        String fault = applied.isEmpty() ? null : String.join(FAULT_SEPARATOR, applied);
        for (int i = 0; i < copies; i++) {
            out.add(new TaggedFrame(bytes, ship.spec.prefix(), fault, seconds));
        }
    }

    private static byte[] concat(byte[] head, byte[] tail) {
        byte[] joined = Arrays.copyOf(head, head.length + tail.length);
        System.arraycopy(tail, 0, joined, head.length, tail.length);
        return joined;
    }

    private static int indexOf(List<FaultSpec> faults, FaultKind kind) {
        for (int i = 0; i < faults.size(); i++) {
            if (faults.get(i).kind() == kind) {
                return i;
            }
        }
        return -1;
    }

    private static final class ShipRun {
        final ShipSpec spec;
        final ShipMotion motion;
        final Deque<FaultSpec> faults = new ArrayDeque<>();
        final Set<String> heldFaults = new LinkedHashSet<>();
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

        /** Puts faults back at the head of the queue, in their original order. */
        void requeue(List<FaultSpec> due) {
            for (int i = due.size() - 1; i >= 0; i--) {
                faults.addFirst(due.get(i));
            }
        }

        boolean isSilentAt(Duration at) {
            return silentUntil != null && at.compareTo(silentUntil) < 0;
        }

        /** Keeps a frame (without its delimiter) for the next emission; a second one is added, not substituted. */
        void hold(byte[] frameWithoutDelimiter, Set<String> faultsApplied) {
            held = held == null ? frameWithoutDelimiter : concat(held, frameWithoutDelimiter);
            heldFaults.addAll(faultsApplied);
        }
    }
}
