package ec.josue.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Sonido mínimo para moverse por la lista: un "tic" suave generado por código (sin archivos de audio).
 * Reacciona a la velocidad: al mover despacio suena grave y un poco más fuerte; al recorrer rápido sube
 * de tono y baja de volumen para que no se vuelva molesto.
 */
final class NavSound {
    private static final int RATE = 22050;
    private static final float BASE_VOL = .34f;

    private SoundPool pool;
    private int id = 0;
    private volatile boolean ready = false;
    private long last = 0L;
    private float speed = 0f;

    NavSound(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), "tick1.wav");
            if (!f.exists() || f.length() < 100) writeWav(f);
            pool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .build();
            pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override public void onLoadComplete(SoundPool sp, int sampleId, int status) { ready = status == 0; }
            });
            id = pool.load(f.getAbsolutePath(), 1);
        } catch (Throwable t) {
            pool = null;
        }
    }

    /** dir: -1 al subir o ir a la izquierda, 1 al bajar o ir a la derecha. */
    void tick(int dir) {
        if (pool == null || !ready) return;
        long now = SystemClock.uptimeMillis();
        long dt = now - last;
        if (dt < 30) return;
        last = now;
        // velocidad 0 (lento, más de 450 ms entre pasos) a 1 (rápido, 70 ms o menos), suavizada
        float s = Math.max(0f, Math.min(1f, (450f - dt) / 380f));
        speed = dt > 700 ? 0f : speed * .5f + s * .5f;
        float rate = (.85f + .75f * speed) * (dir < 0 ? .93f : 1f);
        float vol = BASE_VOL * (1f - .35f * speed);
        try { pool.play(id, vol, vol, 1, 0, rate); } catch (Throwable ignored) { }
    }

    void release() {
        try { if (pool != null) pool.release(); } catch (Throwable ignored) { }
        pool = null;
        ready = false;
    }

    /** Seno de 1500 Hz con un armónico leve, ataque de 2 ms y caída exponencial: dura unos 45 ms. */
    private static void writeWav(File f) throws Exception {
        int n = (int) (RATE * .045f);
        byte[] pcm = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double env = Math.min(1.0, t / .002) * Math.exp(-t / .009);
            double v = Math.sin(2 * Math.PI * 1500 * t) + .25 * Math.sin(2 * Math.PI * 3000 * t);
            int s = (int) (v / 1.25 * env * .8 * 32767);
            pcm[2 * i] = (byte) (s & 0xFF);
            pcm[2 * i + 1] = (byte) ((s >> 8) & 0xFF);
        }
        FileOutputStream o = new FileOutputStream(f);
        try {
            int dataLen = pcm.length;
            byte[] h = new byte[44];
            put(h, 0, "RIFF");
            le32(h, 4, 36 + dataLen);
            put(h, 8, "WAVE");
            put(h, 12, "fmt ");
            le32(h, 16, 16);
            h[20] = 1;
            h[22] = 1;
            le32(h, 24, RATE);
            le32(h, 28, RATE * 2);
            h[32] = 2;
            h[34] = 16;
            put(h, 36, "data");
            le32(h, 40, dataLen);
            o.write(h);
            o.write(pcm);
        } finally {
            o.close();
        }
    }

    private static void put(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }

    private static void le32(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
        b[off + 2] = (byte) (v >> 16);
        b[off + 3] = (byte) (v >> 24);
    }
}
