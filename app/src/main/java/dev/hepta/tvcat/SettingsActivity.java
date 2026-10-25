package dev.hepta.tvcat;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/**
 * Opened from the launcher icon. A small floating card (dialog theme) over a dimmed screen.
 * Every option is the same row: label left, value right, D-pad left/right changes it, OK cycles.
 * The live overlay cat sits above this window, so size/transparency/position preview in place.
 */
public class SettingsActivity extends Activity {
    static final int CREAM = 0xFFFFF8E7, DIM = 0x99FFF8E7, ACCENT = 0xFFF7B267;
    static final int SIZE_STEP = 4, FADE_STEP = 10;
    TextView hint;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences("cat", MODE_PRIVATE);
        if (p.getBoolean("enabled", true)) startForegroundService(new Intent(this, CatService.class)
                .putExtra("demo", getIntent().getBooleanExtra("demo", false))); // demo: see CatService.demo()

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(24), dp(20), dp(24), dp(18));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(4), 0, 0, dp(14));
        ImageView cat = new ImageView(this);
        cat.setImageResource(R.drawable.cat);
        head.addView(cat, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        TextView title = text("TV Cat", 22, CREAM);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(title);
        int m = p.getInt("min", 0);
        titles.addView(text("Streak " + (m == 0 ? "none yet" : Streak.fmt(m)) + "  ·  Today " + Streak.fmt(p.getInt("dayMin", 0)), 15, DIM));
        head.addView(titles);
        col.addView(head);

        String[] onOff = {"Off", "On"};
        LinearLayout first = row("Show cat", "Hide the cat without uninstalling it.", onOff, p.getBoolean("enabled", true) ? 1 : 0, i -> {
            p.edit().putBoolean("enabled", i == 1).apply();
            Intent svc = new Intent(this, CatService.class);
            if (i == 1) startForegroundService(svc); else stopService(svc);
        });
        col.addView(first);
        col.addView(row("Pop-ups", "Speech bubbles for streaks, hourly chimes and notifications. Off = quiet cat.",
                onOff, p.getBoolean("muted", false) ? 0 : 1, i -> p.edit().putBoolean("muted", i == 0).apply()));
        col.addView(row("Weather", "Adds the forecast to hourly chimes. Uses your IP address to find your city.",
                onOff, p.getBoolean("weather", false) ? 1 : 0, i -> p.edit().putBoolean("weather", i == 1).apply()));

        int bed = p.getInt("bed", -1), bi = 0;
        for (int i = 0; i < CatService.BED_HOURS.length; i++) if (CatService.BED_HOURS[i] == bed) bi = i;
        col.addView(row("Bedtime", "From this hour the cat yawns and gets grumpy faster.",
                CatService.BED_NAMES, bi, i -> p.edit().putInt("bed", CatService.BED_HOURS[i]).apply()));

        // ponytail: sizes/fades snap to steps, so an off-grid saved value (default 34 dp, 15%) shows the nearest step until changed
        String[] sizes = new String[(CatService.MAX_DP - CatService.MIN_DP) / SIZE_STEP + 1];
        for (int i = 0; i < sizes.length; i++) {
            StringBuilder s = new StringBuilder();
            for (int j = 0; j < sizes.length; j++) s.append(j <= i ? '●' : '○');
            sizes[i] = s.toString();
        }
        int si = Math.round((p.getInt("size", CatService.DEFAULT_DP) - CatService.MIN_DP) / (float) SIZE_STEP);
        col.addView(row("Size", "Watch the cat in the corner as you change it.", sizes, Math.max(0, Math.min(sizes.length - 1, si)),
                i -> p.edit().putInt("size", CatService.MIN_DP + i * SIZE_STEP).apply()));

        String[] fades = new String[CatService.MAX_FADE / FADE_STEP + 1];
        for (int i = 0; i < fades.length; i++) fades[i] = i * FADE_STEP + "%";
        int fi = Math.round(p.getInt("fade", CatService.DEFAULT_FADE) / (float) FADE_STEP);
        col.addView(row("Transparency", "How see-through the cat is. 0% is solid.", fades, Math.max(0, Math.min(fades.length - 1, fi)),
                i -> p.edit().putInt("fade", i * FADE_STEP).apply()));

        // clockwise round the screen, so pressing right walks the cat corner to corner
        String[] corners = {"Top left", "Top right", "Bottom right", "Bottom left"};
        int[] gravities = {Gravity.TOP | Gravity.START, Gravity.TOP | Gravity.END, Gravity.BOTTOM | Gravity.END, Gravity.BOTTOM | Gravity.START};
        int cur = p.getInt("pos", CatService.DEFAULT_POS), ci = 2;
        for (int i = 0; i < gravities.length; i++) if (gravities[i] == cur) ci = i;
        col.addView(row("Position", "Which corner the cat sits in.", corners, ci, i -> p.edit().putInt("pos", gravities[i]).apply()));

        hint = text("", 14, DIM);
        hint.setPadding(dp(4), dp(12), dp(4), 0);
        hint.setMinLines(2);
        col.addView(hint);
        TextView keys = text("Left / Right to change  ·  Back to close", 13, DIM);
        keys.setPadding(dp(4), dp(6), 0, 0);
        col.addView(keys);

        ScrollView scroll = new ScrollView(this); // ponytail: fits 540 dp tall TVs as is; scrolls only at large font scales
        scroll.addView(col);
        setContentView(scroll);
        GradientDrawable card = new GradientDrawable();
        card.setColor(CatService.INK);
        card.setCornerRadius(dp(18));
        getWindow().setBackgroundDrawable(card);
        getWindow().setLayout(dp(540), -2);
        first.requestFocus();
    }

    /** One option row. values[i] is shown; onSet gets the new index. */
    LinearLayout row(String label, String help, String[] values, int start, IntConsumer onSet) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setFocusable(true);
        r.setClickable(true);
        r.setBackground(getDrawable(R.drawable.focus));
        r.setPadding(dp(16), dp(9), dp(14), dp(9));
        TextView name = text(label, 17, CREAM), val = text("", 17, ACCENT);
        int[][] states = {{android.R.attr.state_focused}, {}};
        name.setTextColor(new ColorStateList(states, new int[]{CatService.INK, CREAM})); // dark ink on the orange focus pill
        val.setTextColor(new ColorStateList(states, new int[]{CatService.INK, ACCENT}));
        name.setDuplicateParentStateEnabled(true);
        val.setDuplicateParentStateEnabled(true);
        r.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(val);

        int[] i = {start};
        val.setText("‹  " + values[start] + "  ›");
        IntConsumer go = n -> {
            if (n == i[0]) return;
            i[0] = n;
            val.setText("‹  " + values[n] + "  ›");
            onSet.accept(n);
        };
        r.setOnClickListener(v -> go.accept((i[0] + 1) % values.length)); // OK / tap cycles, wrapping
        r.setOnKeyListener((v, key, e) -> {
            int d = key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : key == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : 0;
            if (d != 0 && e.getAction() == KeyEvent.ACTION_DOWN) go.accept(Math.max(0, Math.min(values.length - 1, i[0] + d)));
            return d != 0; // eat left/right so focus never leaves the row sideways
        });
        r.setOnFocusChangeListener((v, f) -> { if (f) hint.setText(help); });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(6);
        r.setLayoutParams(lp);
        return r;
    }

    TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
