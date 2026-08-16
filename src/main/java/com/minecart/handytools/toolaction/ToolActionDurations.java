package com.minecart.handytools.toolaction;

/** Tick durations for each phase of a phased tool action. */
public record ToolActionDurations(
        int preparationTicks,
        int raiseTicks,
        int descendTicks,
        int dwellTicks,
        int releaseTicks
) {
    public ToolActionDurations {
        requirePositive("preparationTicks", preparationTicks);
        requirePositive("raiseTicks", raiseTicks);
        requirePositive("descendTicks", descendTicks);
        requirePositive("dwellTicks", dwellTicks);
        requirePositive("releaseTicks", releaseTicks);
    }

    public int ticksFor(ToolActionPhase phase) {
        return switch (phase) {
            case PREPARATION -> preparationTicks;
            case OPERATION_RAISE -> raiseTicks;
            case OPERATION_DESCEND -> descendTicks;
            case OPERATION_DWELL -> dwellTicks;
            case RELEASE -> releaseTicks;
        };
    }

    private static void requirePositive(String name, int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
