package org.wyrdsekai.hermod;

import java.time.Instant;

/**
 * A consent grant that TRAVELS WITH the task and is verified by the
 * EXECUTING device. Consent is enforced at the data, not at the decider:
 * a router running stale policy cannot over-route, because the door
 * checks the grant itself.
 *
 * @param grantId              unique id for this grant
 * @param householdId          the mesh scope the grant is valid in
 * @param dataDomain           the domain this grant opens, e.g. {@code "photos"}
 * @param grantedToDeviceClass which class of origin device may send into that
 *                             domain
 * @param issuedAt             when the grant was minted
 * @param expiresAt            after this the grant is refused
 * @param policyVersion        the policy revision this grant was minted under
 * @param authoritySignature   Ed25519 signature by the authority key, over a
 *                             canonical length-prefixed encoding of every other
 *                             field — so a grant cannot be re-scoped in transit
 */
public record SignedGrant(
    String grantId,
    String householdId,
    String dataDomain,
    String grantedToDeviceClass,
    Instant issuedAt,
    Instant expiresAt,
    String policyVersion,
    byte[] authoritySignature) {
}
