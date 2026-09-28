package bd.ac.kuet.campuscycle.domain;

/**
 * Explicit outcome required every time a maintenance ticket is resolved.
 * A resolve never silently returns a cycle to service (P-032).
 */
public enum ReleaseDecision {
    /** Cycle repaired and verified: back to the rentable pool. */
    RETURN_TO_SERVICE,
    /** Repaired or under investigation but not rentable: visible, blocked. */
    QUARANTINE,
    /** Permanently withdrawn from the fleet. Requires Role.ADMIN. */
    RETIRE
}
