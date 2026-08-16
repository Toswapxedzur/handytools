package com.minecart.handytools.toolaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void releaseDuringRaiseReturnsImmediately() {
        ToolActionTimeline timeline = new ToolActionTimeline(DURATIONS);
        tick(timeline, 10);   // PREPARATION -> OPERATION_RAISE
        tick(timeline, 1);
        assertEquals(ToolActionPhase.OPERATION_RAISE, timeline.phase());

        assertTrue(timeline.requestRelease());   // before the apex -> immediate RELEASE
        assertEquals(ToolActionPhase.RELEASE, timeline.phase());
        assertEquals(0, timeline.completedCycles());
        assertEquals(ToolActionTimeline.TickResult.FINISHED, tick(timeline, 10));
    }

    @Test
    void releaseDuringDescendFinishesTheSlam() {
        ToolActionTimeline timeline = new ToolActionTimeline(DURATIONS);
        tick(timeline, 10);   // -> OPERATION_RAISE
        tick(timeline, 14);   // -> OPERATION_DESCEND
        tick(timeline, 3);
        assertEquals(ToolActionPhase.OPERATION_DESCEND, timeline.phase());

        assertFalse(timeline.requestRelease());  // past the apex -> flag only, no phase change now
        assertEquals(ToolActionPhase.OPERATION_DESCEND, timeline.phase());

        assertEquals(ToolActionTimeline.TickResult.IMPACT, tick(timeline, 5));  // slam still lands
        assertEquals(ToolActionPhase.RELEASE, timeline.phase());
        assertEquals(1, timeline.completedCycles());
        assertEquals(ToolActionTimeline.TickResult.FINISHED, tick(timeline, 10));
    }

    @Test
    void releaseDuringPreparationReturnsImmediately() {
        ToolActionTimeline timeline = new ToolActionTimeline(DURATIONS);
        tick(timeline, 1);
        assertEquals(ToolActionPhase.PREPARATION, timeline.phase());

        assertTrue(timeline.requestRelease());   // before any stroke -> immediate RELEASE
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
