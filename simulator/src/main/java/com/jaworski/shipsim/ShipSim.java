package com.jaworski.shipsim;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Feeds the serial-protocol application with ship-model frames, so that {@code /chart},
 * {@code /terminal} and {@code /tracks} can be watched without the six models and their cables.
 * See {@code docs/e2e-simulator-plan.md} in the application repository.
 */
@Command(name = "ship-sim", mixinStandardHelpOptions = true, version = "ship-sim 1.0",
        description = "Feeds the serial-protocol application with ship-model frames.",
        subcommands = {ReplayCommand.class, RawCommand.class})
public class ShipSim implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new ShipSim()).execute(args));
    }
}
