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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class CatService extends Service {
    static final long TICK_MS = 60_000;
    static final long SHOW_MS = 8_000;
    static final int MAX_DP = 56, MIN_DP = 24, DEFAULT_DP = 34; // default = 30% of the range
    static final int DEFAULT_POS = Gravity.BOTTOM | Gravity.END, DEFAULT_FADE = 15, MAX_FADE = 90; // ponytail: 90 cap so the cat can't vanish; "Show cat" toggle is for that
    static final int[] FACES = {R.drawable.cat_happy, R.drawable.cat_meh, R.drawable.cat_angry, R.drawable.cat_furious};

    final Handler h = new Handler(Looper.getMainLooper());
    final Streak streak = new Streak();
    WindowManager wm;
    LinearLayout root;
    TextView bubble;
    ImageView cat;
    SharedPreferences prefs;
    String launcherPkg;
    long lastTick;

    @Override public void onCreate() {
        super.onCreate();
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
            lastTick = now;
            if (streak.tick(isWatching(), gap)) celebrate();
            prefs.edit().putInt("min", streak.minutes).putLong("last", now).apply();
            h.postDelayed(this, TICK_MS);
        }
    };

    boolean isWatching() {
        if (!getSystemService(PowerManager.class).isInteractive()) return false;
        String fg = foregroundPkg();
        // ponytail: no usage access => we only know screen is on; treat as watching
        return fg == null || !fg.equals(launcherPkg);
    }

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

    void celebrate() {
        if (prefs.getBoolean("muted", false)) return;
        bubble.setText(streak.label());
        bubble.setVisibility(View.VISIBLE);
        cat.setImageResource(FACES[streak.mood()]);
        cat.animate().scaleX(1.5f).scaleY(1.5f).setDuration(300).withEndAction(() ->
                cat.animate().scaleX(1f).scaleY(1f).setDuration(300).start()).start();
        h.removeCallbacks(hide);
        h.postDelayed(hide, SHOW_MS);
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
    }

    // field, not a local: SharedPreferences keeps listeners weakly
    final SharedPreferences.OnSharedPreferenceChangeListener onPref = (p, k) -> {
        if ("size".equals(k)) { cat.getLayoutParams().width = cat.getLayoutParams().height = catPx(); cat.requestLayout(); }
        if ("fade".equals(k)) cat.setAlpha(catAlpha());
        if ("pos".equals(k)) applyPosition();
    };

    void buildOverlay() {
        wm = getSystemService(WindowManager.class);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);

        bubble = new TextView(this);
        bubble.setBackgroundResource(R.drawable.bubble);
        bubble.setTextColor(0xFF2B2D42);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        bubble.setPadding(dp(14), dp(8), dp(14), dp(8));
        bubble.setVisibility(View.GONE);
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

    @Override public int onStartCommand(Intent i, int f, int id) { return START_STICKY; }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(onPref);
        if (root != null) wm.removeView(root);
        super.onDestroy();
    }
}
