package ec.josue.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * Detalle personal: pantalla completa con "te amo analia" y un corazón que late.
 * Vive en su propia tarea (taskAffinity vacío), así que al cerrarla el sistema vuelve a la app anterior sin pasar por el inicio.
 * Cualquier tecla la cierra. Si suena una alarma, la alarma tiene prioridad.
 */
public class LoveActivity extends Activity {
    private LoveView view;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (AlarmService.ringing) { finish(); return; }
        view = new LoveView(this);
        setContentView(view);
        hideBars();
    }

    @Override protected void onResume() {
        super.onResume();
        if (AlarmService.ringing) { finish(); return; }
        hideBars();
        if (view != null) view.start();
    }

    @Override protected void onPause() {
        if (view != null) view.stop();
        super.onPause();
    }

    /** Si algo más pasa al frente (Inicio, una alarma), esta pantalla se cierra sola y no queda escondida en segundo plano. */
    @Override protected void onStop() {
        if (!isFinishing()) finish();
        super.onStop();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    private void hideBars() {
        if (view == null) return;
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        if (k == KeyEvent.KEYCODE_VOLUME_UP || k == KeyEvent.KEYCODE_VOLUME_DOWN || k == KeyEvent.KEYCODE_VOLUME_MUTE) {
            return super.onKeyDown(k, e);
        }
        // el toque que abrió la pantalla no debe cerrarla: se ignoran las repeticiones y el primer instante
        if (e.getRepeatCount() > 0 || (view != null && view.age() < 700)) return true;
        finish();
        return true;
    }

    /** Pulsar otra vez el botón de Prime Video (que el servicio de teclas atiende) también la cierra. */
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        if (view != null && view.age() >= 700) finish();
    }

    @Override public void onBackPressed() {
        if (view != null && view.age() < 700) return;
        finish();
    }

    private static final class LoveView extends View {
        private static final float W = 1920f, H = 1080f;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path heart = new Path();
        private final Typeface face = Typeface.create("sans-serif", Typeface.BOLD);
        private final ValueAnimator beat = ValueAnimator.ofFloat(0f, 1f);
        private long shownAt = 0L;
        private float phase = 0f;
        private Shader glow, textShader, heartShader;

        LoveView(Context c) {
            super(c);
            beat.setDuration(1200);
            beat.setRepeatCount(ValueAnimator.INFINITE);
            beat.setInterpolator(new LinearInterpolator());
            beat.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    phase = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            // corazón de 100 x 90, con el punto de abajo en (50, 90)
            heart.moveTo(50f, 90f);
            heart.cubicTo(8f, 58f, -8f, 28f, 14f, 10f);
            heart.cubicTo(30f, -3f, 46f, 6f, 50f, 22f);
            heart.cubicTo(54f, 6f, 70f, -3f, 86f, 10f);
            heart.cubicTo(108f, 28f, 92f, 58f, 50f, 90f);
            heart.close();
        }

        void start() {
            if (shownAt == 0L) shownAt = SystemClock.uptimeMillis();
            if (!beat.isStarted()) beat.start();
        }

        void stop() { beat.cancel(); }

        long age() { return shownAt == 0L ? 0L : SystemClock.uptimeMillis() - shownAt; }

        /** Dos latidos suaves seguidos y un descanso: 0 a 1. */
        private static float pulse(float t) {
            return bump(t, .10f, .09f) + .65f * bump(t, .27f, .10f);
        }

        private static float bump(float t, float center, float half) {
            float d = Math.abs(t - center);
            if (d >= half) return 0f;
            double x = Math.cos(d / half * Math.PI / 2.0);
            return (float) (x * x);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawColor(0xFF0A0A0B);
            float sc = Math.min(getWidth() / W, getHeight() / H);
            c.translate((getWidth() - W * sc) / 2f, (getHeight() - H * sc) / 2f);
            c.scale(sc, sc);

            // aparición con fundido de 600 ms
            float fade = Math.min(1f, age() / 600f);
            fade = fade * fade * (3f - 2f * fade);
            float pl = pulse(phase);

            // el texto se mueve unos píxeles cada minuto para no marcar la pantalla
            long mi = System.currentTimeMillis() / 60000L;
            float dx = ((mi % 5) - 2) * 4f;
            float dy = (((mi / 5) % 5) - 2) * 4f;

            // resplandor radial tenue detrás del texto
            if (glow == null) {
                glow = new RadialGradient(0f, 0f, 900f, new int[] { 0x40E8456B, 0x14E8456B, 0x00E8456B },
                    new float[] { 0f, .45f, 1f }, Shader.TileMode.CLAMP);
            }
            c.save();
            c.translate(960f + dx, 540f + dy);
            p.setStyle(Paint.Style.FILL);
            p.setShader(glow);
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (255 * fade * (.85f + .15f * pl)));
            c.drawRect(-960f, -540f, 960f, 540f, p);
            p.setShader(null);
            c.restore();

            final String s = "te amo analia";
            final float size = 150f;
            p.setTypeface(face);
            p.setTextSize(size);
            p.setLetterSpacing(-.035f);
            p.setTextAlign(Paint.Align.LEFT);
            float tw = p.measureText(s);
            float hw = 112f, hh = hw * .9f, gap = 44f;
            float left = 960f + dx - (tw + gap + hw) / 2f;
            float base = 560f + dy;

            if (textShader == null) {
                textShader = new LinearGradient(0f, -120f, 0f, 30f, 0xFFFFB3C6, 0xFFEE5A7E, Shader.TileMode.CLAMP);
                heartShader = new LinearGradient(0f, 0f, 0f, 90f, 0xFFFF6F8F, 0xFFD92B52, Shader.TileMode.CLAMP);
            }
            p.setStyle(Paint.Style.FILL);
            c.save();
            c.translate(left, base);
            p.setShader(textShader);
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (255 * fade));
            c.drawText(s, 0f, 0f, p);
            p.setShader(null);
            c.restore();

            // corazón (Path) centrado en la línea del texto, con latido de 1,0 a 1,08
            float k = hw / 100f * (1f + .08f * pl);
            c.save();
            c.translate(left + tw + gap + hw / 2f, base - 44f);
            c.scale(k, k);
            c.translate(-50f, -45f);
            p.setShader(heartShader);
            p.setColor(0xFFFFFFFF);
            p.setAlpha((int) (255 * fade));
            c.drawPath(heart, p);
            p.setShader(null);
            c.restore();
        }
    }
}
