package ec.josue.tvhome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
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
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Pantalla completa del launcher, dibujada en un solo View sobre un lienzo de 1920x1080.
 * Pantallas propias: inicio, todas las apps, editar lista, agregar app y entradas HDMI.
 * Ajustes del sistema se abre con el nativo de Android.
 */
final class LauncherView extends View {
    private static final int HOME = 0, ALL = 1, EDIT = 2, PICK = 3, HDMI = 4;
    private static final float W = 1920f, H = 1080f;
    private static final int TXT = 0xFFF6F4F0, MUTED = 0xFF8E8C87, SOFT = 0xFFA8A6A0;
    private static final String[] BASE_TOOLS = {"Todas las apps", "Entradas HDMI", "Editar lista", "Ajustes"};
    private static final int[] BASE_ACCENT = {0xFF5B5BD6, 0xFFC77700, 0xFF17A2A2, 0xFF7C5CE0};
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
    private final TimeInterpolator flood = new PathInterpolator(.55f, 0f, .15f, 1f);

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface mono = Typeface.MONOSPACE;

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
    private final List<String> inputStates = new ArrayList<>();

    // --- animaciones ---
    private final Tween focusPos = new Tween();
    private final Tween toolsDim = new Tween();
    private final Tween blink = new Tween();
    private final Tween ret = new Tween();
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
        this.weather = new Weather(host);
        setFocusable(true);
        TimeZone tz = TimeZone.getTimeZone("America/Guayaquil");
        fmtClock.setTimeZone(tz);
        fmtDate.setTimeZone(tz);
        fmtHour.setTimeZone(tz);
        p.setStrokeJoin(Paint.Join.ROUND);
        store.refresh();
        tick();
    }

    // ------------------------------------------------------------------ ciclo de vida

    void onResumed() {
        store.refresh();
        findStockHome();
        clampSelection();
        if (overlay == 1 || overlay == 2) {
            if (openAnim != null) { openAnim.removeAllListeners(); openAnim.cancel(); }
            pending = null;
            overlay = 3;
            ret.set(1f);
            ret.to(0f, 600, LIN, new Runnable() { @Override public void run() { overlay = 0; invalidate(); } });
        }
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        if (store.weather) weather.refreshIfStale(new Runnable() { @Override public void run() { invalidate(); } });
        invalidate();
    }

    void onPaused() {
        handler.removeCallbacks(ticker);
        stopInputWatch();
    }

    /** Se pulsó Inicio con el launcher ya abierto. */
    void onHomePressed() {
        if (overlay == 1) return;
        screen = HOME;
        editMoving = false;
        inApps = true;
        focusPos.set(app);
        toolsDim.set(0f);
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
        if (stockHome != null) {
            TOOLS = new String[] {BASE_TOOLS[0], BASE_TOOLS[1], BASE_TOOLS[2], BASE_TOOLS[3], "Google TV"};
            TOOL_ACCENT = new int[] {BASE_ACCENT[0], BASE_ACCENT[1], BASE_ACCENT[2], BASE_ACCENT[3], 0xFF4285F4};
        } else {
            TOOLS = BASE_TOOLS;
            TOOL_ACCENT = BASE_ACCENT;
        }
        if (tool > TOOLS.length - 1) tool = TOOLS.length - 1;
    }

    private void tick() {
        Date now = new Date();
        clockStr = fmtClock.format(now);
        dateStr = fmtDate.format(now);
        int h = 12;
        try { h = Integer.parseInt(fmtHour.format(now)); } catch (NumberFormatException ignored) { }
        greetStr = h < 12 ? "Buenos días" : (h < 19 ? "Buenas tardes" : "Buenas noches");
        if (store.weather) weather.refreshIfStale(new Runnable() { @Override public void run() { invalidate(); } });
        invalidate();
    }

    private void clampSelection() {
        int n = Math.max(1, store.pinned.size());
        if (app > n - 1) app = n - 1;
        focusPos.set(app);
        int total = store.pinned.size() + 5;
        if (editI > total - 1) editI = total - 1;
    }

    // ------------------------------------------------------------------ entrada de teclas

    boolean onKey(int keyCode, boolean longPress) {
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
            default: keyEdit(k); break;
        }
        invalidate();
        return true;
    }

    private static final int UP = 1, DOWN = 2, LEFT = 3, RIGHT = 4, OK = 5, LONG = 6, BACK = 7;

    private void keyHome(int k) {
        int n = Math.max(1, store.pinned.size());
        if (inApps) {
            if (k == UP && app > 0) { app--; focusPos.to(app, 320, smooth); }
            else if (k == DOWN) {
                if (app < n - 1) { app++; focusPos.to(app, 320, smooth); }
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
    }

    private void enterTool(int t) {
        if (t == 0) { go(new Runnable() { @Override public void run() { screen = ALL; } }); }
        else if (t == 1) { go(new Runnable() { @Override public void run() { loadInputs(); hdmiIdx = 0; screen = HDMI; } }); }
        else if (t == 2) { go(new Runnable() { @Override public void run() { editI = 0; editCol = 0; editMoving = false; editScroll.set(0f); screen = EDIT; } }); }
        else if (t == 3) { openSystemSettings(); }
        else if (stockHome != null) {
            try { host.startActivity(stockHome); } catch (Exception e) { toast("No se pudo abrir el inicio del TV"); }
        }
    }

    private void backToHome(final int toolIdx) {
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

    private void keyHdmi(int k) {
        if (k == BACK) { backToHome(1); return; }
        if (inputs.isEmpty()) return;
        if (k == UP && hdmiIdx > 0) hdmiIdx--;
        else if (k == DOWN && hdmiIdx < Math.min(inputs.size(), 5) - 1) hdmiIdx++;
        else if (k == OK || k == LONG) switchInput(inputs.get(hdmiIdx), inputNames.get(hdmiIdx));
    }

    private void keyEdit(int k) {
        int n = store.pinned.size();
        int last = n + 4;
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
                store.resetPinned();
                editI = 0;
                toast("Lista restablecida");
            } else if (editI == n + 2) store.setWeather(!store.weather);
            else if (editI == n + 3) store.setGreeting(!store.greeting);
            else store.setAnim(!store.anim);
            if (store.weather) weather.refreshIfStale(new Runnable() { @Override public void run() { invalidate(); } });
        }
        focusEdit();
    }

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
        if (k == DOWN) return idx + 5 < len ? idx + 5 : idx;
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
        else if (t + 72f - cur > 960f) target = t + 72f - 960f;
        if (target < 0f) target = 0f;
        if (target != cur) editScroll.to(target, 220, smooth);
    }

    private float editRowTop(int idx) {
        int n = store.pinned.size();
        return 250f + idx * 72f + (idx >= n ? 14f : 0f) + (idx >= n + 2 ? 14f : 0f);
    }

    // ------------------------------------------------------------------ acciones del sistema

    private void go(final Runnable r) {
        if (!store.anim) { r.run(); invalidate(); return; }
        lockUntil = SystemClock.uptimeMillis() + 560;
        blink.to(1f, 200, LIN, null);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                r.run();
                blink.to(0f, 320, LIN, null);
                invalidate();
            }
        }, 220);
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
        openAnim = ValueAnimator.ofFloat(0f, 1700f);
        openAnim.setDuration(1700);
        openAnim.setInterpolator(new LinearInterpolator());
        openAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                openMs = (Float) a.getAnimatedValue();
                if (!launched && openMs >= 900f && pending != null) {
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

    private TvInputManager tvim = null;
    private TvInputManager.TvInputCallback inputCb = null;

    private static String stateText(int st) {
        return st == TvInputManager.INPUT_STATE_DISCONNECTED ? "Sin dispositivo" : "Dispositivo conectado";
    }

    private void stopInputWatch() {
        try { if (tvim != null && inputCb != null) tvim.unregisterCallback(inputCb); } catch (Exception ignored) { }
        inputCb = null;
    }

    private void loadInputs() {
        stopInputWatch();
        inputs.clear();
        inputNames.clear();
        inputStates.clear();
        try {
            TvInputManager tim = (TvInputManager) host.getSystemService(Context.TV_INPUT_SERVICE);
            if (tim == null) return;
            tvim = tim;
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput()) continue;
                CharSequence nm = info.loadCustomLabel(host);
                if (nm == null || nm.length() == 0) nm = info.loadLabel(host);
                inputs.add(info);
                inputNames.add(nm == null ? info.getId() : nm.toString());
                // Mientras llega el estado real, no afirmamos nada.
                inputStates.add("Comprobando…");
            }
            // Sin suscripcion Android devuelve CONNECTED por defecto: hay que registrar el callback
            // para recibir el estado real de cada entrada.
            inputCb = new TvInputManager.TvInputCallback() {
                @Override public void onInputStateChanged(String inputId, int state) {
                    for (int i = 0; i < inputs.size(); i++) {
                        if (inputs.get(i).getId().equals(inputId) && i < inputStates.size()) {
                            inputStates.set(i, stateText(state));
                        }
                    }
                    postInvalidate();
                }
            };
            tim.registerCallback(inputCb, new Handler(Looper.getMainLooper()));
        } catch (Exception ignored) {
            // El TV no deja ver sus entradas a las apps: la pantalla muestra el aviso.
        }
    }

    private void switchInput(TvInputInfo info, String name) {
        try {
            Uri u = TvContract.buildChannelUriForPassthroughInput(info.getId());
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

    private void drawHome(Canvas c) {
        int n = store.pinned.size();
        int accent = currentAccent();
        boolean opening = overlay == 1 && homeOrigin;
        float restA = 1f;          // transparencia de las filas no seleccionadas
        float selA = 1f;           // transparencia de la fila seleccionada
        float chromeA = 1f;
        float barA = 1f - toolsDim.v;
        if (overlay == 1) {
            restA = 1f - clamp01(openMs / 260f);
            selA = 1f - clamp01((openMs - 1160f) / 450f);
            chromeA = restA;
            barA = 0f;
        } else if (overlay == 2) {
            restA = 0f; selA = 0f; chromeA = 0f; barA = 0f;
        } else if (overlay == 3) {
            restA = selA = chromeA = 1f - ret.v;
            barA = (1f - ret.v) * (1f - toolsDim.v);
        }
        float glowA = (overlay == 1) ? (1f - clamp01(openMs / 380f)) * (1f - toolsDim.v) : barA;

        // brillo de fondo
        if (glowA > 0.001f) {
            c.save();
            c.translate(460f, 540f);
            c.scale(1f, .5f);
            fill.setShader(new RadialGradient(0f, 0f, 600f, withAlpha(accent, .30f * glowA), withAlpha(accent, 0f), Shader.TileMode.CLAMP));
            c.drawCircle(0f, 0f, 600f, fill);
            fill.setShader(null);
            c.restore();
        }

        // relleno de color al abrir
        if (overlay != 0) drawOverlay(c);

        // lista de apps
        c.save();
        c.clipRect(0f, 0f, 1400f, H);
        if (n == 0) {
            drawRow(c, "Agregar apps", 465f, 1f - toolsDim.v, TXT, 1f, true);
        } else {
            for (int i = 0; i < n; i++) {
                float dist = Math.abs(i - focusPos.v);
                float y = 465f + (i - focusPos.v) * 150f;
                float op = distAlpha(dist);
                float opTools = dist < .5f ? .55f : op * .8f;
                op = op + (opTools - op) * toolsDim.v;
                float foc = clamp01(1f - dist) * (1f - toolsDim.v);
                boolean sel = i == app;
                float a;
                if (opening || overlay == 2) a = sel ? selA : restA;
                else a = (overlay == 3) ? restA : 1f;
                if (opening && sel) { op = 1f; foc = 1f; }
                drawRow(c, store.pinned.get(i).label, y, foc, TXT, op * a, false);
            }
        }
        c.restore();

        // barra de acento
        if (barA > 0.001f) {
            fill.setShader(null);
            fill.setColor(accent | 0xFF000000);
            fill.setAlpha((int) (255 * barA));
            rect.set(112f, 492f, 120f, 588f);
            c.drawRoundRect(rect, 4f, 4f, fill);
        }

        if (chromeA > 0.001f) drawChrome(c, accent, chromeA);
    }

    private void drawRow(Canvas c, String label, float top, float foc, int color, float alpha, boolean single) {
        float cy = top + 75f;
        float m = maskAt(cy);
        float aa = alpha * m;
        if (aa <= 0.003f) return;
        float by = cy + 46f;
        if (foc < 0.999f) strokeText(c, label, 160f, by, bold, 128f, TXT, .7f * aa * (1f - foc), -.04f);
        if (foc > 0.001f) text(c, label, 160f, by, bold, 128f, color, aa * foc, -.04f, Paint.Align.LEFT);
    }

    private void drawChrome(Canvas c, int accent, float a) {
        if (store.greeting) {
            fill.setShader(null);
            fill.setColor(accent | 0xFF000000);
            fill.setAlpha((int) (255 * a));
            c.drawCircle(119f, 117f, 7f, fill);
            text(c, greetStr.toUpperCase(new Locale("es")), 144f, 126f, mono, 28f, MUTED, a, .14f, Paint.Align.LEFT);
        }
        if (store.weather) {
            int t = weather.cached();
            String temp = (t == Weather.NONE ? "--" : String.valueOf(t)) + "°";
            text(c, temp, 112f, 190f, medium, 44f, TXT, a, -.01f, Paint.Align.LEFT);
            float w = tw(temp, medium, 44f, -.01f);
            text(c, " · Guayaquil", 112f + w, 190f, medium, 44f, MUTED, a, -.01f, Paint.Align.LEFT);
        }
        text(c, clockStr, 1808f, 168f, mono, 104f, TXT, a, -.04f, Paint.Align.RIGHT);
        text(c, dateStr, 1808f, 232f, medium, 38f, SOFT, a, 0f, Paint.Align.RIGHT);

        // fila inferior
        float x = 112f;
        for (int j = 0; j < TOOLS.length; j++) {
            boolean f = !inApps && j == tool;
            float w = tw(TOOLS[j], medium, 36f, 0f);
            float ta = a * (f ? 1f : .78f);
            text(c, TOOLS[j], x, 988f, medium, 36f, f ? TXT : SOFT, ta, 0f, Paint.Align.LEFT);
            if (f) {
                fill.setShader(null);
                fill.setColor(TOOL_ACCENT[j]);
                fill.setAlpha((int) (255 * a));
                c.drawRect(x, 1005f, x + w, 1008f, fill);
            }
            x += w + 56f;
        }
        // ayuda y red
        String hint = inApps ? "↑ ↓ EXPLORAR · OK ABRIR" : "← → ELEGIR · OK ENTRAR";
        text(c, hint, 1808f, 962f, mono, 22f, MUTED, a, .1f, Paint.Align.RIGHT);
        String net = networkLabel();
        text(c, net, 1808f, 1010f, mono, 22f, MUTED, a, .06f, Paint.Align.RIGHT);
        drawWifi(c, 1808f - tw(net, mono, 22f, .06f) - 44f, 984f, a);
    }

    private void drawWifi(Canvas c, float x, float y, float a) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.4f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(MUTED);
        p.setAlpha((int) (255 * a));
        float cx = x + 15f, cy = y + 24f;
        float[] r = {22f, 14f, 6f};
        for (float rr : r) {
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr);
            c.drawArc(rect, 225f, 90f, false, p);
        }
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy - 1f, 1.8f, p);
    }

    private String networkLabel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) host.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            if (ni == null || !ni.isConnected()) return "Sin conexión";
            if (ni.getType() == ConnectivityManager.TYPE_ETHERNET) return "Ethernet conectado";
            return "Wi-Fi conectado";
        } catch (Exception e) {
            return "Wi-Fi";
        }
    }

    private void drawOverlay(Canvas c) {
        int accent = openAccent();
        float bandP, floodP, darkP, alpha = 1f;
        if (overlay == 1) {
            if (homeOrigin) {
                bandP = smooth.getInterpolation(clamp01(openMs / 380f));
                floodP = flood.getInterpolation(clamp01((openMs - 380f) / 520f));
            } else {
                bandP = 1f; floodP = 1f;
                alpha = clamp01(openMs / 400f);
            }
            darkP = clamp01((openMs - 1160f) / 450f);
        } else if (overlay == 2) {
            bandP = 1f; floodP = 1f; darkP = 1f;
        } else {
            bandP = 1f; floodP = 1f; darkP = 1f; alpha = ret.v;
        }
        float left = 112f + (0f - 112f) * floodP;
        float top = 465f + (0f - 465f) * floodP;
        float right = 112f + (W - 112f) * bandP;
        float bottom = 615f + (H - 615f) * floodP;
        fill.setShader(new LinearGradient(0f, 0f, W, 0f,
            new int[] {accent | 0xFF000000, hot(accent, .18f), accent | 0xFF000000},
            new float[] {0f, .55f, 1f}, Shader.TileMode.CLAMP));
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
            hintBottom(c, "← → ↑ ↓ ELEGIR · OK ABRIR · MANTÉN OK PARA FIJAR EN INICIO · ATRÁS VOLVER");
        } else if (screen == PICK) {
            header(c, "EDITAR LISTA", "Agregar app");
            if (pickList.isEmpty()) text(c, "Ya agregaste todas las apps.", 112f, 420f, bold, 56f, SOFT, 1f, -.03f, Paint.Align.LEFT);
            else drawGrid(c, pickList, pickIdx, pickTop, false);
            hintBottom(c, "← → ↑ ↓ ELEGIR · OK AGREGAR AL FINAL · ATRÁS VOLVER");
        } else if (screen == HDMI) {
            header(c, "ENTRADAS", "Entradas HDMI");
            if (inputs.isEmpty()) {
                text(c, "El TV no muestra sus entradas a las apps.", 112f, 420f, bold, 56f, SOFT, 1f, -.03f, Paint.Align.LEFT);
                text(c, "Usa el botón Entrada del control remoto.", 112f, 500f, mono, 28f, MUTED, 1f, .04f, Paint.Align.LEFT);
            } else {
                int cnt = Math.min(inputs.size(), 5);
                for (int i = 0; i < cnt; i++) {
                    float y = 340f + i * 120f;
                    boolean on = i == hdmiIdx;
                    float a = on ? 1f : .42f;
                    if (on) bar(c, 78f, y + 14f, 0xFFC77700, 8f, 72f);
                    text(c, inputNames.get(i), 112f, y + 74f, bold, 72f, TXT, a, -.035f, Paint.Align.LEFT);
                    text(c, inputStates.get(i), 1808f, y + 66f, mono, 28f, SOFT, a, .08f, Paint.Align.RIGHT);
                }
            }
            hintBottom(c, "↑ ↓ ELEGIR · OK CAMBIAR DE ENTRADA · ATRÁS VOLVER");
        } else {
            drawEdit(c);
        }
    }

    private void header(Canvas c, String kicker, String title) {
        text(c, kicker, 112f, 118f, mono, 24f, MUTED, 1f, .16f, Paint.Align.LEFT);
        text(c, title, 112f, 192f, bold, 96f, TXT, 1f, -.04f, Paint.Align.LEFT);
        text(c, clockStr, 1808f, 142f, mono, 52f, SOFT, 1f, -.03f, Paint.Align.RIGHT);
    }

    private void hintBottom(Canvas c, String s) {
        text(c, s, 112f, 1016f, mono, 22f, MUTED, 1f, .1f, Paint.Align.LEFT);
    }

    private void bar(Canvas c, float x, float y, int color, float w, float h) {
        fill.setShader(null);
        fill.setColor(color | 0xFF000000);
        fill.setAlpha(255);
        rect.set(x, y, x + w, y + h);
        c.drawRoundRect(rect, 4f, 4f, fill);
    }

    private void drawGrid(Canvas c, List<AppEntry> list, int idx, int topRow, boolean marks) {
        c.save();
        c.clipRect(0f, 300f, W, 1010f);
        for (int i = 0; i < list.size(); i++) {
            int row = i / 5 - topRow, col = i % 5;
            if (row < 0 || row > 2) continue;
            float x = 112f + col * 308f, y = 340f + row * 200f;
            boolean on = i == idx;
            AppEntry e = list.get(i);
            if (on) {
                fill.setShader(new LinearGradient(0f, y + 170f, 0f, y, withAlpha(e.accent, .22f), withAlpha(e.accent, 0f), Shader.TileMode.CLAMP));
                c.drawRect(x, y, x + 280f, y + 170f, fill);
                fill.setShader(null);
            }
            fill.setColor(on ? (e.accent | 0xFF000000) : 0xFFF6F4F0);
            fill.setAlpha(on ? 255 : 36);
            c.drawRect(x, y + 164f, x + 280f, y + 170f, fill);
            float a = on ? 1f : .5f;
            text(c, fit(e.label, bold, 46f, -.03f, 272f), x, y + 118f, bold, 46f, TXT, a, -.03f, Paint.Align.LEFT);
            if (marks && store.isPinned(e)) text(c, "EN INICIO", x, y + 146f, mono, 18f, MUTED, a, .14f, Paint.Align.LEFT);
        }
        c.restore();
    }

    private void drawEdit(Canvas c) {
        header(c, "INICIO", "Editar lista");
        int n = store.pinned.size();
        c.save();
        c.clipRect(0f, 235f, W, 985f);
        for (int idx = 0; idx < n + 5; idx++) {
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
            else if (idx == n + 1) { label = "Restablecer lista"; right = "Netflix · YouTube · Disney+ · Xuper"; }
            else if (idx == n + 2) { label = "Clima en inicio"; chipOn = store.weather; right = chipOn ? "Activado" : "Apagado"; }
            else if (idx == n + 3) { label = "Saludo según la hora"; chipOn = store.greeting; right = chipOn ? "Activado" : "Apagado"; }
            else { label = "Efecto de apertura"; chipOn = store.anim; right = chipOn ? "Activado" : "Apagado"; }
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
                if (editMoving) chip(c, "↑ ↓ MOVIENDO · OK LISTO", 1808f, y + 8f, true);
                else {
                    float x2 = chip(c, "QUITAR", 1808f, y + 8f, editCol == 1);
                    chip(c, "MOVER", x2 - 14f, y + 8f, editCol == 0);
                }
            } else if (right != null) {
                if (idx >= n + 2) chip(c, right.toUpperCase(Locale.ROOT), 1808f, y + 8f, chipOn && on);
                else text(c, right, 1808f, y + 44f, mono, 24f, SOFT, a, .08f, Paint.Align.RIGHT);
            }
        }
        c.restore();
        hintBottom(c, editMoving ? "↑ ↓ MOVER · OK O ATRÁS PARA TERMINAR" : "↑ ↓ ELEGIR · OK MOVER · → QUITAR · ATRÁS LISTO");
    }

    /** Dibuja una etiqueta alineada a la derecha en xRight y devuelve su borde izquierdo. */
    private float chip(Canvas c, String s, float xRight, float y, boolean filled) {
        float w = tw(s, mono, 22f, .1f) + 36f;
        float x = xRight - w;
        rect.set(x, y, xRight, y + 50f);
        fill.setShader(null);
        if (filled) {
            fill.setColor(TXT);
            fill.setAlpha(255);
            c.drawRoundRect(rect, 4f, 4f, fill);
        } else {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(TXT);
            p.setAlpha(90);
            c.drawRoundRect(rect, 4f, 4f, p);
            p.setStyle(Paint.Style.FILL);
        }
        text(c, s, x + 18f, y + 34f, mono, 22f, filled ? 0xFF0A0A0B : TXT, 1f, .1f, Paint.Align.LEFT);
        return x;
    }

    private void drawToast(Canvas c) {
        float w = tw(toastText, mono, 26f, .06f) + 68f;
        float x = (W - w) / 2f, y = 880f;
        rect.set(x, y, x + w, y + 64f);
        fill.setShader(null);
        fill.setColor(TXT);
        fill.setAlpha(255);
        c.drawRoundRect(rect, 6f, 6f, fill);
        text(c, toastText, x + 34f, y + 42f, mono, 26f, 0xFF0A0A0B, 1f, .06f, Paint.Align.LEFT);
    }

    // ---- utilidades de texto y color

    private void text(Canvas c, String s, float x, float y, Typeface tf, float size, int rgb, float alpha, float ls, Paint.Align al) {
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

    private void strokeText(Canvas c, String s, float x, float y, Typeface tf, float size, int rgb, float alpha, float ls) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setLetterSpacing(ls);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(rgb | 0xFF000000);
        p.setAlpha(Math.max(0, Math.min(255, (int) (alpha * 255))));
        c.drawText(s, x, y, p);
        p.setStyle(Paint.Style.FILL);
    }

    private float tw(String s, Typeface tf, float size, float ls) {
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
        if (f <= .14f) return 0f;
        if (f < .34f) return (f - .14f) / .20f;
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
