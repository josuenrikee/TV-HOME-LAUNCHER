package ec.josue.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static final int[] PALETTE = {
        0x3D7BFF, 0x1FA866, 0x8A4DFF, 0x00A8E1, 0xF2C94C, 0xF29F05, 0xFF8A00, 0xD14DFF, 0x17A2D8, 0x34A8A0
    };

    private final Context ctx;
    private final PackageManager pm;
    private final SharedPreferences sp;

    final List<AppEntry> installed = new ArrayList<>();
    final List<AppEntry> pinned = new ArrayList<>();
    boolean weather;
    boolean greeting;
    boolean anim;

    AppStore(Context ctx) {
        this.ctx = ctx;
        this.pm = ctx.getPackageManager();
        this.sp = ctx.getSharedPreferences("tvhome", Context.MODE_PRIVATE);
        weather = sp.getBoolean(KEY_WEATHER, true);
        greeting = sp.getBoolean(KEY_GREETING, true);
        anim = sp.getBoolean(KEY_ANIM, true);
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
                if (pkg.equals(ctx.getPackageName()) || byPkg.containsKey(pkg)) continue;
                CharSequence label = ri.loadLabel(pm);
                AppEntry e = new AppEntry(pkg, label == null ? pkg : label.toString(), accentFor(pkg, label == null ? "" : label.toString()));
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

    private static int accentFor(String pkg, String label) {
        String l = label.toLowerCase(Locale.ROOT);
        if (pkg.startsWith("com.netflix")) return 0xFFE50914;
        if (pkg.startsWith("com.google.android.youtube")) return 0xFFFF5A36;
        if (pkg.startsWith("com.disney")) return 0xFF3D7BFF;
        if (l.contains("xuper")) return 0xFF1FA866;
        if (pkg.equals("com.wbd.stream") || l.equals("max")) return 0xFF8A4DFF;
        if (pkg.startsWith("com.amazon.amazonvideo")) return 0xFF00A8E1;
        int h = pkg.hashCode();
        return 0xFF000000 | PALETTE[(h & 0x7fffffff) % PALETTE.length];
    }
}
