package com.example.companion.entity.ai;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Text-to-Speech system. Sends companion messages as audio to clients.
 *
 * Flow:
 * 1. Server calls speakToNearby(text, world, pos)
 * 2. Async: fetch MP3 audio bytes from StreamElements TTS API (free, supports Russian)
 * 3. Send custom S2C packet with audio bytes to nearby players
 * 4. Client-side handler plays the audio (see CompanionClientMod)
 *
 * If TTS API fails, falls back to text-only (no crash).
 */
public class TTSSystem {

    /** Packet ID for buddy TTS audio. */
    public static final Identifier TTS_PACKET_ID = new Identifier("companion", "tts_audio");

    /** StreamElements free TTS endpoint (Russian voice). */
    private static final String TTS_URL = "https://api.streamelements.com/kappa/v2/speech";
    private static final String TTS_VOICE = "ru-RU-DmitryNeural";

    /** Max text length we send to TTS (to keep audio small). */
    private static final int MAX_TEXT_LENGTH = 200;

    /** Whether TTS is enabled. Can be toggled. */
    private static volatile boolean enabled = true;

    public static void setEnabled(boolean e) { enabled = e; }
    public static boolean isEnabled() { return enabled; }

    /**
     * Speak a message: fetch audio async, send to all players in the server world.
     */
    public static void speakToAll(String text, ServerWorld world) {
        if (!enabled || text == null || text.isEmpty()) return;

        // Strip color codes (§x)
        String clean = text.replaceAll("§.", "").trim();
        if (clean.isEmpty()) return;
        if (clean.length() > MAX_TEXT_LENGTH) clean = clean.substring(0, MAX_TEXT_LENGTH);

        final String finalText = clean;

        CompletableFuture.supplyAsync(() -> fetchAudio(finalText)).thenAccept(audioBytes -> {
            if (audioBytes == null || audioBytes.length == 0) return;

            // Send to all players on the server thread
            world.getServer().execute(() -> {
                for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
                    sendTTSPacket(player, audioBytes);
                }
            });
        });
    }

    /**
     * Fetch MP3 audio bytes from the TTS API.
     * Returns null on failure (never throws).
     */
    private static byte[] fetchAudio(String text) {
        try {
            String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String urlStr = TTS_URL + "?voice=" + TTS_VOICE + "&text=" + encoded;

            HttpURLConnection con = (HttpURLConnection) new URL(urlStr).openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(5000);
            con.setReadTimeout(10000);
            con.setRequestProperty("User-Agent", "MinecraftCompanionMod/1.0");

            int code = con.getResponseCode();
            if (code != 200) {
                System.err.println("[BuddyTTS] API error " + code);
                return null;
            }

            try (InputStream is = con.getInputStream()) {
                return is.readAllBytes();
            }
        } catch (Exception e) {
            System.err.println("[BuddyTTS] " + e.getMessage());
            return null;
        }
    }

    /**
     * Send raw audio bytes to a player via custom S2C packet.
     */
    private static void sendTTSPacket(ServerPlayerEntity player, byte[] audioBytes) {
        try {
            // Split into chunks if audio is very large (max ~32KB per packet is safe)
            // For typical short phrases the MP3 is 10-50KB, fits in one packet.
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeInt(audioBytes.length);
            buf.writeBytes(audioBytes);
            ServerPlayNetworking.send(player, TTS_PACKET_ID, buf);
        } catch (Exception e) {
            System.err.println("[BuddyTTS] Send error: " + e.getMessage());
        }
    }
}
