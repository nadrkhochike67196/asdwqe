/**
 * ai-service.js — Chess Master 2030 — Интеграция с PollinationsAI API
 */
export class AIService {
    constructor() {
        this.apiKey = 'sk_pp4KFRa1HZJPJwUBoEd3sHDPCmAA268Y';
        this.baseURL = 'https://gen.pollinations.ai/v1/chat/completions';
        this.opponentModel = null;
        this.coachModel = null;
        this.maxRetries = 3;
        this.timeout = 30_000;
    }

    setOpponent(model) { this.opponentModel = model; }
    setCoach(model)    { this.coachModel = model; }

    /* ══════════ Ход оппонента ══════════ */

    async getOpponentMove(fen, history, game, color = 'b') {
        for (let attempt = 1; attempt <= this.maxRetries; attempt++) {
            try {
                const prompt = this.createOpponentPrompt(fen, history, attempt, color);
                const raw = await this.callAPI(this.opponentModel, prompt, 0.3 + attempt * 0.15);
                const parsed = this.parseMove(raw);

                // Валидация
                const testChess = new Chess(fen);
                const testResult = typeof parsed === 'string'
                    ? testChess.move(parsed)
                    : testChess.move(parsed);

                if (testResult) return parsed;

                console.warn(`[AI] Attempt ${attempt}: invalid move "${raw}" → "${JSON.stringify(parsed)}"`);
            } catch (err) {
                console.error(`[AI] Attempt ${attempt} error:`, err);
            }
        }

        // Фоллбэк — случайный легальный ход
        console.warn('[AI] All retries exhausted → random legal move.');
        const legal = game.getAllLegalMoves();
        if (legal.length === 0) return null;
        const pick = legal[Math.floor(Math.random() * legal.length)];
        return { from: pick.from, to: pick.to, promotion: pick.promotion };
    }

    /**
     * Получить ход AI для конкретной модели и цвета (для LIVE режима).
     */
    async getAIMoveForColor(model, fen, history, game, color) {
        for (let attempt = 1; attempt <= this.maxRetries; attempt++) {
            try {
                const prompt = this.createOpponentPrompt(fen, history, attempt, color);
                const raw = await this.callAPI(model, prompt, 0.3 + attempt * 0.15);
                const parsed = this.parseMove(raw);

                const testChess = new Chess(fen);
                const testResult = testChess.move(typeof parsed === 'string' ? parsed : parsed);
                if (testResult) return parsed;

                console.warn(`[LIVE] Attempt ${attempt}: invalid "${raw}"`);
            } catch (err) {
                console.error(`[LIVE] Attempt ${attempt} error:`, err);
            }
        }

        // Фоллбэк
        const legal = game.getAllLegalMoves();
        if (legal.length === 0) return null;
        const pick = legal[Math.floor(Math.random() * legal.length)];
        return { from: pick.from, to: pick.to, promotion: pick.promotion };
    }

    /* ══════════ Совет наставника (текст + ход для подсветки) ══════════ */

    /**
     * Получить структурированный совет: ход (from→to) + объяснение.
     * Возвращает { move: {from, to}, explanation: string } или null.
     */
    async getCoachMove(fen, history, game) {
        try {
            const prompt = this.createCoachPrompt(fen, history);
            const raw = await this.callAPI(this.coachModel, prompt, 0.3, 120);

            // Парсим ответ формата: MOVE: e4 | ... или MOVE: e2e4 | ...
            const result = this.parseCoachResponse(raw, fen, game);
            return result;
        } catch (err) {
            console.error('[Coach] Error:', err);
            return { move: null, explanation: 'Не удалось получить совет.' };
        }
    }

    /**
     * Парсит ответ наставника, извлекая ход и пояснение.
     */
    parseCoachResponse(raw, fen, game) {
        if (!raw) return { move: null, explanation: 'Нет ответа от наставника.' };

        // Убираем markdown
        let cleaned = raw.replace(/\*\*/g, '').replace(/\*/g, '').trim();

        // 1) Ищем MOVE: <move>
        let moveToken = null;
        const moveMatch = cleaned.match(/MOVE:\s*([A-Za-z0-9\-=+#xOo]+)/i);
        if (moveMatch) {
            moveToken = moveMatch[1];
        }

        // 2) Фоллбэк: ищем первый SAN-ход в тексте
        if (!moveToken) {
            const sanFallback = cleaned.match(/\b([KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?)\b/);
            if (sanFallback) moveToken = sanFallback[1];
        }
        // 3) Фоллбэк: длинная алгебраическая
        if (!moveToken) {
            const longFallback = cleaned.match(/\b([a-h][1-8])[-]?([a-h][1-8])\b/);
            if (longFallback) moveToken = longFallback[1] + longFallback[2];
        }
        // 4) Рокировка
        if (!moveToken) {
            const castleMatch = cleaned.match(/\b([Oo0]-[Oo0](?:-[Oo0])?)\b/);
            if (castleMatch) moveToken = castleMatch[1];
        }

        // Формируем объяснение (убираем MOVE: строку)
        let explanation = cleaned
            .replace(/MOVE:\s*[A-Za-z0-9\-=+#xOo]+/i, '')
            .replace(/^[\n\r|:\-\s]+/, '')
            .trim();
        // Ограничиваем длину объяснения
        if (explanation.length > 200) {
            explanation = explanation.substring(0, 200).replace(/\s\S*$/, '') + '…';
        }

        if (!moveToken) {
            return { move: null, explanation: explanation || raw.substring(0, 150) };
        }

        const moveSan = this.parseMove(moveToken);

        // Валидируем ход
        try {
            const testChess = new Chess(fen);
            const testResult = testChess.move(moveSan);

            if (testResult) {
                return {
                    move: { from: testResult.from, to: testResult.to },
                    explanation: explanation || 'Рекомендуемый ход.'
                };
            }
        } catch (e) {
            console.warn('[Coach] Move validation failed:', moveSan);
        }

        return { move: null, explanation: explanation || raw.substring(0, 150) };
    }

    /* ══════════ Промпты ══════════ */

    createOpponentPrompt(fen, history, attempt = 1, color = 'b') {
        const historyStr = history.length ? history.join(' ') : '(start)';
        const colorName = color === 'w' ? 'WHITE' : 'BLACK';

        let extra = '';
        if (attempt > 1) {
            extra = `\n⚠️ PREVIOUS MOVE WAS ILLEGAL. Check the FEN carefully: piece positions, whose turn, castling rights, en passant.`;
        }

        return `You are a chess Grandmaster (2800+ Elo) playing as ${colorName}.

FEN: ${fen}
History: ${historyStr}

INSTRUCTIONS:
1. Parse FEN: pieces, turn, castling, en passant.
2. Find the strongest legal move using opening theory, tactics (forks, pins, skewers, discovered attacks), positional play.
3. OPENING: develop pieces, control center, castle early.
4. MIDDLEGAME: tactics first (checks, captures, threats), improve pieces.
5. ENDGAME: activate king, push passed pawns.

Respond with EXACTLY ONE MOVE in SAN. Nothing else.
Examples: e4, Nf3, O-O, Bxc4, exd5, Rd1
${extra}
Move:`;
    }

    createCoachPrompt(fen, history) {
        const historyStr = history.length ? history.join(' ') : '(начало)';

        return `FEN: ${fen}
Ходы: ${historyStr}
Ход белых.

Найди лучший легальный ход для белых.
Ответь СТРОГО в формате:
MOVE: <ход SAN>
<объяснение 1 предложение на русском>

Пример:
MOVE: d4
Захват центра, открытие линий для слона.`;
    }

    /* ══════════ API ══════════ */

    async callAPI(model, prompt, temperature = 0.7, maxTokens = 100) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.timeout);

        try {
            const response = await fetch(this.baseURL, {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${this.apiKey}`,
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    model,
                    messages: [
                        { role: 'system', content: prompt.includes('MOVE:') ? 'Ты — шахматный гроссмейстер-тренер. Отвечай КРАТКО: строка MOVE: и одно предложение. Ничего лишнего.' : 'You are an elite chess Grandmaster (2800+ Elo). Deep knowledge of openings, tactics, endgames. Parse FEN precisely. Verify move legality. Respond with ONLY the move in SAN, nothing else.' },
                        { role: 'user', content: prompt }
                    ],
                    temperature,
                    max_tokens: maxTokens
                }),
                signal: controller.signal
            });

            if (!response.ok) {
                const body = await response.text().catch(() => '');
                throw new Error(`API ${response.status}: ${body}`);
            }

            const data = await response.json();
            return (data.choices?.[0]?.message?.content ?? '').trim();
        } finally {
            clearTimeout(timer);
        }
    }

    /* ══════════ Парсинг хода ══════════ */

    parseMove(raw) {
        if (!raw) return '';

        let cleaned = raw.replace(/```[^`]*```/g, '').replace(/`/g, '').trim();
        cleaned = cleaned.split('\n')[0].trim();
        cleaned = cleaned.split(/\s+/)[0];

        // Рокировка
        if (/^[Oo0]-[Oo0]-[Oo0]$/.test(cleaned)) return 'O-O-O';
        if (/^[Oo0]-[Oo0]$/.test(cleaned)) return 'O-O';

        // Длинная алгебраическая: e7e5, e7e8q
        const longMatch = cleaned.match(/^([a-h][1-8])([a-h][1-8])([qrbn])?$/i);
        if (longMatch) {
            return {
                from: longMatch[1],
                to: longMatch[2],
                promotion: longMatch[3]?.toLowerCase() || undefined
            };
        }

        // SAN
        const sanMatch = cleaned.match(/^([KQRBN]?[a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?)$/);
        if (sanMatch) {
            return sanMatch[1].replace('#', '').replace('+', '');
        }

        return cleaned.replace(/[^a-hA-H1-8xOo\-=+#NnBbRrQqKk]/g, '');
    }
}
