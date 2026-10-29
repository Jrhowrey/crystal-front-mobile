package com.crystalfront.mobilecommand;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Native Android 44.1kHz 16-bit PCM Procedural DSP Audio & Music Engine.
 * Synthesizes multi-layered studio-grade combat SFX (sub-bass explosions, prism lasers,
 * hydraulic mech servos, rotary cannons, ion strikes) and an adaptive orchestral synth soundtrack.
 */
public class SoundEngine {
    private static final int SAMPLE_RATE = 44100;
    private final Map<String, short[]> soundBank = new HashMap<>();
    private volatile boolean sfxEnabled = true;
    private volatile boolean musicEnabled = true;
    private volatile boolean running = false;
    private Thread musicThread;
    private final Random rng = new Random(42);

    public void init() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                buildSoundBank();
                startMusicLoop();
            }
        }, "CrystalFront-AudioInit").start();
    }

    private void buildSoundBank() {
        soundBank.put("select", synthChime(580f, 880f, 0.09f));
        soundBank.put("move", synthChime(440f, 660f, 0.11f));
        soundBank.put("build", synthServoBuild(0.32f));
        soundBank.put("rifle", synthRifleBurst(0.14f));
        soundBank.put("cannon", synthTankCannon(0.38f));
        soundBank.put("laser", synthPrismLaser(0.42f));
        soundBank.put("explode", synthExplosion(0.65f, 1.0f));
        soundBank.put("ion", synthIonStrike(1.25f));
        soundBank.put("harvest", synthCrystalHarvest(0.24f));
        soundBank.put("intro_boom", synthExplosion(1.10f, 1.3f));
    }

    private short[] synthChime(float f1, float f2, float durationSec) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float prog = (float) i / n;
            float env = (float) Math.exp(-prog * 5.5f);
            float freq = (prog < 0.45f) ? f1 : f2;
            float sig = (float) (Math.sin(2.0 * Math.PI * freq * t) * 0.65
                    + Math.sin(2.0 * Math.PI * freq * 2.0 * t) * 0.25);
            pcm[i] = clamp16(sig * env * 0.45f);
        }
        return pcm;
    }

    private short[] synthServoBuild(float durationSec) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float prog = (float) i / n;
            float env = (float) Math.sin(prog * Math.PI);
            float freq = 180f + 240f * prog;
            float sig = (float) (Math.sin(2.0 * Math.PI * freq * t) * 0.5
                    + Math.sin(2.0 * Math.PI * (freq * 1.5f) * t) * 0.3);
            pcm[i] = clamp16(sig * env * 0.40f);
        }
        return pcm;
    }

    private short[] synthRifleBurst(float durationSec) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float pulse = ((i % (SAMPLE_RATE / 22)) < (SAMPLE_RATE / 45)) ? 1.0f : 0.15f;
            float env = (1.0f - (float) i / n) * pulse;
            float noise = (rng.nextFloat() * 2f - 1f);
            float punch = (float) Math.sin(2.0 * Math.PI * 165f * t);
            pcm[i] = clamp16((noise * 0.6f + punch * 0.4f) * env * 0.45f);
        }
        return pcm;
    }

    private short[] synthTankCannon(float durationSec) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        float phase = 0f;
        for (int i = 0; i < n; i++) {
            float prog = (float) i / n;
            float env = (float) Math.exp(-prog * 6.5f);
            float freq = 42f + 135f * (float) Math.exp(-prog * 14f);
            phase += (2.0f * (float) Math.PI * freq) / SAMPLE_RATE;
            float sub = (float) Math.sin(phase);
            float crack = (rng.nextFloat() * 2f - 1f) * (float) Math.exp(-prog * 12f);
            pcm[i] = clamp16((sub * 0.72f + crack * 0.55f) * env * 0.78f);
        }
        return pcm;
    }

    private short[] synthPrismLaser(float durationSec) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        float phase = 0f;
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float prog = (float) i / n;
            float env = (float) Math.exp(-prog * 3.8f);
            float freq = 920f - 580f * prog + (float) Math.sin(t * 95f) * 45f;
            phase += (2.0f * (float) Math.PI * freq) / SAMPLE_RATE;
            float beam = (float) (Math.sin(phase) * 0.6 + Math.sin(phase * 2.01f) * 0.3);
            float sizzle = (rng.nextFloat() * 2f - 1f) * 0.18f;
            pcm[i] = clamp16((beam + sizzle) * env * 0.55f);
        }
        return pcm;
    }

    private short[] synthExplosion(float durationSec, float intensity) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        float lp = 0f;
        float phase = 0f;
        for (int i = 0; i < n; i++) {
            float prog = (float) i / n;
            float env = (float) Math.exp(-prog * 4.2f);
            float rumbleFreq = 32f + 65f * (float) Math.exp(-prog * 8f);
            phase += (2.0f * (float) Math.PI * rumbleFreq) / SAMPLE_RATE;
            float sub = (float) Math.sin(phase);
            float rawNoise = rng.nextFloat() * 2f - 1f;
            lp = lp * 0.88f + rawNoise * 0.12f;
            pcm[i] = clamp16((sub * 0.65f + lp * 1.35f) * env * 0.85f * intensity);
        }
        return pcm;
    }

    private short[] synthIonStrike(float durationSec) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        float phase = 0f;
        float lp = 0f;
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float prog = (float) i / n;
            float charge = (prog < 0.25f) ? (float) Math.sin(2.0 * Math.PI * (220f + prog * 1800f) * t) * 0.5f : 0f;
            float blastEnv = (prog >= 0.20f) ? (float) Math.exp(-(prog - 0.20f) * 3.5f) : 0f;
            float freq = 36f + 90f * (float) Math.exp(-prog * 6f);
            phase += (2.0f * (float) Math.PI * freq) / SAMPLE_RATE;
            float rawNoise = rng.nextFloat() * 2f - 1f;
            lp = lp * 0.85f + rawNoise * 0.15f;
            float sig = charge + ((float) Math.sin(phase) * 0.7f + lp * 1.1f) * blastEnv;
            pcm[i] = clamp16(sig * 0.82f);
        }
        return pcm;
    }

    private short[] synthCrystalHarvest(float durationSec) {
        int n = (int) (SAMPLE_RATE * durationSec);
        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            float prog = (float) i / n;
            float env = (float) Math.exp(-prog * 5.0f);
            float sig = (float) (Math.sin(2.0 * Math.PI * 1046.5f * t) * 0.45
                    + Math.sin(2.0 * Math.PI * 1318.5f * t) * 0.35
                    + Math.sin(2.0 * Math.PI * 1567.9f * t) * 0.20);
            pcm[i] = clamp16(sig * env * 0.40f);
        }
        return pcm;
    }

    private short clamp16(float val) {
        if (val > 0.98f) val = 0.98f;
        if (val < -0.98f) val = -0.98f;
        return (short) (val * 32767f);
    }

    public void play(final String name) {
        if (!sfxEnabled) return;
        final short[] data = soundBank.get(name);
        if (data == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AudioTrack track = new AudioTrack(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build(),
                            new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build(),
                            data.length * 2,
                            AudioTrack.MODE_STATIC,
                            AudioManager.AUDIO_SESSION_ID_GENERATE
                    );
                    track.write(data, 0, data.length);
                    track.play();
                    Thread.sleep((data.length * 1000L) / SAMPLE_RATE + 60L);
                    track.release();
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private void startMusicLoop() {
        if (running) return;
        running = true;
        musicThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    int bufSamples = SAMPLE_RATE / 4;
                    short[] buf = new short[bufSamples];
                    AudioTrack musicTrack = new AudioTrack(
                            new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build(),
                            new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(SAMPLE_RATE)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                    .build(),
                            bufSamples * 2,
                            AudioTrack.MODE_STREAM,
                            AudioManager.AUDIO_SESSION_ID_GENERATE
                    );
                    musicTrack.play();

                    double sampleIdx = 0;
                    final float[] bassNotes = {55.0f, 55.0f, 65.41f, 49.0f, 58.27f, 55.0f, 41.20f, 49.0f};
                    final float[] arpNotes = {220.0f, 261.63f, 329.63f, 293.66f, 220.0f, 329.63f, 392.0f, 329.63f};

                    while (running) {
                        if (!musicEnabled) {
                            Thread.sleep(200);
                            continue;
                        }
                        for (int i = 0; i < bufSamples; i++) {
                            double t = sampleIdx / SAMPLE_RATE;
                            int bar = ((int) (t * 0.5)) % bassNotes.length;
                            int step = ((int) (t * 4.0)) % arpNotes.length;
                            double stepFrac = (t * 4.0) - Math.floor(t * 4.0);

                            float bassFreq = bassNotes[bar];
                            float arpFreq = arpNotes[step];

                            float bass = (float) (Math.sin(2.0 * Math.PI * bassFreq * t) * 0.55
                                    + Math.sin(2.0 * Math.PI * bassFreq * 2.0 * t) * 0.25);
                            float arpEnv = (float) Math.exp(-stepFrac * 3.5);
                            float arp = (float) Math.sin(2.0 * Math.PI * arpFreq * t) * arpEnv * 0.22f;

                            // Subtle tactical pulse kick every beat
                            double beatFrac = (t * 2.0) - Math.floor(t * 2.0);
                            float kick = (float) (Math.sin(2.0 * Math.PI * (95.0 * Math.exp(-beatFrac * 12.0)) * beatFrac)
                                    * Math.exp(-beatFrac * 6.5) * 0.35);

                            buf[i] = clamp16((bass + arp + kick) * 0.16f);
                            sampleIdx++;
                        }
                        musicTrack.write(buf, 0, bufSamples);
                    }
                    musicTrack.stop();
                    musicTrack.release();
                } catch (Throwable ignored) {}
            }
        }, "CrystalFront-MusicLoop");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public boolean isSfxEnabled() { return sfxEnabled; }
    public void setSfxEnabled(boolean enabled) { this.sfxEnabled = enabled; }
    public boolean isMusicEnabled() { return musicEnabled; }
    public void setMusicEnabled(boolean enabled) { this.musicEnabled = enabled; }

    public void shutdown() {
        running = false;
    }
}
