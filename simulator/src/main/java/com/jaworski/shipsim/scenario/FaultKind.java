package com.jaworski.shipsim.scenario;

import java.util.Arrays;
import java.util.Locale;

/**
 * The deliberate faults a scenario can inject, each with a documented effect in the application
 * (docs/e2e-simulator-plan.md, stage 2).
 */
public enum FaultKind {
    /** The checksum byte is off by one: dropped before {@code /json}. */
    BAD_CRC("bad-crc"),
    /** Heading forced to 333.8 degrees, so bytes 2-3 are CR LF: a serial listener splits the frame. */
    CRLF_IN_PAYLOAD("crlf-in-payload"),
    /** The delimiter between this frame and the ship's next one is lost: both arrive as one message. */
    GLUE_NEXT("glue-next"),
    /** Prefix {@code x9}: decodes with {@code modelName=-1}. */
    UNKNOWN_PREFIX("unknown-prefix"),
    /** {@code count} copies of the frame back to back. */
    BURST("burst"),
    /** The ship sends nothing for {@code seconds}; it keeps moving meanwhile. */
    SILENCE("silence");

    private final String yamlName;

    FaultKind(String yamlName) {
        this.yamlName = yamlName;
    }

    public String yamlName() {
        return yamlName;
    }

    public static FaultKind fromYaml(String name) {
        String wanted = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(kind -> kind.yamlName.equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown fault kind '" + name + "'; allowed: "
                        + Arrays.stream(values()).map(FaultKind::yamlName).toList()));
    }
}
