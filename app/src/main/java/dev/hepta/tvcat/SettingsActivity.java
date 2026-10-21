package dev.hepta.tvcat;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Opened from the launcher icon. Toggles, sliders, position picker; D-pad friendly, no layout XML. */
public class SettingsActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences("cat", MODE_PRIVATE);
        if (p.getBoolean("enabled", true)) startForegroundService(new Intent(this, CatService.class));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(0xFF2B2D42);
        int pad = dp(40);
        col.setPadding(pad, pad, pad, pad);

        ImageView cat = new ImageView(this);
        cat.setImageResource(R.drawable.cat);
        cat.setAlpha(1f - p.getInt("fade", CatService.DEFAULT_FADE) / 100f);
        int d0 = p.getInt("size", CatService.DEFAULT_DP);
        col.addView(cat, new LinearLayout.LayoutParams(dp(d0 * 5 / 2), dp(d0 * 5 / 2)));

        int m = p.getInt("min", 0);
        TextView streak = new TextView(this);
        streak.setText((m == 0 ? "No streak yet" : "Current streak: " + Streak.fmt(m)) + " · " + Streak.fmt(p.getInt("dayMin", 0)) + " today");
        streak.setTextColor(0xFFFFF8E7);
        streak.setGravity(Gravity.CENTER);
        streak.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        streak.setPadding(0, dp(12), 0, dp(32));
        col.addView(streak);

        Switch enabled = toggle("Show cat on screen", p.getBoolean("enabled", true));
        enabled.setOnCheckedChangeListener((v, on) -> {
            p.edit().putBoolean("enabled", on).apply();
            Intent svc = new Intent(this, CatService.class);
            if (on) startForegroundService(svc); else stopService(svc);
        });
        col.addView(enabled);

        Switch quiet = toggle("Quiet mode (no pop-ups)", p.getBoolean("muted", false));
        quiet.setOnCheckedChangeListener((v, on) -> p.edit().putBoolean("muted", on).apply());
        col.addView(quiet);

        Switch weather = toggle("Weather in bubble (uses your IP for location)", p.getBoolean("weather", false));
        weather.setOnCheckedChangeListener((v, on) -> p.edit().putBoolean("weather", on).apply());
        col.addView(weather);

        int bed = p.getInt("bed", -1), bi = 0;
        for (int i = 0; i < CatService.BED_HOURS.length; i++) if (CatService.BED_HOURS[i] == bed) bi = i;
        TextView bedLbl = label("Bedtime: " + CatService.BED_NAMES[bi]);
        col.addView(bedLbl);
        col.addView(slider(CatService.BED_HOURS.length - 1, bi, v -> {
            p.edit().putInt("bed", CatService.BED_HOURS[v]).apply();
            bedLbl.setText("Bedtime: " + CatService.BED_NAMES[v]);
        }));

        col.addView(label("Cat size"));
        col.addView(slider(CatService.MAX_DP - CatService.MIN_DP, p.getInt("size", CatService.DEFAULT_DP) - CatService.MIN_DP, v -> {
            int d = v + CatService.MIN_DP;
            p.edit().putInt("size", d).apply();
            cat.setLayoutParams(new LinearLayout.LayoutParams(dp(d * 5 / 2), dp(d * 5 / 2)));
        }));

        col.addView(label("Transparency"));
        col.addView(slider(CatService.MAX_FADE, p.getInt("fade", CatService.DEFAULT_FADE), v -> {
            p.edit().putInt("fade", v).apply();
            cat.setAlpha(1f - v / 100f);
        }));

        col.addView(label("Position"));
        RadioGroup pos = new RadioGroup(this);
        pos.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Left top", "Right top", "Left bottom", "Right bottom"};
        int[] gravities = {Gravity.TOP | Gravity.START, Gravity.TOP | Gravity.END, Gravity.BOTTOM | Gravity.START, Gravity.BOTTOM | Gravity.END};
        int cur = p.getInt("pos", CatService.DEFAULT_POS);
        for (int i = 0; i < names.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setId(i + 1);
            r.setText(names[i]);
            r.setTextColor(0xFFFFF8E7);
            r.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            r.setPadding(dp(8), dp(10), dp(16), dp(10));
            r.setBackground(getDrawable(R.drawable.focus));
            r.setChecked(gravities[i] == cur);
            final int g = gravities[i];
            r.setOnCheckedChangeListener((v, on) -> { if (on) p.edit().putInt("pos", g).apply(); });
            pos.addView(r);
        }
        col.addView(pos, new LinearLayout.LayoutParams(dp(520), -2));

        setContentView(col);
        enabled.requestFocus();
    }

    Switch toggle(String label, boolean on) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setChecked(on);
        s.setTextColor(0xFFFFF8E7);
        s.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        s.setPadding(dp(16), dp(14), dp(16), dp(14));
        s.setFocusable(true);
        s.setBackground(getDrawable(R.drawable.focus));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(520), -2);
        lp.bottomMargin = dp(12);
        s.setLayoutParams(lp);
        return s;
    }

    TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFFFF8E7);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setPadding(dp(16), dp(16), 0, dp(4));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(520), -2));
        return t;
    }

    SeekBar slider(int max, int progress, java.util.function.IntConsumer onChange) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(progress);
        sb.setPadding(dp(24), dp(14), dp(24), dp(14));
        sb.setBackground(getDrawable(R.drawable.focus));
        sb.setLayoutParams(new LinearLayout.LayoutParams(dp(520), -2));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean user) { onChange.accept(v); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        return sb;
    }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
}
