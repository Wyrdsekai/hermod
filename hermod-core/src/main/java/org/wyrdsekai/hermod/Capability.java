package org.wyrdsekai.hermod;

import java.time.Instant;
import java.util.List;

/**
 * What one device advertises to the mesh. Advertisement only — it carries no
 * obligation, and admission stays with the device that receives the work.
 *
 * @param deviceId            stable identifier for the advertising device
 * @param householdId         the mesh scope this advertisement belongs to;
 *                            devices only consider peers in their own scope
 * @param capabilityClass     the kind of work this device will take, e.g.
 *                            {@code "llm.local-gpu"}, {@code "llm.phone"},
 *                            {@code "embed"}. Routing matches on this first.
 * @param models              model names this device offers, if any
 * @param residentDataDomains data that lives on this device and must not
 *                            travel; a task naming one of these is routed here
 *                            rather than having the data sent elsewhere
 * @param charging            whether the device is on mains power. Preferred
 *                            over battery when neither is idle.
 * @param idle                whether the device is otherwise unoccupied.
 *                            Preferred over everything else.
 * @param loadFactor          current load, 0..1, used as the last tiebreak
 * @param advertisedAt        when this advertisement was made. Entries expire
 *                            by TTL, which is how a device leaves the mesh.
 */
public record Capability(
    String deviceId,
    String householdId,
    String capabilityClass,
    List<String> models,
    List<String> residentDataDomains,
    boolean charging,
    boolean idle,
    double loadFactor,
    Instant advertisedAt) {
}
