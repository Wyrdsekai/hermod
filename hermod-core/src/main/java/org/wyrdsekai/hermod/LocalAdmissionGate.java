package org.wyrdsekai.hermod;

import java.time.Clock;
import java.util.function.Predicate;

/**
 * Default door: verifies expiry, budget against a local ceiling, and —
 * for any data-domain task — the presence AND validity of the traveling
 * grant. Grant cryptography is pluggable (the household authority's
 * verifier); refusal reasons are honest and specific.
 */
public final class LocalAdmissionGate implements AdmissionGate {

    private final Clock clock;
    private final long tokenCeiling;
    private final Predicate<SignedGrant> grantVerifier;
    private final Predicate<TaskEnvelope> busyCheck;

    /**
     * A gate for this device.
     *
     * @param clock         used to check envelope expiry
     * @param tokenCeiling  the largest token budget this device will accept
     * @param grantVerifier verifies a grant against the authority key. Pass a
     *                      predicate that always returns false to refuse every
     *                      task touching a data domain — which is what having
     *                      no authority key should mean.
     * @param busyCheck     true when this device is too busy to take the task
     */
    public LocalAdmissionGate(Clock clock, long tokenCeiling,
                              Predicate<SignedGrant> grantVerifier,
                              Predicate<TaskEnvelope> busyCheck) {
        this.clock = clock;
        this.tokenCeiling = tokenCeiling;
        this.grantVerifier = grantVerifier;
        this.busyCheck = busyCheck;
    }

    @Override
    public Decision consider(TaskEnvelope e) {
        if (e.expiresAt().isBefore(clock.instant())) {
            return Decision.refuse("envelope expired");
        }
        if (e.tokenBudget() > tokenCeiling) {
            return Decision.refuse("budget " + e.tokenBudget() + " exceeds local ceiling " + tokenCeiling);
        }
        if (e.requiresGrant()) {
            if (e.grant().isEmpty()) {
                return Decision.refuse("no grant for domain '" + e.dataDomain() + "'");
            }
            var g = e.grant().get();
            if (!g.dataDomain().equals(e.dataDomain())) {
                return Decision.refuse("grant is for domain '" + g.dataDomain() + "', task wants '" + e.dataDomain() + "'");
            }
            if (g.expiresAt().isBefore(clock.instant())) {
                return Decision.refuse("grant expired");
            }
            if (!grantVerifier.test(g)) {
                return Decision.refuse("grant signature invalid");
            }
        }
        if (busyCheck.test(e)) {
            return Decision.queue("busy");
        }
        return Decision.admit();
    }
}
