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

    /**
     * Requests release. Before the overhead apex (PREPARATION or
     * OPERATION_RAISE) this transitions straight to RELEASE so the action
     * returns immediately from its current pose; during OPERATION_DESCEND it
     * only flags release so the in-flight slam finishes (and fires impact)
     * before RELEASE. Returns true when it changed the phase this call.
     */
    boolean requestRelease() {
        if (phase == ToolActionPhase.RELEASE) {
            return false;
        }
        releaseRequested = true;
        if (phase == ToolActionPhase.PREPARATION
                || phase == ToolActionPhase.OPERATION_RAISE) {
            transition(ToolActionPhase.RELEASE);
            return true;
        }
        return false;
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
