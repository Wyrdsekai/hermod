package org.wyrdsekai.hermod;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The origin signature on a task envelope: the sending device's Ed25519 key
 * over the envelope's stable fields, verified at the receiving door before
 * anything else (protocol §4).
 *
 * <p>In protocol version 1 the signature field was carried but never defined
 * or checked: a door had no proof of who sent a task, and the grant check
 * could not tell whose request it was. Version 2 defines it and doors verify.
 */
public final class EnvelopeSigning {

    private EnvelopeSigning() {}

    /**
     * The bytes a sender signs and a door verifies: everything but the signature,
     * in one fixed order (protocol §4).
     *
     * @param e the envelope
     * @return its signing bytes
     */
    public static byte[] signingBytes(TaskEnvelope e) {
        var buf = ByteBuffer.allocate(64 * 1024);
        for (var s : new String[]{e.envelopeId(), e.householdId(), e.originDeviceId(), e.taskType(),
                                  e.dataDomain(), e.capabilityClass()}) {
            put(buf, s);
        }
        // Params in key order, so the same envelope always gives the same bytes.
        var params = new TreeMap<String, String>();
        if (e.params() != null) e.params().forEach((k, v) -> params.put(k == null ? "" : k, v == null ? "" : v));
        buf.putInt(params.size());
        for (var en : params.entrySet()) { put(buf, en.getKey()); put(buf, en.getValue()); }
        buf.putLong(e.tokenBudget());
        buf.putLong(e.issuedAt() == null ? 0L : e.issuedAt().toEpochMilli());
        buf.putLong(e.expiresAt() == null ? 0L : e.expiresAt().toEpochMilli());
        put(buf, e.grant().map(SignedGrant::grantId).orElse(""));
        var out = new byte[buf.position()];
        buf.rewind();
        buf.get(out);
        return out;
    }

    private static void put(ByteBuffer buf, String s) {
        var b = (s == null ? "" : s).getBytes(StandardCharsets.UTF_8);
        buf.putInt(b.length).put(b);
    }

    /**
     * The same envelope, signed by the sender.
     *
     * @param e      the envelope to sign; its signature field is replaced
     * @param signer the device's identity: signing bytes in, Ed25519 signature out
     * @return the envelope carrying the signature
     */
    public static TaskEnvelope sign(TaskEnvelope e, Function<byte[], byte[]> signer) {
        var sig = signer.apply(signingBytes(e));
        return new TaskEnvelope(e.envelopeId(), e.householdId(), e.originDeviceId(), e.taskType(), e.dataDomain(),
            e.capabilityClass(), e.params(), e.tokenBudget(), e.issuedAt(), e.expiresAt(), e.grant(), sig);
    }

    /**
     * Whether the envelope's signature is the given device key's signature over
     * its fields.
     *
     * @param e          the envelope as received
     * @param originSpki the origin device's Ed25519 public key, X.509 SPKI bytes,
     *                   as its advertisement carries it
     * @return true only when the signature verifies; false for a missing key,
     *         a missing or short signature, or any failure
     */
    public static boolean verify(TaskEnvelope e, byte[] originSpki) {
        if (e == null || e.originSignature() == null || e.originSignature().length < 32
                || originSpki == null || originSpki.length == 0) return false;
        try {
            var sig = Signature.getInstance("Ed25519");
            sig.initVerify(GrantAuthority.publicKeyFromSpki(originSpki));
            sig.update(signingBytes(e));
            return sig.verify(e.originSignature());
        } catch (Exception ex) {
            return false;
        }
    }
}
