package org.wyrdsekai.hermod.node;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * A deliberately minimal node identity for standalone hermod nodes:
 * a stable node id plus one Ed25519 keypair, persisted as a small JSON
 * file. First run mints it; every later run loads it.
 *
 * <p>This is NOT the identity file format of a full wyrdsekai zone
 * (which carries relay keys, grant keys, and encryption alongside).
 * A standalone compute node needs exactly two things: a name that
 * survives restarts, and a key that can sign. Anything more belongs
 * to the platform embedding this library.</p>
 */
public final class NodeIdentity {

    private final String nodeId;
    private final KeyPair keyPair;

    private NodeIdentity(String nodeId, KeyPair keyPair) {
        this.nodeId = nodeId;
        this.keyPair = keyPair;
    }

    /**
     * This node's stable identifier, derived from its key.
     *
     * @return the node id
     */
    public String nodeId() { return nodeId; }

    /**
     * This node's public key.
     *
     * @return the public key
     */
    public PublicKey publicKey() { return keyPair.getPublic(); }

    /**
     * This node's private key, used to sign the envelopes it issues.
     *
     * @return the private key
     */
    public PrivateKey privateKey() { return keyPair.getPrivate(); }

    /** X.509/SPKI encoding — what capability advertisements carry. */
    /**
     * The public half of this node's key.
     *
     * @return the public key in SPKI form
     */
    public byte[] publicKeyBytes() { return keyPair.getPublic().getEncoded(); }

    /**
     * Read this node's identity, creating one on first run.
     *
     * @param file where the identity is stored
     * @return the existing identity, or a newly generated one
     * @throws IOException if the file cannot be read or written
     */
    public static NodeIdentity loadOrGenerate(Path file) throws IOException {
        if (Files.isRegularFile(file)) return load(file);
        return generate(file);
    }

    private static NodeIdentity generate(Path file) throws IOException {
        try {
            var nodeId = UUID.randomUUID().toString();
            var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            var json = "{\n"
                + "  \"nodeId\": \"" + nodeId + "\",\n"
                + "  \"algorithm\": \"Ed25519\",\n"
                + "  \"publicKey\": \""
                + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()) + "\",\n"
                + "  \"privateKey\": \""
                + Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()) + "\"\n"
                + "}\n";
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
            return new NodeIdentity(nodeId, keyPair);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("JVM lacks Ed25519", e);
        }
    }

    private static NodeIdentity load(Path file) throws IOException {
        try {
            var json = Files.readString(file, StandardCharsets.UTF_8);
            var nodeId = extract(json, "nodeId");
            var pub = Base64.getDecoder().decode(extract(json, "publicKey"));
            var priv = Base64.getDecoder().decode(extract(json, "privateKey"));
            var kf = KeyFactory.getInstance("Ed25519");
            var keyPair = new KeyPair(
                kf.generatePublic(new X509EncodedKeySpec(pub)),
                kf.generatePrivate(new PKCS8EncodedKeySpec(priv)));
            return new NodeIdentity(nodeId, keyPair);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IOException("Corrupt or incompatible identity file: " + file, e);
        }
    }

    private static String extract(String json, String field) throws IOException {
        var marker = "\"" + field + "\": \"";
        int start = json.indexOf(marker);
        if (start < 0) throw new IOException("identity file missing field: " + field);
        start += marker.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
