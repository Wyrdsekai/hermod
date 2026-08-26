package org.wyrdsekai.hermod;

import java.util.function.Consumer;

/**
 * How advertisements travel. hermod binds no wire protocol: the deployment
 * supplies an adapter — {@code hermod-nats} is the reference one — and tests
 * supply an in-memory loopback. This interface is the whole contract, so the
 * core carries no broker dependency.
 */
public interface GossipTransport {
    /**
     * Advertise this device to the mesh.
     *
     * @param capability what this device currently offers
     */
    void publish(Capability capability);

    /**
     * Listen for advertisements from other devices.
     *
     * @param onCapability called for each advertisement received
     */
    void subscribe(Consumer<Capability> onCapability);
}
