package org.wyrdsekai.hermod;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * The unit that moves. A task envelope is signed, scoped to one data
 * domain and one capability class, executes data-locally, and returns
 * only its result. Any device may refuse one — refusal is the
 * concurrency mechanism, not an error.
 *
 * <p>hermod places WORK; it never places PRESENCE. Nothing in this package
 * refers to who is present or who is speaking.</p>
 *
 * @param envelopeId      unique id for this task, used to correlate the result
 * @param householdId     the mesh scope; a device ignores envelopes from others
 * @param originDeviceId  the device that issued the task
 * @param taskType        what to do, e.g. {@code "inference.chat"},
 *                        {@code "index.local"}
 * @param dataDomain      the data this task touches, e.g. {@code "photos"}.
 *                        {@code "none"} means it touches none and needs no
 *                        grant; anything else requires one.
 * @param capabilityClass the class a device must advertise to be eligible;
 *                        matches {@link Capability#capabilityClass()}
 * @param params          task parameters. Small values only — hermod is not a
 *                        data plane.
 * @param tokenBudget     an upper bound on work, for task types that consume
 *                        tokens
 * @param issuedAt        when the task was created
 * @param expiresAt       after this the task is stale and must be declined
 * @param grant           consent to touch {@code dataDomain}, verified where
 *                        the data lives. Required whenever the domain is not
 *                        {@code "none"}.
 * @param originSignature the origin device's signature over the envelope
 */
public record TaskEnvelope(
    String envelopeId,
    String householdId,
    String originDeviceId,
    String taskType,
    String dataDomain,
    String capabilityClass,
    Map<String, String> params,
    long tokenBudget,
    Instant issuedAt,
    Instant expiresAt,
    Optional<SignedGrant> grant,
    byte[] originSignature) {

    /**
     * Whether this task needs a {@link SignedGrant}: true for every data
     * domain other than {@code "none"}.
     *
     * @return true when a grant is required for this envelope
     */
    public boolean requiresGrant() {
        return !"none".equals(dataDomain);
    }
}
