package gdg.sharinglog.domain.rotation;

public enum AssignmentEndReason {
    COMPLETED,
    SKIPPED_ALREADY_DONE,
    DECLINED_BY_ASSIGNEE,
    ASSIGNEE_LEFT_GROUP,
    PARTICIPATION_REMOVED,
    SUBSTITUTE_ACCEPTED;

    public boolean requiresReassignment() {
        return this == DECLINED_BY_ASSIGNEE
                || this == ASSIGNEE_LEFT_GROUP
                || this == PARTICIPATION_REMOVED
                || this == SUBSTITUTE_ACCEPTED;
    }

    public boolean excludesAssigneeFromSameOccurrence() {
        return this == DECLINED_BY_ASSIGNEE || this == SUBSTITUTE_ACCEPTED;
    }
}
