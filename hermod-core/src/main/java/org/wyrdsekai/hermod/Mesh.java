package org.wyrdsekai.hermod;

import java.util.ArrayList;
import java.util.function.BiFunction;

/**
 * The origin-side loop: rank candidates, OFFER the envelope to each door
 * in turn, take the first completed result. A door is one round trip —
 * admission and execution answered together — so remote doors cost one
 * exchange, not two. Refusals are expected traffic. When nobody
 * capable-and-consented exists, the failure says so honestly
 * (capability-door pattern: "can't, and why").
 */
public final class Mesh {

    /** One knock: either a completed result, or a decline with a reason. */
    public interface DoorProtocol {
        /** What came back: either the work was done, or it was declined. */
        sealed interface Outcome permits Completed, Declined {}

        /**
         * The device took the task and ran it.
         *
         * @param result what running it produced
         */
        record Completed(TaskExecutor.TaskResult result) implements Outcome {}

        /**
         * The device declined. Normal traffic — the caller tries the next door.
         *
         * @param reason why it declined
         */
        record Declined(String reason) implements Outcome {}

        /**
         * Offer a task to this door. One round trip: admission and execution
         * are answered together, so a remote door costs one exchange, not two.
         *
         * @param envelope the task being offered
         * @return the result, or a decline
         */
        Outcome offer(TaskEnvelope envelope);
    }

    /**
     * A door onto this same process: the local gate decides, the local
     * executor runs.
     *
     * @param gate     decides whether to take the task
     * @param executor runs it once admitted
     * @return a door backed by this device
     */
    public static DoorProtocol local(AdmissionGate gate, TaskExecutor executor) {
        return envelope -> {
            var decision = gate.consider(envelope);
            return switch (decision.verdict()) {
                case ADMIT -> {
                    if (executor == null || !executor.handles(envelope.taskType())) {
                        yield new DoorProtocol.Declined("no executor for " + envelope.taskType());
                    }
                    yield new DoorProtocol.Completed(executor.execute(envelope));
                }
                case QUEUE, REFUSE -> new DoorProtocol.Declined(decision.reason());
            };
        };
    }

    /**
     * A door that always declines.
     *
     * @param reason why it is closed, returned to the origin
     * @return a door that never admits anything
     */
    public static DoorProtocol closed(String reason) {
        return envelope -> new DoorProtocol.Declined(reason);
    }

    private final Router router;
    private final BiFunction<TaskEnvelope, Capability, DoorProtocol> doorOf;

    /**
     * A mesh view for one device.
     *
     * @param router picks candidates from this device's own view
     * @param doorOf how to reach a chosen device — given the task and the
     *               device, return the door to knock on
     */
    public Mesh(Router router, BiFunction<TaskEnvelope, Capability, DoorProtocol> doorOf) {
        this.router = router;
        this.doorOf = doorOf;
    }

    /**
     * Offer a task to each candidate in turn and return the first result.
     *
     * <p>A decline is not a failure: it moves on to the next door. Only when
     * every candidate has declined — or none matched at all — does this
     * return a failed result, and it says which of the two happened.</p>
     *
     * @param e the task to place
     * @return the first completed result, or a failure explaining why none
     */
    public TaskExecutor.TaskResult submit(TaskEnvelope e) {
        var remaining = new ArrayList<>(router.candidates(e));
        var refusals = new ArrayList<String>();
        while (true) {
            var next = router.place(e, remaining);
            if (next.isEmpty()) {
                return TaskExecutor.TaskResult.fail(e.envelopeId(),
                    refusals.isEmpty()
                        ? "no device advertises capability '" + e.capabilityClass() + "'"
                          + (e.requiresGrant() ? " with resident domain '" + e.dataDomain() + "'" : "")
                        : "all candidates declined: " + String.join("; ", refusals));
            }
            var target = next.get();
            remaining.remove(target);
            switch (doorOf.apply(e, target).offer(e)) {
                case DoorProtocol.Completed c -> { return c.result(); }
                case DoorProtocol.Declined d -> refusals.add(target.deviceId() + ": " + d.reason());
            }
        }
    }
}
