package ec.josue.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pantalla completa del launcher, dibujada en un solo View sobre un lienzo de 1920x1080.
 * Pantallas propias: inicio, todas las apps, editar lista, agregar app y entradas HDMI.
 * Ajustes del sistema se abre con el nativo de Android.
 */
final class LauncherView extends View {
    private static final int HOME = 0, ALL = 1, EDIT = 2, PICK = 3, HDMI = 4, ALARM = 5;
    private static final float W = 1920f, H = 1080f;
    private static final int TXT = 0xFFF6F4F0, MUTED = 0xFF8E8C87, SOFT = 0xFFA8A6A0;
    private static final String[] BASE_TOOLS = {"Todas las apps", "Entradas", "Editar lista", "Alarma", "Ajustes"};
    private static final int[] BASE_ACCENT = {0xFF5B5BD6, 0xFFC77700, 0xFF17A2A2, 0xFFE0A21B, 0xFF7C5CE0};
    private String[] TOOLS = BASE_TOOLS;
    private int[] TOOL_ACCENT = BASE_ACCENT;
    /** Pantalla de inicio del fabricante (p. ej. Google TV), si sigue activa. null cuando está desactivada. */
    private Intent stockHome = null;
    private static final float[] DIST_ALPHA = {1f, .5f, .22f, .08f};

    private final Activity host;
    private final AppStore store;
    private final Weather weather;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TimeInterpolator smooth = new PathInterpolator(.2f, .8f, .2f, 1f);
    /** Frenado con un leve rebote, como la inercia de una rueda. */
    private final TimeInterpolator wheel = new PathInterpolator(.34f, 1.45f, .64f, 1f);
    private static final float WHEEL_STEP = .5f, WHEEL_R = 150f / .5f;
    private final TimeInterpolator flood = new PathInterpolator(.55f, 0f, .15f, 1f);

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface mono = Typeface.MONOSPACE;
    private final Typeface clockFace;

    // --- estado de navegación ---
    private int screen = HOME;
    private boolean inApps = true;
    private int app = 0, tool = 0;
    private int allIdx = 0, allTop = 0, pickIdx = 0, pickTop = 0, hdmiIdx = 0;
    private int editI = 0, editCol = 0;
    private boolean editMoving = false;
    private List<AppEntry> pickList = new ArrayList<>();
    private final List<TvInputInfo> inputs = new ArrayList<>();
    private final List<String> inputNames = new ArrayList<>();

    // --- animaciones ---
    private final Tween focusPos = new Tween();
    private final Tween toolsDim = new Tween();
    private final Tween blink = new Tween();
    private final Tween ret = new Tween();
    private final Tween consoleA = new Tween();
    private final Tween cFocus = new Tween();
    private boolean consoleTarget = false, consoleFocus = false, resumed = false;
    /** Entradas se abrió con el botón del control mientras otra app estaba al frente: Atrás debe volver a esa app. */
    private boolean inputsFromApp = false;
    /** Atrás en Entradas mandó el launcher al fondo y falta dejar el inicio listo cuando la ventana ya no se vea. */
    private boolean homeResetPending = false;
    /**
     * true mientras la actividad está parada (en segundo plano). No cambia con la pausa temporal que hace el sistema al entregar un
     * intent nuevo a la actividad que ya está al frente (onPause, onNewIntent, onResume), a diferencia de {@code resumed}.
     */
    private boolean stopped = true;
    /** Cuándo se abrió Entradas por última vez (antirrebote del botón). */
    private long hdmiOpenedAt = 0L;
    /** Tic suave al mover la selección por la lista de inicio. */
    private final NavSound sfx;
    private int cBtn = 1;
    private long lastPausedAt = 0L;
    // --- reposo con reloj ---
    private final Tween rest = new Tween();
    private final Tween restMusic = new Tween();
    private boolean restOn = false, restMusicTarget = false;
    private long lastInput = SystemClock.uptimeMillis();
    private float driftX = 0f, driftY = 0f;
    private boolean alarmArmed = false;
    private final Tween clockAnim = new Tween();
    private String clockPrevStr = "";
    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            tick();
            long ms = System.currentTimeMillis();
            handler.postDelayed(this, 60000L - (ms % 60000L) + 40L);
        }
    };
    private int lastArtColor = 0, prevArtShown = 0;
    private final Tween artFade = new Tween();
    private long resetArmedUntil = 0L;
    private String alarmTimeStr = "";
    private final MediaWatcher media;
    private final Path ctlPath = new Path();
    private final Matrix artMatrix = new Matrix();
    private BitmapShader artShader = null;
    private android.graphics.Bitmap artShaderBmp = null;
    private String consolePkg = "";
    private int consoleAcc = 0xFFF6F4F0;
    private final Runnable progTick = new Runnable() {
        @Override public void run() {
            if (resumed && screen == HOME && consoleA.v > .01f && media.playing && !(restOn && rest.v > .99f)) invalidate();
            handler.postDelayed(this, 1000);
        }
    };
    private final Tween editScroll = new Tween();
    private int overlay = 0; // 0 nada, 1 abriendo, 2 cubierto, 3 volviendo
    private boolean homeOrigin = true;
    private float openMs = 0f;
    private boolean launched = false;
    private Intent pending = null;
    private ValueAnimator openAnim;
    private long lockUntil = 0L;

    // --- textos ---
    private String clockStr = "--:--", dateStr = "", greetStr = "";
    private String toastText = null;
    private long toastUntil = 0L;
    private final SimpleDateFormat fmtClock = new SimpleDateFormat("HH:mm", Locale.ROOT);
    private final SimpleDateFormat fmtDate = new SimpleDateFormat("EEEE d 'de' MMMM", new Locale("es", "EC"));
    private final SimpleDateFormat fmtHour = new SimpleDateFormat("H", Locale.ROOT);

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            tick();
            handler.postDelayed(this, 15000);
        }
    };

    LauncherView(Activity host) {
        super(host);
        this.host = host;
        this.store = new AppStore(host);
        this.sfx = new NavSound(host);
        this.weather = new Weather(host);
        this.media = new MediaWatcher(host, new Runnable() {
            @Override public void run() { syncConsole(); invalidate(); }
        });
        Typeface cf;
        try { cf = Typeface.createFromAsset(host.getAssets(), "fonts/BebasNeue-Regular.ttf"); }
        catch (Throwable t) { cf = Typeface.create("sans-serif-condensed", Typeface.BOLD); }
        clockFace = cf;
        setFocusable(true);
        TimeZone tz = TimeZone.getTimeZone("America/Guayaquil");
        fmtClock.setTimeZone(tz);
        fmtDate.setTimeZone(tz);
        fmtHour.setTimeZone(tz);
        p.setStrokeJoin(Paint.Join.ROUND);
        clockAnim.v = 1f;
        store.refresh();
        app = homeIndex();
        focusPos.set(app);
        tick();
    }

    // ------------------------------------------------------------------ ciclo de vida

    void onResumed() {
        if (homeResetPending) settleHome();
        applyAlarmState();
        lastInput = SystemClock.uptimeMillis();
        if (restOn && SystemClock.uptimeMillis() - lastPausedAt > 1500) { restOn = false; rest.set(0f); }
        store.refresh();
        startCardLoad();
        findStockHome();
        clampSelection();
        if (overlay != 0) {
            // Regreso suave: sin el relleno de color, el inicio aparece con un fundido desde oscuro.
            if (openAnim != null) { openAnim.removeAllListeners(); openAnim.cancel(); }
            pending = null;
            overlay = 0;
            ret.set(0f);
            blink.set(1f);
            blink.to(0f, 520, smooth);
        } else if (blink.v > .02f) {
            blink.to(0f, 520, smooth);
        }
        resumed = true;
        media.start();
        syncConsole();
        handler.removeCallbacks(progTick);
        handler.post(progTick);
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        // el reloj cambia justo al empezar cada minuto, no con hasta 15 s de retraso
        handler.removeCallbacks(minuteTick);
        handler.postDelayed(minuteTick, 60000L - (System.currentTimeMillis() % 60000L) + 40L);
        if (store.weather) weather.refreshIfStale(new Runnable() { @Override public void run() { invalidate(); } });
        invalidate();
    }

    private void startCardLoad() {
        final List<AppEntry> snap = new ArrayList<>(store.installed);
        new Thread(new Runnable() {
            @Override public void run() {
                store.loadCards(snap);
                postInvalidate();
            }
        }, "cards").start();
    }

    /** Deja el inicio listo (primera app enfocada) sin animar ni dibujar. */
    private void settleHome() {
        homeResetPending = false;
        screen = HOME;
        inApps = true;
        app = homeIndex();
        focusPos.set(app);
        toolsDim.set(0f);
    }

    /** La ventana ya no se ve (lo llama onStop): ahora sí se puede dejar el inicio listo sin que se vea un fotograma. */
    void onStopped() {
        stopped = true;
        if (homeResetPending) settleHome();
    }

    /** La ventana volvió a ser visible. */
    void onStarted() {
        stopped = false;
    }

    /** Mientras el launcher se esconde tras Atrás en Entradas no se redibuja nada: así no se ve un fotograma del inicio. */
    @Override
    public void invalidate() {
        if (homeResetPending) return;
        super.invalidate();
    }

    @Override
    public void postInvalidate() {
        if (homeResetPending) return;
        super.postInvalidate();
    }

    void onPaused() {
        resumed = false;
        lastPausedAt = SystemClock.uptimeMillis();
        media.stop();
        handler.removeCallbacks(progTick);
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(minuteTick);
    }

    /** Se pulsó Inicio: siempre vuelve a la primera app de la lista, con suavidad. */
    void onHomePressed() {
        inputsFromApp = false;
        if (overlay == 1) return;
        lastInput = SystemClock.uptimeMillis();
        if (restOn) leaveRest();
        final int first = homeIndex();
        editMoving = false;
        if (consoleFocus) { consoleFocus = false; cFocus.to(0f, 200, LIN); }
        boolean quick = resumed || SystemClock.uptimeMillis() - lastPausedAt < 700;
        if (!quick || overlay != 0) {
            // Llega desde otra app: el estado queda listo detrás del fundido de entrada.
            screen = HOME;
            inApps = true;
            app = first;
            focusPos.set(first);
            toolsDim.set(0f);
            cFocus.set(0f);
            consoleFocus = false;
            invalidate();
            return;
        }
        if (screen != HOME) {
            go(new Runnable() {
                @Override public void run() {
                    screen = HOME;
                    inApps = true;
                    app = first;
                    focusPos.set(first);
                    toolsDim.set(0f);
                    clampSelection();
                }
            });
            return;
        }
        if (!inApps) { inApps = true; toolsDim.to(0f, 220, smooth); }
        if (app != first || Math.abs(focusPos.v - first) > .001f) { app = first; scrollWheelTo(first); }
        invalidate();
    }

    /** Recorre la rueda hasta una posición; dura más cuanto más lejos. */
    private void scrollWheelTo(int idx) {
        float dist = Math.abs(focusPos.v - idx);
        long ms = (long) Math.min(760f, 380f + 80f * dist);
        focusPos.to(idx, ms, dist > 2.2f ? smooth : wheel);
    }

    /** Muestra u oculta la mini consola según haya algo sonando. */
    private void syncConsole() {
        boolean on = media.visible();
        if (on != consoleTarget) {
            consoleTarget = on;
            consoleA.to(on ? 1f : 0f, 350, smooth);
        }
        if (!on && consoleFocus) {
            consoleFocus = false;
            cFocus.to(0f, 200, LIN);
        }
        if (media.artColor != lastArtColor) {
            prevArtShown = shownArt();
            lastArtColor = media.artColor;
            artFade.set(0f);
            artFade.to(1f, 600, smooth);
        }
        boolean pl = media.hasSession && media.playing;
        if (pl != restMusicTarget) {
            restMusicTarget = pl;
            restMusic.to(pl ? 1f : 0f, 450, smooth);
        }
    }

    /** Con una alarma activa la pantalla no se apaga sola mientras el launcher está al frente. */
    private void applyAlarmState() {
        boolean feat = AlarmStore.feature(host);
        if (feat != alarmFeature) { alarmFeature = feat; rebuildTools(); }
        alarmArmed = AlarmStore.armed(host);
        long[] nx = AlarmStore.next(host);
        if (nx != null) {
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/Guayaquil"));
            cal.setTimeInMillis(nx[0]);
            alarmTimeStr = two(cal.get(Calendar.HOUR_OF_DAY)) + ":" + two(cal.get(Calendar.MINUTE));
        } else alarmTimeStr = "";
        boolean keep = AlarmStore.hold(host);
        try {
            if (keep) host.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else host.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception ignored) { }
        AlarmStore.updatePower(host, false);
    }

    /** Arma la fila inferior según lo que esté disponible: la alarma solo sale si su función está encendida. */
    private void rebuildTools() {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> acc = new java.util.ArrayList<>();
        names.add("Todas las apps"); acc.add(0xFF5B5BD6);
        names.add("Entradas"); acc.add(0xFFC77700);
        names.add("Editar lista"); acc.add(0xFF17A2A2);
        if (alarmFeature) { names.add("Alarma"); acc.add(0xFFE0A21B); }
        names.add("Ajustes"); acc.add(0xFF7C5CE0);
        if (stockHome != null) { names.add("Google TV"); acc.add(0xFF4285F4); }
        TOOLS = names.toArray(new String[0]);
        TOOL_ACCENT = new int[acc.size()];
        for (int i = 0; i < acc.size(); i++) TOOL_ACCENT[i] = acc.get(i);
        if (tool > TOOLS.length - 1) tool = TOOLS.length - 1;
    }

    /** Color de la portada con fundido al cambiar de canción (0 si no hay). */
    private int shownArt() {
        int cur = media.artColor;
        if (cur == 0) return 0;
        if (prevArtShown == 0) return cur;
        return lerpColor(prevArtShown | 0xFF000000, cur | 0xFF000000, artFade.v);
    }

    /** Pasa al reposo: la lista y los datos salen y el reloj se desliza al centro. */
    private void enterRest() {
        if (restOn) return;
        restOn = true;
        consoleFocus = false;
        cFocus.set(0f);
        rest.to(1f, 1100, smooth);
    }

    /** Sale del reposo: todo vuelve a su lugar. */
    private void leaveRest() {
        if (!restOn) return;
        restOn = false;
        lastInput = SystemClock.uptimeMillis();
        rest.to(0f, 750, smooth);
    }

    /** Lo pide el servicio de teclas al pulsar Entradas/Fuentes del control, desde cualquier app. */
    void openInputsFromKey() {
        if (overlay == 1) return;
        if (screen == HDMI && !stopped) {
            // Entradas ya está abierta: pulsar el botón otra vez la cierra, como Atrás (con antirrebote para que un solo toque no abra y cierre).
            if (SystemClock.uptimeMillis() - hdmiOpenedAt >= 400) closeInputs();
            return;
        }
        hdmiOpenedAt = SystemClock.uptimeMillis();
        inputsFromApp = stopped;
        homeResetPending = false;
        lastInput = SystemClock.uptimeMillis();
        if (restOn) { restOn = false; rest.set(0f); }
        editMoving = false;
        consoleFocus = false;
        cFocus.set(0f);
        inApps = true;
        app = homeIndex();
        focusPos.set(app);
        toolsDim.set(0f);
        loadInputs();
        hdmiIdx = 0;
        screen = HDMI;
        invalidate();
    }

    /** Busca otra pantalla de inicio activa en el TV y, si existe, agrega el botón para abrirla. */
    private void findStockHome() {
        stockHome = null;
        try {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (android.content.pm.ResolveInfo ri : host.getPackageManager().queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(host.getPackageName()) || pkg.contains("setupwraith") || pkg.contains("provision")
                    || pkg.equals("com.android.settings") || pkg.equals("com.android.tv.settings")
                    || ri.activityInfo.name.endsWith("FallbackHome")) continue;
                stockHome = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setClassName(pkg, ri.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                break;
            }
        } catch (Exception ignored) { stockHome = null; }
        rebuildTools();
    }

    private void tick() {
        Date now = new Date();
        String oldClock = clockStr, oldDate = dateStr;
        applyAlarmState();
        clockStr = fmtClock.format(now);
        syncConsole();
        dateStr = fmtDate.format(now);
        if (oldClock.length() >= 5 && clockStr.length() >= 5 && !oldClock.equals(clockStr) && resumed) {
            clockPrevStr = oldClock;
            clockAnim.set(0f);
            clockAnim.to(1f, 450, smooth);
        }
        if (clockStr.length() >= 5) {
            int mi = 0;
            try { mi = Integer.parseInt(clockStr.substring(3, 5)); } catch (NumberFormatException ignored) { }
            // desplazamiento mínimo del reloj de reposo, para no marcar siempre los mismos píxeles
            driftX = ((mi % 5) - 2) * 4f;
            driftY = (((mi / 5) % 5) - 2) * 4f;
        }
        if (!restOn && resumed && screen == HOME && overlay == 0 && store.restMin > 0
            && SystemClock.uptimeMillis() - lastInput >= store.restMin * 60000L) {
            enterRest();
        }
        if (restOn && rest.v > .99f && clockStr.equals(oldClock) && dateStr.equals(oldDate)) return;
        int h = 12;
        try { h = Integer.parseInt(fmtHour.format(now)); } catch (NumberFormatException ignored) { }
        greetStr = (h >= 5 && h < 12) ? "Buenos días" : ((h >= 12 && h < 19) ? "Buenas tardes" : "Buenas noches");
        checkNet();
        if (store.weather) weather.refreshIfStale(new Runnable() { @Override public void run() { invalidate(); } });
        invalidate();
    }

    private void clampSelection() {
        int n = Math.max(1, store.pinned.size());
        if (app > n - 1) { app = n - 1; focusPos.set(app); }
        int total = store.pinned.size() + 8;
        if (editI > total - 1) editI = total - 1;
    }

    // ------------------------------------------------------------------ entrada de teclas

    boolean onKey(int keyCode, boolean longPress) {
        lastInput = SystemClock.uptimeMillis();
        if (restOn) {
            // En reposo cualquier tecla solo despierta: no se ejecuta nada más.
            leaveRest();
            invalidate();
            return true;
        }
        // Botones dedicados del control: como el inicio de Google TV esta desactivado, los atiende el launcher
        boolean wantSettings = keyCode == KeyEvent.KEYCODE_SETTINGS;
        boolean wantInputs = keyCode == KeyEvent.KEYCODE_TV_INPUT || keyCode == KeyEvent.KEYCODE_TV
            || (keyCode >= KeyEvent.KEYCODE_TV_INPUT_HDMI_1 && keyCode <= KeyEvent.KEYCODE_TV_INPUT_COMPOSITE_2)
            || keyCode == KeyEvent.KEYCODE_TV_INPUT_VGA_1;
        if (wantSettings || wantInputs) {
            if (overlay != 0 || SystemClock.uptimeMillis() < lockUntil) return true;
            if (wantSettings) openSystemSettings();
            else if (screen != HDMI) enterTool(1);
            else if (SystemClock.uptimeMillis() - hdmiOpenedAt >= 400) closeInputs();
            invalidate();
            return true;
        }
        int k;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: k = 1; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: k = 2; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: k = 3; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: k = 4; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A: k = longPress ? 6 : 5; break;
            case KeyEvent.KEYCODE_BACK: k = 7; break;
            default: return false;
        }
        if (overlay == 1 || SystemClock.uptimeMillis() < lockUntil) return true;
        if (overlay == 2 || overlay == 3) return true;
        switch (screen) {
            case HOME: keyHome(k); break;
            case ALL: keyAll(k); break;
            case PICK: keyPick(k); break;
            case HDMI: keyHdmi(k); break;
            case ALARM: keyAlarm(k); break;
            default: keyEdit(k); break;
        }
        invalidate();
        return true;
    }

    private static final int UP = 1, DOWN = 2, LEFT = 3, RIGHT = 4, OK = 5, LONG = 6, BACK = 7;

    /** Punto de reposo del inicio: siempre la primera app de la lista. */
    private int homeIndex() {
        return 0;
    }

    private void keyHome(int k) {
        int n = Math.max(1, store.pinned.size());
        if (consoleFocus) { keyConsole(k); return; }
        if (k == BACK) {
            // Atrás vuelve siempre a la primera app, desde la fila inferior o desde cualquier posición.
            if (!inApps) { inApps = true; toolsDim.to(0f, 220, smooth); }
            int first = homeIndex();
            if (app != first || Math.abs(focusPos.v - first) > .001f) { app = first; scrollWheelTo(first); }
            return;
        }
        final int app0 = app, tool0 = tool;
        final boolean inApps0 = inApps;
        if (inApps) {
            if (k == UP && app > 0) { app--; focusPos.to(app, 420, wheel); }
            else if (k == UP && consoleTarget && consoleA.v > .5f) {
                consoleFocus = true;
                cBtn = 1;
                cFocus.to(1f, 220, smooth);
            }
            else if (k == DOWN) {
                if (app < n - 1) { app++; focusPos.to(app, 420, wheel); }
                else { inApps = false; toolsDim.to(1f, 200, LIN); }
            } else if (k == OK || k == LONG) {
                if (store.pinned.isEmpty()) { openPick(); }
                else { startOpen(store.pinned.get(app), true); }
            }
        } else {
            if (k == LEFT && tool > 0) tool--;
            else if (k == RIGHT && tool < TOOLS.length - 1) tool++;
            else if (k == UP) { inApps = true; toolsDim.to(0f, 200, LIN); }
            else if (k == OK || k == LONG) enterTool(tool);
        }
        // sonido suave solo cuando la selección cambió de verdad (no al chocar con un extremo)
        if (store.sounds && (app != app0 || tool != tool0 || inApps != inApps0)) {
            sfx.tick((k == UP || k == LEFT) ? -1 : 1);
        }
    }

    private void keyConsole(int k) {
        if (k == BACK || k == DOWN) { consoleFocus = false; cFocus.to(0f, 220, smooth); return; }
        if (k == LEFT && cBtn > -1) cBtn--;
        else if (k == RIGHT && cBtn < 2) cBtn++;
        else if (k == OK || k == LONG) {
            if (cBtn == -1) openPlayingApp();
            else if (cBtn == 0) media.prev();
            else if (cBtn == 1) media.toggle();
            else media.next();
        }
    }

    /** Abre la app que está reproduciendo (BitChord, Spotify...) desde la consola. */
    private void openPlayingApp() {
        String pkg = media.pkg;
        if (pkg == null || pkg.length() == 0) return;
        Intent in = null;
        for (AppEntry e : store.installed) {
            if (e.pkg.equals(pkg)) { in = store.launchIntent(e); break; }
        }
        if (in == null) {
            in = host.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (in == null) in = host.getPackageManager().getLaunchIntentForPackage(pkg);
            if (in != null) in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (in == null) { toast("No se pudo abrir la app"); return; }
        final Intent fin = in;
        lockUntil = SystemClock.uptimeMillis() + 500;
        blink.to(1f, 190, smooth, new Runnable() {
            @Override public void run() {
                try {
                    host.startActivity(fin);
                } catch (Exception ex) {
                    toast("No se pudo abrir la app");
                    blink.to(0f, 300, smooth);
                }
            }
        });
    }

    private void enterTool(int t) {
        if (t < 0 || t >= TOOLS.length) return;
        inputsFromApp = false;
        String nm = TOOLS[t];
        if (nm.equals("Todas las apps")) { go(new Runnable() { @Override public void run() { screen = ALL; } }); }
        else if (nm.equals("Entradas")) { go(new Runnable() { @Override public void run() { loadInputs(); hdmiIdx = 0; screen = HDMI; hdmiOpenedAt = SystemClock.uptimeMillis(); } }); }
        else if (nm.equals("Editar lista")) { go(new Runnable() { @Override public void run() { editI = 0; editCol = 0; editMoving = false; editScroll.set(0f); screen = EDIT; } }); }
        else if (nm.equals("Alarma")) { go(new Runnable() { @Override public void run() { openAlarms(); } }); }
        else if (nm.equals("Ajustes")) { openSystemSettings(); }
        else if (stockHome != null) {
            try { host.startActivity(stockHome); } catch (Exception e) { toast("No se pudo abrir el inicio del TV"); }
        }
    }

    private void backToHome(final int toolIdx) {
        inputsFromApp = false;
        go(new Runnable() {
            @Override public void run() {
                screen = HOME;
                inApps = false;
                tool = toolIdx;
                toolsDim.set(1f);
                clampSelection();
            }
        });
    }

    private void keyAll(int k) {
        List<AppEntry> l = store.installed;
        if (k == BACK) { backToHome(0); return; }
        if (l.isEmpty()) return;
        if (k == OK) { startOpen(l.get(allIdx), false); return; }
        if (k == LONG) { pinWithToast(l.get(allIdx)); return; }
        allIdx = gridMove(k, allIdx, l.size());
        allTop = gridScroll(allIdx, allTop);
    }

    private void keyPick(int k) {
        if (k == BACK) { go(new Runnable() { @Override public void run() { screen = EDIT; } }); return; }
        if (pickList.isEmpty()) { if (k == OK) go(new Runnable() { @Override public void run() { screen = EDIT; } }); return; }
        if (k == OK) {
            AppEntry e = pickList.get(pickIdx);
            if (store.pin(e)) {
                toast(e.label + " agregado al final");
                editI = store.pinned.size() - 1;
                editCol = 0;
                editMoving = false;
                screen = EDIT;
                focusEdit();
            } else {
                toast("Máximo " + AppStore.MAX_PINNED + " apps en inicio");
            }
            return;
        }
        pickIdx = gridMove(k, pickIdx, pickList.size());
        pickTop = gridScroll(pickIdx, pickTop);
    }

    // --- alarmas ---
    private List<AlarmStore.Alarm> alarms = new ArrayList<>();
    private int alarmSel = 0, alarmEditIdx = -1, alarmField = 0, alarmDay = 0;
    private long alarmDelArmed = 0L;
    private boolean alarmTimeEdit = false;
    private int alarmCol = 0;
    private boolean alarmFeature = true;

    private void persistAlarms() { AlarmStore.save(host, alarms); applyAlarmState(); }

    private void openAlarms() {
        alarms = AlarmStore.list(host);
        alarmSel = 0;
        alarmEditIdx = -1;
        alarmTimeEdit = false;
        alarmField = 0;
        alarmDay = 0;
        screen = ALARM;
    }

    private String alarmWhen() {
        long[] nx = AlarmStore.next(host);
        if (nx == null) return "";
        long mins = Math.max(0, (nx[0] - System.currentTimeMillis()) / 60000L);
        return "próxima: sonará en " + (mins / 60) + " h " + (mins % 60) + " min";
    }

    private static String two(int v) { return (v < 10 ? "0" : "") + v; }

    private static String daysText(int mask) {
        if (mask == 127) return "todos los días";
        if (mask == 0) return "una vez";
        if (mask == 31) return "lunes a viernes";
        if (mask == 96) return "fin de semana";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) { if (sb.length() > 0) sb.append(' '); sb.append(AlarmStore.DAYS[i]); }
        return sb.toString();
    }

    private void keyAlarm(int k) {
        if (alarmEditIdx >= 0) { keyAlarmEdit(k); return; }
        int n = alarms.size();
        boolean canAdd = n < AlarmStore.MAX;
        int rows = n + (canAdd ? 1 : 0) + 1;
        if (k == BACK) { backToHome(3); return; }
        if (k == UP && alarmSel > 0) alarmSel--;
        else if (k == DOWN && alarmSel < rows - 1) alarmSel++;
        else if (alarmSel < n) {
            AlarmStore.Alarm a = alarms.get(alarmSel);
            if (k == LEFT || k == RIGHT) { a.on = !a.on; persistAlarms(); }
            else if (k == OK || k == LONG) { alarmEditIdx = alarmSel; alarmField = 0; alarmDay = 0; alarmDelArmed = 0L; alarmTimeEdit = false; }
        } else if (canAdd && alarmSel == n) {
            if (k == OK || k == LONG) {
                alarms.add(new AlarmStore.Alarm());
                persistAlarms();
                alarmEditIdx = n;
                alarmTimeEdit = false;
                alarmField = 0;
                alarmDay = 0;
                alarmDelArmed = 0L;
            }
        } else if (k == OK || k == LONG) {
            AlarmStore.once(host, System.currentTimeMillis() + 60000L, 1, 0);
            applyAlarmState();
            toast("Sonará en 1 minuto");
        }
    }

    private void keyAlarmEdit(int k) {
        if (alarmEditIdx < 0 || alarmEditIdx >= alarms.size()) { alarmEditIdx = -1; return; }
        AlarmStore.Alarm a = alarms.get(alarmEditIdx);
        if (alarmTimeEdit) {
            // modo hora: izquierda y derecha alternan horas y minutos; arriba y abajo cambian el número
            if (k == LEFT) alarmCol = 0;
            else if (k == RIGHT) alarmCol = 1;
            else if (k == UP || k == DOWN) {
                int d = k == UP ? 1 : -1;
                if (alarmCol == 0) a.h = (a.h + d + 24) % 24; else a.m = (a.m + d + 60) % 60;
                a.on = true;
                persistAlarms();
            } else alarmTimeEdit = false;
            return;
        }
        if (k == BACK) { alarmEditIdx = -1; alarmDelArmed = 0L; return; }
        if (k == UP) { if (alarmField > 0) alarmField--; return; }
        if (k == DOWN) { if (alarmField < 3) alarmField++; return; }
        int d = k == RIGHT ? 1 : (k == LEFT ? -1 : 0);
        switch (alarmField) {
            case 0:
                if (k == OK || k == LONG) { alarmTimeEdit = true; alarmCol = 0; }
                break;
            case 1:
                if (d != 0) alarmDay = Math.max(0, Math.min(6, alarmDay + d));
                else if (k == OK || k == LONG) { a.days ^= (1 << alarmDay); persistAlarms(); }
                break;
            case 2:
                if (d != 0) { a.label = (a.label + d + AlarmStore.LABELS.length) % AlarmStore.LABELS.length; persistAlarms(); }
                break;
            default:
                if (k == OK || k == LONG) {
                    if (SystemClock.uptimeMillis() < alarmDelArmed) {
                        alarms.remove(alarmEditIdx);
                        alarmEditIdx = -1;
                        alarmSel = Math.max(0, Math.min(alarmSel, alarms.size()));
                        alarmDelArmed = 0L;
                        persistAlarms();
                    } else {
                        alarmDelArmed = SystemClock.uptimeMillis() + 3500;
                        toast("Pulsa OK otra vez para eliminar la alarma");
                    }
                }
        }
    }

    /** Cierra Entradas: si se abrió desde otra app la revela de nuevo; si se abrió desde el launcher, vuelve al inicio. */
    private void closeInputs() {
        if (inputsFromApp) {
            // Primero se esconde la ventana y, sin dibujar nada más, el estado queda listo en onStopped().
            inputsFromApp = false;
            homeResetPending = true;
            host.moveTaskToBack(true);
            // por si el sistema no mandó el launcher al fondo (no hay otra tarea), no se queda atascado en Entradas
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    // si el launcher sigue al frente se repone el estado; si ya se escondió, lo hace onStopped()
                    if (homeResetPending && resumed) { settleHome(); invalidate(); }
                }
            }, 400);
            return;
        }
        backToHome(1);
    }

    private void keyHdmi(int k) {
        if (k == BACK) { closeInputs(); return; }
        if (inputs.isEmpty()) return;
        if (k == UP && hdmiIdx > 0) hdmiIdx--;
        else if (k == DOWN && hdmiIdx < Math.min(inputs.size(), 5) - 1) hdmiIdx++;
        else if (k == OK || k == LONG) switchInput(inputs.get(hdmiIdx), inputNames.get(hdmiIdx));
    }

    private void keyEdit(int k) {
        int n = store.pinned.size();
        int last = n + 7;
        if (k == BACK) {
            if (editMoving) { editMoving = false; return; }
            backToHome(2);
            return;
        }
        if (editMoving) {
            if (k == UP && editI > 0) { store.swap(editI, editI - 1); editI--; }
            else if (k == DOWN && editI < n - 1) { store.swap(editI, editI + 1); editI++; }
            else if (k == OK || k == LONG) editMoving = false;
            focusEdit();
            return;
        }
        if (editI == n + 5 && (k == LEFT || k == RIGHT)) {
            cycleRest(k == RIGHT ? 1 : -1, false);
            focusEdit();
            return;
        }
        if (k == UP && editI > 0) { editI--; editCol = 0; }
        else if (k == DOWN && editI < last) { editI++; editCol = 0; }
        else if (k == RIGHT && editI < n) editCol = 1;
        else if (k == LEFT) editCol = 0;
        else if (k == OK || k == LONG) {
            if (editI < n) {
                if (editCol == 0) editMoving = true;
                else {
                    String nm = store.pinned.get(editI).label;
                    store.unpin(editI);
                    toast(nm + " quitado de inicio");
                    editCol = 0;
                    editI = Math.max(0, Math.min(editI, store.pinned.size() - 1));
                }
            } else if (editI == n) {
                if (n >= AppStore.MAX_PINNED) toast("Máximo " + AppStore.MAX_PINNED + " apps en inicio");
                else openPick();
            } else if (editI == n + 1) {
                if (SystemClock.uptimeMillis() < resetArmedUntil) {
                    resetArmedUntil = 0L;
                    store.resetPinned();
                    editI = 0;
                    toast("Lista restablecida");
                } else {
                    resetArmedUntil = SystemClock.uptimeMillis() + 3500;
                    toast("Pulsa OK otra vez para restablecer la lista");
                }
            } else if (editI == n + 2) store.setWeather(!store.weather);
            else if (editI == n + 3) store.setGreeting(!store.greeting);
            else if (editI == n + 4) store.setAnim(!store.anim);
            else if (editI == n + 5) cycleRest(1, true);
            else if (editI == n + 7) {
                store.setSounds(!store.sounds);
                if (store.sounds) sfx.tick(1);
            } else {
                // función de alarma: al apagarla desaparece de la fila inferior y no se programa nada
                AlarmStore.setFeature(host, !AlarmStore.feature(host));
                applyAlarmState();
            }
            if (store.weather) weather.refreshIfStale(new Runnable() { @Override public void run() { invalidate(); } });
        }
        focusEdit();
    }

    /** Cambia el tiempo de reposo entre apagado, 5, 10, 15, 30 y 60 minutos. */
    private void cycleRest(int dir, boolean wrap) {
        int[] o = AppStore.REST_OPTIONS;
        int i = 0;
        for (int j = 0; j < o.length; j++) if (o[j] == store.restMin) i = j;
        i += dir;
        if (i < 0) i = wrap ? o.length - 1 : 0;
        if (i > o.length - 1) i = wrap ? 0 : o.length - 1;
        store.setRestMin(o[i]);
        lastInput = SystemClock.uptimeMillis();
    }

    private static String restLabel(int m) { return m == 0 ? "Apagado" : m + " min"; }

    private void openPick() {
        go(new Runnable() {
            @Override public void run() {
                pickList = store.notPinned();
                pickIdx = 0;
                pickTop = 0;
                screen = PICK;
            }
        });
    }

    private void pinWithToast(AppEntry e) {
        if (store.isPinned(e)) toast(e.label + " ya está en inicio");
        else if (store.pin(e)) toast(e.label + " fijado en inicio");
        else toast("Máximo " + AppStore.MAX_PINNED + " apps en inicio");
    }

    private int gridMove(int k, int idx, int len) {
        if (k == LEFT) return Math.max(0, idx - 1);
        if (k == RIGHT) return Math.min(len - 1, idx + 1);
        if (k == UP) return idx - 5 >= 0 ? idx - 5 : idx;
        if (k == DOWN) {
            if (idx + 5 < len) return idx + 5;
            return (idx / 5 < (len - 1) / 5) ? len - 1 : idx;
        }
        return idx;
    }

    private int gridScroll(int idx, int top) {
        int row = idx / 5;
        if (row < top) return row;
        if (row > top + 2) return row - 2;
        return top;
    }

    private void focusEdit() {
        float t = editRowTop(editI);
        float cur = editScroll.v;
        float target = cur;
        if (t - cur < 250f) target = t - 250f;
        else if (t + 64f - cur > 960f) target = t + 64f - 960f;
        if (target < 0f) target = 0f;
        if (target != cur) editScroll.to(target, 220, smooth);
    }

    private float editRowTop(int idx) {
        int n = store.pinned.size();
        return 250f + idx * 64f + (idx >= n ? 14f : 0f) + (idx >= n + 2 ? 14f : 0f);
    }

    // ------------------------------------------------------------------ acciones del sistema

    private void go(final Runnable r) {
        if (!store.anim) { r.run(); invalidate(); return; }
        lockUntil = SystemClock.uptimeMillis() + 480;
        blink.to(1f, 150, smooth, null);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                r.run();
                blink.to(0f, 320, smooth, null);
                invalidate();
            }
        }, 160);
    }

    private void startOpen(AppEntry e, boolean fromHome) {
        Intent in = store.launchIntent(e);
        if (in == null) { toast("No se pudo abrir " + e.label); return; }
        if (!store.anim) {
            try { host.startActivity(in); } catch (Exception ex) { toast("No se pudo abrir " + e.label); }
            return;
        }
        pending = in;
        homeOrigin = fromHome;
        launched = false;
        overlay = 1;
        openMs = 0f;
        openAnim = ValueAnimator.ofFloat(0f, 760f);
        openAnim.setDuration(760);
        openAnim.setInterpolator(new LinearInterpolator());
        openAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                openMs = (Float) a.getAnimatedValue();
                if (!launched && openMs >= 300f && pending != null) {
                    launched = true;
                    try { host.startActivity(pending); }
                    catch (Exception ex) { abortOpen(); return; }
                }
                invalidate();
            }
        });
        openAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { if (overlay == 1) overlay = 2; invalidate(); }
        });
        openAnim.start();
    }

    private void abortOpen() {
        if (openAnim != null) { openAnim.removeAllListeners(); openAnim.cancel(); }
        pending = null;
        overlay = 3;
        ret.set(1f);
        ret.to(0f, 400, LIN, new Runnable() { @Override public void run() { overlay = 0; invalidate(); } });
        toast("No se pudo abrir la app");
    }

    private void openSystemSettings() {
        try {
            host.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("No se pudo abrir Ajustes");
        }
    }

    private void loadInputs() {
        inputs.clear();
        inputNames.clear();
        try {
            TvInputManager tim = (TvInputManager) host.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return;
            for (TvInputInfo info : tim.getTvInputList()) {
                boolean tuner = info.getType() == TvInputInfo.TYPE_TUNER && info.getId().contains("/HW");
                if (!info.isPassthroughInput() && !tuner) continue;
                CharSequence nm = info.loadCustomLabel(host);
                if (nm == null || nm.length() == 0) nm = info.loadLabel(host);
                inputs.add(info);
                inputNames.add(tuner ? "TV (antena)" : (nm == null ? info.getId() : nm.toString().replaceAll("(?i)hdmi\\s*(\\d)", "HDMI $1")));
            }
            // Orden fijo: HDMI 1, HDMI 2, AV y TV (antena).
            for (int i = 1; i < inputs.size(); i++) {
                for (int j = i; j > 0 && inputRank(inputNames.get(j)) < inputRank(inputNames.get(j - 1)); j--) {
                    TvInputInfo ti = inputs.get(j); inputs.set(j, inputs.get(j - 1)); inputs.set(j - 1, ti);
                    String tn = inputNames.get(j); inputNames.set(j, inputNames.get(j - 1)); inputNames.set(j - 1, tn);
                }
            }
        } catch (Exception ignored) {
            // El TV no deja ver sus entradas a las apps: la pantalla muestra el aviso.
        }
    }

    private static float inputRank(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("hdmi")) {
            float d = 9f;
            for (int i = 0; i < n.length(); i++) { char ch = n.charAt(i); if (ch >= '0' && ch <= '9') { d = ch - '0'; break; } }
            return d / 10f;
        }
        if (n.contains("antena")) return 3f;
        if (n.equals("av") || n.contains("av ") || n.startsWith("av") || n.contains("compuesto") || n.contains("composite")) return 2f;
        return 2.5f;
    }

    private void switchInput(TvInputInfo info, String name) {
        inputsFromApp = false;
        try {
            Uri u = info.isPassthroughInput() ? TvContract.buildChannelUriForPassthroughInput(info.getId())
                    : TvContract.Channels.CONTENT_URI;
            host.startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            toast("El TV no permitió cambiar a " + name);
        }
    }

    private void toast(String s) {
        toastText = s;
        toastUntil = SystemClock.uptimeMillis() + 2300;
        postInvalidateDelayed(2350);
        invalidate();
    }

    // ------------------------------------------------------------------ dibujo

    @Override protected void onDraw(Canvas c) {
        c.drawColor(0xFF0A0A0B);
        float sc = Math.min(getWidth() / W, getHeight() / H);
        c.translate((getWidth() - W * sc) / 2f, (getHeight() - H * sc) / 2f);
        c.scale(sc, sc);
        c.clipRect(0, 0, W, H);

        if (screen == HOME) drawHome(c);
        else {
            drawScreen(c);
            if (overlay != 0) drawOverlay(c);
        }
        if (blink.v > 0.001f) {
            fill.setShader(null);
            fill.setColor(0xFF000000);
            fill.setAlpha((int) (255 * blink.v));
            c.drawRect(0, 0, W, H, fill);
        }
        if (toastText != null && SystemClock.uptimeMillis() < toastUntil) drawToast(c);
    }

    private int currentAccent() {
        int n = store.pinned.size();
        if (n == 0) return MUTED;
        float pos = Math.max(0f, Math.min(n - 1, focusPos.v));
        int i = (int) Math.floor(pos);
        int j = Math.min(i + 1, n - 1);
        return lerpColor(store.pinned.get(i).accent, store.pinned.get(j).accent, pos - i);
    }

    private int openAccent() {
        int n = store.pinned.size();
        if (screen == HOME && n > 0) return store.pinned.get(Math.min(app, n - 1)).accent;
        if (screen == ALL && !store.installed.isEmpty()) return store.installed.get(Math.min(allIdx, store.installed.size() - 1)).accent;
        return MUTED;
    }

    private Shader glowShader = null, bandShader = null;
    private int glowKey = 0, bandKey = 0;
    private final java.util.HashMap<Integer, Shader> cellShaders = new java.util.HashMap<>();

    private void drawHome(Canvas c) {
        int n = store.pinned.size();
        int accent = currentAccent();
        boolean opening = overlay == 1 && homeOrigin;
        float restA = 1f;          // transparencia de las filas no seleccionadas
        float selA = 1f;           // transparencia de la fila seleccionada
        float chromeA = 1f;
        float barA = (1f - toolsDim.v) * (1f - cFocus.v);
        if (overlay == 1) {
            restA = 1f - clamp01(openMs / 180f);
            selA = 1f - clamp01((openMs - 520f) / 240f);
            chromeA = restA;
            barA = 0f;
        } else if (overlay == 2) {
            restA = 0f; selA = 0f; chromeA = 0f; barA = 0f;
        } else if (overlay == 3) {
            restA = selA = chromeA = 1f - ret.v;
            barA = (1f - ret.v) * (1f - toolsDim.v) * (1f - cFocus.v);
        }
        float glowA = (overlay == 1) ? (1f - clamp01(openMs / 260f)) * (1f - toolsDim.v) : (overlay == 0 ? 1f - .55f * toolsDim.v : barA);
        final float lf = 1f - clamp01(rest.v * 1.5f);   // lo que sale en el reposo
        glowA *= lf;
        barA *= lf;

        // brillo de fondo
        if (glowA > 0.001f) {
            c.save();
            c.translate(460f, 540f);
            c.scale(1f, .5f);
            if (glowShader == null || glowKey != accent) {
                glowShader = new RadialGradient(0f, 0f, 600f, withAlpha(accent, .30f), withAlpha(accent, 0f), Shader.TileMode.CLAMP);
                glowKey = accent;
            }
            fill.setShader(glowShader);
            fill.setAlpha((int) (255 * glowA));
            c.drawCircle(0f, 0f, 600f, fill);
            fill.setShader(null);
            c.restore();
        }

        // relleno de color al abrir
        if (overlay != 0) drawOverlay(c);

        // lista de apps
        c.save();
        c.translate(-300f * rest.v, 0f);
        c.clipRect(0f, 0f, 1400f, H);
        if (lf < .003f) {
            // en reposo la lista no se dibuja
        } else if (n == 0) {
            drawRow(c, "Agregar apps", 465f, 1f - toolsDim.v, TXT, lf, true);
        } else {
            for (int i = 0; i < n; i++) {
                float dd = i - focusPos.v;
                float dist = Math.abs(dd);
                float ang = dd * WHEEL_STEP;
                if (Math.abs(ang) > 1.4f) continue;
                float y = 540f + WHEEL_R * (float) Math.sin(ang) - 75f;
                float op = distAlpha(dist);
                float opTools = dist < .5f ? .55f : op * .8f;
                op = op + (opTools - op) * toolsDim.v;
                op *= (1f - .6f * cFocus.v) * lf;
                float foc = clamp01(1f - dist);
                boolean sel = i == app;
                float a;
                if (opening || overlay == 2) a = sel ? selA : restA;
                else a = (overlay == 3) ? restA : 1f;
                if (opening && sel) { op = 1f; foc = 1f; }
                float ds = depthScale(dist);
                float dx = dist < 1f ? 28f * (1f - dist) : -10f * Math.min(dist - 1f, 2f);
                drawRow(c, store.pinned.get(i).label, y, foc, TXT, op * a, false, ds, ds, dx);
            }
        }
        c.restore();

        // barra de acento
        if (barA > 0.001f) {
            fill.setShader(null);
            fill.setColor(accent | 0xFF000000);
            fill.setAlpha((int) (255 * barA));
            rect.set(140f, 492f, 148f, 588f);
            c.drawRoundRect(rect, 4f, 4f, fill);
        }

        if (chromeA > 0.001f) drawChrome(c, accent, chromeA);
    }

    private void drawRow(Canvas c, String label, float top, float foc, int color, float alpha, boolean single) {
        drawRow(c, label, top, foc, color, alpha, single, 1f, 1f, 0f);
    }

    /** Tamaño según la distancia al centro: la seleccionada crece un poco y el resto se aleja. */
    private static float depthScale(float d) {
        if (d < 1f) return 1.06f + (.86f - 1.06f) * d;
        if (d < 2f) return .86f + (.72f - .86f) * (d - 1f);
        return Math.max(.6f, .72f + (.6f - .72f) * (d - 2f));
    }

    /** Fila de la lista; sx y sy aplastan el texto para dar el efecto de rueda. */
    private void drawRow(Canvas c, String label, float top, float foc, int color, float alpha, boolean single, float sx, float sy, float dx) {
        float cy = top + 75f;
        float m = maskAt(cy);
        float aa = alpha * m;
        if (aa <= 0.003f) return;
        float by = 46f;
        c.save();
        c.translate(160f + dx, cy);
        c.scale(sx, sy);
        if (foc < 0.999f) strokeText(c, label, 0f, by, bold, 128f, TXT, .7f * aa * (1f - foc), -.04f);
        if (foc > 0.001f) text(c, label, 0f, by, bold, 128f, color, aa * foc, -.04f, Paint.Align.LEFT);
        c.restore();
    }

    private Shader clockShader = null;
    private int clockKey = 0;
    private float clockR = -1f, clockTop = -1f;

    /** Hora grande apilada (hh sobre mm) a la derecha, con degradado del color de la app; la fecha va centrada encima. */
    private void drawBigClock(Canvas c, int accent, float a) {
        boolean ok = clockStr.length() >= 5;
        String hh = ok ? clockStr.substring(0, 2) : "--";
        String mm = ok ? clockStr.substring(3, 5) : "--";
        final float r = rest.v;
        final float cx = 1572f + (960f - 1572f) * r + driftX * r;
        final float capH = 390f + 23f * r, gap = 24f + r, top = 170f - 9f * r + driftY * r;
        boolean showAlarm = alarmArmed && alarmTimeStr.length() > 0;
        float dateY = 128f - 11f * r + driftY * r + (showAlarm ? 9f * r : 0f);
        text(c, dateStr, cx, dateY, medium, 34f, SOFT, a, 0f, Paint.Align.CENTER);
        // en reposo, la alarma va arriba de la fecha
        if (showAlarm && r > .01f) text(c, "alarma " + alarmTimeStr, cx, dateY - 40f, medium, 26f, MUTED, a * r, 0f, Paint.Align.CENTER);
        if (clockShader == null || clockKey != accent || clockR != r || clockTop != top) {
            clockShader = new LinearGradient(0f, top, 0f, top + 2f * capH + gap,
                withAlpha(accent, .55f), withAlpha(accent, .16f), Shader.TileMode.CLAMP);
            clockKey = accent;
            clockR = r;
            clockTop = top;
        }
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(clockFace);
        p.setTextSize(capH / .7f);
        p.setLetterSpacing(.01f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShader(clockShader);
        p.setColor(0xFFFFFFFF);
        float ca = a * (1f - (alarmArmed ? .35f * r : 0f));
        p.setAlpha(Math.max(0, Math.min(255, (int) (ca * 255))));
        float t = clockAnim.v;
        boolean anim = t < .999f && clockPrevStr.length() >= 5;
        String ph = anim ? clockPrevStr.substring(0, 2) : hh, pm = anim ? clockPrevStr.substring(3, 5) : mm;
        drawDigits(c, hh, ph, anim && !ph.equals(hh), t, cx, top + capH, ca);
        drawDigits(c, mm, pm, anim && !pm.equals(mm), t, cx, top + 2f * capH + gap, ca);
        p.setShader(null);
    }

    /** Dígitos del reloj: al cambiar, los viejos suben y se desvanecen y los nuevos entran desde abajo. */
    private void drawDigits(Canvas c, String now, String prev, boolean anim, float t, float x, float y, float alpha) {
        if (!anim) {
            p.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255))));
            c.drawText(now, x, y, p);
            return;
        }
        p.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255 * (1f - t)))));
        c.drawText(prev, x, y - 40f * t, p);
        p.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255 * t))));
        c.drawText(now, x, y + 40f * (1f - t), p);
    }

    private void drawChrome(Canvas c, int accent, float aFull) {
        float lf = 1f - clamp01(rest.v * 1.5f);
        float a = aFull * lf;
        c.save();
        c.translate(-120f * rest.v, 0f);
        drawChromeLeft(c, accent, a);
        c.restore();
        int art = shownArt();
        int clockAcc = (art != 0 && rest.v > .001f)
            ? lerpColor(accent | 0xFF000000, art | 0xFF000000, restMusic.v * rest.v) : accent;
        drawBigClock(c, clockAcc, aFull);
        drawRestConsole(c, aFull);
    }

    /** Campana pequeña: hay una alarma activa. */
    private void drawBell(Canvas c, float cx, float cy, float a) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(MUTED | 0xFF000000);
        p.setAlpha(Math.max(0, Math.min(255, (int) (a * 255))));
        rect.set(cx - 8f, cy - 12f, cx + 8f, cy + 4f);
        ctlPath.reset();
        ctlPath.addArc(rect, 180f, 180f);
        ctlPath.lineTo(cx + 11f, cy + 8f);
        ctlPath.lineTo(cx - 11f, cy + 8f);
        ctlPath.close();
        c.drawPath(ctlPath, p);
        c.drawCircle(cx, cy + 12f, 3.2f, p);
        c.drawCircle(cx, cy - 13f, 2.2f, p);
    }

    private void drawChromeLeft(Canvas c, int accent, float a) {
        if (a < .003f) return;
        if (store.greeting) {
            fill.setShader(null);
            fill.setColor(accent | 0xFF000000);
            fill.setAlpha((int) (255 * a));
            c.drawCircle(119f, 117f, 7f, fill);
            text(c, greetStr, 144f, 128f, medium, 34f, MUTED, a, 0f, Paint.Align.LEFT);
        }
        if (alarmArmed) {
            float bx = store.greeting ? 144f + tw(greetStr, medium, 34f, 0f) + 30f : 124f;
            drawBell(c, bx, 117f, a * .85f);
        }
        float wifiX = 112f;
        if (store.weather) {
            int t = weather.cached();
            String temp = (t == Weather.NONE ? "--" : String.valueOf(t)) + "°";
            text(c, temp, 112f, 190f, medium, 44f, TXT, a, -.01f, Paint.Align.LEFT);
            float w = tw(temp, medium, 44f, -.01f);
            text(c, " · Guayaquil", 112f + w, 190f, medium, 44f, MUTED, a, -.01f, Paint.Align.LEFT);
            wifiX = 112f + w + tw(" · Guayaquil", medium, 44f, -.01f);
        }
        drawConsole(c, a);

        // fila inferior: oculta mientras se exploran las apps, aparece al bajar del final de la lista
        float ra = a * toolsDim.v;
        if (ra > 0.003f) {
            float x = 112f;
            for (int j = 0; j < TOOLS.length; j++) {
                boolean f = !inApps && j == tool;
                float w = tw(TOOLS[j], medium, 32f, 0f);
                float ta = ra * (f ? 1f : .8f);
                text(c, TOOLS[j], x, 992f, medium, 32f, f ? TXT : SOFT, ta, 0f, Paint.Align.LEFT);
                if (f) {
                    fill.setShader(null);
                    fill.setColor(TOOL_ACCENT[j]);
                    fill.setAlpha((int) (255 * ra));
                    c.drawRect(x, 1004f, x + w, 1008f, fill);
                }
                x += w + 48f;
            }
        }
        // red: solo el icono, discreto, a la derecha del clima
        drawWifi(c, wifiX + 15f, 166f, a * .75f, netOk);
    }

    private int consoleAccent() {
        if (!media.pkg.equals(consolePkg)) {
            consolePkg = media.pkg;
            consoleAcc = TXT;
            for (AppEntry e : store.installed) {
                if (e.pkg.equals(consolePkg) && e.accent != 0) { consoleAcc = e.accent | 0xFF000000; break; }
            }
        }
        return consoleAcc;
    }

    /** Mini consola de multimedia entre el saludo y la fecha: portada, título, artista y controles. */
    private void drawConsole(Canvas c, float a) {
        float ca = a * consoleA.v;
        if (ca < .004f) return;
        int ac = consoleAccent();
        final float x0 = 520f, cy = 150f, cs = 96f, cy0 = cy - cs / 2f;
        // bloque completo enfocado: portada y textos como un solo botón (abre la app)
        if (consoleFocus && cBtn == -1) {
            rect.set(x0 - 16f, cy0 - 12f, 1004f, cy0 + cs + 12f);
            fill.setShader(null);
            fill.setColor(ac | 0xFF000000);
            fill.setAlpha((int) (255 * ca * .12f));
            c.drawRoundRect(rect, 22f, 22f, fill);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(ac | 0xFF000000);
            p.setAlpha((int) (255 * ca));
            c.drawRoundRect(rect, 22f, 22f, p);
            p.setStyle(Paint.Style.FILL);
        }
        drawCover(c, x0, cy0, cs, 12f, ca, ac);
        // texto
        final float tx = x0 + cs + 24f, tw0 = 340f;
        text(c, fit(media.title, bold, 36f, -.02f, tw0), tx, 132f, bold, 36f, TXT, ca, -.02f, Paint.Align.LEFT);
        text(c, fit(media.artist, medium, 28f, 0f, tw0), tx, 170f, medium, 28f, SOFT, ca, 0f, Paint.Align.LEFT);
        // progreso
        fill.setShader(null);
        fill.setColor(0xFFF6F4F0);
        fill.setAlpha((int) (255 * ca * .14f));
        rect.set(tx, 190f, tx + tw0, 194f);
        c.drawRoundRect(rect, 2f, 2f, fill);
        float pr = media.progress();
        if (pr > 0f) {
            fill.setColor(ac | 0xFF000000);
            fill.setAlpha((int) (255 * ca));
            rect.set(tx, 190f, tx + Math.max(4f, tw0 * pr), 194f);
            c.drawRoundRect(rect, 2f, 2f, fill);
        }
        // controles
        drawCtl(c, 0, 1062f, cy, 32f, ca, consoleFocus && cBtn == 0, ac);
        drawCtl(c, 1, 1146f, cy, 36f, ca, consoleFocus && cBtn == 1, ac);
        drawCtl(c, 2, 1230f, cy, 32f, ca, consoleFocus && cBtn == 2, ac);
    }

    /** Portada del álbum (o un marcador de color si la app no la entrega). */
    private void drawCover(Canvas c, float x0, float y0, float cs, float rad, float ca, int ac) {
        rect.set(x0, y0, x0 + cs, y0 + cs);
        android.graphics.Bitmap art = media.art;
        if (art != null) {
            if (artShader == null || artShaderBmp != art) {
                artShader = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                artShaderBmp = art;
            }
            artMatrix.setScale(cs / art.getWidth(), cs / art.getHeight());
            artMatrix.postTranslate(x0, y0);
            artShader.setLocalMatrix(artMatrix);
            fill.setColor(0xFFFFFFFF);
            fill.setShader(artShader);
            fill.setAlpha((int) (255 * ca));
            c.drawRoundRect(rect, rad, rad, fill);
            fill.setShader(null);
        } else {
            float k = cs / 96f;
            fill.setShader(null);
            fill.setColor(ac | 0xFF000000);
            fill.setAlpha((int) (255 * ca * .9f));
            c.drawRoundRect(rect, rad, rad, fill);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5f * k);
            p.setColor(0xFF0A0A0B);
            p.setAlpha((int) (255 * ca * .55f));
            c.drawCircle(x0 + cs / 2f, y0 + cs / 2f, 24f * k, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(x0 + cs / 2f, y0 + cs / 2f, 6f * k, p);
        }
    }

    /** En reposo, con música sonando: portada, título y artista abajo a la izquierda, sin controles. */
    private void drawRestConsole(Canvas c, float a) {
        float ra = a * rest.v * restMusic.v;
        if (ra < .004f) return;
        int ac = consoleAccent();
        final float x0 = 75f, y0 = 912f, cs = 90f;
        drawCover(c, x0, y0, cs, 12f, ra, ac);
        final float tx = x0 + cs + 24f;
        text(c, fit(media.title, bold, 30f, -.02f, 520f), tx, y0 + 40f, bold, 30f, TXT, ra, -.02f, Paint.Align.LEFT);
        text(c, fit(media.artist, medium, 24f, 0f, 520f), tx, y0 + 74f, medium, 24f, SOFT, ra, 0f, Paint.Align.LEFT);
    }

    private void drawCtl(Canvas c, int kind, float cx, float cy, float r, float ca, boolean foc, int ac) {
        p.setShader(null);
        if (foc && kind == 1) {
            fill.setShader(null);
            fill.setColor(ac | 0xFF000000);
            fill.setAlpha((int) (255 * ca));
            c.drawCircle(cx, cy, r, fill);
        } else if (foc) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(ac | 0xFF000000);
            p.setAlpha((int) (255 * ca));
            c.drawCircle(cx, cy, r - 1.5f, p);
        } else if (kind == 1) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            p.setColor(0xFFF6F4F0);
            p.setAlpha((int) (255 * ca * .35f));
            c.drawCircle(cx, cy, r - 1.5f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor((foc && kind == 1 ? 0xFF0A0A0B : (foc ? TXT : SOFT)) | 0xFF000000);
        p.setAlpha((int) (255 * ca * (foc ? 1f : .9f)));
        if (kind == 1) {
            if (media.playing) {
                rect.set(cx - 12f, cy - 14f, cx - 3f, cy + 14f);
                c.drawRoundRect(rect, 3f, 3f, p);
                rect.set(cx + 3f, cy - 14f, cx + 12f, cy + 14f);
                c.drawRoundRect(rect, 3f, 3f, p);
            } else {
                ctlPath.reset();
                ctlPath.moveTo(cx - 8f, cy - 15f);
                ctlPath.lineTo(cx + 14f, cy);
                ctlPath.lineTo(cx - 8f, cy + 15f);
                ctlPath.close();
                c.drawPath(ctlPath, p);
            }
        } else if (kind == 0) {
            rect.set(cx - 15f, cy - 13f, cx - 11f, cy + 13f);
            c.drawRoundRect(rect, 2f, 2f, p);
            ctlPath.reset();
            ctlPath.moveTo(cx + 14f, cy - 13f);
            ctlPath.lineTo(cx - 6f, cy);
            ctlPath.lineTo(cx + 14f, cy + 13f);
            ctlPath.close();
            c.drawPath(ctlPath, p);
        } else {
            rect.set(cx + 11f, cy - 13f, cx + 15f, cy + 13f);
            c.drawRoundRect(rect, 2f, 2f, p);
            ctlPath.reset();
            ctlPath.moveTo(cx - 14f, cy - 13f);
            ctlPath.lineTo(cx + 6f, cy);
            ctlPath.lineTo(cx - 14f, cy + 13f);
            ctlPath.close();
            c.drawPath(ctlPath, p);
        }
    }

    private void drawWifi(Canvas c, float x, float y, float a, boolean ok) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.4f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(MUTED);
        p.setAlpha((int) (255 * a));
        c.save();
        c.scale(1.35f, 1.35f, x + 15f, y + 24f);
        float cx = x + 15f, cy = y + 24f;
        float[] r = {22f, 14f, 6f};
        for (float rr : r) {
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(rect, 225f, 90f, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy - 1f, 1.8f, p);
        if (!ok) {
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(cx + 12f, cy - 8f, cx + 24f, cy + 4f, p);
            c.drawLine(cx + 24f, cy - 8f, cx + 12f, cy + 4f, p);
            p.setStyle(Paint.Style.FILL);
        }
        c.restore();
    }

    private boolean netOk = true;

    private void checkNet() {
        try {
            ConnectivityManager cm = (ConnectivityManager) host.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            netOk = ni != null && ni.isConnected();
        } catch (Exception e) {
            netOk = true;
        }
    }

    private void drawOverlay(Canvas c) {
        int accent = openAccent();
        float bandP, floodP, darkP, alpha = 1f;
        if (overlay == 1) {
            if (homeOrigin) {
                bandP = smooth.getInterpolation(clamp01(openMs / 240f));
                floodP = flood.getInterpolation(clamp01((openMs - 240f) / 280f));
            } else {
                bandP = 1f; floodP = 1f;
                alpha = clamp01(openMs / 260f);
            }
            darkP = clamp01((openMs - 520f) / 240f);
        } else if (overlay == 2) {
            bandP = 1f; floodP = 1f; darkP = 1f;
        } else {
            bandP = 1f; floodP = 1f; darkP = 1f; alpha = ret.v;
        }
        float left = 112f + (0f - 112f) * floodP;
        float top = 465f + (0f - 465f) * floodP;
        float right = 112f + (W - 112f) * bandP;
        float bottom = 615f + (H - 615f) * floodP;
        if (bandShader == null || bandKey != accent) {
            bandShader = new LinearGradient(0f, 0f, W, 0f,
                new int[] {accent | 0xFF000000, hot(accent, .18f), accent | 0xFF000000},
                new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP);
            bandKey = accent;
        }
        fill.setShader(bandShader);
        fill.setAlpha((int) (255 * alpha));
        c.drawRect(left, top, right, bottom, fill);
        fill.setShader(null);
        if (darkP > 0f) {
            fill.setColor(0xFF050506);
            fill.setAlpha((int) (255 * darkP * alpha));
            c.drawRect(0f, 0f, W, H, fill);
        }
    }

    // ---- pantallas secundarias

    private void drawScreen(Canvas c) {
        if (screen == ALL) {
            header(c, "APLICACIONES", "Todas las apps");
            drawGrid(c, store.installed, allIdx, allTop, true);
        } else if (screen == PICK) {
            header(c, "EDITAR LISTA", "Agregar app");
            if (pickList.isEmpty()) text(c, "Ya agregaste todas las apps.", 112f, 420f, bold, 56f, SOFT, 1f, -.03f, Paint.Align.LEFT);
            else drawGrid(c, pickList, pickIdx, pickTop, false);
        } else if (screen == HDMI) {
            header(c, "ENTRADAS", "Entradas");
            if (inputs.isEmpty()) {
                text(c, "El TV no muestra sus entradas a las apps.", 112f, 420f, bold, 56f, SOFT, 1f, -.03f, Paint.Align.LEFT);
                text(c, "Usa el botón Entrada del control remoto.", 112f, 500f, medium, 30f, MUTED, 1f, 0f, Paint.Align.LEFT);
            } else {
                int cnt = Math.min(inputs.size(), 5);
                for (int i = 0; i < cnt; i++) {
                    float y = 340f + i * 120f;
                    boolean on = i == hdmiIdx;
                    float a = on ? 1f : .42f;
                    if (on) bar(c, 78f, y + 14f, 0xFFC77700, 8f, 72f);
                    text(c, inputNames.get(i), 112f, y + 74f, bold, 72f, TXT, a, -.035f, Paint.Align.LEFT);
                }
            }
        } else if (screen == ALARM) {
            drawAlarm(c);
        } else {
            drawEdit(c);
        }
    }

    private void drawAlarm(Canvas c) {
        header(c, "ALARMA", "Alarma");
        final int AMB = 0xFFE0A21B;
        if (alarmEditIdx >= 0 && alarmEditIdx < alarms.size()) { drawAlarmEdit(c, alarms.get(alarmEditIdx), AMB); return; }
        int n = alarms.size();
        boolean canAdd = n < AlarmStore.MAX;
        int rows = n + (canAdd ? 1 : 0) + 1;
        for (int i = 0; i < rows; i++) {
            float y = 262f + i * 94f;
            boolean on = i == alarmSel;
            float al = on ? 1f : .42f;
            if (on) bar(c, 78f, y + 6f, AMB, 8f, 60f);
            if (i < n) {
                AlarmStore.Alarm a = alarms.get(i);
                text(c, two(a.h) + ":" + two(a.m), 112f, y + 62f, bold, 64f, TXT, al * (a.on ? 1f : .6f), -.03f, Paint.Align.LEFT);
                String d = daysText(a.days) + (a.label > 0 ? "  ·  " + AlarmStore.LABELS[a.label] : "");
                text(c, d, 400f, y + 60f, medium, 30f, SOFT, al, 0f, Paint.Align.LEFT);
                drawSwitch(c, 1808f - 88f, y + 10f, a.on, al);
            } else if (canAdd && i == n) {
                text(c, "+ Nueva alarma", 112f, y + 60f, bold, 44f, TXT, al, -.035f, Paint.Align.LEFT);
            } else {
                text(c, "Probar en 1 minuto", 112f, y + 60f, bold, 44f, TXT, al, -.035f, Paint.Align.LEFT);
            }
        }
        String w = alarmWhen();
        if (w.length() > 0) text(c, w, 112f, 990f, medium, 30f, MUTED, 1f, 0f, Paint.Align.LEFT);
        text(c, "Izquierda y derecha activan o apagan · OK edita", 112f, 1038f, medium, 26f, SOFT, 1f, 0f, Paint.Align.LEFT);
    }

    private void drawAlarmEdit(Canvas c, AlarmStore.Alarm a, int amb) {
        // hora y minutos
        float w2 = tw("00", bold, 170f, -.03f), cw = tw(":", bold, 170f, -.03f);
        float x0 = 112f, base = 470f;
        boolean ft = alarmField == 0;
        float ta = ft ? 1f : .42f;
        if (ft && !alarmTimeEdit) bar(c, 78f, base - 110f, amb, 8f, 110f);
        text(c, two(a.h), x0, base, bold, 170f, TXT, alarmTimeEdit && alarmCol == 1 ? .45f : ta, -.03f, Paint.Align.LEFT);
        text(c, ":", x0 + w2 + 6f, base, bold, 170f, TXT, .5f * ta, -.03f, Paint.Align.LEFT);
        text(c, two(a.m), x0 + w2 + cw + 12f, base, bold, 170f, TXT, alarmTimeEdit && alarmCol == 0 ? .45f : ta, -.03f, Paint.Align.LEFT);
        if (ft && alarmTimeEdit) {
            float ux = alarmCol == 0 ? x0 : x0 + w2 + cw + 12f;
            fill.setShader(null);
            fill.setColor(amb);
            fill.setAlpha(255);
            rect.set(ux, base + 22f, ux + w2, base + 30f);
            c.drawRoundRect(rect, 4f, 4f, fill);
            // flechas: arriba sube, abajo baja
            float mx = ux + w2 / 2f;
            ctlPath.reset();
            ctlPath.moveTo(mx - 20f, base - 128f);
            ctlPath.lineTo(mx + 20f, base - 128f);
            ctlPath.lineTo(mx, base - 160f);
            ctlPath.close();
            c.drawPath(ctlPath, fill);
            ctlPath.reset();
            ctlPath.moveTo(mx - 20f, base + 50f);
            ctlPath.lineTo(mx + 20f, base + 50f);
            ctlPath.lineTo(mx, base + 82f);
            ctlPath.close();
            c.drawPath(ctlPath, fill);
        }
        // días
        boolean fd = alarmField == 1;
        float dy = 590f;
        if (fd) bar(c, 78f, dy + 6f, amb, 8f, 64f);
        for (int i = 0; i < 7; i++) {
            float x = 112f + i * 104f;
            boolean sel = (a.days & (1 << i)) != 0;
            rect.set(x, dy, x + 88f, dy + 76f);
            fill.setShader(null);
            fill.setColor(TXT);
            fill.setAlpha(sel ? 255 : 34);
            c.drawRoundRect(rect, 8f, 8f, fill);
            if (fd && i == alarmDay) {
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(5f);
                p.setColor(amb | 0xFF000000);
                p.setAlpha(255);
                rect.set(x - 5f, dy - 5f, x + 93f, dy + 81f);
                c.drawRoundRect(rect, 11f, 11f, p);
                p.setStyle(Paint.Style.FILL);
            }
            text(c, AlarmStore.DAYS[i], x + 44f, dy + 52f, bold, 38f, sel ? 0xFF0A0A0B : TXT, fd ? 1f : .8f, 0f, Paint.Align.CENTER);
        }
        text(c, daysText(a.days), 112f + 7f * 104f + 16f, dy + 50f, medium, 30f, SOFT, 1f, 0f, Paint.Align.LEFT);
        // etiqueta
        boolean fl = alarmField == 2;
        float ly = 730f;
        if (fl) bar(c, 78f, ly + 10f, amb, 8f, 50f);
        String lab = a.label > 0 ? AlarmStore.LABELS[a.label] : "sin etiqueta";
        text(c, "Etiqueta", 112f, ly + 48f, bold, 44f, TXT, fl ? 1f : .42f, -.035f, Paint.Align.LEFT);
        text(c, (fl ? "‹  " : "") + lab + (fl ? "  ›" : ""), 560f, ly + 46f, medium, 36f, fl ? TXT : SOFT, 1f, 0f, Paint.Align.LEFT);
        // eliminar
        boolean fe = alarmField == 3;
        float ey = 850f;
        if (fe) bar(c, 78f, ey + 10f, amb, 8f, 50f);
        boolean armed = SystemClock.uptimeMillis() < alarmDelArmed;
        text(c, armed ? "Pulsa OK otra vez para eliminar" : "Eliminar alarma", 112f, ey + 48f, bold, 44f, TXT, fe ? 1f : .42f, -.035f, Paint.Align.LEFT);
        String hint = alarmTimeEdit ? "Arriba y abajo cambian el número · izquierda y derecha, horas o minutos · OK termina"
            : (ft ? "OK para cambiar la hora · arriba y abajo cambian de campo · atrás guarda"
                  : "Arriba y abajo cambian de campo · izquierda y derecha cambian el valor · atrás guarda");
        text(c, hint, 112f, 1038f, medium, 26f, SOFT, 1f, 0f, Paint.Align.LEFT);
    }

    private void header(Canvas c, String kicker, String title) {
        text(c, title, 112f, 192f, bold, 96f, TXT, 1f, -.04f, Paint.Align.LEFT);
        text(c, clockStr, 1808f, 142f, medium, 52f, SOFT, 1f, -.02f, Paint.Align.RIGHT);
    }

    private void bar(Canvas c, float x, float y, int color, float w, float h) {
        fill.setShader(null);
        fill.setColor(color | 0xFF000000);
        fill.setAlpha(255);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 4f, 4f, fill);
    }

    private final Path clipPath = new Path();
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final ColorMatrixColorFilter dimFilter = buildDim();

    private static ColorMatrixColorFilter buildDim() {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(.55f);
        return new ColorMatrixColorFilter(m);
    }

    /** Cuadrícula de tarjetas: banner o icono de cada app; el nombre solo se ve en la enfocada. */
    private void drawGrid(Canvas c, List<AppEntry> list, int idx, int topRow, boolean marks) {
        c.save();
        c.clipRect(0f, 286f, W, 1012f);
        final float cw = 300f, ch = 169f, step = 349f, rowH = 232f;
        for (int i = 0; i < list.size(); i++) {
            int row = i / 5 - topRow, col = i % 5;
            if (row < 0 || row > 2) continue;
            float x = 112f + col * step, y = 310f + row * rowH;
            boolean on = i == idx;
            AppEntry e = list.get(i);
            AppStore.Card cd = store.cards.get(e.pkg);
            int dim = on ? 255 : 150;

            c.save();
            if (on) c.scale(1.06f, 1.06f, x + cw / 2f, y + ch / 2f);
            c.save();
            rect.set(x, y, x + cw, y + ch);
            clipPath.reset();
            clipPath.addRoundRect(rect, 10f, 10f, Path.Direction.CW);
            c.clipPath(clipPath);
            fill.setShader(null);
            fill.setColor(lerpColor(0xFF121214, e.accent | 0xFF000000, .32f));
            fill.setAlpha(dim);
            c.drawRect(rect, fill);
            cardPaint.setAlpha(dim);
            cardPaint.setColorFilter(on ? null : dimFilter);
            if (cd != null && cd.banner) {
                c.drawBitmap(cd.bmp, null, rect, cardPaint);
            } else if (cd != null) {
                float s2 = 96f;
                rect.set(x + (cw - s2) / 2f, y + (ch - s2) / 2f, x + (cw + s2) / 2f, y + (ch + s2) / 2f);
                c.drawBitmap(cd.bmp, null, rect, cardPaint);
            } else {
                text(c, e.label, x + cw / 2f, y + ch / 2f + 12f, bold, 36f, TXT, on ? 1f : .6f, -.02f, Paint.Align.CENTER);
            }
            cardPaint.setColorFilter(null);
            c.restore();
            if (on) {
                p.setShader(null);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(4f);
                p.setColor(e.accent | 0xFF000000);
                p.setAlpha(255);
                rect.set(x + 2f, y + 2f, x + cw - 2f, y + ch - 2f);
                c.drawRoundRect(rect, 9f, 9f, p);
                p.setStyle(Paint.Style.FILL);
            }
            c.restore();

            if (on) text(c, e.label, x, y + ch + 36f, medium, 30f, TXT, 1f, 0f, Paint.Align.LEFT);
        }
        c.restore();
        if (list.size() > 15) {
            text(c, (idx + 1) + " / " + list.size(), 1808f, 1050f, medium, 26f, MUTED, 1f, 0f, Paint.Align.RIGHT);
        }
    }

    private void drawEdit(Canvas c) {
        header(c, "INICIO", "Editar lista");
        int n = store.pinned.size();
        c.save();
        c.clipRect(0f, 235f, W, 985f);
        for (int idx = 0; idx < n + 8; idx++) {
            float y = editRowTop(idx) - editScroll.v;
            if (y < 170f || y > 1000f) continue;
            boolean on = idx == editI;
            float a = on ? 1f : .42f;
            int accent = TXT;
            String label;
            String right = null;
            boolean chipOn = false;
            if (idx < n) { accent = store.pinned.get(idx).accent; label = store.pinned.get(idx).label; }
            else if (idx == n) { label = "+ Agregar app"; right = n >= AppStore.MAX_PINNED ? "Máximo " + AppStore.MAX_PINNED : null; }
            else if (idx == n + 1) { label = "Restablecer lista"; right = null; }
            else if (idx == n + 2) { label = "Clima en inicio"; chipOn = store.weather; right = chipOn ? "Activado" : "Apagado"; }
            else if (idx == n + 3) { label = "Saludo según la hora"; chipOn = store.greeting; right = chipOn ? "Activado" : "Apagado"; }
            else if (idx == n + 4) { label = "Efecto de apertura"; chipOn = store.anim; right = chipOn ? "Activado" : "Apagado"; }
            else if (idx == n + 5) { label = "Reposo con reloj"; right = restLabel(store.restMin); }
            else if (idx == n + 6) { label = "Alarma"; chipOn = AlarmStore.feature(host); right = chipOn ? "Activado" : "Apagado"; }
            else { label = "Sonidos al navegar"; chipOn = store.sounds; right = chipOn ? "Activado" : "Apagado"; }
            if (on) bar(c, 78f, y + 10f, accent, 8f, 44f);
            float tx = 112f;
            if (idx < n) {
                fill.setShader(null);
                fill.setColor(accent | 0xFF000000);
                fill.setAlpha((int) (255 * a));
                c.drawCircle(120f, y + 33f, 8f, fill);
                tx = 144f;
            }
            text(c, label, tx, y + 46f, bold, 44f, TXT, a, -.035f, Paint.Align.LEFT);
            if (idx < n && on) {
                if (editMoving) chip(c, "MOVIENDO", 1808f, y + 8f, true);
                else {
                    float x2 = chip(c, "QUITAR", 1808f, y + 8f, editCol == 1);
                    chip(c, "MOVER", x2 - 14f, y + 8f, editCol == 0);
                }
            } else if (right != null) {
                if (idx == n + 5) {
                    if (on) {
                        // enfocada: las seis opciones, la activa rellena y las demás apagadas
                        float xr = 1808f;
                        int[] o = AppStore.REST_OPTIONS;
                        for (int j = o.length - 1; j >= 0; j--) {
                            boolean act = o[j] == store.restMin;
                            xr = chip(c, restLabel(o[j]), xr, y + 8f, act, act ? 1f : .5f) - 10f;
                        }
                    } else text(c, right, 1808f, y + 44f, medium, 28f, SOFT, a, 0f, Paint.Align.RIGHT);
                }
                else if (idx >= n + 2) drawSwitch(c, 1808f - 88f, y + 4f, chipOn, a);
                else text(c, right, 1808f, y + 44f, medium, 28f, SOFT, a, 0f, Paint.Align.RIGHT);
            }
        }
        c.restore();
    }

    /** Dibuja una etiqueta alineada a la derecha en xRight y devuelve su borde izquierdo. */
    private float chip(Canvas c, String s, float xRight, float y, boolean filled) {
        return chip(c, s, xRight, y, filled, 1f);
    }

    private float chip(Canvas c, String s, float xRight, float y, boolean filled, float al) {
        float w = tw(s, medium, 26f, 0f) + 40f;
        float x = xRight - w;
        rect.set(x, y, xRight, y + 50f);
        fill.setShader(null);
        if (filled) {
            fill.setColor(TXT);
            fill.setAlpha((int) (255 * al));
            c.drawRoundRect(rect, 4f, 4f, fill);
        } else {
            fill.setColor(TXT);
            fill.setAlpha((int) (34 * al));
            c.drawRoundRect(rect, 4f, 4f, fill);
        }
        text(c, s, x + 20f, y + 35f, medium, 26f, filled ? 0xFF0A0A0B : TXT, filled ? 1f : al, 0f, Paint.Align.LEFT);
        return x;
    }

    /** Interruptor: el estado se ve por forma y relleno, sin depender del foco. */
    private void drawSwitch(Canvas c, float x, float y, boolean on, float a) {
        fill.setShader(null);
        rect.set(x, y, x + 88f, y + 44f);
        fill.setColor(TXT);
        fill.setAlpha((int) (255 * a * (on ? 1f : .22f)));
        c.drawRoundRect(rect, 22f, 22f, fill);
        boolean foc = a >= .99f;
        fill.setColor(on && foc ? 0xFF0A0A0B : TXT);
        fill.setAlpha((int) (255 * (on ? 1f : .75f)));
        c.drawCircle(on ? x + 88f - 22f : x + 22f, y + 22f, 15f, fill);
    }

    private void drawToast(Canvas c) {
        float w = tw(toastText, medium, 28f, 0f) + 68f;
        float x = (W - w) / 2f, y = 880f;
        rect.set(x, y, x + w, y + 64f);
        fill.setShader(null);
        fill.setColor(TXT);
        fill.setAlpha(255);
        c.drawRoundRect(rect, 6f, 6f, fill);
        text(c, toastText, x + 34f, y + 43f, medium, 28f, 0xFF0A0A0B, 1f, 0f, Paint.Align.LEFT);
    }

    // ---- utilidades de texto y color

    private static final Locale ES = new Locale("es", "EC");

    /** Decisión de diseño: todo el texto del launcher va en minúsculas. */
    private final java.util.HashMap<String, String> lcCache = new java.util.HashMap<>();

    /** Minúsculas con caché: el dibujo repite los mismos textos en cada cuadro y no debe crear cadenas nuevas. */
    private String lc(String s) {
        if (s == null) return "";
        String r = lcCache.get(s);
        if (r == null) {
            if (lcCache.size() > 600) lcCache.clear();
            r = s.toLowerCase(ES);
            lcCache.put(s, r);
        }
        return r;
    }

    private void text(Canvas c, String s, float x, float y, Typeface tf, float size, int rgb, float alpha, float ls, Paint.Align al) {
        s = lc(s);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setLetterSpacing(ls);
        p.setTextAlign(al);
        p.setColor(rgb | 0xFF000000);
        p.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255))));
        c.drawText(s, x, y, p);
    }

    /** Contorno de una palabra ya unido: la fuente variable trae contornos solapados que, trazados uno a uno, dejan ver su esqueleto. */
    private static final class OutlineEntry {
        Path path;
        float size;
        float ls;
        Typeface tf;
    }

    private final java.util.HashMap<String, OutlineEntry> outlineCache = new java.util.HashMap<>();
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Path de la palabra con los contornos unidos; se guarda en caché para no recalcularlo en cada cuadro. */
    private Path outline(String s, Typeface tf, float size, float ls) {
        OutlineEntry e = outlineCache.get(s);
        if (e != null && e.size == size && e.ls == ls && e.tf == tf) return e.path;
        if (outlineCache.size() > 200) outlineCache.clear();
        Paint q = outlinePaint;
        q.setStyle(Paint.Style.FILL);
        q.setTypeface(tf);
        q.setTextSize(size);
        q.setLetterSpacing(ls);
        q.setTextAlign(Paint.Align.LEFT);
        Path raw = new Path();
        q.getTextPath(s, 0, s.length(), 0f, 0f, raw);
        Path merged = new Path();
        Path use = merged.op(raw, raw, Path.Op.UNION) ? merged : raw;
        if (e == null) e = new OutlineEntry();
        e.path = use;
        e.size = size;
        e.ls = ls;
        e.tf = tf;
        outlineCache.put(s, e);
        return use;
    }

    private void strokeText(Canvas c, String s, float x, float y, Typeface tf, float size, int rgb, float alpha, float ls) {
        s = lc(s);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setLetterSpacing(ls);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(rgb | 0xFF000000);
        p.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255))));
        c.save();
        c.translate(x, y);
        c.drawPath(outline(s, tf, size, ls), p);
        c.restore();
        p.setStyle(Paint.Style.FILL);
    }

    private float tw(String s, Typeface tf, float size, float ls) {
        s = lc(s);
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setLetterSpacing(ls);
        return p.measureText(s);
    }

    private String fit(String s, Typeface tf, float size, float ls, float maxW) {
        if (tw(s, tf, size, ls) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && tw(t + "…", tf, size, ls) > maxW) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    private static float distAlpha(float d) {
        if (d >= 3f) return Math.max(.04f, DIST_ALPHA[3] - (d - 3f) * .04f);
        int i = (int) Math.floor(d);
        float f = d - i;
        return DIST_ALPHA[i] + (DIST_ALPHA[i + 1] - DIST_ALPHA[i]) * f;
    }

    /** Desvanece la lista arriba y abajo, como la máscara de la maqueta. */
    private static float maskAt(float cy) {
        float f = cy / H;
        if (f <= .24f) return 0f;
        if (f < .40f) return (f - .24f) / .16f;
        if (f <= .70f) return 1f;
        if (f < .86f) return (.86f - f) / .16f;
        return 0f;
    }

    private static int withAlpha(int rgb, float a) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int lerpColor(int a, int b, float f) {
        f = clamp01(f);
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f);
        return Color.rgb(r, g, bl);
    }

    private static int hot(int rgb, float k) {
        int r = Color.red(rgb), g = Color.green(rgb), b = Color.blue(rgb);
        return Color.rgb((int) (r + (255 - r) * k), (int) (g + (255 - g) * k), (int) (b + (255 - b) * k));
    }

    private static final TimeInterpolator LIN = new LinearInterpolator();

    /** Un número animado que vuelve a dibujar la pantalla en cada paso. */
    private final class Tween {
        float v = 0f;
        private ValueAnimator a;

        void set(float t) {
            if (a != null) { a.removeAllListeners(); a.cancel(); }
            v = t;
            invalidate();
        }

        void to(float t, long ms, TimeInterpolator in) { to(t, ms, in, null); }

        void to(float t, long ms, TimeInterpolator in, final Runnable end) {
            if (a != null) { a.removeAllListeners(); a.cancel(); }
            a = ValueAnimator.ofFloat(v, t);
            a.setDuration(ms);
            a.setInterpolator(in);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator x) { v = (Float) x.getAnimatedValue(); invalidate(); }
            });
            if (end != null) {
                a.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator x) { end.run(); }
                });
            }
            a.start();
        }
    }
}
