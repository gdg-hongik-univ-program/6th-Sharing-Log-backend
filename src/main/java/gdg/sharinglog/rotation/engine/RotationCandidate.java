package gdg.sharinglog.rotation.engine;

/**
 * A point-in-time input used to choose an assignee for one occurrence.
 */
public record RotationCandidate(
        long membershipId,
        boolean active,
        boolean eligible,
        boolean declinedCurrentOccurrence,
        int completedSameChoreCount,
        int activePeriodLoad,
        boolean previousAssignee
) {

    public RotationCandidate {
        if (membershipId <= 0) {
            throw new IllegalArgumentException("membershipId must be positive");
        }
        if (completedSameChoreCount < 0) {
            throw new IllegalArgumentException("completedSameChoreCount must not be negative");
        }
        if (activePeriodLoad < 0) {
            throw new IllegalArgumentException("activePeriodLoad must not be negative");
        }
    }
}
