package org.wyrdsekai.hermod.nats;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.wyrdsekai.hermod.Mesh;
import org.wyrdsekai.hermod.SignedGrant;
import org.wyrdsekai.hermod.TaskEnvelope;
import org.wyrdsekai.hermod.TaskExecutor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * The knock and the answer, on the wire. Hand-rolled DTOs (nullable
 * grant, no Optional in JSON) so the codec needs nothing beyond the
 * modules the server already ships. One request/reply per offer —
 * admission and result travel together, matching DoorProtocol.
 */
public final class DoorWire {

    private DoorWire() {}

    // ISO-8601 instants and lenient reads: phones (Kotlin) speak this wire
    // too, and devices in one household update at different times.
    static final ObjectMapper JSON = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    /**
     * A task envelope as it travels. Mirrors {@link TaskEnvelope}, except the
     * grant is nullable rather than an {@code Optional} — JSON has no Optional,
     * and a null reads the same in every language on the wire.
     *
     * @param envelopeId      unique id for the task
     * @param householdId     the mesh scope
     * @param originDeviceId  who issued it
     * @param taskType        what to do
     * @param dataDomain      the data it touches, {@code "none"} if none
     * @param capabilityClass the class a device must advertise to be eligible
     * @param params          task parameters
     * @param tokenBudget     upper bound on work
     * @param issuedAt        when it was created
     * @param expiresAt       when it goes stale
     * @param grant           consent for the data domain; null when none is needed
     * @param originSignature the origin's signature over the envelope
     */
    public record EnvelopeDto(
        String envelopeId, String householdId, String originDeviceId,
        String taskType, String dataDomain, String capabilityClass,
        Map<String, String> params, long tokenBudget,
        Instant issuedAt, Instant expiresAt,
        SignedGrant grant,
        byte[] originSignature) {

        static EnvelopeDto of(TaskEnvelope e) {
            return new EnvelopeDto(e.envelopeId(), e.householdId(), e.originDeviceId(),
                e.taskType(), e.dataDomain(), e.capabilityClass(), e.params(),
                e.tokenBudget(), e.issuedAt(), e.expiresAt(),
                e.grant().orElse(null), e.originSignature());
        }

        TaskEnvelope toEnvelope() {
            return new TaskEnvelope(envelopeId, householdId, originDeviceId, taskType,
                dataDomain, capabilityClass, params, tokenBudget, issuedAt, expiresAt,
                Optional.ofNullable(grant), originSignature);
        }
    }

    /**
     * The answer to one knock. Admission and result travel together, so
     * {@code completed} says whether the device took the task at all, and
     * {@code ok} says whether the work then succeeded.
     *
     * @param completed     true if the device admitted and ran the task
     * @param ok            true if the run succeeded; meaningless when not completed
     * @param output        the result, when it succeeded
     * @param error         why the run failed, when it did
     * @param declineReason why the device refused, when it did not complete
     */
    public record AnswerDto(boolean completed, boolean ok, String output, String error, String declineReason) {

        static AnswerDto of(Mesh.DoorProtocol.Outcome outcome) {
            return switch (outcome) {
                case Mesh.DoorProtocol.Completed c ->
                    new AnswerDto(true, c.result().ok(), c.result().output(), c.result().error(), null);
                case Mesh.DoorProtocol.Declined d ->
                    new AnswerDto(false, false, null, null, d.reason());
            };
        }

        Mesh.DoorProtocol.Outcome toOutcome(String envelopeId) {
            if (!completed) {
                return new Mesh.DoorProtocol.Declined(declineReason == null ? "declined" : declineReason);
            }
            return new Mesh.DoorProtocol.Completed(ok
                ? TaskExecutor.TaskResult.ok(envelopeId, output == null ? "" : output)
                : TaskExecutor.TaskResult.fail(envelopeId, error == null ? "" : error));
        }
    }

    /**
     * The subject one device's door listens on.
     *
     * @param scopeId  the mesh scope
     * @param deviceId the device whose door it is
     * @return the subject to publish a knock to
     */
    public static String doorSubject(String scopeId, String deviceId) {
        return "hh." + scopeId + ".hermod.door." + deviceId;
    }

    /**
     * Encode a task for sending.
     *
     * @param e the task to send
     * @return the encoded envelope
     * @throws Exception if it cannot be serialised
     */
    public static byte[] encodeEnvelope(TaskEnvelope e) throws Exception {
        return JSON.writeValueAsBytes(EnvelopeDto.of(e));
    }

    /**
     * Decode a received task.
     *
     * @param b the received bytes
     * @return the task they encode
     * @throws Exception if the bytes are not a valid envelope
     */
    public static TaskEnvelope decodeEnvelope(byte[] b) throws Exception {
        return JSON.readValue(b, EnvelopeDto.class).toEnvelope();
    }

    /**
     * Encode an outcome for sending back.
     *
     * @param o the outcome to send back
     * @return the encoded answer
     * @throws Exception if it cannot be serialised
     */
    public static byte[] encodeAnswer(Mesh.DoorProtocol.Outcome o) throws Exception {
        return JSON.writeValueAsBytes(AnswerDto.of(o));
    }

    /**
     * Decode a received answer.
     *
     * @param b          the received bytes
     * @param envelopeId the task this answers, used to build the result
     * @return the outcome they encode
     * @throws Exception if the bytes are not a valid answer
     */
    public static Mesh.DoorProtocol.Outcome decodeAnswer(byte[] b, String envelopeId) throws Exception {
        return JSON.readValue(b, AnswerDto.class).toOutcome(envelopeId);
    }

    /**
     * The server side of one knock: decode the request, offer it to this
     * device's door, encode whatever comes back.
     *
     * <p>A malformed knock is answered with a decline rather than an error.
     * The origin then tries the next candidate, which is what it would do for
     * any other refusal.</p>
     *
     * @param request the received knock
     * @param ownDoor this device's door
     * @return the encoded answer
     */
    public static byte[] answer(byte[] request, Mesh.DoorProtocol ownDoor) {
        try {
            var envelope = decodeEnvelope(request);
            return encodeAnswer(ownDoor.offer(envelope));
        } catch (Exception e) {
            try {
                return encodeAnswer(new Mesh.DoorProtocol.Declined(
                    "malformed knock: " + e.getMessage()));
            } catch (Exception impossible) {
                return new byte[0];
            }
        }
    }
}
