package gdg.sharinglog.rotation.engine;

/**
 * A point-in-time input used to choose an assignee for one occurrence.
 */
public record RotationCandidate(
        long membershipId,
        boolean active,
        boolean eligible,
        boolean declinedCurrentOccurrence,
        long completedSameChoreCount,
        long fairnessCredit,
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
        if (fairnessCredit < 0) {
            throw new IllegalArgumentException("fairnessCredit must not be negative");
        }
        if (activePeriodLoad < 0) {
            throw new IllegalArgumentException("activePeriodLoad must not be negative");
        }
    }

    public RotationCandidate(
            long membershipId,
            boolean active,
            boolean eligible,
            boolean declinedCurrentOccurrence,
            long completedSameChoreCount,
            int activePeriodLoad,
            boolean previousAssignee
    ) {
        this(
                membershipId,
                active,
                eligible,
                declinedCurrentOccurrence,
                completedSameChoreCount,
                0L,
                activePeriodLoad,
                previousAssignee
        );
    }

    public long effectiveCompletedSameChoreCount() {
        return Math.addExact(completedSameChoreCount, fairnessCredit);
    }
}
