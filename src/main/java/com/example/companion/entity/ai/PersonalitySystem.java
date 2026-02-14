package com.example.companion.entity.ai;

import java.util.Random;

/**
 * Personality system: makes the companion talk like a real Russian gamer bro.
 * Adds mats, slang, emotions, and contextual reactions.
 */
public class PersonalitySystem {

    private static final Random RNG = new Random();

    public enum ResponseStyle {
        CASUAL,
        EXCITED,
        ANNOYED,
        PANICKED,
        SMUG
    }

    public enum GameEventType {
        FOUND_DIAMONDS,
        FOUND_IRON,
        FOUND_GOLD,
        PLAYER_DIED,
        PLAYER_LOW_HP,
        CREEPER_NEARBY,
        CRAFTED_DIAMOND_PICKAXE,
        CRAFTED_ITEM,
        KILLED_MOB,
        PLAYER_BUILDING_USELESS,
        ENTERED_NETHER,
        ENTERED_END,
        NIGHT_TIME
    }

    // ── public API ──────────────────────────────────────────────────────────

    /**
     * Format a base message with personality style.
     */
    public static String formatMessage(String baseMessage, ResponseStyle style) {
        if (baseMessage == null || baseMessage.isEmpty()) return baseMessage;
        return switch (style) {
            case EXCITED  -> addExcitement(baseMessage);
            case ANNOYED  -> addAnnoyance(baseMessage);
            case PANICKED -> addPanic(baseMessage);
            case SMUG     -> addSmug(baseMessage);
            default       -> addCasual(baseMessage);
        };
    }

    /**
     * Determine the best style from action context.
     */
    public static ResponseStyle determineStyle(String action, boolean hasHostiles, int selfHp, int maxHp) {
        if (hasHostiles && selfHp < maxHp / 3) return ResponseStyle.PANICKED;
        if (hasHostiles) return ResponseStyle.EXCITED;
        if (selfHp < maxHp / 4) return ResponseStyle.PANICKED;

        return switch (action) {
            case "mine", "craft", "place_block", "build" -> ResponseStyle.SMUG;
            case "attack"                                -> ResponseStyle.EXCITED;
            case "eat"                                   -> ResponseStyle.CASUAL;
            default                                      -> ResponseStyle.CASUAL;
        };
    }

    /**
     * Generate a spontaneous reaction for a game event.
     */
    public static String reactToEvent(GameEventType event) {
        return switch (event) {
            case FOUND_DIAMONDS -> formatMessage(
                    pick("НАШЁЛ АЛМАЗЫ БЛЯ!", "АЛМАААЗЫ!", "ЕБАААТЬ АЛМАЗЫ!"),
                    ResponseStyle.EXCITED);
            case FOUND_IRON -> formatMessage(
                    pick("О, железо! Идём крафтить", "Железо нашёл, кайф"),
                    ResponseStyle.SMUG);
            case FOUND_GOLD -> formatMessage(
                    pick("Золото, неплохо", "О, голда"),
                    ResponseStyle.CASUAL);
            case PLAYER_DIED -> formatMessage(
                    pick("Ну ты лох конечно... я же говорил не лезь туда",
                         "Бля, ты умер... ну ладно, бывает",
                         "RIP бро... я же предупреждал"),
                    ResponseStyle.ANNOYED);
            case PLAYER_LOW_HP -> formatMessage(
                    pick("У тебя мало хп, жри еду!",
                         "Бро, ты почти сдох, аккуратнее!",
                         "ХП НИЗКОЕ! ЕШЬ БЛЯТЬ!"),
                    ResponseStyle.PANICKED);
            case CREEPER_NEARBY -> formatMessage(
                    pick("КРИПЕР РЯДОМ!", "ЁБАНЫЙ КРИПЕР, БЕГИ!",
                         "КРИПЕР БЛЯТЬ, ОТБЕГАЙ!"),
                    ResponseStyle.PANICKED);
            case CRAFTED_DIAMOND_PICKAXE -> formatMessage(
                    pick("Скрафтил алмазную кирку. Теперь заживём",
                         "Алмазная кирка готова, изи катка"),
                    ResponseStyle.SMUG);
            case CRAFTED_ITEM -> formatMessage(
                    pick("Готово", "Скрафтил", "Сделал"),
                    ResponseStyle.SMUG);
            case KILLED_MOB -> formatMessage(
                    pick("Убил! Легко", "Сдох, лол", "Изи килл"),
                    ResponseStyle.SMUG);
            case PLAYER_BUILDING_USELESS -> formatMessage(
                    pick("Ты там дом строишь? Нам дракона убить надо!",
                         "Бля, не время для стройки, давай к дракону",
                         "Строишь? Потом, сейчас ресурсы важнее"),
                    ResponseStyle.ANNOYED);
            case ENTERED_NETHER -> formatMessage(
                    pick("О, Незер! Ищем крепость", "Незер, бля. Аккуратнее тут"),
                    ResponseStyle.EXCITED);
            case ENTERED_END -> formatMessage(
                    pick("ЭНД! ДРАКОН СДОХНЕТ!", "ПОЕХАЛИ! ДРАКОН, МЫ ИДЁМ!"),
                    ResponseStyle.EXCITED);
            case NIGHT_TIME -> formatMessage(
                    pick("Ночь, мобы полезут. Будь начеку",
                         "Темнеет, аккуратнее"),
                    ResponseStyle.CASUAL);
        };
    }

    // ── style formatters ────────────────────────────────────────────────────

    private static String addExcitement(String msg) {
        String[] laughs = {
            "АХАХХАХА", "ААХХАХХАХ", "ХАХАХ БЛЯ", "ЛЕЕЕТС ГООООУ",
            "ЕБАААААТЬ", "ВОТ ЭТО ДА", "ОООО ЕБАТЬ"
        };
        String[] prefixes = {
            "ЙОООООО! ", "БЛЯЯЯЯ! ", "ООООО! ", "СМОТРИ! "
        };
        return pick(prefixes) + msg + " " + pick(laughs) + "!!!";
    }

    private static String addAnnoyance(String msg) {
        String[] prefixes = {
            "Бля, ", "Серьёзно? ", "Ну ты даёшь... ", "Эх... ",
            "Ай блять... ", "Мать твою... "
        };
        String[] suffixes = {
            ", ну ты понял да?", ", капец", ", думай башкой",
            ", не тупи", ", бля"
        };
        return pick(prefixes) + msg + pick(suffixes);
    }

    private static String addPanic(String msg) {
        String[] intros = {
            "ААААА! ", "БЕГИ НАХУЙ! ", "ПИЗДЕЦ! ", "ДУЙЙ ОТСЮДА! "
        };
        return pick(intros) + msg.toUpperCase() + "!!!";
    }

    private static String addSmug(String msg) {
        String[] prefixes = {
            "Вот так вот. ", "Легко. ", "Изи катка. ", "Профи сделал. "
        };
        String[] suffixes = {
            " хех", " авхахыахых", " кайф", ""
        };
        return pick(prefixes) + msg + pick(suffixes);
    }

    private static String addCasual(String msg) {
        String[] connectors = {"бля", "короче", "слушай", "кстати", "ну"};
        if (RNG.nextBoolean()) {
            return pick(connectors) + ", " + msg;
        }
        return msg;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String pick(String... options) {
        return options[RNG.nextInt(options.length)];
    }
}
