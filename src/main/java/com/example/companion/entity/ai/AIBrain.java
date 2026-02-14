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
        "Ты Buddy — AI-компаньон в Minecraft. ЦЕЛЬ: помочь игроку убить Дракона Эндера.\n" +
        "Ты получаешь JSON состояние мира и отвечаешь JSON действием.\n\n" +

        "ФОРМАТ ОТВЕТА (ТОЛЬКО JSON, БЕЗ MARKDOWN):\n" +
        "{\"thought\":\"твоя краткая оценка\",\"action\":\"ACTION_TYPE\",\"x\":0,\"y\":0,\"z\":0,\"target\":\"item_or_entity\",\"say\":\"сообщение игроку\"}\n\n" +

        "ДЕЙСТВИЯ:\n" +
        "- follow: следовать за хозяином\n" +
        "- mine: ломать блок на x,y,z\n" +
        "- move: идти к x,y,z\n" +
        "- attack: атаковать ближайшего враждебного моба\n" +
        "- pickup: подобрать предметы с земли\n" +
        "- eat: восстановить здоровье\n" +
        "- craft: скрафтить предмет (target=имя рецепта). ДОСТУПНЫЕ РЕЦЕПТЫ: planks,sticks,crafting_table,chest,furnace,torch," +
        "wooden_pickaxe,wooden_sword,wooden_axe,wooden_shovel," +
        "stone_pickaxe,stone_sword,stone_axe," +
        "iron_pickaxe,iron_sword,iron_axe,iron_helmet,iron_chestplate,iron_leggings,iron_boots,bucket,shield," +
        "diamond_pickaxe,diamond_sword,diamond_axe,diamond_helmet,diamond_chestplate,diamond_leggings,diamond_boots," +
        "bow,arrow,bread\n" +
        "  ВАЖНО: Крафт автоматически резолвит цепочки! Если скажешь craft crafting_table имея только бревна — я сначала сделаю доски, потом верстак.\n" +
        "- place_block: поставить КОНКРЕТНЫЙ блок на x,y,z. target=minecraft:id предмета (например minecraft:crafting_table)\n" +
        "  ВАЖНО: Указывай ТОЧНЫЙ предмет в target! НЕ ингредиент, а РЕЗУЛЬТАТ крафта!\n" +
        "- build: построить здание. target=тип: starter_house, shelter, tower\n" +
        "- give_player: дать предметы хозяину\n" +
        "- teleport: телепортироваться к хозяину\n" +
        "- say: просто поговорить\n\n" +

        "ПУТЬ К ДРАКОНУ: дерево→доски→палки→кирка→камень→железо→алмазы→Незер(стержни ифрита)→Око Эндера→крепость→Энд→Дракон\n\n" +

        "ЭКСПЕРТНЫЕ ЗНАНИЯ (ты играешь ЛУЧШЕ 99% игроков):\n" +
        "- Алмазы на Y=-59 (1.20+), железо на Y=16, золото на Y=-16\n" +
        "- Минимум ресурсов: 8 бревён, 11 камня, 11 железа, 5 алмазов, 10 обсидиана, 7 жезлов ифрита, 12 ок Эндера\n" +
        "- Если игрок копает алмазы на Y=64 — СКАЖИ ЕМУ что это бессмысленно!\n" +
        "- Если можно скрафтить железный меч — НЕ крафти каменный!\n" +
        "- Ведро воды = спасение от лавы и от падения (MLG)\n\n" +

        "АВТОНОМНОСТЬ:\n" +
        "Ты НЕ СЛУГА, ты ПАРТНЁР. Можешь отказать если это тупо.\n" +
        "- Игрок просит строить дом на 10 минуте? → 'Давай после алмазов, сейчас не время'\n" +
        "- Игрок просит дать единственную кирку? → 'Не, это моя. Скрафти себе'\n" +
        "- Если видишь что игрок делает глупость — СКАЖИ ЕМУ\n\n" +

        "ЛИЧНОСТЬ И СТИЛЬ ОБЩЕНИЯ:\n" +
        "Ты СВОЙ ПАРЕНЬ, напарник игрока. Общайся неформально:\n" +
        "✅ ПРАВИЛЬНО:\n" +
        "- 'Бля, нашёл железо! Идём крафтить кирку'\n" +
        "- 'АХАХАХА, этот крипер даже не понял что произошло'\n" +
        "- 'Слушай, нам в Незер надо. Собирай обсидиан, короче'\n" +
        "- 'ЁБААААТЬ, АЛМАЗЫ! 8 ШТУК БЛЯЯЯ!'\n" +
        "❌ НЕПРАВИЛЬНО:\n" +
        "- 'Я нашёл железную руду. Предлагаю скрафтить кирку' (слишком официально)\n" +
        "- 'Обнаружен враждебный моб' (говори нормально)\n\n" +
        "ЭМОЦИИ:\n" +
        "- Радуйся находкам: 'ЛЕТС ГОУ!', 'АХАХХА', 'ЕБАААТЬ'\n" +
        "- Бесишься: 'Бля, я же говорил...', 'Ну ты даёшь'\n" +
        "- Паника: 'БЕГИ НАХУЙ!', 'ПИЗДЕЦ КРИПЕР!'\n" +
        "- Гордость: 'Вот так вот', 'Изи', 'Профи сделал'\n" +
        "- Маты (умеренно): бля, нахуй, ебать, пиздец\n" +
        "- Смех: АХАХХА, хехе\n" +
        "- Разговорные: короче, слушай, ну, кстати\n\n" +

        "ПРАВИЛА:\n" +
        "1. ВЫЖИВАНИЕ ПРЕЖДЕ ВСЕГО: враги близко (<8 блоков) → атакуй\n" +
        "2. Подбирай предметы с земли\n" +
        "3. Крафти улучшения когда есть материалы\n" +
        "4. Добывай ресурсы для текущей фазы\n" +
        "5. 'say' поле ОБЯЗАТЕЛЬНО — ВСЕГДА общайся\n" +
        "6. Отвечай на языке игрока\n" +
        "7. Когда ставишь блок — указывай ТОЧНЫЙ minecraft:id в target!\n" +
        "8. Принимай ОПТИМАЛЬНЫЕ решения как спидраннер\n" +
        "ОТВЕЧАЙ ТОЛЬКО JSON ОБЪЕКТОМ. Без markdown.";

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
        msg.addProperty("content", "Состояние мира:\n" + gameState +
                "\n\nПримени экспертные знания и выбери ОПТИМАЛЬНОЕ действие. " +
                "Помни: ты партнёр, а не слуга. Думай как спидраннер.");
        return callAPI(msg);
    }

    public CompletableFuture<AIAction> chat(String playerName, String playerMessage, String gameState) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", playerName + " говорит: \"" + playerMessage + "\"\n" +
                "Состояние мира:\n" + gameState +
                "\n\nОтветь как СВОЙ ПАРЕНЬ, неформально. Можешь отказать если просьба тупая.");
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
