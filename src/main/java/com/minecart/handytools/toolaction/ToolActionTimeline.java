package com.minecart.handytools.toolaction;

/** Pure, server-authoritative phase clock for one continuous tool action. */
final class ToolActionTimeline {
    private final ToolActionDurations durations;
    private ToolActionPhase phase = ToolActionPhase.PREPARATION;
    private int elapsedTicks;
    private int completedCycles;
    private boolean releaseRequested;

    ToolActionTimeline(ToolActionDurations durations) {
        this.durations = durations;
    }

    TickResult tick() {
        elapsedTicks++;
        if (elapsedTicks < duration()) {
            return TickResult.CONTINUE;
        }

        return switch (phase) {
            case PREPARATION -> {
                transition(releaseRequested
                        ? ToolActionPhase.RELEASE
                        : ToolActionPhase.OPERATION_RAISE);
                yield TickResult.PHASE_CHANGED;
            }
            case OPERATION_RAISE -> {
                transition(ToolActionPhase.OPERATION_DESCEND);
                yield TickResult.PHASE_CHANGED;
            }
            case OPERATION_DESCEND -> {
                completedCycles++;
                transition(releaseRequested
                        ? ToolActionPhase.RELEASE
                        : ToolActionPhase.OPERATION_RAISE);
                yield TickResult.IMPACT;
            }
            case RELEASE -> TickResult.FINISHED;
        };
    }

    void requestRelease() {
        if (phase != ToolActionPhase.RELEASE) {
            releaseRequested = true;
        }
    }

    ToolActionPhase phase() {
        return phase;
    }

    int elapsedTicks() {
        return elapsedTicks;
    }

    int duration() {
        return durations.ticksFor(phase);
    }

    int completedCycles() {
        return completedCycles;
    }

    boolean releaseRequested() {
        return releaseRequested;
    }

    private void transition(ToolActionPhase nextPhase) {
        phase = nextPhase;
        elapsedTicks = 0;
    }

    enum TickResult {
        CONTINUE,
        PHASE_CHANGED,
        IMPACT,
        FINISHED
    }
}
