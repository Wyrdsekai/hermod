package org.wyrdsekai.hermod.nats;

import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wyrdsekai.hermod.Mesh;

import java.time.Duration;

/**
 * NATS request/reply binding for hermod doors. Server side answers this
 * device's own door subject; client side knocks on any other device's.
 * Unreachable or silent doors DECLINE (the mesh simply tries the next
 * candidate) — a dead peer must never fail an errand outright.
 */
public final class NatsDoors implements HermodService.RemoteDoors {

    private static final Logger log = LoggerFactory.getLogger(NatsDoors.class);
    private static final Duration KNOCK_TIMEOUT = Duration.ofSeconds(150);

    private final Connection nats;
    private final String scopeId;

    /**
     * Doors over a NATS connection.
     *
     * @param nats    an open NATS connection
     * @param scopeId the mesh scope; doors are namespaced by it
     */
    public NatsDoors(Connection nats, String scopeId) {
        this.nats = nats;
        this.scopeId = scopeId;
    }

    /** Answer knocks on this device's own door. */
    /**
     * Answer this device's own door.
     *
     * @param deviceId this device
     * @param ownDoor  what decides and runs incoming tasks
     */
    public void serve(String deviceId, Mesh.DoorProtocol ownDoor) {
        var subject = DoorWire.doorSubject(scopeId, deviceId);
        var dispatcher = nats.createDispatcher(msg -> {
            var reply = DoorWire.answer(msg.getData(), ownDoor);
            if (msg.getReplyTo() != null) {
                nats.publish(msg.getReplyTo(), reply);
            }
        });
        dispatcher.subscribe(subject);
        log.info("hermod: door open at {}", subject);
    }

    @Override
    /**
     * A door onto another device.
     *
     * @param deviceId the peer to knock on
     * @return a door that sends over NATS. An unreachable or silent peer
     *         declines rather than failing, so the origin moves on.
     */
    public Mesh.DoorProtocol doorTo(String deviceId) {
        return envelope -> {
            try {
                var msg = nats.request(
                    DoorWire.doorSubject(scopeId, deviceId),
                    DoorWire.encodeEnvelope(envelope),
                    KNOCK_TIMEOUT);
                if (msg == null) {
                    return new Mesh.DoorProtocol.Declined(deviceId + " did not answer");
                }
                return DoorWire.decodeAnswer(msg.getData(), envelope.envelopeId());
            } catch (Exception e) {
                return new Mesh.DoorProtocol.Declined(
                    deviceId + " unreachable: " + e.getMessage());
            }
        };
    }
}
