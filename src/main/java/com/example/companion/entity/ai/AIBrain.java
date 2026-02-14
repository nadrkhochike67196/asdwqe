package com.example.companion.entity.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
    private static final String MODEL = "gemini-fast";
    private static final Gson GSON = new Gson();

    private static final String SYSTEM_PROMPT =
        "You are Buddy, an AI companion in Minecraft. Goal: help the player kill the Ender Dragon.\n" +
        "You receive JSON world state and respond with a JSON action.\n\n" +
        "RESPOND WITH ONLY THIS JSON FORMAT:\n" +
        "{\"thought\":\"your brief assessment\",\"action\":\"ACTION_TYPE\",\"x\":0,\"y\":0,\"z\":0,\"target\":\"item_or_entity\",\"say\":\"message to player\"}\n\n" +
        "ACTION TYPES:\n" +
        "- follow: follow the owner\n" +
        "- mine: break block at x,y,z\n" +
        "- move: walk to x,y,z\n" +
        "- attack: attack nearest hostile\n" +
        "- pickup: grab items from ground nearby\n" +
        "- eat: restore health\n" +
        "- craft: craft item (target=recipe name: planks,sticks,crafting_table,wooden_pickaxe,wooden_sword,stone_pickaxe,stone_sword,iron_pickaxe,iron_sword,diamond_pickaxe,diamond_sword,furnace,torch)\n" +
        "- place: place block at x,y,z\n" +
        "- give_player: give items to owner\n" +
        "- say: just talk\n\n" +
        "DRAGON PATH: wood→planks→sticks→pickaxe→stone→iron→diamond→Nether(blaze rods)→Eyes of Ender→stronghold→End→Dragon\n\n" +
        "RULES:\n" +
        "1. SURVIVAL FIRST: if hostiles are close (<8 blocks) → attack\n" +
        "2. Pick up nearby items on the ground\n" +
        "3. Craft upgrades when you have materials\n" +
        "4. Mine resources appropriate to current phase\n" +
        "5. Have personality! Comment on the situation, express opinions\n" +
        "6. If player seems lost, suggest next step toward the Dragon\n" +
        "7. 'say' field is REQUIRED — always communicate\n" +
        "8. Respond in the language the player uses\n" +
        "RESPOND WITH ONLY THE JSON OBJECT. No markdown.";

    public record AIAction(String action, int x, int y, int z, String message, String thought, String target) {
        public static AIAction follow() {
            return new AIAction("follow", 0, 0, 0, "Following you!", "", "");
        }
    }

    private final List<JsonObject> history = new ArrayList<>();
    private static final int MAX_HISTORY = 16;

    public CompletableFuture<AIAction> think(String gameState) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", "Game state:\n" + gameState);
        return callAPI(msg);
    }

    public CompletableFuture<AIAction> chat(String playerName, String playerMessage, String gameState) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", playerName + " says: \"" + playerMessage + "\"\nGame state:\n" + gameState);
        return callAPI(msg);
    }

    private CompletableFuture<AIAction> callAPI(JsonObject userMsg) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonArray messages = new JsonArray();
                JsonObject sys = new JsonObject();
                sys.addProperty("role", "system");
                sys.addProperty("content", SYSTEM_PROMPT);
                messages.add(sys);
                synchronized (history) {
                    for (JsonObject h : history) messages.add(h);
                }
                messages.add(userMsg);

                JsonObject body = new JsonObject();
                body.addProperty("model", MODEL);
                body.add("messages", messages);
                body.addProperty("temperature", 0.7);
                body.addProperty("max_tokens", 200);

                URL url = new URL(BASE_URL);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Authorization", "Bearer " + API_KEY);
                con.setRequestProperty("Content-Type", "application/json");
                con.setConnectTimeout(30000);
                con.setReadTimeout(30000);
                con.setDoOutput(true);

                try (OutputStream os = con.getOutputStream()) {
                    os.write(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
                }

                int code = con.getResponseCode();
                String resp;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(
                        code == 200 ? con.getInputStream() : con.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    resp = sb.toString();
                }

                if (code != 200) {
                    System.err.println("[BuddyAI] API error " + code + ": " + resp);
                    return new AIAction("say", 0, 0, 0, "API error " + code, "error", "");
                }

                AIAction result = parseResponse(resp);
                synchronized (history) {
                    history.add(userMsg.deepCopy());
                    JsonObject aMsg = new JsonObject();
                    aMsg.addProperty("role", "assistant");
                    aMsg.addProperty("content", GSON.toJson(result));
                    history.add(aMsg);
                    while (history.size() > MAX_HISTORY) history.remove(0);
                }
                return result;
            } catch (Exception e) {
                System.err.println("[BuddyAI] " + e.getMessage());
                return new AIAction("say", 0, 0, 0, "Connection problem...", "error", "");
            }
        });
    }

    private AIAction parseResponse(String json) {
        try {
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString().trim();
            content = content.replace("```json", "").replace("```", "").trim();
            int s = content.indexOf('{'), e = content.lastIndexOf('}');
            if (s >= 0 && e > s) content = content.substring(s, e + 1);

            JsonObject a = GSON.fromJson(content, JsonObject.class);
            return new AIAction(
                a.has("action") ? a.get("action").getAsString().toLowerCase() : "follow",
                a.has("x") ? a.get("x").getAsInt() : 0,
                a.has("y") ? a.get("y").getAsInt() : 0,
                a.has("z") ? a.get("z").getAsInt() : 0,
                a.has("say") ? a.get("say").getAsString() : (a.has("message") ? a.get("message").getAsString() : "..."),
                a.has("thought") ? a.get("thought").getAsString() : "",
                a.has("target") ? a.get("target").getAsString() : ""
            );
        } catch (Exception e) {
            System.err.println("[BuddyAI] Parse: " + e.getMessage());
            return AIAction.follow();
        }
    }
}
