package org.wyrdsekai.hermod.node;

import io.nats.client.Nats;
import org.wyrdsekai.hermod.nats.HermodService;
import org.wyrdsekai.hermod.nats.NatsDoors;
import org.wyrdsekai.hermod.nats.NatsGossip;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * A COMPUTE-ONLY mesh node: no world, no application — just the three
 * verbs of membership. Advertise what this box can run, answer knocks on
 * its own door, execute admitted errands on its LOCAL inference. This is
 * how a big idle GPU (or a mac in a closet) lends itself to a household
 * without hosting anything.
 *
 * <p>Deliberately tiny and killable: one JVM, no listening sockets of its
 * own, NATS as its only door to the world. Ctrl-C / SIGTERM = the box
 * leaves the mesh; its advertisement ages out by TTL everywhere.</p>
 *
 * <pre>
 * Usage: hermod-node &lt;natsUrl&gt; &lt;scopeId&gt; &lt;capabilityClass&gt; &lt;inferenceUrl&gt; &lt;model&gt;
 *   e.g. hermod-node nats://127.0.0.1:4222 home llm.local-gpu http://127.0.0.1:8200 default
 * </pre>
 *
 * <p>Identity: {@code $HERMOD_DATA_DIR|~/.hermod/node-identity.json}
 * (minted on first run). Resident data domains:
 * {@code HERMOD_DOMAINS} (comma list), default none — a node with no
 * domains only ever receives tasks that carry no data-domain consent
 * requirement.</p>
 */
public final class HermodNodeMain {

    private HermodNodeMain() {}


    /**
     * Join the mesh and answer the door until stopped.
     *
     * @param args natsUrl, scopeId, capabilityClass, inferenceUrl, model
     * @throws Exception if the connection or startup fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("usage: hermod-node <natsUrl> <scopeId> <capabilityClass>"
                + " <inferenceUrl> <model>");
            System.exit(2);
        }
        var natsUrl = args[0];
        var scope = args[1];
        var capClass = args[2];
        var inferenceUrl = args[3];
        var model = args[4];

        var dataDir = Path.of(System.getenv().getOrDefault(
            "HERMOD_DATA_DIR", System.getProperty("user.home") + "/.hermod"));
        var identity = NodeIdentity.loadOrGenerate(dataDir.resolve("node-identity.json"));

        var nats = Nats.connect(natsUrl);
        var gossip = new NatsGossip(nats, scope);
        var executor = new OpenAiChatExecutor(inferenceUrl, model, Duration.ofSeconds(120));
        var service = new HermodService(gossip, scope, identity.nodeId(),
            capClass, model.isBlank() ? List.of() : List.of(model),
            executor, Clock.systemUTC(), identity.publicKeyBytes());
        var domainsRaw = System.getenv().getOrDefault("HERMOD_DOMAINS", "");
        service.residentDomains(domainsRaw.isBlank() ? List.of()
            : Arrays.stream(domainsRaw.split(",")).map(String::trim)
                .filter(d -> !d.isBlank()).toList());
        service.start();

        var doors = new NatsDoors(nats, scope);
        doors.serve(service.deviceId(), service.ownDoor());
        service.remoteDoors(doors);

        System.out.println("[hermod-node] " + identity.nodeId() + " lending '" + capClass
            + "' (" + model + ") on scope " + scope + " via " + natsUrl
            + " — Ctrl-C to leave the mesh");
        Thread.currentThread().join(); // serve until killed; TTL cleans up after us
    }
}
