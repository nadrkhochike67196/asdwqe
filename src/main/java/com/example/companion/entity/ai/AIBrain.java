package com.example.companion.entity.ai;

import com.example.companion.entity.CompanionEntity;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AIBrain {
    private static final String BASE_URL = "https://gen.pollinations.ai/v1/chat/completions";
    private static final String API_KEY = "sk_LAfN0hmVGrw5mMAoSaGNPfJJF4NVm5eH";
    private static final String MODEL = "gemini";
    private static final Gson GSON = new Gson();

    private static final String SYSTEM_PROMPT =
            "You are a Minecraft companion AI named Buddy. You help your owner survive in Minecraft.\n" +
            "You MUST respond ONLY with a single JSON object. No markdown, no backticks, no explanation.\n\n" +
            "Available actions:\n" +
            "{\"action\":\"FOLLOW\",\"say\":\"text\"}\n" +
            "{\"action\":\"ATTACK\",\"say\":\"text\"}\n" +
            "{\"action\":\"MINE\",\"x\":0,\"y\":64,\"z\":0,\"say\":\"text\"}\n" +
            "{\"action\":\"EAT\",\"say\":\"text\"}\n" +
            "{\"action\":\"IDLE\",\"say\":\"text\"}\n\n" +
            "The \"say\" field is REQUIRED. Write a short message (1 sentence) about what you're doing or thinking.\n" +
            "Write the say field in the same language the player uses.\n\n" +
            "Priority: 1.Low health->EAT 2.Hostiles nearby->ATTACK 3.Owner far->FOLLOW 4.Ores nearby->MINE 5.IDLE\n" +
            "RESPOND WITH ONLY THE JSON OBJECT.";

    // Conversation memory
    private final List<JsonObject> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 20;

    public record AIAction(String action, int x, int y, int z, String message) {
        public static AIAction idle() {
            return new AIAction("FOLLOW", 0, 0, 0, "Following you!");
        }
    }

    public String collectGameState(CompanionEntity companion) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== GAME STATE ===\n");
        sb.append("My Health: ").append((int) companion.getHealth()).append("/")
                .append((int) companion.getMaxHealth()).append("\n");
        sb.append("My Position: ").append(companion.getBlockPos().toShortString()).append("\n");

        PlayerEntity owner = companion.getOwnerPlayer();
        if (owner != null) {
            sb.append("Owner: ").append(owner.getName().getString()).append("\n");
            sb.append("Owner Distance: ").append(String.format("%.0f", companion.distanceTo(owner)))
                    .append(" blocks\n");
            sb.append("Owner Health: ").append((int) owner.getHealth()).append("/")
                    .append((int) owner.getMaxHealth()).append("\n");
        }

        World world = companion.getWorld();
        long time = world.getTimeOfDay() % 24000;
        sb.append("Time: ").append(time < 13000 ? "Day" : "Night").append("\n");

        var hostiles = world.getEntitiesByClass(HostileEntity.class,
                companion.getBoundingBox().expand(16.0), e -> true);
        if (!hostiles.isEmpty()) {
            sb.append("DANGER! Hostile mobs nearby: ");
            for (int i = 0; i < Math.min(hostiles.size(), 3); i++) {
                if (i > 0) sb.append(", ");
                sb.append(hostiles.get(i).getType().getName().getString());
                sb.append(" (").append(String.format("%.0f", companion.distanceTo(hostiles.get(i)))).append("m)");
            }
            sb.append("\n");
        } else {
            sb.append("No hostile mobs nearby.\n");
        }

        BlockPos pos = companion.getBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos cp = pos.add(dx, dy, dz);
                    var state = world.getBlockState(cp);
                    if (state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.COAL_ORE) ||
                            state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DIAMOND_ORE) ||
                            state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.BIRCH_LOG)) {
                        sb.append("Found: ").append(state.getBlock().getName().getString())
                                .append(" at ").append(cp.toShortString()).append("\n");
                        break;
                    }
                }
            }
        }

        return sb.toString();
    }

    /** Called by the entity every 5 seconds for autonomous decisions */
    public CompletableFuture<AIAction> think(String gameState) {
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", gameState);
        return callAndParse(userMsg);
    }

    /** Called when a player sends a chat message to the companion */
    public CompletableFuture<AIAction> chat(String playerName, String playerMessage, String gameState) {
        String combined = "Player " + playerName + " says: \"" + playerMessage + "\"\n\n" +
                "Current game state:\n" + gameState + "\n" +
                "Respond to the player and decide your next action.";

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", combined);
        return callAndParse(userMsg);
    }

    private CompletableFuture<AIAction> callAndParse(JsonObject userMsg) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build messages array: system + history + new message
                JsonArray messages = new JsonArray();

                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", SYSTEM_PROMPT);
                messages.add(sysMsg);

                // Add conversation history
                synchronized (conversationHistory) {
                    for (JsonObject msg : conversationHistory) {
                        messages.add(msg);
                    }
                }

                messages.add(userMsg);

                // Build request body (matching ai-service.js pattern exactly)
                JsonObject body = new JsonObject();
                body.addProperty("model", MODEL);
                body.add("messages", messages);
                body.addProperty("temperature", 0.7);
                body.addProperty("max_tokens", 150);

                String requestJson = GSON.toJson(body);

                // HTTP call (same pattern as ai-service.js)
                URL url = new URL(BASE_URL);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Authorization", "Bearer " + API_KEY);
                con.setRequestProperty("Content-Type", "application/json");
                con.setConnectTimeout(30000);
                con.setReadTimeout(30000);
                con.setDoOutput(true);

                try (OutputStream os = con.getOutputStream()) {
                    os.write(requestJson.getBytes(StandardCharsets.UTF_8));
                }

                int responseCode = con.getResponseCode();
                String responseBody;

                if (responseCode == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        responseBody = sb.toString();
                    }
                } else {
                    // Read error body for debugging
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                        responseBody = sb.toString();
                    }
                    System.err.println("[Companion AI] API error " + responseCode + ": " + responseBody);
                    return new AIAction("SPEAK", 0, 0, 0, "API error " + responseCode);
                }

                // Parse response: data.choices[0].message.content (same as ai-service.js)
                AIAction result = parseResponse(responseBody);

                // Save to conversation history
                synchronized (conversationHistory) {
                    conversationHistory.add(userMsg.deepCopy());
                    JsonObject assistantMsg = new JsonObject();
                    assistantMsg.addProperty("role", "assistant");
                    assistantMsg.addProperty("content", GSON.toJson(result));
                    conversationHistory.add(assistantMsg);

                    while (conversationHistory.size() > MAX_HISTORY) {
                        conversationHistory.remove(0);
                    }
                }

                return result;

            } catch (Exception e) {
                System.err.println("[Companion AI] Exception: " + e.getMessage());
                return new AIAction("SPEAK", 0, 0, 0, "I had a connection error...");
            }
        });
    }

    private AIAction parseResponse(String responseJson) {
        try {
            JsonObject root = GSON.fromJson(responseJson, JsonObject.class);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && !choices.isEmpty()) {
                String content = choices.get(0).getAsJsonObject()
                        .getAsJsonObject("message")
                        .get("content").getAsString().trim();

                // Clean markdown if present
                content = content.replace("```json", "").replace("```", "").trim();
                // Remove any leading/trailing non-JSON chars
                int start = content.indexOf('{');
                int end = content.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    content = content.substring(start, end + 1);
                }

                JsonObject action = GSON.fromJson(content, JsonObject.class);
                String act = action.has("action") ? action.get("action").getAsString() : "FOLLOW";
                int x = action.has("x") ? action.get("x").getAsInt() : 0;
                int y = action.has("y") ? action.get("y").getAsInt() : 0;
                int z = action.has("z") ? action.get("z").getAsInt() : 0;
                String say = action.has("say") ? action.get("say").getAsString() : null;
                if (say == null && action.has("message")) {
                    say = action.get("message").getAsString();
                }
                return new AIAction(act, x, y, z, say);
            }
        } catch (Exception e) {
            System.err.println("[Companion AI] Parse error: " + e.getMessage());
        }
        return new AIAction("FOLLOW", 0, 0, 0, "Hmm, let me think...");
    }
}
