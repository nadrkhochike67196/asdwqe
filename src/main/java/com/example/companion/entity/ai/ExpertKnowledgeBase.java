package com.example.companion.entity.ai;

/**
 * Expert Minecraft knowledge base — speedrun strategies,
 * optimal Y-levels, phase-specific advice, and smart commentary.
 */
public class ExpertKnowledgeBase {

    // ── Optimal Y-levels ────────────────────────────────────────────────────

    public static final int DIAMOND_Y = -59;   // 1.18+ deep-slate
    public static final int IRON_Y    = 16;
    public static final int GOLD_Y    = -16;
    public static final int LAPIS_Y   = 0;

    // ── Minimum resource thresholds ─────────────────────────────────────────

    public static final int MIN_WOOD        = 8;   // 32 planks
    public static final int MIN_STONE       = 11;  // pickaxe+sword+axe
    public static final int MIN_IRON        = 11;  // pick+sword+bucket+shield
    public static final int MIN_DIAMONDS    = 5;   // pick+enchanting table
    public static final int MIN_OBSIDIAN    = 10;  // nether portal
    public static final int MIN_BLAZE_RODS  = 7;   // brewing + eyes
    public static final int MIN_ENDER_PEARLS = 16; // eyes + spare

    // ── Phase-specific optimal strategy ─────────────────────────────────────

    public static String getOptimalStrategy(String phase) {
        return switch (phase) {
            case "START" -> """
                    1. Набей 4 бревна → верстак
                    2. Крафт деревянная кирка
                    3. Накопай 11 камня
                    4. Крафт каменная кирка + каменный меч + каменный топор
                    5. Убей 3 овцы → кровать
                    6. Найди пещеру или копай до Y=16""";

            case "STONE_AGE" -> """
                    1. Ищи пещеру или копай вниз
                    2. Собирай уголь по дороге (факелы)
                    3. Цель: найти железо (Y=16 оптимально)
                    4. Минимум 11 железных слитков""";

            case "IRON_HUNTING" -> """
                    1. Копай на Y=16, ищи железо
                    2. Нужно 11 слитков минимум
                    3. Крафт: жел. кирка + жел. меч + ведро
                    4. Набери воду в ведро (MLG спасение)
                    5. Потом → копай до Y=-59 за алмазами""";

            case "DIAMOND_HUNTING" -> """
                    1. Strip-mining на Y=-59
                    2. Минимум 5 алмазов (кирка + стол зачарований)
                    3. Крафт алмазная кирка
                    4. Накопай 10+ обсидиана
                    5. Если есть лава рядом → портал Незера""";

            case "NETHER_PREP" -> """
                    1. Собери 10 обсидиана
                    2. Построй портал Незера
                    3. Возьми: алм. кирку, жел. меч, лук, еду, ведро воды
                    4. Кровать для респавна РЯДОМ с порталом
                    5. Вперёд в Незер""";

            case "NETHER" -> """
                    1. Найди Адскую крепость (fortress)
                    2. Убей ифритов (минимум 7 жезлов)
                    3. Опционально: бастион для золота/зелья огнестойкости
                    4. Собери эндер-жемчуг (бартер с пиглинами или эндермены)
                    5. Вернись живым!""";

            case "STRONGHOLD" -> """
                    1. Крафт очей Эндера (минимум 12)
                    2. Триангуляция: кинь око, иди 500 блоков, кинь снова
                    3. Копай до крепости
                    4. Активируй портал (12 очей)
                    5. Подготовься: лук+стрелы, кровати(5-7), золотые яблоки, ведро воды""";

            case "END_FIGHT" -> """
                    1. MLG ведро воды при падении со спавна
                    2. Расстреляй кристаллы в клетках (или построй столб)
                    3. Расстреляй открытые кристаллы
                    4. Жди фазу приземления дракона
                    5. Взрывай кроватями когда дракон сядет
                    6. Добей мечом""";

            default -> "Прогрессируй дальше! Добывай ресурсы и двигайся к дракону.";
        };
    }

    // ── Expert contextual commentary ────────────────────────────────────────

    /**
     * Generate expert commentary based on current situation.
     * Used as extra context injected into the AI prompt.
     */
    public static String getExpertCommentary(String phase, int playerHp, int selfHp,
                                              boolean hasHostiles, int yLevel) {
        StringBuilder sb = new StringBuilder();

        // Critical danger
        if (playerHp <= 6 && hasHostiles) {
            sb.append("КРИТИЧНО: Игрок при смерти и враги рядом! Защити его! ");
        } else if (playerHp <= 6) {
            sb.append("Игрок на низком ХП, нужна еда или отступление. ");
        }

        if (selfHp <= 10 && hasHostiles) {
            sb.append("Моё ХП тоже низкое, надо быть осторожнее. ");
        }

        // Y-level advice
        if (phase.equals("DIAMOND_HUNTING") || phase.equals("IRON_HUNTING")) {
            if (yLevel > 30) {
                sb.append("Сейчас на Y=").append(yLevel).append(" — слишком высоко для руды! ");
                if (phase.equals("DIAMOND_HUNTING")) {
                    sb.append("Алмазы на Y=-59, КОПАЙ ВНИЗ. ");
                } else {
                    sb.append("Железо на Y=16, копай вниз. ");
                }
            } else if (phase.equals("DIAMOND_HUNTING") && yLevel > 0) {
                sb.append("Для алмазов нужно на Y=-59, сейчас Y=").append(yLevel).append(". ");
            }
        }

        // Phase-specific tips
        switch (phase) {
            case "START" -> {
                sb.append("Ранняя игра: приоритет — дерево, верстак, кирка. ");
            }
            case "NETHER" -> {
                sb.append("В Незере: ищи крепость (кирпичи незера), не отвлекайся. ");
                if (yLevel > 80) {
                    sb.append("Слишком высоко для крепости, обычно Y=40-70. ");
                }
            }
            case "END_FIGHT" -> {
                sb.append("БОЙ С ДРАКОНОМ: кристаллы первым делом, потом кровати. ");
            }
        }

        return sb.toString().trim();
    }

    // ── Optimization check ──────────────────────────────────────────────────

    /**
     * Check if a proposed craft action should be upgraded.
     * Returns null if no optimization, or the better recipe name.
     */
    public static String optimizeCraft(String proposedRecipe, int ironCount, int diamondCount) {
        // Don't craft stone tools if iron is available
        if (proposedRecipe.startsWith("stone_") && ironCount >= 3) {
            String ironVersion = proposedRecipe.replace("stone_", "iron_");
            return ironVersion;
        }
        // Don't craft iron tools if diamonds are available
        if (proposedRecipe.startsWith("iron_") && diamondCount >= 3) {
            String diamondVersion = proposedRecipe.replace("iron_", "diamond_");
            return diamondVersion;
        }
        return null; // no optimization
    }
}
