package dev.hepta.tvcat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.TabStopSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Scanner;

public class CatService extends Service {
    static final long TICK_MS = 60_000;
    static final long SHOW_MS = 8_000;
    static final int MAX_DP = 56, MIN_DP = 24, DEFAULT_DP = 34; // default = 30% of the range
    static final int DEFAULT_POS = Gravity.BOTTOM | Gravity.END, DEFAULT_FADE = 15, MAX_FADE = 90; // ponytail: 90 cap so the cat can't vanish; "Show cat" toggle is for that
    static final int LOUD_PCT = 80, LOUD_LATE_PCT = 50, HOME_NUDGE_MIN = 5;
    static final int[] FACES = {R.drawable.cat_happy, R.drawable.cat_meh, R.drawable.cat_angry, R.drawable.cat_furious};
    static final String[] BED_NAMES = {"off", "20:00", "21:00", "22:00", "23:00", "00:00", "01:00"};
    static final int[] BED_HOURS = {-1, 20, 21, 22, 23, 0, 1};
    static final int INK = 0xFF2B2D42, LABEL = 0x992B2D42; // card text; labels are the same ink at 60%

    static CatService me; // ponytail: static handle so NotifListener can reach the bubble; a bound service if this grows

    final Handler h = new Handler(Looper.getMainLooper());
    final Streak streak = new Streak();
    WindowManager wm;
    LinearLayout root, bubble;
    TextView head, rows;
    ImageView cat;
    SharedPreferences prefs;
    String launcherPkg;
    long lastTick, weatherAt;
    volatile String weather;
    boolean loud, offline;

    @Override public void onCreate() {
        super.onCreate();
        me = this;
        startForeground(1, buildNotification());
        prefs = getSharedPreferences("cat", MODE_PRIVATE);
        streak.minutes = prefs.getInt("min", 0);
        lastTick = prefs.getLong("last", 0);
        android.content.pm.ResolveInfo home = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        launcherPkg = home == null ? "" : home.activityInfo.packageName;
        buildOverlay();
        h.postDelayed(tick, TICK_MS);
    }

    final Runnable tick = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            long gap = lastTick == 0 ? TICK_MS : now - lastTick;
            boolean newHour = lastTick != 0 && hour(now) != hour(lastTick);
            lastTick = now;
            boolean on = getSystemService(PowerManager.class).isInteractive();
            String fg = on ? foregroundPkg() : null;
            // ponytail: no usage access => we only know screen is on; treat as watching
            boolean watching = on && (fg == null || !fg.equals(launcherPkg));
            streak.lateHours = lateHours(now);
            tally(watching, fg);
            if (streak.tick(watching, gap)) say(streak.label(), rows(fg, false), FACES[streak.mood()], SHOW_MS);
            else if (newHour && on && hour(now) == prefs.getInt("bed", -1)) say("It's " + time(now) + ". Bedtime!", R.drawable.cat_sleepy, SHOW_MS);
            else if (newHour && on) say(null, rows(fg, true), FACES[streak.mood()], SHOW_MS); // hourly chime
            else if (on && launcherPkg.equals(fg) && streak.idleMinutes == HOME_NUDGE_MIN) say(HOME_NUDGE_MIN + " min on the home screen. Pick something!", R.drawable.cat_meh, SHOW_MS);
            checkVolume();
            checkNet();
            if (prefs.getBoolean("weather", false) && now - weatherAt > 3_600_000) fetchWeather();
            prefs.edit().putInt("min", streak.minutes).putLong("last", now).apply();
            h.postDelayed(this, TICK_MS);
        }
    };

    String foregroundPkg() {
        UsageStatsManager usm = getSystemService(UsageStatsManager.class);
        long now = System.currentTimeMillis();
        UsageEvents ev = usm.queryEvents(now - 30 * 60_000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String pkg = null;
        while (ev.getNextEvent(e)) {
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) pkg = e.getPackageName();
        }
        return pkg;
    }

    /** Daily total and per-app minutes in prefs ("dayMin", "app:<pkg>"); cleared on the first tick of a new day. */
    void tally(boolean watching, String fg) {
        long today = LocalDate.now().toEpochDay();
        if (prefs.getLong("day", 0) != today) {
            SharedPreferences.Editor e = prefs.edit().putLong("day", today).putInt("dayMin", 0);
            for (String k : prefs.getAll().keySet()) if (k.startsWith("app:")) e.remove(k);
            e.apply();
        }
        if (!watching) return;
        SharedPreferences.Editor e = prefs.edit().putInt("dayMin", prefs.getInt("dayMin", 0) + 1);
        if (fg != null) e.putInt("app:" + fg, prefs.getInt("app:" + fg, 0) + 1);
        e.apply();
    }

    /** Card rows as "label\tvalue" lines: Time, Today, Streak (chime only; the milestone title already says it), the app, Weather. */
    String rows(String fg, boolean withStreak) {
        StringBuilder s = new StringBuilder("Time\t").append(time(lastTick)).append("\nToday\t").append(Streak.fmt(prefs.getInt("dayMin", 0)));
        if (withStreak && streak.minutes > 0) s.append("\nStreak\t").append(Streak.fmt(streak.minutes));
        if (fg != null && !fg.equals(launcherPkg)) s.append('\n').append(appName(fg)).append('\t').append(Streak.fmt(prefs.getInt("app:" + fg, 0)));
        if (weather != null) s.append("\nWeather\t").append(weather);
        return s.toString();
    }

    String appName(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg.substring(pkg.lastIndexOf('.') + 1); }
    }

    int hour(long ms) { return Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).getHour(); }
    String time(long ms) { ZonedDateTime t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()); return String.format("%02d:%02d", t.getHour(), t.getMinute()); }

    int lateHours(long now) {
        int bed = prefs.getInt("bed", -1);
        if (bed < 0) return 0;
        int past = (hour(now) - bed + 24) % 24;
        return past < 12 ? past + 1 : 0; // ponytail: "late" = within 12 h after bedtime
    }

    void checkVolume() {
        AudioManager am = getSystemService(AudioManager.class);
        if (am.isVolumeFixed()) return; // ponytail: HDMI/CEC TVs may report a fixed volume; nothing to warn about then
        int pct = am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        boolean l = pct >= (streak.lateHours > 0 ? LOUD_LATE_PCT : LOUD_PCT);
        if (l && !loud) say("Loud! My ears!", R.drawable.cat_ears, SHOW_MS);
        loud = l;
    }

    void checkNet() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network net = cm.getActiveNetwork();
        NetworkCapabilities nc = cm.getNetworkCapabilities(net);
        boolean off = nc == null || !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (off) say("No internet!", R.drawable.cat_meh, 0); // sticky until back online
        else if (offline) hide.run();
        // ponytail: Android re-probes an unvalidated network with backoff up to 10 min; asking it to re-check every tick clears the bubble within a minute of being back online
        if (off && net != null) cm.reportNetworkConnectivity(net, true);
        offline = off;
    }

    void fetchWeather() {
        weatherAt = System.currentTimeMillis();
        new Thread(() -> {
            try {
                JSONObject geo = new JSONObject(get("https://get.geojs.io/v1/ip/geo.json"));
                JSONObject w = new JSONObject(get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code&latitude="
                        + geo.getString("latitude") + "&longitude=" + geo.getString("longitude"))).getJSONObject("current");
                int c = w.getInt("weather_code"); // WMO codes
                String sky = c == 0 ? "clear" : c <= 3 ? "cloudy" : c <= 48 ? "fog" : c <= 67 ? "rain" : c <= 77 ? "snow" : c <= 82 ? "showers" : "storm";
                weather = Math.round(w.getDouble("temperature_2m")) + "°C " + sky;
            } catch (Exception e) { weather = null; } // ponytail: offline or API down => no weather in the bubble, retry next hour
        }).start();
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        try (InputStream in = c.getInputStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) { return s.next(); }
    }

    /** Show the card: bold title and/or "label\tvalue" rows (either may be null), with a face; showMs 0 keeps it until the next hide. Honors quiet mode. */
    void say(String title, int face, long showMs) { say(title, null, face, showMs); }
    void say(String title, String body, int face, long showMs) {
        if (!prefs.getBoolean("muted", false)) show(title, body, face, showMs);
    }

    /** Every pop-up once, 4 s apart: adb shell am start --activity-clear-task --ez demo true -n dev.hepta.tvcat/.SettingsActivity (clear-task: a live Settings task would otherwise just come to the front and never read the extra) */
    void demo() {
        String tm = time(System.currentTimeMillis()), st = "Time\t" + tm + "\nToday\t2 h\nYouTube\t1 h 30 m\nWeather\t14°C clear";
        String[] t = {"Yay! 30 min streak!", "Hmm. 1 h streak.", "2 h streak. Still watching?!", "3 h streak!!! GO OUTSIDE.",
                null, "It's " + tm + ". Bedtime!", HOME_NUDGE_MIN + " min on the home screen. Pick something!",
                "Loud! My ears!", "No internet!", "YouTube"};
        String[] b = {st, st, st, st, st.replace("\nYouTube", "\nStreak\t45 min\nYouTube"), null, null, null, null, "New video — Cats vs. cucumbers"};
        int[] f = {FACES[0], FACES[1], FACES[2], FACES[3], FACES[0], R.drawable.cat_sleepy, R.drawable.cat_meh, R.drawable.cat_ears, R.drawable.cat_meh, R.drawable.cat};
        for (int i = 0; i < t.length; i++) { final int k = i; h.postDelayed(() -> show(t[k], b[k], f[k], 3_500), i * 4_000L); }
    }

    void show(String title, String body, int face, long showMs) {
        head.setText(title);
        head.setVisibility(title == null ? View.GONE : View.VISIBLE);
        if (body != null) {
            SpannableString s = new SpannableString(body);
            s.setSpan(new TabStopSpan.Standard(dp(96)), 0, s.length(), 0); // ponytail: one column stop; a label wider than 96dp (long app name) pushes only its own value right
            for (int t = body.indexOf('\t'); t >= 0; t = body.indexOf('\t', t + 1)) s.setSpan(new ForegroundColorSpan(LABEL), body.lastIndexOf('\n', t) + 1, t, 0);
            rows.setText(s);
        }
        rows.setVisibility(body == null ? View.GONE : View.VISIBLE);
        bubble.setVisibility(View.VISIBLE);
        cat.setImageResource(face);
        cat.animate().scaleX(1.5f).scaleY(1.5f).setDuration(300).withEndAction(() ->
                cat.animate().scaleX(1f).scaleY(1f).setDuration(300).start()).start();
        h.removeCallbacks(hide);
        if (showMs > 0) h.postDelayed(hide, showMs);
    }

    final Runnable hide = () -> { bubble.setVisibility(View.GONE); cat.setImageResource(R.drawable.cat); };

    int catPx() { return dp(Math.max(MIN_DP, Math.min(MAX_DP, prefs.getInt("size", DEFAULT_DP)))); }
    float catAlpha() { return 1f - Math.min(MAX_FADE, prefs.getInt("fade", DEFAULT_FADE)) / 100f; }

    void applyPosition() {
        int g = prefs.getInt("pos", DEFAULT_POS);
        // RTL flips child order so the bubble sits on the inner side of the cat in left corners
        root.setLayoutDirection((g & Gravity.START) == Gravity.START ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) root.getLayoutParams();
        lp.gravity = g;
        wm.updateViewLayout(root, lp);
        root.setGravity(g & Gravity.VERTICAL_GRAVITY_MASK); // card grows away from the screen edge; the cat stays put
    }

    // field, not a local: SharedPreferences keeps listeners weakly
    final SharedPreferences.OnSharedPreferenceChangeListener onPref = (p, k) -> {
        if ("size".equals(k)) { cat.getLayoutParams().width = cat.getLayoutParams().height = catPx(); cat.requestLayout(); }
        if ("fade".equals(k)) cat.setAlpha(catAlpha());
        if ("pos".equals(k)) applyPosition();
        if ("weather".equals(k)) { weather = null; weatherAt = 0; }
    };

    void buildOverlay() {
        wm = getSystemService(WindowManager.class);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);

        bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // the card's contents never flip with the root
        bubble.setBackgroundResource(R.drawable.bubble);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
        bubble.setVisibility(View.GONE);
        head = new TextView(this);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        head.setPadding(0, 0, 0, dp(3));
        rows = new TextView(this);
        rows.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        rows.setLineSpacing(dp(2), 1f);
        for (TextView t : new TextView[]{head, rows}) { t.setTextColor(INK); t.setMaxWidth(dp(400)); bubble.addView(t); }
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
        bp.setMarginEnd(dp(8));
        root.addView(bubble, bp);

        cat = new ImageView(this);
        cat.setImageResource(R.drawable.cat);
        cat.setAlpha(catAlpha());
        root.addView(cat, new LinearLayout.LayoutParams(catPx(), catPx()));
        prefs.registerOnSharedPreferenceChangeListener(onPref);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = DEFAULT_POS;
        lp.x = dp(24);
        lp.y = dp(24);
        wm.addView(root, lp);
        applyPosition();
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    Notification buildNotification() {
        NotificationChannel ch = new NotificationChannel("cat", "TV Cat", NotificationManager.IMPORTANCE_MIN);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        return new Notification.Builder(this, "cat").setSmallIcon(R.drawable.cat)
                .setContentTitle("TV Cat is watching with you").build();
    }

    @Override public int onStartCommand(Intent i, int f, int id) {
        if (i != null && i.getBooleanExtra("demo", false)) demo();
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        me = null;
        prefs.unregisterOnSharedPreferenceChangeListener(onPref);
        if (root != null) wm.removeView(root);
        super.onDestroy();
    }
}
