package org.wyrdsekai.hermod.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.wyrdsekai.hermod.TaskEnvelope;
import org.wyrdsekai.hermod.TaskExecutor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The reference executor for a standalone compute node: runs
 * {@code inference.chat} envelopes against any OpenAI-compatible
 * chat-completions endpoint (llama.cpp server, vLLM, Ollama, SGLang…).
 *
 * <p>Envelope params, mirroring the wyrdsekai wire shape so nodes and
 * zones interoperate: {@code model}, {@code prompt}, optional
 * {@code system}. Output is the assistant message content.</p>
 *
 * <p>{@code inference.chat.full} (a serialized tool-carrying request)
 * is deliberately NOT handled here — that shape belongs to platforms
 * with their own request types. A node lends words, not opinions about
 * your schema.</p>
 */
public final class OpenAiChatExecutor implements TaskExecutor {

    /** The task type this executor handles. */
    public static final String TASK_TYPE = "inference.chat";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final String defaultModel;
    private final HttpClient http;
    private final Duration timeout;

    /**
     * An executor that runs chat tasks against an OpenAI-compatible endpoint.
     *
     * @param baseUrl      the endpoint, e.g. {@code http://127.0.0.1:8200}
     * @param defaultModel model to use when a task names none
     * @param timeout      how long to wait before giving up on a request
     */
    public OpenAiChatExecutor(String baseUrl, String defaultModel, Duration timeout) {
        this.baseUrl = baseUrl.replaceAll("/v1/?$", "").replaceAll("/$", "");
        this.defaultModel = defaultModel;
        this.timeout = timeout != null ? timeout : Duration.ofSeconds(120);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public boolean handles(String taskType) {
        return TASK_TYPE.equals(taskType);
    }

    @Override
    public TaskResult execute(TaskEnvelope envelope) {
        try {
            var params = envelope.params();
            var messages = new ArrayList<Map<String, String>>();
            var system = params.getOrDefault("system", "");
            if (!system.isBlank()) {
                messages.add(Map.of("role", "system", "content", system));
            }
            messages.add(Map.of("role", "user",
                "content", params.getOrDefault("prompt", "")));

            var body = new LinkedHashMap<String, Object>();
            var model = params.getOrDefault("model", defaultModel);
            body.put("model", model == null || model.isBlank() ? "default" : model);
            body.put("messages", messages);

            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                    MAPPER.writeValueAsString(body)))
                .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return TaskResult.fail(envelope.envelopeId(),
                    "inference endpoint returned " + response.statusCode());
            }
            var content = MAPPER.readTree(response.body())
                .path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                return TaskResult.fail(envelope.envelopeId(),
                    "inference endpoint returned an empty completion");
            }
            return TaskResult.ok(envelope.envelopeId(), content);
        } catch (Exception e) {
            return TaskResult.fail(envelope.envelopeId(),
                "executor error: " + e.getMessage());
        }
    }
}
