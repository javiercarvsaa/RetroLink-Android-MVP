package cl.retrolink.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public final class NativeAudioSink {
    private AudioTrack track;

    public synchronized void start(int sampleRate) {
        stop();
        try {
            int min = AudioTrack.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) min = sampleRate / 5 * 4;
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            int bufferBytes = Math.max(min * 2, 16384);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                track = new AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(format)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(bufferBytes)
                        .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                        .build();
            } else {
                track = new AudioTrack(attrs, format, bufferBytes,
                        AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            }
            track.play();
        } catch (Throwable t) {
            stop();
        }
    }

    public synchronized void write(short[] pcm, int samples) {
        if (track == null || pcm == null || samples <= 0) return;
        try { track.write(pcm, 0, samples, AudioTrack.WRITE_NON_BLOCKING); }
        catch (Throwable ignored) {}
    }

    public synchronized void pause() {
        if (track != null) try { track.pause(); } catch (Throwable ignored) {}
    }

    public synchronized void resume() {
        if (track != null) try { track.play(); } catch (Throwable ignored) {}
    }

    public synchronized void stop() {
        if (track != null) {
            try { track.pause(); } catch (Throwable ignored) {}
            try { track.flush(); } catch (Throwable ignored) {}
            try { track.release(); } catch (Throwable ignored) {}
            track = null;
        }
    }
}
