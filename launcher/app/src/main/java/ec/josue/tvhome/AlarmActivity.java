package ec.josue.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Pantalla de la alarma: hora grande; OK apaga y Abajo pospone. */
public class AlarmActivity extends Activity {
    private View view;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!AlarmService.ringing) { finish(); return; }
            if (view != null) view.invalidate();
            h.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        view = new AlarmView(this);
        setContentView(view);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { h.removeCallbacks(tick); super.onPause(); }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) return super.onKeyDown(k, e);
        if (e.getRepeatCount() > 0) return true;
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER) { send(AlarmService.ACT_STOP); return true; }
        if (k == KeyEvent.KEYCODE_DPAD_DOWN) { send(AlarmService.ACT_SNOOZE); return true; }
        return true;
    }

    private void send(String action) {
        startService(new Intent(this, AlarmService.class).setAction(action));
        finish();
    }

    @Override public void onBackPressed() { }

    private static final class AlarmView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Typeface clock;
        private final SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.ROOT);

        AlarmView(Context c) {
            super(c);
            f.setTimeZone(TimeZone.getTimeZone("America/Guayaquil"));
            Typeface t;
            try { t = Typeface.createFromAsset(c.getAssets(), "fonts/BebasNeue-Regular.ttf"); }
            catch (Throwable e) { t = Typeface.create("sans-serif-condensed", Typeface.BOLD); }
            clock = t;
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xFF0A0A0B);
            float sc = Math.min(getWidth() / 1920f, getHeight() / 1080f);
            c.translate((getWidth() - 1920f * sc) / 2f, (getHeight() - 1080f * sc) / 2f);
            c.scale(sc, sc);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setTextSize(40f);
            p.setColor(0xFF8E8C87);
            c.drawText("alarma", 960f, 250f, p);
            p.setTypeface(clock);
            p.setTextSize(620f);
            p.setLetterSpacing(.01f);
            p.setColor(0xFFF6F4F0);
            c.drawText(f.format(new Date()), 960f, 700f, p);
            p.setLetterSpacing(0f);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setTextSize(36f);
            p.setColor(0xFFC9C6C0);
            c.drawText("OK  apagar        ·        abajo  posponer " + AlarmStore.SNOOZE_MIN + " min", 960f, 900f, p);
        }
    }
}
