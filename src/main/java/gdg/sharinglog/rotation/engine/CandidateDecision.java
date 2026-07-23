package gdg.sharinglog.rotation.engine;

/**
 * The first decisive reason a candidate was selected or removed.
 */
public enum CandidateDecision {
    INACTIVE,
    NOT_ELIGIBLE,
    DECLINED_CURRENT_OCCURRENCE,
    HIGHER_COMPLETED_SAME_CHORE_COUNT,
    HIGHER_ACTIVE_PERIOD_LOAD,
    PREVIOUS_ASSIGNEE_DEPRIORITIZED,
    RANDOM_TIE_NOT_SELECTED,
    SELECTED
}
