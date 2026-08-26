package org.wyrdsekai.hermod;

import java.util.List;
import java.util.Optional;

/**
 * Every device runs one. It routes ONLY its own originating requests,
 * using the gossiped (eventually consistent) capability table. There is
 * no central router and no placement lock: proposals go to a device's
 * AdmissionGate, which may refuse, and the router then tries the next
 * candidate.
 *
 * <p>hermod places WORK. Presence — who is somewhere — is never routable.</p>
 */
public interface Router {

    /**
     * Rank the devices that could take this envelope, from the local view.
     *
     * @param envelope the task to be placed
     * @return eligible devices, best first; empty when none match
     */
    List<Capability> candidates(TaskEnvelope envelope);

    /**
     * Choose the next device to offer this envelope to.
     *
     * @param envelope  the task to be placed
     * @param remaining candidates not yet tried; callers remove each one they
     *                  have offered to, so a refusal moves on to the next
     * @return the next device to try, or empty when none are left
     */
    Optional<Capability> place(TaskEnvelope envelope, List<Capability> remaining);
}
