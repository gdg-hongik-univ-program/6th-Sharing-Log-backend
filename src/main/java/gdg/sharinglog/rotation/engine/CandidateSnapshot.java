package gdg.sharinglog.rotation.engine;

import java.util.Objects;

/**
 * An immutable audit snapshot of a candidate and the assignment decision.
 */
public record CandidateSnapshot(
        long membershipId,
        boolean active,
        boolean eligible,
        boolean declinedCurrentOccurrence,
        long completedSameChoreCount,
        long fairnessCredit,
        long effectiveCompletedSameChoreCount,
        int activePeriodLoad,
        boolean previousAssignee,
        CandidateDecision decision
) {

    public CandidateSnapshot {
        Objects.requireNonNull(decision, "decision must not be null");
    }

    static CandidateSnapshot from(RotationCandidate candidate, CandidateDecision decision) {
        return new CandidateSnapshot(
                candidate.membershipId(),
                candidate.active(),
                candidate.eligible(),
                candidate.declinedCurrentOccurrence(),
                candidate.completedSameChoreCount(),
                candidate.fairnessCredit(),
                candidate.effectiveCompletedSameChoreCount(),
                candidate.activePeriodLoad(),
                candidate.previousAssignee(),
                decision
        );
    }
}
