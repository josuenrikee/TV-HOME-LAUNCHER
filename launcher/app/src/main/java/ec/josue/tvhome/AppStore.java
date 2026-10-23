package ec.josue.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Lista de apps instaladas, lista fijada en inicio y opciones. Todo se guarda en SharedPreferences. */
final class AppStore {
    static final int MAX_PINNED = 8;

    private static final String KEY_PINNED = "pinned";
    private static final String KEY_INIT = "init";
    private static final String KEY_LISTV = "listv";
    private static final int LIST_VERSION = 2;
    private static final String KEY_WEATHER = "weather";
    private static final String KEY_GREETING = "greeting";
    private static final String KEY_ANIM = "anim";
    private static final String KEY_REST = "rest_min";
    private static final String KEY_SOUNDS = "sounds";
    static final int[] REST_OPTIONS = {0, 5, 10, 15, 30, 60};

    private static final int[] PALETTE = {
        0x3D7BFF, 0x1FA866, 0x8A4DFF, 0x00A8E1, 0xF2C94C, 0xF29F05, 0xFF8A00, 0xD14DFF, 0x17A2D8, 0x34A8A0
    };

    private final Context ctx;
    private final PackageManager pm;
    private final SharedPreferences sp;

    /** Imagen de tarjeta de una app: su banner de TV, o su icono si no tiene banner. */
    static final class Card {
        final Bitmap bmp;
        final boolean banner;
        Card(Bitmap bmp, boolean banner) { this.bmp = bmp; this.banner = banner; }
    }

    final Map<String, Card> cards = new ConcurrentHashMap<>();
    final List<AppEntry> installed = new ArrayList<>();
    final List<AppEntry> pinned = new ArrayList<>();
    boolean weather;
    boolean greeting;
    boolean anim;
    /** Minutos sin tocar el control para pasar al reposo con reloj (0 = apagado). */
    int restMin;
    /** Sonido suave al moverse por la lista de apps. */
    boolean sounds;

    AppStore(Context ctx) {
        this.ctx = ctx;
        this.pm = ctx.getPackageManager();
        this.sp = ctx.getSharedPreferences("tvhome", Context.MODE_PRIVATE);
        weather = sp.getBoolean(KEY_WEATHER, true);
        greeting = sp.getBoolean(KEY_GREETING, true);
        anim = sp.getBoolean(KEY_ANIM, true);
        restMin = sp.getInt(KEY_REST, 10);
        sounds = sp.getBoolean(KEY_SOUNDS, true);
    }

    /** Vuelve a leer las apps instaladas y reconstruye la lista fijada (omite lo desinstalado o desactivado). */
    void refresh() {
        installed.clear();
        Map<String, AppEntry> byPkg = new HashMap<>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
            for (ResolveInfo ri : list) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(ctx.getPackageName()) || byPkg.containsKey(pkg) || isHidden(pkg, ri.loadLabel(pm))) continue;
                CharSequence label = ri.loadLabel(pm);
                String raw0 = label == null ? pkg : label.toString();
                AppEntry e = new AppEntry(pkg, displayName(pkg, raw0), accentFor(pkg, raw0));
                e.cls = ri.activityInfo.name;
                byPkg.put(pkg, e);
                installed.add(e);
            }
        }
        final Collator col = Collator.getInstance(new Locale("es", "EC"));
        Collections.sort(installed, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry a, AppEntry b) { return col.compare(a.label, b.label); }
        });

        if ((!sp.getBoolean(KEY_INIT, false) || sp.getInt(KEY_LISTV, 0) < LIST_VERSION) && !installed.isEmpty()) {
            List<String> defaults = new ArrayList<>();
            for (AppEntry e : installed) {
                String l = e.label.toLowerCase(Locale.ROOT);
                if (e.pkg.equals("com.wbd.stream") || e.pkg.startsWith("com.hbo") || l.contains("hbo") || l.equals("max")) { defaults.add(e.pkg); break; }
            }
            addDefault(defaults, "com.google.android.youtube.tv", "com.google.android.youtube");
            for (AppEntry e : installed) {
                if (e.label.toLowerCase(Locale.ROOT).contains("xuper")) { defaults.add(e.pkg); break; }
            }
            addDefault(defaults, "com.netflix.ninja", "com.netflix.mediaclient");
            addDefault(defaults, "com.disney.disneyplus");
            sp.edit().putString(KEY_PINNED, join(defaults)).putBoolean(KEY_INIT, true).putInt(KEY_LISTV, LIST_VERSION).apply();
        }

        // BitChord (cliente de YouTube Music para TV): se agrega una sola vez al final de la lista cuando se instala
        if (sp.getInt("append_v", 0) < 1 && !installed.isEmpty()) {
            AppEntry bc = byPkg.get("com.music.bitchord.tv");
            if (bc == null) bc = byPkg.get("com.dev.bitchord.tv");
            if (bc != null) {
                String cur = sp.getString(KEY_PINNED, "");
                if (cur == null) cur = "";
                if (!("," + cur + ",").contains("," + bc.pkg + ",")) cur = cur.isEmpty() ? bc.pkg : cur + "," + bc.pkg;
                sp.edit().putString(KEY_PINNED, cur).putInt("append_v", 1).apply();
            }
        }

        pinned.clear();
        String raw = sp.getString(KEY_PINNED, "");
        if (!installed.isEmpty() && raw != null && !raw.isEmpty()) {
            for (String pkg : raw.split(",")) {
                AppEntry e = byPkg.get(pkg);
                if (e != null && !pinned.contains(e) && pinned.size() < MAX_PINNED) pinned.add(e);
            }
        }
    }

    private void addDefault(List<String> out, String... pkgs) {
        for (String p : pkgs) {
            for (AppEntry e : installed) {
                if (e.pkg.equals(p)) { out.add(p); return; }
            }
        }
    }

    boolean isPinned(AppEntry e) { return pinned.contains(e); }

    List<AppEntry> notPinned() {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : installed) if (!pinned.contains(e)) out.add(e);
        return out;
    }

    /** @return false si la lista está llena o ya contiene la app. */
    boolean pin(AppEntry e) {
        if (pinned.contains(e) || pinned.size() >= MAX_PINNED) return false;
        pinned.add(e);
        savePinned();
        return true;
    }

    void unpin(int index) {
        if (index < 0 || index >= pinned.size()) return;
        pinned.remove(index);
        savePinned();
    }

    void swap(int a, int b) {
        if (a < 0 || b < 0 || a >= pinned.size() || b >= pinned.size()) return;
        Collections.swap(pinned, a, b);
        savePinned();
    }

    void resetPinned() {
        sp.edit().putBoolean(KEY_INIT, false).putString(KEY_PINNED, "").apply();
        refresh();
    }

    void setWeather(boolean v) { weather = v; sp.edit().putBoolean(KEY_WEATHER, v).apply(); }
    void setGreeting(boolean v) { greeting = v; sp.edit().putBoolean(KEY_GREETING, v).apply(); }
    void setSounds(boolean v) { sounds = v; sp.edit().putBoolean(KEY_SOUNDS, v).apply(); }
    void setRestMin(int v) { restMin = v; sp.edit().putInt(KEY_REST, v).apply(); }
    void setAnim(boolean v) { anim = v; sp.edit().putBoolean(KEY_ANIM, v).apply(); }

    /** Intent para abrir la app: primero la versión de TV, luego la normal. */
    Intent launchIntent(AppEntry e) {
        Intent in = pm.getLeanbackLaunchIntentForPackage(e.pkg);
        if (in == null) in = pm.getLaunchIntentForPackage(e.pkg);
        if (in != null) in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return in;
    }

    private void savePinned() {
        List<String> pk = new ArrayList<>();
        for (AppEntry e : pinned) pk.add(e.pkg);
        sp.edit().putString(KEY_PINNED, join(pk)).apply();
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.toString();
    }

    /** Apps del sistema que no aportan en "todas las apps": ota del control y la pantalla de configuración (ya existe "ajustes"). */
    private static boolean isHidden(String pkg, CharSequence label) {
        if (pkg.equals("com.realsil.ota") || pkg.equals("com.android.tv.settings")) return true;
        return label != null && label.toString().trim().equalsIgnoreCase("ota");
    }

    /** Nombre corto para mostrar. */
    private static String displayName(String pkg, String raw) {
        String l = raw.toLowerCase(Locale.ROOT);
        if (l.contains("xuper")) return "Xuper TV";
        if (pkg.startsWith("com.kick")) return "Kick";
        if (pkg.contains("bitchord")) return "BitChord";
        if (pkg.equals("com.directv.dtvlatam")) return "DirecTV Go";
        return raw;
    }

    /** Carga el banner (o icono) de cada app. Se llama fuera del hilo principal. */
    void loadCards(List<AppEntry> snapshot) {
        for (AppEntry e : snapshot) {
            if (cards.containsKey(e.pkg)) continue;
            try {
                ComponentName cn = e.cls == null ? null : new ComponentName(e.pkg, e.cls);
                Drawable d = null;
                boolean banner = true;
                if (cn != null) { try { d = pm.getActivityBanner(cn); } catch (Exception ignored) { } }
                if (d == null) { try { d = pm.getApplicationBanner(e.pkg); } catch (Exception ignored) { } }
                if (d == null) {
                    banner = false;
                    d = cn != null ? pm.getActivityIcon(cn) : pm.getApplicationIcon(e.pkg);
                }
                if (d == null) continue;
                int w = banner ? 320 : 128, h = banner ? 180 : 128;
                Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(b);
                d.setBounds(0, 0, w, h);
                d.draw(cv);
                cards.put(e.pkg, new Card(b, banner));
            } catch (Throwable ignored) { }
        }
    }

    /** Color de acento: tabla de marcas conocidas, luego el color dominante del icono, luego una paleta. */
    private int accentFor(String pkg, String label) {
        int known = knownAccent(pkg, label.toLowerCase(Locale.ROOT));
        if (known != 0) return known;
        String key = "ac_" + pkg;
        if (sp.contains(key)) return sp.getInt(key, 0);
        int c = 0;
        try { c = iconAccent(pkg); } catch (Throwable ignored) { }
        if (c == 0) c = 0xFF000000 | PALETTE[(pkg.hashCode() & 0x7fffffff) % PALETTE.length];
        sp.edit().putInt(key, c).apply();
        return c;
    }

    private static int knownAccent(String pkg, String l) {
        if (pkg.startsWith("com.netflix")) return 0xFFE50914;
        if (pkg.equals("com.google.android.youtube.tvmusic") || pkg.startsWith("com.google.android.apps.youtube.music")) return 0xFFFF2D55;
        if (pkg.startsWith("com.google.android.youtube")) return 0xFFFF3B30;
        if (pkg.equals("com.wbd.stream") || pkg.startsWith("com.hbo") || l.contains("hbo") || l.equals("max")) return 0xFF6C4CFF;
        if (l.contains("xuper")) return 0xFF2D9CFF;
        if (pkg.startsWith("com.disney")) return 0xFF3D7BFF;
        if (pkg.startsWith("com.amazon.amazonvideo")) return 0xFF00A8E1;
        if (pkg.startsWith("com.apple.atve")) return 0xFFC9C9CE;
        if (pkg.startsWith("com.directv")) return 0xFF0071BA;
        if (pkg.startsWith("tv.pluto")) return 0xFFFFE400;
        if (pkg.startsWith("com.kick")) return 0xFF53FC18;
        if (pkg.contains("bitchord")) return 0xFFFFD60A;
        if (pkg.startsWith("com.spotify")) return 0xFF1DB954;
        if (pkg.equals("com.android.mgstxNF")) return 0xFFF2C94C;
        if (pkg.equals("com.android.tv")) return 0xFF17A2D8;
        if (pkg.equals("com.android.vending")) return 0xFF34A853;
        if (pkg.equals("com.android.tv.settings")) return 0xFF7C5CE0;
        if (pkg.equals("com.rtk.mediabrowser")) return 0xFFF29F05;
        if (pkg.equals("com.esaba.downloader")) return 0xFF17A2A2;
        return 0;
    }

    /** Color dominante y saturado del banner o icono de la app; 0 si no hay uno claro (iconos blancos o grises). */
    private int iconAccent(String pkg) throws Exception {
        Drawable d = pm.getApplicationBanner(pkg);
        if (d == null) d = pm.getApplicationIcon(pkg);
        if (d == null) return 0;
        final int w = 48, h = 27;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        d.setBounds(0, 0, w, h);
        d.draw(cv);
        float[] hsv = new float[3];
        float[] wsum = new float[12];
        float[] rs = new float[12], gs = new float[12], bs = new float[12];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = bmp.getPixel(x, y);
                if (Color.alpha(px) < 128) continue;
                Color.colorToHSV(px, hsv);
                if (hsv[1] < .4f || hsv[2] < .4f) continue;
                int bin = Math.min(11, (int) (hsv[0] / 30f));
                float wt = hsv[1] * hsv[2];
                wsum[bin] += wt;
                rs[bin] += Color.red(px) * wt;
                gs[bin] += Color.green(px) * wt;
                bs[bin] += Color.blue(px) * wt;
            }
        }
        bmp.recycle();
        int best = 0;
        for (int i = 1; i < 12; i++) if (wsum[i] > wsum[best]) best = i;
        if (wsum[best] < 12f) return 0;
        int r = Math.round(rs[best] / wsum[best]);
        int g = Math.round(gs[best] / wsum[best]);
        int b = Math.round(bs[best] / wsum[best]);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
