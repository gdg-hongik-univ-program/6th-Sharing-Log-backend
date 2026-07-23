package gdg.sharinglog.rotation.engine;

import static gdg.sharinglog.rotation.engine.CandidateDecision.DECLINED_CURRENT_OCCURRENCE;
import static gdg.sharinglog.rotation.engine.CandidateDecision.HIGHER_ACTIVE_PERIOD_LOAD;
import static gdg.sharinglog.rotation.engine.CandidateDecision.HIGHER_COMPLETED_SAME_CHORE_COUNT;
import static gdg.sharinglog.rotation.engine.CandidateDecision.INACTIVE;
import static gdg.sharinglog.rotation.engine.CandidateDecision.NOT_ELIGIBLE;
import static gdg.sharinglog.rotation.engine.CandidateDecision.PREVIOUS_ASSIGNEE_DEPRIORITIZED;
import static gdg.sharinglog.rotation.engine.CandidateDecision.RANDOM_TIE_NOT_SELECTED;
import static gdg.sharinglog.rotation.engine.CandidateDecision.SELECTED;
import static gdg.sharinglog.rotation.engine.SelectionReasonCode.NO_ASSIGNABLE_CANDIDATE;
import static gdg.sharinglog.rotation.engine.SelectionReasonCode.PREVIOUS_ASSIGNEE_WAS_ONLY_REMAINING_OPTION;
import static gdg.sharinglog.rotation.engine.SelectionReasonCode.RANDOM_TIE_BREAK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class RotationAssignmentEngineTest {

    @Test
    void onlyActiveAndEligibleCandidatesCanBeAssigned() {
        RotationAssignmentResult.Assigned result = assigned(new RotationAssignmentEngine(new Random(1)), List.of(
                candidate(1, false, true, false, 0, 0, false),
                candidate(2, true, false, false, 0, 0, false),
                candidate(3, true, true, false, 5, 5, false)
        ));

        assertEquals(3L, result.selectedMembershipId());
        assertDecisions(result,
                Map.of(1L, INACTIVE, 2L, NOT_ELIGIBLE, 3L, SELECTED));
    }

    @Test
    void candidateWhoDeclinedCurrentOccurrenceIsExcluded() {
        RotationAssignmentResult.Assigned result = assigned(new RotationAssignmentEngine(new Random(2)), List.of(
                candidate(1, true, true, true, 0, 0, false),
                candidate(2, true, true, false, 3, 3, false)
        ));

        assertEquals(2L, result.selectedMembershipId());
        assertDecisions(result, Map.of(1L, DECLINED_CURRENT_OCCURRENCE, 2L, SELECTED));
    }

    @Test
    void minimumSameChoreCompletionCountHasPriorityOverLoad() {
        RotationAssignmentResult.Assigned result = assigned(new RotationAssignmentEngine(new Random(3)), List.of(
                candidate(1, true, true, false, 1, 9, false),
                candidate(2, true, true, false, 2, 0, false)
        ));

        assertEquals(1L, result.selectedMembershipId());
        assertDecisions(result, Map.of(1L, SELECTED, 2L, HIGHER_COMPLETED_SAME_CHORE_COUNT));
    }

    @Test
    void minimumActivePeriodLoadBreaksCompletionCountTie() {
        RotationAssignmentResult.Assigned result = assigned(new RotationAssignmentEngine(new Random(4)), List.of(
                candidate(1, true, true, false, 2, 3, false),
                candidate(2, true, true, false, 2, 1, false)
        ));

        assertEquals(2L, result.selectedMembershipId());
        assertDecisions(result, Map.of(1L, HIGHER_ACTIVE_PERIOD_LOAD, 2L, SELECTED));
    }

    @Test
    void previousAssigneeIsDeprioritizedWhenEqualAlternativeExists() {
        RotationAssignmentResult.Assigned result = assigned(new RotationAssignmentEngine(new Random(5)), List.of(
                candidate(1, true, true, false, 2, 1, true),
                candidate(2, true, true, false, 2, 1, false)
        ));

        assertEquals(2L, result.selectedMembershipId());
        assertDecisions(result, Map.of(1L, PREVIOUS_ASSIGNEE_DEPRIORITIZED, 2L, SELECTED));
    }

    @Test
    void previousAssigneeCanBeSelectedWhenNoEqualAlternativeRemains() {
        RotationAssignmentResult.Assigned result = assigned(new RotationAssignmentEngine(new Random(6)), List.of(
                candidate(1, true, true, false, 0, 0, true),
                candidate(2, true, true, false, 1, 0, false)
        ));

        assertEquals(1L, result.selectedMembershipId());
        assertTrue(result.selectionReasons().stream()
                .anyMatch(reason -> reason.code() == PREVIOUS_ASSIGNEE_WAS_ONLY_REMAINING_OPTION));
    }

    @Test
    void finalTieUsesInjectedFixedSeedAndProducesAuditableSnapshot() {
        RotationAssignmentResult.Assigned result = assigned(new RotationAssignmentEngine(new Random(12345)), List.of(
                candidate(3, true, true, false, 0, 0, false),
                candidate(1, true, true, false, 0, 0, false),
                candidate(2, true, true, false, 0, 0, false)
        ));

        assertEquals(2L, result.selectedMembershipId());
        assertEquals(List.of(1L, 2L, 3L), result.candidateSnapshot().stream()
                .map(CandidateSnapshot::membershipId)
                .toList());
        assertDecisions(result, Map.of(
                1L, RANDOM_TIE_NOT_SELECTED,
                2L, SELECTED,
                3L, RANDOM_TIE_NOT_SELECTED
        ));
        assertTrue(result.selectionReasons().stream()
                .anyMatch(reason -> reason.code() == RANDOM_TIE_BREAK));
    }

    @Test
    void noAssignableCandidateIsAnExplicitAuditableResult() {
        RotationAssignmentResult result = new RotationAssignmentEngine(new Random(7)).assign(List.of(
                candidate(1, false, true, false, 0, 0, false),
                candidate(2, true, false, false, 0, 0, false),
                candidate(3, true, true, true, 0, 0, false)
        ));

        RotationAssignmentResult.NoCandidate noCandidate =
                assertInstanceOf(RotationAssignmentResult.NoCandidate.class, result);
        assertEquals(
                NoCandidateReason.NO_ACTIVE_ELIGIBLE_NON_DECLINED_CANDIDATE,
                noCandidate.reason()
        );
        assertDecisions(noCandidate,
                Map.of(1L, INACTIVE, 2L, NOT_ELIGIBLE, 3L, DECLINED_CURRENT_OCCURRENCE));
        assertTrue(noCandidate.selectionReasons().stream()
                .anyMatch(reason -> reason.code() == NO_ASSIGNABLE_CANDIDATE));
    }

    @Test
    void duplicateMembershipIdsAreRejected() {
        RotationAssignmentEngine engine = new RotationAssignmentEngine(new Random(8));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> engine.assign(List.of(
                candidate(1, true, true, false, 0, 0, false),
                candidate(1, true, true, false, 0, 0, false)
        )));

        assertEquals("Duplicate membershipId: 1", exception.getMessage());
    }

    private RotationCandidate candidate(
            long membershipId,
            boolean active,
            boolean eligible,
            boolean declined,
            int completedCount,
            int activePeriodLoad,
            boolean previousAssignee
    ) {
        return new RotationCandidate(
                membershipId,
                active,
                eligible,
                declined,
                completedCount,
                activePeriodLoad,
                previousAssignee
        );
    }

    private RotationAssignmentResult.Assigned assigned(
            RotationAssignmentEngine engine,
            List<RotationCandidate> candidates
    ) {
        return assertInstanceOf(
                RotationAssignmentResult.Assigned.class,
                engine.assign(candidates)
        );
    }

    private void assertDecisions(
            RotationAssignmentResult result,
            Map<Long, CandidateDecision> expected
    ) {
        Map<Long, CandidateDecision> actual = result.candidateSnapshot().stream()
                .collect(Collectors.toMap(
                        CandidateSnapshot::membershipId,
                        CandidateSnapshot::decision
                ));
        assertEquals(expected, actual);
    }
}
