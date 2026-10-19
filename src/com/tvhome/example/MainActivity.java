package com.tvhome.example;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // Top-row apps, in this order of preference:
    //  1. a list saved at install time:  am start -n com.tvhome.example/.MainActivity --es apps "pkg1,pkg2"
    //     (--es apps auto  goes back to 2; add --es stamp <unique> so it's applied only once, see saveConfig)
    //  2. apps.txt (built in as Apps.LIST), topped up with other TV apps installed on this TV.
    // Missing apps are skipped. Topping up stops at eight apps, but a chosen list can be any length:
    // apps wrap into rows of eight and the page scrolls down once they don't all fit.
    // On the TV, hold OK on an app (or press Menu) to move or remove it, or add another; that saves list 1.
    // The TMDB key for the trending row is saved the same way:  --es tmdb <key>
    private static final String PREFS = "config";
    private static final List<String> NOT_APPS = Arrays.asList("com.android.vending", "com.android.tv.settings",
            "com.android.tv", "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher");
    private static final String PLAY_STORE = "com.android.vending";
    private static final String SETTINGS = "settings";
    private static final String ADD = "add";
    private static final String POSTER = "poster:";
    private static final String[] EDIT_ITEMS = {"Mover", "Substituir", "Remover da tela inicial"};
    private static final int DEFAULT_COLS = 8, MIN_COLS = 4, MAX_COLS = 10, MAX_INPUTS = 4;
    private static final float[] TILE_SCALE = {0.85f, 1f, 1.2f}; // compact, normal, large
    private static final String CUSTOMIZE = "customize";
    private static final long WEATHER_EVERY = 30 * 60 * 1000L, TREND_EVERY = 6 * 60 * 60 * 1000L, RETRY = 5 * 60 * 1000L;

    // Layout is designed at 1920x1080 and scaled to the TV's real UI size by px().
    private static final int MARGIN = 96, GAP = 24, RADIUS = 28, TILE_H = 140, GRID_TOP = 340;
    private static final int POSTER_H = 292, CHIP_H = 64;
    private static final int TREND_HEAD = 58, TREND_BLOCK = TREND_HEAD + POSTER_H + 44; // heading + posters + gap
    private float scale = 1f;

    private static Typeface thin, light, regular, medium, semibold;

    private FrameLayout root; // the page, inside a vertical scroller
    private int tileW, tileH, gridTop, trendTop; // geometry, set by buildTiles
    private int perRow = DEFAULT_COLS;           // apps per row, from the saved layout
    private final List<View> tiles = new ArrayList<>();
    private final List<View> trendViews = new ArrayList<>(); // everything buildTrend added, to swap it out
    private final List<View> trendCards = new ArrayList<>();
    private boolean dirty = true;
    private String focusedKey;
    private final List<String> rowApps = new ArrayList<>(); // the app rows as shown, in order
    private String movingKey;   // the app being moved with the arrows, if any
    private TextView moveHint;  // shown while moving
    private boolean longPressed; // OK was held: swallow the key-up so the app doesn't open

    private Look look = Look.NIGHT;
    private Bitmap landscape;
    private TextView dateView, clockView, tempView, cityView, descView;
    private ImageView nowIcon;
    private final ImageView[] dayIcon = new ImageView[Weather.DAYS];
    private final TextView[] dayDow = new TextView[Weather.DAYS], dayTemp = new TextView[Weather.DAYS];
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.US);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR"));

    private Weather weather;
    private final List<Trending.Item> movies = new ArrayList<>(), shows = new ArrayList<>();
    private long weatherAt, weatherTry, trendAt, trendTry;
    private boolean weatherBusy, trendBusy;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { dirty = true; }
    };

    // Once a minute, on the minute: the clock, the night/morning switch, and data that has gone stale.
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateClock();
            Look want = Look.at(hourNow());
            if (want != look) {
                look = want;
                applyBackground();
                buildTiles();
            }
            refreshData(false);
            ui.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 300);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        saveConfig(getIntent());
        loadFonts(getAssets());
        scale = getResources().getDisplayMetrics().widthPixels / 1920f;
        look = Look.at(hourNow());
        applyBackground();
        loadCache();

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setClipChildren(false);
        root = new FrameLayout(this);
        root.setClipChildren(false);
        scroller.addView(root, new FrameLayout.LayoutParams(-1, -2));
        setContentView(scroller);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(packagesChanged, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(packagesChanged, f);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (saveConfig(intent)) {
            buildTiles();
            refreshData(true);
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, 0);
    }

    // Saves what an install or setup command passes: --es apps "...", --es tmdb <key>. Returns true if
    // something changed. When Android kills the home screen in the background, it can start it again with
    // the same old intent; the stamp install.sh sends stops that from undoing changes made on the TV since.
    private boolean saveConfig(Intent intent) {
        if (intent == null) return false;
        String apps = intent.getStringExtra("apps"), key = intent.getStringExtra("tmdb");
        if (apps == null && key == null) return false;
        SharedPreferences prefs = prefs();
        String stamp = intent.getStringExtra("stamp");
        if (stamp != null && stamp.equals(prefs.getString("stamp", null))) return false;
        SharedPreferences.Editor e = prefs.edit();
        if (stamp != null) e.putString("stamp", stamp);
        if (apps != null) {
            apps = apps.trim();
            if (apps.isEmpty() || apps.equalsIgnoreCase("auto")) e.remove("apps");
            else e.putString("apps", apps);
        }
        if (key != null) {
            key = key.trim();
            if (key.isEmpty()) e.remove("tmdb");
            else e.putString("tmdb", key);
            e.remove("tr_movies").remove("tr_shows").remove("tr_at");
            movies.clear();
            shows.clear();
            trendAt = 0;
        }
        e.apply();
        dirty = true;
        return true;
    }

    private List<String> appList(PackageManager pm) {
        List<String> out = new ArrayList<>();
        String saved = prefs().getString("apps", null);
        String[] wanted = saved != null ? saved.split("[,\\s]+") : Apps.LIST;
        for (String p : wanted) if (!p.isEmpty() && !out.contains(p) && launchIntent(pm, p) != null) out.add(p);
        if (saved == null) {
            // Top up with other TV apps people installed (or updated), skipping launchers and system tools.
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                if (out.size() >= DEFAULT_COLS) break;
                String p = ri.activityInfo.packageName;
                int flags = ri.activityInfo.applicationInfo.flags;
                boolean stockSystem = (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (stockSystem || out.contains(p) || NOT_APPS.contains(p) || p.equals(getPackageName())) continue;
                out.add(p);
            }
        }
        return out;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Rebuild only when apps were installed, removed or updated.
        if (dirty) {
            dirty = false;
            buildTiles();
        }
        View f = getCurrentFocus();
        if (f == null && !tiles.isEmpty()) tiles.get(0).requestFocus();
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    public void onBackPressed() { /* home screen: nothing to go back to */ }

    private static int hourNow() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
    }

    // The landscape for the current look, drawn once by the window: no extra view, no animation.
    private void applyBackground() {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        Bitmap b = Landscape.render(look.night, dm.widthPixels, dm.heightPixels);
        getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), b));
        if (landscape != null) landscape.recycle();
        landscape = b;
    }

    // ---- data: weather and trending ----

    private void loadCache() {
        SharedPreferences p = prefs();
        String w = p.getString("wx_raw", null);
        if (w != null) weather = Weather.parse(w);
        weatherAt = p.getLong("wx_at", 0);
        String m = p.getString("tr_movies", null), s = p.getString("tr_shows", null);
        if (m != null) movies.addAll(Trending.parse(m, false));
        if (s != null) shows.addAll(Trending.parse(s, true));
        trendAt = p.getLong("tr_at", 0);
    }

    // Fetches what is stale (or everything, when forced). A failed fetch keeps what was cached.
    private void refreshData(boolean force) {
        final long now = System.currentTimeMillis();
        if (!weatherBusy && (force || (now - weatherAt > WEATHER_EVERY && now - weatherTry > RETRY))) {
            weatherBusy = true;
            weatherTry = now;
            io.execute(() -> {
                final String raw = Weather.fetchRaw();
                final Weather w = raw == null ? null : Weather.parse(raw);
                ui.post(() -> {
                    weatherBusy = false;
                    if (w == null) return;
                    weather = w;
                    weatherAt = System.currentTimeMillis();
                    prefs().edit().putString("wx_raw", raw).putLong("wx_at", weatherAt).apply();
                    updateWeather();
                });
            });
        }
        final String key = prefs().getString("tmdb", null);
        boolean empty = movies.isEmpty() || shows.isEmpty();
        if (key != null && showTrending() && !trendBusy && (force || ((empty || now - trendAt > TREND_EVERY) && now - trendTry > RETRY))) {
            trendBusy = true;
            trendTry = now;
            io.execute(() -> {
                final String rawMovies = Trending.fetchRaw(key, false), rawShows = Trending.fetchRaw(key, true);
                final List<Trending.Item> gotMovies = rawMovies == null ? null : Trending.parse(rawMovies, false);
                final List<Trending.Item> gotShows = rawShows == null ? null : Trending.parse(rawShows, true);
                List<Trending.Item> all = new ArrayList<>();
                if (gotMovies != null) all.addAll(gotMovies);
                if (gotShows != null) all.addAll(gotShows);
                for (Trending.Item it : all) Trending.poster(getCacheDir(), it);
                if (!all.isEmpty()) Trending.prune(getCacheDir(), all);
                ui.post(() -> {
                    trendBusy = false;
                    SharedPreferences.Editor e = prefs().edit();
                    if (gotMovies != null && !gotMovies.isEmpty()) {
                        movies.clear();
                        movies.addAll(gotMovies);
                        e.putString("tr_movies", rawMovies);
                    }
                    if (gotShows != null && !gotShows.isEmpty()) {
                        shows.clear();
                        shows.addAll(gotShows);
                        e.putString("tr_shows", rawShows);
                    }
                    trendAt = System.currentTimeMillis();
                    e.putLong("tr_at", trendAt).apply();
                    buildTrend();
                });
            });
        }
    }

    private void updateClock() {
        if (clockView == null) return;
        Date now = new Date();
        clockView.setText(timeFmt.format(now));
        String d = dateFmt.format(now);
        dateView.setText(d.substring(0, 1).toUpperCase() + d.substring(1));
    }

    private void updateWeather() {
        if (tempView == null) return;
        if (weather == null) {
            tempView.setText("–");
            cityView.setText("Clima indisponível");
            descView.setText("Sem conexão");
            nowIcon.setImageDrawable(null);
            for (int i = 0; i < Weather.DAYS; i++) {
                dayIcon[i].setImageDrawable(null);
                dayDow[i].setText("");
                dayTemp[i].setText("");
            }
            return;
        }
        tempView.setText(weather.temp + "°");
        cityView.setText(weather.city);
        descView.setText(weather.description);
        nowIcon.setImageDrawable(new LineIcon(Weather.icon(weather.code)).tint(look.fg));
        for (int i = 0; i < Weather.DAYS; i++) {
            dayIcon[i].setImageDrawable(new LineIcon(Weather.icon(weather.dayCode[i])).tint(look.fg));
            dayDow[i].setText(weather.dow[i]);
            String hi = weather.max[i] + "°";
            SpannableString s = new SpannableString(hi + "  " + weather.min[i] + "°");
            s.setSpan(new ForegroundColorSpan(look.fg), 0, hi.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            dayTemp[i].setText(s);
        }
    }

    // ---- layout ----

    private void buildTiles() {
        // While tiles are swapped out, removing the focused one would hand focus down the row (and
        // overwrite focusedKey), so hold focus off until the new tiles are in.
        String want = focusedKey;
        root.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        root.removeAllViews();
        tiles.clear();
        trendViews.clear();
        trendCards.clear();
        rowApps.clear();
        movingKey = null;

        PackageManager pm = getPackageManager();
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int m = px(MARGIN), g = px(GAP);
        perRow = Math.max(MIN_COLS, Math.min(MAX_COLS, prefs().getInt("cols", DEFAULT_COLS)));
        int w = (screenW - 2 * m - g * (perRow - 1)) / perRow;
        int h = px(Math.round(TILE_H * tileScale()));
        tileW = w;
        tileH = h;
        gridTop = px(GRID_TOP);

        buildHeader(screenW);

        moveHint = text(px(24), look.fgMute, regular);
        moveHint.setText("Use as setas para mover e aperte OK");
        moveHint.setGravity(Gravity.CENTER_HORIZONTAL);
        moveHint.setVisibility(View.GONE);
        root.addView(moveHint, at(m, gridTop - px(48), screenW - 2 * m, -2));

        List<String> apps = appList(pm);
        int launchable = 0;
        for (String pkg : apps) if (launchIntent(pm, pkg) != null) launchable++;
        int rows = Math.max(1, (launchable + perRow - 1) / perRow);
        int trendTopD = GRID_TOP + rows * rowStepD() + 12;
        int footTopD = trendTopD + (showTrending() ? 2 * TREND_BLOCK + 36 : 0); // two rows of posters, then the pills
        trendTop = px(trendTopD);
        root.setMinimumHeight(px(footTopD + CHIP_H + 48));

        for (String pkg : apps) {
            Intent li = launchIntent(pm, pkg);
            if (li == null) continue;
            String label = labelOf(pkg);
            Drawable icon = null;
            try { icon = pm.getApplicationIcon(pkg); } catch (Exception ignored) {}
            int i = rowApps.size();
            rowApps.add(pkg);
            addAppTile(pkg, label, icon, gridX(i), gridY(i), w, h);
        }

        buildTrend();

        buildFooter(pm, apps, screenW, px(footTopD));

        updateClock();
        updateWeather();
        root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        View restore = null;
        for (View t : tiles) if (t.getTag().equals(want)) restore = t;
        if (restore != null) restore.requestFocus();
        if (!prefs().getBoolean("hint_seen", false)) {
            prefs().edit().putBoolean("hint_seen", true).apply();
            Toast.makeText(this, "Segure OK num app para mover ou remover. Em Personalizar você muda o layout.", Toast.LENGTH_LONG).show();
        }
    }

    // Date and clock on the left, the weather card on the right.
    private void buildHeader(int screenW) {
        int m = px(MARGIN);
        dateView = text(px(34), look.fgMute, regular);
        root.addView(dateView, at(m, px(72), -2, -2));
        clockView = text(px(150), look.fg, thin);
        clockView.setLetterSpacing(-0.025f);
        clockView.setIncludeFontPadding(false);
        clockView.setFontFeatureSettings("tnum");
        root.addView(clockView, at(m, px(118), -2, -2));

        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(30));
        bg.setColor(look.glass);
        bg.setStroke(Math.max(1, px(1)), look.glassEdge);
        card.setBackground(bg);
        nowIcon = new ImageView(this);
        card.addView(nowIcon, at(px(32), px(28), px(62), px(62)));
        tempView = text(px(72), look.fg, light);
        tempView.setIncludeFontPadding(false);
        card.addView(tempView, at(px(118), px(26), -2, -2));
        cityView = text(px(28), look.fg, semibold);
        cityView.setSingleLine(true);
        cityView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(cityView, at(px(276), px(26), px(320), -2));
        descView = text(px(24), look.fgMute, regular);
        descView.setSingleLine(true);
        descView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(descView, at(px(276), px(64), px(320), -2));
        View line = new View(this);
        line.setBackgroundColor(look.glassEdge);
        card.addView(line, at(px(32), px(122), px(556), Math.max(1, px(1))));
        for (int i = 0; i < Weather.DAYS; i++) {
            int x = px(32 + i * 190);
            dayIcon[i] = new ImageView(this);
            card.addView(dayIcon[i], at(x, px(148), px(34), px(34)));
            dayDow[i] = text(px(22), look.fg, semibold);
            card.addView(dayDow[i], at(x + px(46), px(138), -2, -2));
            dayTemp[i] = text(px(22), look.fgMute, regular);
            dayTemp[i].setSingleLine(true);
            card.addView(dayTemp[i], at(x + px(46), px(168), -2, -2));
        }
        int w = px(620);
        root.addView(card, at(screenW - m - w, px(72), w, px(238)));
    }

    // The trending rows, movies then series; swapped out whole when new ones arrive.
    private void buildTrend() {
        for (View v : trendViews) root.removeView(v);
        tiles.removeAll(trendCards);
        trendViews.clear();
        trendCards.clear();
        if (!showTrending()) return;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        if (movies.isEmpty() && shows.isEmpty()) {
            trendHeading("Em alta hoje", trendTop, screenW);
            TextView note = text(px(26), look.fgMute, regular);
            note.setText(prefs().getString("tmdb", null) == null
                    ? "Para ver sugestões, salve a chave do TMDB na TV."
                    : "Sugestões indisponíveis agora. Tentando de novo em instantes.");
            root.addView(note, at(px(MARGIN), trendTop + px(TREND_HEAD + 110), -2, -2));
            trendViews.add(note);
            return;
        }
        trendRow("Filmes em alta", movies, "m", trendTop, screenW);
        trendRow("Séries em alta", shows, "s", trendTop + px(TREND_BLOCK), screenW);
    }

    private void trendHeading(String title, int y, int screenW) {
        int m = px(MARGIN);
        TextView heading = text(px(30), look.fg, medium);
        heading.setText(title);
        root.addView(heading, at(m, y, -2, -2));
        TextView via = text(px(22), look.fgMute, regular);
        via.setText("Abre no Stremio");
        via.setGravity(Gravity.END);
        root.addView(via, at(m, y + px(8), screenW - 2 * m, -2));
        trendViews.add(heading);
        trendViews.add(via);
    }

    // One row of posters under its heading. kind ("m" or "s") is part of each poster's key.
    // A poster shows only its rating until it has focus; then its title and year slide over the picture.
    private void trendRow(String title, List<Trending.Item> list, String kind, int y, int screenW) {
        if (list.isEmpty()) return;
        trendHeading(title, y, screenW);
        int posterY = y + px(TREND_HEAD);
        int h = px(POSTER_H);
        for (int i = 0; i < list.size(); i++) {
            Trending.Item it = list.get(i);
            FrameLayout card = card(POSTER + kind + i, px(24), false, false);
            card.setBackground(posterBackground(i));
            Bitmap bmp = decodePoster(it);
            if (bmp != null) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(bmp);
                card.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            }
            final View scrim = new View(this);
            GradientDrawable sg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x00070A12, 0xE6070A12});
            scrim.setBackground(sg);
            card.addView(scrim, new FrameLayout.LayoutParams(-1, h * 7 / 10, Gravity.BOTTOM));

            final LinearLayout caption = new LinearLayout(this);
            caption.setOrientation(LinearLayout.VERTICAL);
            caption.setPadding(px(16), 0, px(16), px(16));
            TextView name = text(px(22), 0xFFF3F6FC, semibold);
            name.setText(it.title);
            name.setMaxLines(3);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE); // wrap between words, not inside them
            name.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
            caption.addView(name);
            if (!it.year.isEmpty()) {
                TextView meta = text(px(19), 0xCCF3F6FC, regular);
                meta.setText(it.year);
                caption.addView(meta);
            }
            card.addView(caption, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            scrim.setVisibility(View.INVISIBLE);
            caption.setVisibility(View.INVISIBLE);
            final View.OnFocusChangeListener base = card.getOnFocusChangeListener();
            card.setOnFocusChangeListener((v, has) -> {
                base.onFocusChange(v, has);
                scrim.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
                caption.setVisibility(has ? View.VISIBLE : View.INVISIBLE);
            });

            if (it.rating > 0) {
                TextView r = text(px(19), 0xFFF3F6FC, semibold);
                r.setText(String.format(Locale.US, "★ %.1f", it.rating));
                r.setPadding(px(11), px(5), px(11), px(5));
                GradientDrawable rb = new GradientDrawable();
                rb.setCornerRadius(px(40));
                rb.setColor(0x8C080B10);
                r.setBackground(rb);
                FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
                rp.setMargins(0, px(16), px(16), 0);
                card.addView(r, rp);
            }
            View wrap = place(card, gridX(i), posterY, tileW, h);
            trendViews.add(wrap);
            trendCards.add(card);
        }
    }

    // A quiet colour behind each poster until its picture loads (or when it can't).
    private Drawable posterBackground(int i) {
        int[][] pairs = {{0xFF3B4A7A, 0xFF16203A}, {0xFF7A3B4A, 0xFF2A121B}, {0xFF3B7A63, 0xFF10261F}, {0xFF7A6A3B, 0xFF272010},
                {0xFF5A3B7A, 0xFF1D1230}, {0xFF3B6D7A, 0xFF10232A}, {0xFF7A4E3B, 0xFF2A1A12}, {0xFF3B5A7A, 0xFF101C2A}};
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, pairs[i % pairs.length]);
        g.setCornerRadius(px(24));
        g.setStroke(Math.max(1, px(1)), look.glassEdge);
        return g;
    }

    // Decodes a cached poster at the size it is shown, in 565 to halve the memory.
    private Bitmap decodePoster(Trending.Item it) {
        File f = new File(getCacheDir(), "poster" + it.poster.replace('/', '_'));
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= tileW) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Throwable t) {
            return null;
        }
    }

    // One pill per input and shortcut: Play Store and the HDMI inputs on the left, Settings and
    // Add app on the right. When they don't all fit, the last inputs on the left are dropped.
    private void buildFooter(PackageManager pm, List<String> apps, int screenW, int y) {
        List<Chip> left = new ArrayList<>(), right = new ArrayList<>();
        if (!apps.contains(PLAY_STORE) && launchIntent(pm, PLAY_STORE) != null) left.add(new Chip(PLAY_STORE, "Play Store", LineIcon.PLAY));
        if (prefs().getBoolean("show_inputs", true))
            for (TvInputInfo in : hdmiInputs()) left.add(new Chip(in.getId(), inputLabel(in), LineIcon.HDMI));
        right.add(new Chip(CUSTOMIZE, "Personalizar", LineIcon.SETTINGS));
        right.add(new Chip(SETTINGS, "Configurações", LineIcon.SETTINGS));
        right.add(new Chip(ADD, "Adicionar app", LineIcon.PLUS));

        Paint mp = new Paint();
        mp.setTypeface(medium);
        mp.setTextSize(px(23));
        int gap = px(18), avail = screenW - 2 * px(MARGIN);
        for (Chip c : left) c.w = chipWidth(mp, c);
        for (Chip c : right) c.w = chipWidth(mp, c);
        while (left.size() > 1 && total(left, gap) + total(right, gap) + gap > avail) left.remove(left.size() - 1);

        int x = px(MARGIN);
        for (Chip c : left) { addChip(c, x, y); x += c.w + gap; }
        x = screenW - px(MARGIN) - total(right, gap);
        for (Chip c : right) { addChip(c, x, y); x += c.w + gap; }
    }

    private static final class Chip {
        final String key, label;
        final int[] icon;
        int w;

        Chip(String key, String label, int[] icon) {
            this.key = key;
            this.label = label;
            this.icon = icon;
        }
    }

    private int chipWidth(Paint p, Chip c) {
        return px(30) * 2 + px(27) + px(12) + (int) Math.ceil(p.measureText(c.label));
    }

    private static int total(List<Chip> chips, int gap) {
        int t = 0;
        for (Chip c : chips) t += c.w + gap;
        return Math.max(0, t - gap);
    }

    private void addChip(Chip c, int x, int y) {
        FrameLayout card = card(c.key, px(CHIP_H / 2), true, false);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(new LineIcon(c.icon).tint(look.fgMute));
        inner.addView(iv, new LinearLayout.LayoutParams(px(27), px(27)));
        TextView t = text(px(23), look.fg, medium);
        t.setText(c.label);
        t.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(12);
        inner.addView(t, lp);
        card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        place(card, x, y, c.w, px(CHIP_H));
    }

    private void addAppTile(String key, String label, Drawable appIcon, int x, int y, int w, int h) {
        FrameLayout card = card(key, px(RADIUS), true, rowApps.contains(key));
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        int size = px(Math.round(64 * tileScale()));
        ImageView iv = new ImageView(this);
        if (appIcon != null) iv.setImageDrawable(fit(appIcon, size, size));
        inner.addView(iv, new LinearLayout.LayoutParams(size, size));
        TextView t = text(px(24), look.fg, medium);
        t.setText(label);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setGravity(Gravity.CENTER);
        t.setPadding(px(12), 0, px(12), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = px(10);
        inner.addView(t, lp);
        card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        place(card, x, y, w, h);
    }

    // A focusable glass card with the focus ring, the click and the hold-OK / Menu behaviour.
    // fill: the card has a translucent fill that brightens on focus (posters bring their own picture).
    private FrameLayout card(final String key, final int radius, final boolean fill, final boolean editable) {
        FrameLayout card = new FrameLayout(this);
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(radius);
        bg.setColor(fill ? look.glass : Color.TRANSPARENT);
        bg.setStroke(Math.max(1, px(1)), look.glassEdge);
        final GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(radius);
        ring.setStroke(px(4), look.focus);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setTag(key);

        card.setOnFocusChangeListener((v, has) -> {
            if (has) focusedKey = key;
            if (fill) bg.setColor(has ? look.glassOn : look.glass);
            if (!key.equals(movingKey)) ((FrameLayout) v).setForeground(has ? ring : null); // moving keeps its outline
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).translationZ(has ? px(26) : 0)
                    .setDuration(140).withLayer().start();
        });
        card.setOnClickListener(v -> open(key));
        if (editable) card.setOnLongClickListener(v -> { showEditMenu(key); return true; });
        card.setOnKeyListener((v, code, ev) -> {
            boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            boolean down = ev.getAction() == KeyEvent.ACTION_DOWN;
            if (key.equals(movingKey)) return moveKey(key, code, ok, down);
            if (editable && code == KeyEvent.KEYCODE_MENU) {
                if (!down) showEditMenu(key);
                return true;
            }
            if (!ok) return false;
            if (down) {
                if (ev.getRepeatCount() == 0) longPressed = false;
                else if (editable && !longPressed && (ev.isLongPress()
                        || ev.getEventTime() - ev.getDownTime() >= ViewConfiguration.getLongPressTimeout())) {
                    longPressed = true;
                    showEditMenu(key);
                }
            } else if (!longPressed) {
                open(key);
            }
            return true;
        });
        return card;
    }

    // Puts a card on the page inside its own wrapper (the move mode slides the wrapper). Returns the wrapper.
    private View place(FrameLayout card, int x, int y, int w, int h) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.addView(card, new FrameLayout.LayoutParams(w, h));
        root.addView(wrap, at(x, y, w, -2));
        tiles.add(card);
        return wrap;
    }

    private void open(String key) {
        Intent i;
        if (ADD.equals(key)) {
            showAddMenu(-1);
            return;
        } else if (CUSTOMIZE.equals(key)) {
            showCustomize();
            return;
        } else if (key.startsWith(POSTER)) {
            String rest = key.substring(POSTER.length()); // "m3": movie number 3, "s0": series number 0
            List<Trending.Item> list = rest.charAt(0) == 's' ? shows : movies;
            int index = Integer.parseInt(rest.substring(1));
            if (index < list.size()) openTitle(list.get(index));
            return;
        } else if (SETTINGS.equals(key)) {
            i = new Intent(Settings.ACTION_SETTINGS);
        } else if (isInput(key)) {
            openInput(key);
            return;
        } else {
            i = launchIntent(getPackageManager(), key);
            if (i == null) return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) {}
    }

    // Opens a trending title in Stremio, by its IMDb id.
    private void openTitle(final Trending.Item it) {
        final String key = prefs().getString("tmdb", null);
        io.execute(() -> {
            final String imdb = key == null ? null : Trending.imdbId(key, it);
            ui.post(() -> {
                if (imdb == null || imdb.isEmpty()) {
                    Toast.makeText(this, "Não foi possível abrir este título agora.", Toast.LENGTH_LONG).show();
                    return;
                }
                String path = it.series ? "series/" + imdb : "movie/" + imdb + "/" + imdb;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("stremio:///detail/" + path))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, "Instale o Stremio pela Play Store para abrir os títulos.", Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    // Live TV apps an HDMI tile can open, in order. Droidlogic (Amlogic) TVs ship Live TV under their
    // own package, with TvActivity as its entry point.
    private static final String[][] LIVE_TV = {{"com.android.tv", "com.android.tv.MainActivity"},
            {"com.droidlogic.android.tv", "com.android.tv.TvActivity"}};

    // Opens an HDMI input: the stock Live TV app first, then Droidlogic's, then whatever the TV picks.
    // Each one is tried only when the one before it can't start, so a TV where the stock app already
    // worked takes exactly the same path as before.
    private void openInput(String id) {
        Intent view = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id));
        List<Intent> tries = new ArrayList<>();
        for (String[] live : LIVE_TV) {
            Intent t = new Intent(view).setClassName(live[0], live[1]);
            if (t.resolveActivity(getPackageManager()) != null) tries.add(t);
        }
        tries.add(view);
        for (Intent t : tries) {
            t.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(t);
                return;
            } catch (Exception ignored) {}
        }
    }

    interface Pick { void at(int which); }

    // A side panel in the home screen's own look: a title, then one row per choice, with the same
    // glass focus as the tiles. Back closes it. iconPkgs (or null) puts each app's icon by its name.
    private void panel(String title, final List<String> labels, final List<String> iconPkgs, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(48), px(92), px(48), px(48));

        TextView head = text(px(40), look.fg, semibold);
        head.setText(title);
        head.setSingleLine(true);
        head.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.setPadding(px(24), 0, px(24), 0);
        box.addView(head);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable focus = new GradientDrawable();
        focus.setCornerRadius(px(RADIUS));
        focus.setColor(look.glassOn);
        focus.setStroke(px(3), look.focus);
        list.setSelector(focus);
        // Icons load as rows appear, drawn small to save RAM.
        final PackageManager pm = getPackageManager();
        final int size = px(48);
        final Drawable[] icons = new Drawable[labels.size()];
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return labels.size(); }
            @Override public Object getItem(int pos) { return labels.get(pos); }
            @Override public long getItemId(int pos) { return pos; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                TextView t = convert != null ? (TextView) convert : text(px(30), look.fg, regular);
                t.setText(labels.get(pos));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setGravity(Gravity.CENTER_VERTICAL);
                t.setMinHeight(px(88));
                t.setPadding(px(24), 0, px(24), 0);
                Drawable icon = null;
                if (iconPkgs != null) {
                    if (icons[pos] == null) {
                        try { icons[pos] = fit(pm.getApplicationIcon(iconPkgs.get(pos)), size, size); }
                        catch (Exception e) { icons[pos] = new ColorDrawable(Color.TRANSPARENT); }
                        icons[pos].setBounds(0, 0, size, size);
                    }
                    icon = icons[pos];
                }
                t.setCompoundDrawablesRelative(icon, null, null, null);
                t.setCompoundDrawablePadding(px(24));
                return t;
            }
        });
        list.setOnItemClickListener((parent, v, pos, id) -> {
            d.dismiss();
            pick.at(pos);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1f);
        lp.topMargin = px(36);
        box.addView(list, lp);

        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(look.panel));
        w.setLayout(px(640), -1);
        w.setGravity(Gravity.END);
        w.setDimAmount(0.4f);
        d.show();
    }

    // A small menu that drops down under a tile, in the same glass as the tiles. Opens above the
    // tile when there's no room below. Back closes it.
    private void dropdown(View anchor, String[] items, final Pick pick) {
        final Dialog d = new Dialog(this, android.R.style.Theme_Material_Dialog_NoActionBar);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = px(10), rowH = px(64);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(px(RADIUS));
        bg.setColor(look.panel);
        bg.setStroke(Math.max(1, px(2)), look.glassEdge);
        box.setBackground(bg);
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            final TextView t = text(px(26), look.fgMute, regular);
            t.setText(items[i]);
            t.setSingleLine(true);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(px(22), 0, px(22), 0);
            final GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(px(RADIUS - pad));
            focus.setColor(Color.TRANSPARENT);
            t.setBackground(focus);
            t.setFocusable(true);
            t.setOnFocusChangeListener((v, has) -> {
                focus.setColor(has ? look.glassOn : Color.TRANSPARENT);
                t.setTextColor(has ? look.fg : look.fgMute);
            });
            t.setOnClickListener(v -> {
                d.dismiss();
                pick.at(which);
            });
            box.addView(t, new LinearLayout.LayoutParams(-1, rowH));
        }
        d.setContentView(box);

        // Line it up with the tile as drawn (focused tiles are scaled up), just below it.
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        int tileBottom = at[1] + Math.round(anchor.getHeight() * anchor.getScaleY());
        int w = Math.max(Math.round(anchor.getWidth() * anchor.getScaleX()), px(320));
        int h = items.length * rowH + 2 * pad;
        int y = tileBottom + px(12);
        if (y + h > getResources().getDisplayMetrics().heightPixels - px(24)) y = at[1] - h - px(16);
        Window win = d.getWindow();
        win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        win.setGravity(Gravity.TOP | Gravity.START);
        android.view.WindowManager.LayoutParams lp = win.getAttributes();
        lp.x = at[0];
        lp.y = y;
        lp.width = w;
        lp.height = h;
        win.setAttributes(lp);
        d.show();
    }

    // Hold OK (or Menu) on an app: move it, swap it for another, or take it off the home
    // screen. Removing only takes the tile away: the app stays installed and can be added back.
    private void showEditMenu(final String key) {
        final int index = rowApps.indexOf(key);
        if (index < 0) return;
        dropdown(tile(key), EDIT_ITEMS, which -> {
            if (which == 0) startMove(key);
            else if (which == 1) showAddMenu(index);
            else {
                rowApps.remove(index);
                saveRow();
                focusedKey = rowApps.isEmpty() ? ADD : rowApps.get(Math.min(index, rowApps.size() - 1));
                buildTiles();
            }
        });
    }

    // Every launchable app that isn't on the home screen yet, by name. The pick is added at the end,
    // or takes the place of the app at replaceIndex (-1 to add).
    private void showAddMenu(final int replaceIndex) {
        final PackageManager pm = getPackageManager();
        TreeMap<String, String> byLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String category : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (rowApps.contains(p) || byLabel.containsValue(p) || p.equals(getPackageName())
                        || (NOT_APPS.contains(p) && !p.equals(PLAY_STORE))) continue;
                String label = labelOf(p);
                if (!byLabel.containsKey(label)) byLabel.put(label, p);
            }
        }
        if (byLabel.isEmpty()) {
            panel("Todos os apps já estão aqui", Collections.singletonList("OK"), null, which -> {});
            return;
        }
        final List<String> pkgs = new ArrayList<>(byLabel.values());
        String title = replaceIndex < 0 ? "Adicionar um app" : "Substituir " + labelOf(rowApps.get(replaceIndex));
        panel(title, new ArrayList<>(byLabel.keySet()), pkgs, which -> {
            if (replaceIndex < 0) rowApps.add(pkgs.get(which));
            else rowApps.set(replaceIndex, pkgs.get(which));
            saveRow();
            focusedKey = pkgs.get(which);
            buildTiles();
        });
    }

    private float tileScale() {
        return TILE_SCALE[Math.max(0, Math.min(TILE_SCALE.length - 1, prefs().getInt("size", 1)))];
    }

    // Distance between app rows, in design pixels: the tile plus the gap.
    private int rowStepD() {
        return Math.round(TILE_H * tileScale()) + GAP;
    }

    private boolean showTrending() {
        return prefs().getBoolean("show_trending", true);
    }

    // Layout choices. Each pick changes one setting and the menu opens again with the new values.
    private void showCustomize() {
        final SharedPreferences p = prefs();
        String[] sizes = {"Compacto", "Normal", "Grande"};
        List<String> labels = Arrays.asList(
                "Apps por linha: " + p.getInt("cols", DEFAULT_COLS),
                "Tamanho dos ícones: " + sizes[Math.max(0, Math.min(sizes.length - 1, p.getInt("size", 1)))],
                "Filmes e séries em alta: " + (showTrending() ? "Sim" : "Não"),
                "Entradas HDMI: " + (p.getBoolean("show_inputs", true) ? "Sim" : "Não"),
                "Restaurar o padrão");
        panel("Personalizar", labels, null, which -> {
            SharedPreferences.Editor e = p.edit();
            if (which == 0) e.putInt("cols", p.getInt("cols", DEFAULT_COLS) >= MAX_COLS ? MIN_COLS : p.getInt("cols", DEFAULT_COLS) + 1);
            else if (which == 1) e.putInt("size", (p.getInt("size", 1) + 1) % TILE_SCALE.length);
            else if (which == 2) e.putBoolean("show_trending", !showTrending());
            else if (which == 3) e.putBoolean("show_inputs", !p.getBoolean("show_inputs", true));
            else e.remove("cols").remove("size").remove("show_trending").remove("show_inputs");
            e.apply();
            focusedKey = CUSTOMIZE;
            buildTiles();
            refreshData(false);
            showCustomize();
        });
    }

    private String labelOf(String pkg) {
        PackageManager pm = getPackageManager();
        try { return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }

    // Saves the app rows as the app list, so it no longer follows apps.txt.
    private void saveRow() {
        prefs().edit().putString("apps", android.text.TextUtils.join(",", rowApps)).apply();
    }

    // Where app number i sits in the grid.
    private int gridX(int i) {
        return px(MARGIN) + (i % perRow) * (tileW + px(GAP));
    }

    private int gridY(int i) {
        return gridTop + (i / perRow) * px(rowStepD());
    }

    private View tile(String key) {
        for (View t : tiles) if (t.getTag().equals(key)) return t;
        return null;
    }

    // Move mode: the tile gets an outline, the arrows move it through the grid, OK or Back finishes.
    private void startMove(String key) {
        View card = tile(key);
        if (card == null) return;
        movingKey = key;
        GradientDrawable outline = new GradientDrawable();
        outline.setCornerRadius(px(RADIUS));
        outline.setStroke(px(4), look.fg);
        ((FrameLayout) card).setForeground(outline);
        moveHint.setVisibility(View.VISIBLE);
        card.requestFocus();
    }

    private boolean moveKey(String key, int code, boolean ok, boolean down) {
        int step = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : code == KeyEvent.KEYCODE_DPAD_RIGHT ? 1
                : code == KeyEvent.KEYCODE_DPAD_UP ? -perRow : code == KeyEvent.KEYCODE_DPAD_DOWN ? perRow : 0;
        if (step != 0) {
            int from = rowApps.indexOf(key), to = from + step;
            if (down && to >= 0 && to < rowApps.size()) {
                // Slide the tiles into their new places: no rebuild, so it stays quick on slow TVs.
                rowApps.add(to, rowApps.remove(from));
                for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                    View wrap = (View) tile(rowApps.get(i)).getParent();
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) wrap.getLayoutParams();
                    lp.leftMargin = gridX(i);
                    lp.topMargin = gridY(i);
                    wrap.setLayoutParams(lp);
                }
                // Scroll along with it once it has moved.
                final View card = tile(key);
                card.post(() -> card.requestRectangleOnScreen(
                        new android.graphics.Rect(0, 0, card.getWidth(), card.getHeight())));
            }
            return true;
        }
        if (ok || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (!down) {
                movingKey = null;
                longPressed = false;
                FrameLayout card = (FrameLayout) tile(key);
                card.setForeground(null);
                card.getOnFocusChangeListener().onFocusChange(card, card.hasFocus());
                moveHint.setVisibility(View.GONE);
                saveRow();
            }
            return true;
        }
        return true; // everything else stays put while moving
    }

    private static boolean isInput(String key) {
        return key.indexOf('/') > 0;
    }

    // The TV's own HDMI inputs (not devices behind them), in order, skipping hidden ones.
    private List<TvInputInfo> hdmiInputs() {
        List<TvInputInfo> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        for (TvInputInfo in : tim.getTvInputList()) {
            if (in.getType() == TvInputInfo.TYPE_HDMI && in.getParentId() == null && !in.isHidden(this)) out.add(in);
        }
        Collections.sort(out, (a, b) -> a.getId().compareTo(b.getId()));
        return out.size() > MAX_INPUTS ? out.subList(0, MAX_INPUTS) : out;
    }

    // The name set in Settings > Inputs (like "Bedroom"), else the TV's own name (like "HDMI 1").
    private String inputLabel(TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) return custom.toString();
        CharSequence label = in.loadLabel(this);
        return label != null && label.length() > 0 ? label.toString() : "HDMI";
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // Urbanist (bundled, one variable font) where the TV can use it, else the TV's own sans-serif.
    private static void loadFonts(AssetManager am) {
        if (regular != null) return;
        thin = urbanist(am, 200, Typeface.create("sans-serif-light", Typeface.NORMAL));
        light = urbanist(am, 300, Typeface.create("sans-serif-light", Typeface.NORMAL));
        regular = urbanist(am, 400, Typeface.create("sans-serif", Typeface.NORMAL));
        medium = urbanist(am, 500, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        semibold = urbanist(am, 600, Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private static Typeface urbanist(AssetManager am, int weight, Typeface fallback) {
        if (android.os.Build.VERSION.SDK_INT < 26) return fallback;
        try {
            Typeface t = new Typeface.Builder(am, "Urbanist.ttf")
                    .setFontVariationSettings("'wght' " + weight).setWeight(weight).build();
            return t != null ? t : fallback;
        } catch (Throwable e) {
            return fallback;
        }
    }

    // Draws an icon once into a tile-sized bitmap (center-cropped). Some apps ship huge icons
    // that Android 9 refuses to draw ("Canvas: trying to draw too large bitmap"); this also saves RAM.
    private Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}
