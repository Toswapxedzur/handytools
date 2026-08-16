package com.minecart.handytools.toolaction;

/**
 * The shared lifecycle for a continuous, physically meaningful tool action.
 *
 * <p>Every adjacent phase meets at the same pose: preparation ends at the
 * operating/contact pose, raising starts there and ends overhead, descending
 * returns to contact, and release returns from contact to the normal held
 * pose. A handled tool's preparation animation must bring its primary axis
 * into line with the acting arm at the contact pose, not leave the handle
 * perpendicular to that arm.</p>
 */
public enum ToolActionPhase {
    PREPARATION,
    OPERATION_RAISE,
    OPERATION_DESCEND,
    /** Holds the landed contact pose for a beat after impact, before raising again. */
    OPERATION_DWELL,
    RELEASE;

    public boolean isOperation() {
        return this == OPERATION_RAISE
                || this == OPERATION_DESCEND
                || this == OPERATION_DWELL;
    }
}
