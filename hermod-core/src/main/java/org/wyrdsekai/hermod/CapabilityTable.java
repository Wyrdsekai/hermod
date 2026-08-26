package org.wyrdsekai.hermod;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The local, eventually-consistent view every router reads. Merge is
 * last-writer-wins PER DEVICE on advertisedAt — a stale advertisement
 * can never regress a fresher one, so tables converge regardless of
 * gossip order. Entries expire; absence of advertisement is absence of
 * capability.
 */
public final class CapabilityTable {

    private final Map<String, Capability> byDevice = new ConcurrentHashMap<>();
    private final Duration ttl;

    /**
     * A device's own view of the mesh.
     *
     * @param ttl how long an advertisement stays live. A device that stops
     *            advertising leaves the mesh when its entry expires; there is
     *            no goodbye message.
     */
    public CapabilityTable(Duration ttl) {
        this.ttl = ttl;
    }

    /**
     * Merge an advertisement. Last-write-wins per device, by the timestamp the
     * advertisement carries — so an out-of-order arrival cannot move a device
     * backwards.
     *
     * @param incoming the advertisement received
     * @return true if this changed the table
     */
    public boolean merge(Capability incoming) {
        var updated = byDevice.merge(incoming.deviceId(), incoming,
            (old, in) -> in.advertisedAt().isAfter(old.advertisedAt()) ? in : old);
        return updated == incoming;
    }

    /**
     * The devices currently visible, freshest first. Entries older than the
     * TTL are left out.
     *
     * @param now the current time
     * @return live advertisements
     */
    public List<Capability> snapshot(Instant now) {
        var cutoff = now.minus(ttl);
        byDevice.values().removeIf(c -> c.advertisedAt().isBefore(cutoff));
        return byDevice.values().stream()
            .sorted(Comparator.comparing(Capability::advertisedAt).reversed())
            .toList();
    }

    /**
     * Subscribe to a transport so every advertisement it receives is merged.
     *
     * @param transport the gossip transport to listen on
     */
    public void attach(GossipTransport transport) {
        transport.subscribe(this::merge);
    }
}
