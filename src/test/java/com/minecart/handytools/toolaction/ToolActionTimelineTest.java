package com.minecart.handytools.toolaction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ToolActionTimelineTest {
    private static final ToolActionDurations DURATIONS =
            new ToolActionDurations(10, 14, 8, 10);

    @Test
    void heldActionEntersAndRepeatsOperation() {
        ToolActionTimeline timeline = new ToolActionTimeline(DURATIONS);

        tick(timeline, 10);
        assertEquals(ToolActionPhase.OPERATION_RAISE, timeline.phase());

        tick(timeline, 14);
        assertEquals(ToolActionPhase.OPERATION_DESCEND, timeline.phase());

        assertEquals(ToolActionTimeline.TickResult.IMPACT, tick(timeline, 8));
        assertEquals(ToolActionPhase.OPERATION_RAISE, timeline.phase());
        assertEquals(1, timeline.completedCycles());
    }

    @Test
    void releaseDuringOperationFinishesTheCurrentStroke() {
        ToolActionTimeline timeline = new ToolActionTimeline(DURATIONS);
        tick(timeline, 10);
        tick(timeline, 1);
        timeline.requestRelease();

        tick(timeline, 13);
        assertEquals(ToolActionPhase.OPERATION_DESCEND, timeline.phase());
        assertEquals(ToolActionTimeline.TickResult.IMPACT, tick(timeline, 8));
        assertEquals(ToolActionPhase.RELEASE, timeline.phase());
        assertEquals(ToolActionTimeline.TickResult.FINISHED, tick(timeline, 10));
    }

    @Test
    void releaseDuringPreparationSkipsOperationAfterPreparationCompletes() {
        ToolActionTimeline timeline = new ToolActionTimeline(DURATIONS);
        tick(timeline, 1);
        timeline.requestRelease();

        tick(timeline, 9);
        assertEquals(ToolActionPhase.RELEASE, timeline.phase());
        assertEquals(0, timeline.completedCycles());
    }

    private static ToolActionTimeline.TickResult tick(
            ToolActionTimeline timeline,
            int ticks
    ) {
        ToolActionTimeline.TickResult result =
                ToolActionTimeline.TickResult.CONTINUE;
        for (int tick = 0; tick < ticks; tick++) {
            result = timeline.tick();
        }
        return result;
    }
}
