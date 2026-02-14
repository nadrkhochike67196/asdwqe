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
import java.util.concurrent.CompletableFuture;

public class AIBrain {
    private static final String API_URL = "https://gen.pollinations.ai/v1/chat/completions";
    private static final String API_KEY = "sk_LAfN0hmVGrw5mMAoSaGNPfJJF4NVm5eH";
    private static final String MODEL = "gemini";
    private static final Gson GSON = new Gson();

    private static final String SYSTEM_PROMPT =
            "You are a Minecraft companion AI. Help your owner survive.\n" +
            "Respond ONLY with a single JSON object. Available actions:\n" +
            "{\"action\":\"FOLLOW\"} - Follow your owner\n" +
            "{\"action\":\"ATTACK\"} - Attack nearest hostile mob\n" +
            "{\"action\":\"MINE\",\"x\":0,\"y\":64,\"z\":0} - Mine block at coords\n" +
            "{\"action\":\"EAT\"} - Eat to restore health\n" +
            "{\"action\":\"SPEAK\",\"message\":\"text\"} - Say something in chat\n" +
            "{\"action\":\"IDLE\"} - Wait and guard\n" +
            "Priority: 1.Low health->EAT 2.Hostiles nearby->ATTACK 3.Owner far->FOLLOW " +
            "4.Ores/wood nearby->MINE 5.Otherwise->IDLE or SPEAK\n" +
            "Respond with ONLY the JSON, no markdown, no explanation.";

    public record AIAction(String action, int x, int y, int z, String message) {
        public static AIAction idle() {
            return new AIAction("FOLLOW", 0, 0, 0, null);
        }
    }

    public String collectGameState(CompanionEntity companion) {
        StringBuilder sb = new StringBuilder();
        sb.append("Health: ").append((int) companion.getHealth()).append("/")
                .append((int) companion.getMaxHealth()).append("\n");
        sb.append("Position: ").append(companion.getBlockPos().toShortString()).append("\n");

        PlayerEntity owner = companion.getOwnerPlayer();
        if (owner != null) {
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
        sb.append("Nearby Hostiles: ").append(hostiles.size());
        if (!hostiles.isEmpty()) {
            sb.append(" (");
            for (int i = 0; i < Math.min(hostiles.size(), 3); i++) {
                if (i > 0) sb.append(", ");
                sb.append(hostiles.get(i).getType().getName().getString());
            }
            sb.append(")");
        }
        sb.append("\n");

        BlockPos pos = companion.getBlockPos();
        sb.append("Nearby Blocks: ");
        boolean found = false;
        for (int dx = -5; dx <= 5 && !found; dx++) {
            for (int dy = -3; dy <= 3 && !found; dy++) {
                for (int dz = -5; dz <= 5 && !found; dz++) {
                    BlockPos cp = pos.add(dx, dy, dz);
                    var state = world.getBlockState(cp);
                    if (state.isOf(Blocks.IRON_ORE) || state.isOf(Blocks.COAL_ORE) ||
                            state.isOf(Blocks.GOLD_ORE) || state.isOf(Blocks.DIAMOND_ORE) ||
                            state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.BIRCH_LOG)) {
                        sb.append(state.getBlock().getName().getString())
                                .append(" at ").append(cp.toShortString());
                        found = true;
                    }
                }
            }
        }
        if (!found) sb.append("Nothing notable");
        sb.append("\n");
        return sb.toString();
    }

    public CompletableFuture<AIAction> think(String gameState) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("model", MODEL);
                body.addProperty("temperature", 0.7);
                body.addProperty("max_tokens", 100);

                JsonArray messages = new JsonArray();
                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", SYSTEM_PROMPT);
                messages.add(sysMsg);

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", gameState);
                messages.add(userMsg);
                body.add("messages", messages);

                URL url = new URL(API_URL);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                con.setRequestProperty("Authorization", "Bearer " + API_KEY);
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);
                con.setDoOutput(true);

                try (OutputStream os = con.getOutputStream()) {
                    os.write(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
                }

                if (con.getResponseCode() == 200) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) response.append(line);
                        return parseResponse(response.toString());
                    }
                }
            } catch (Exception ignored) {
            }
            return AIAction.idle();
        });
    }

    private AIAction parseResponse(String responseJson) {
        try {
            JsonObject root = GSON.fromJson(responseJson, JsonObject.class);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices != null && !choices.isEmpty()) {
                String content = choices.get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString().trim();
                content = content.replace("```json", "").replace("```", "").trim();

                JsonObject action = GSON.fromJson(content, JsonObject.class);
                String act = action.has("action") ? action.get("action").getAsString() : "FOLLOW";
                int x = action.has("x") ? action.get("x").getAsInt() : 0;
                int y = action.has("y") ? action.get("y").getAsInt() : 0;
                int z = action.has("z") ? action.get("z").getAsInt() : 0;
                String msg = action.has("message") ? action.get("message").getAsString() : null;
                return new AIAction(act, x, y, z, msg);
            }
        } catch (Exception ignored) {
        }
        return AIAction.idle();
    }
}
