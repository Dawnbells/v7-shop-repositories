package cn.v7soft.dao.enums;

import java.util.EnumSet;
import java.util.Map;

public enum TaskState {
    PENDING, PROCESSING, RESOLVED, COMPLETED, FAILED, CANCELLED, INSUFFICIENT_CREDITS;

    private static final Map<TaskState, EnumSet<TaskState>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(PROCESSING, FAILED, CANCELLED, INSUFFICIENT_CREDITS),
            PROCESSING, EnumSet.of(COMPLETED, FAILED, CANCELLED, RESOLVED, PROCESSING, PENDING),
            RESOLVED, EnumSet.of(COMPLETED, FAILED),
            FAILED, EnumSet.noneOf(TaskState.class),
            CANCELLED, EnumSet.noneOf(TaskState.class),
            INSUFFICIENT_CREDITS, EnumSet.of(PENDING, CANCELLED)
    );

    public boolean canTransitionTo(TaskState target) {
        if (this == target) {
            return true;
        }
        EnumSet<TaskState> allowed = ALLOWED_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }
}
