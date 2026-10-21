package ec.josue.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.List;

/** Observa la sesion de multimedia activa (BitChord, Spotify, YouTube Music...) para la consola del inicio. */
final class MediaWatcher {
    private static final long KEEP_PAUSED_MS = 10 * 60 * 1000L;

    private final Context ctx;
    private final Runnable onChange;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ComponentName listener;
    private MediaSessionManager msm;
    private MediaController current;
    private boolean started = false;
    private Bitmap lastArtSrc = null;

    String title = "", artist = "", pkg = "";
    Bitmap art = null;
    boolean playing = false;
    boolean hasSession = false;
    private long duration = 0L;
    private PlaybackState state = null;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
        new MediaSessionManager.OnActiveSessionsChangedListener() {
            @Override public void onActiveSessionsChanged(List<MediaController> controllers) {
                pick(controllers);
                onChange.run();
            }
        };

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { recompute(); onChange.run(); }
        @Override public void onMetadataChanged(MediaMetadata m) { recompute(); onChange.run(); }
        @Override public void onSessionDestroyed() {
            handler.post(new Runnable() { @Override public void run() { refresh(); onChange.run(); } });
        }
    };

    MediaWatcher(Context ctx, Runnable onChange) {
        this.ctx = ctx;
        this.onChange = onChange;
        this.listener = new ComponentName(ctx, MediaListener.class);
    }

    void start() {
        try {
            if (msm == null) msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return;
            if (!started) {
                msm.addOnActiveSessionsChangedListener(sessionsListener, listener, handler);
                started = true;
            }
            refresh();
        } catch (Exception e) {
            // Sin acceso a notificaciones activado: la consola simplemente no aparece.
            started = false;
            clear();
        }
    }

    void stop() {
        try {
            if (started && msm != null) msm.removeOnActiveSessionsChangedListener(sessionsListener);
        } catch (Exception ignored) { }
        started = false;
        detach();
    }

    private void refresh() {
        try {
            pick(msm.getActiveSessions(listener));
        } catch (Exception e) {
            clear();
        }
    }

    private void detach() {
        if (current != null) {
            try { current.unregisterCallback(callback); } catch (Exception ignored) { }
            current = null;
        }
    }

    private void clear() {
        detach();
        hasSession = false;
        playing = false;
        title = "";
        artist = "";
        pkg = "";
        art = null;
        state = null;
        duration = 0L;
    }

    private static boolean isPlaying(PlaybackState s) {
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController mc : list) {
                if (mc.getPackageName().equals(ctx.getPackageName())) continue;
                PlaybackState ps = mc.getPlaybackState();
                if (isPlaying(ps)) { best = mc; break; }
                if (best == null && ps != null && ps.getState() != PlaybackState.STATE_NONE
                    && ps.getState() != PlaybackState.STATE_STOPPED && mc.getMetadata() != null) best = mc;
            }
        }
        if (best == null) { clear(); return; }
        if (current == null || !current.getSessionToken().equals(best.getSessionToken())) {
            detach();
            current = best;
            try { current.registerCallback(callback, handler); } catch (Exception ignored) { }
        }
        recompute();
    }

    private static String firstText(MediaMetadata md, String... keys) {
        for (String k : keys) {
            String v = md.getString(k);
            if (v != null && v.trim().length() > 0) return v.trim();
        }
        return "";
    }

    private void recompute() {
        if (current == null) { clear(); return; }
        try {
            state = current.getPlaybackState();
            playing = isPlaying(state);
            pkg = current.getPackageName();
            MediaMetadata md = current.getMetadata();
            if (md == null) { hasSession = false; return; }
            title = firstText(md, MediaMetadata.METADATA_KEY_TITLE, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            artist = firstText(md, MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM_ARTIST,
                MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            hasSession = title.length() > 0;
            Bitmap src = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (src == null) src = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (src == null) src = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (src != lastArtSrc) {
                lastArtSrc = src;
                art = square(src);
            }
        } catch (Exception e) {
            clear();
        }
    }

    /** Recorte cuadrado y pequeno de la portada, para dibujarla barata en el lienzo. */
    private static Bitmap square(Bitmap src) {
        if (src == null || src.getWidth() < 2 || src.getHeight() < 2) return null;
        try {
            int s = Math.min(src.getWidth(), src.getHeight());
            Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - s) / 2, (src.getHeight() - s) / 2, s, s);
            return Bitmap.createScaledBitmap(sq, 192, 192, true);
        } catch (Exception e) {
            return null;
        }
    }

    /** true si hay algo sonando, o se pauso hace menos de 10 minutos. */
    boolean visible() {
        if (!hasSession) return false;
        if (playing) return true;
        if (state == null) return false;
        return SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime() < KEEP_PAUSED_MS;
    }

    /** Avance 0..1 de la cancion, o -1 si la app no informa la duracion. */
    float progress() {
        if (state == null || duration <= 0L) return -1f;
        long pos = state.getPosition();
        if (playing) pos += (long) ((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * state.getPlaybackSpeed());
        float f = (float) pos / (float) duration;
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }

    void toggle() {
        if (current == null) return;
        try {
            if (playing) current.getTransportControls().pause(); else current.getTransportControls().play();
        } catch (Exception ignored) { }
    }

    void next() {
        if (current == null) return;
        try { current.getTransportControls().skipToNext(); } catch (Exception ignored) { }
    }

    void prev() {
        if (current == null) return;
        try { current.getTransportControls().skipToPrevious(); } catch (Exception ignored) { }
    }
}
