package com.example.companion.client;

import com.example.companion.entity.ai.TTSSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side handler that receives TTS audio packets and plays them.
 * Uses javax.sound.sampled for playback (works on all Java platforms).
 * Supports MP3 if the JVM has an SPI provider, otherwise tries WAV.
 *
 * Register in CompanionClientMod.onInitializeClient().
 */
public class TTSClientHandler implements ClientPlayNetworking.PlayChannelHandler {

    private static final ExecutorService AUDIO_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BuddyTTS-Audio");
        t.setDaemon(true);
        return t;
    });

    /** Currently playing clip (so we can stop it if a new message arrives). */
    private static volatile Clip currentClip;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TTSSystem.TTS_PACKET_ID, new TTSClientHandler());
    }

    @Override
    public void receive(MinecraftClient client, ClientPlayNetworkHandler handler,
                        PacketByteBuf buf, PacketSender responseSender) {
        int len = buf.readInt();
        byte[] audioBytes = new byte[len];
        buf.readBytes(audioBytes);

        // Play on audio thread, not on render/network thread
        AUDIO_POOL.submit(() -> playAudio(audioBytes));
    }

    private static void playAudio(byte[] audioBytes) {
        try {
            // Stop previous clip if still playing
            Clip prev = currentClip;
            if (prev != null && prev.isRunning()) {
                prev.stop();
                prev.close();
            }

            InputStream bais = new ByteArrayInputStream(audioBytes);
            AudioInputStream ais = AudioSystem.getAudioInputStream(bais);

            // If format is not directly supported (e.g. MP3), try to convert to PCM
            AudioFormat srcFormat = ais.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    srcFormat.getSampleRate(),
                    16,
                    srcFormat.getChannels(),
                    srcFormat.getChannels() * 2,
                    srcFormat.getSampleRate(),
                    false
            );

            AudioInputStream decoded;
            if (AudioSystem.isConversionSupported(decodedFormat, srcFormat)) {
                decoded = AudioSystem.getAudioInputStream(decodedFormat, ais);
            } else {
                decoded = ais; // hope it's directly playable
            }

            Clip clip = AudioSystem.getClip();
            clip.open(decoded);

            // Adjust volume to ~80%
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl vol = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                vol.setValue(-4.0f); // ~80% volume
            }

            currentClip = clip;
            clip.start();

            // Wait for playback to finish
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });

        } catch (UnsupportedAudioFileException e) {
            // MP3 not supported by this JVM — fail silently, text chat still works
            System.err.println("[BuddyTTS] Audio format not supported (need MP3 SPI): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[BuddyTTS] Playback error: " + e.getMessage());
        }
    }
}
