package dev.hepta.tvcat;

/** Pure streak logic: counts watched minutes, resets after a long idle gap. No Android imports. */
public final class Streak {
    public static final int MILESTONE_MIN = 30;
    public static final int IDLE_RESET_MIN = 10; // ponytail: fixed threshold; make it a setting if it annoys

    public int minutes;
    public int idleMinutes;
    public int lateHours; // hours past bedtime (0 = not late); set by the service, bumps the mood

    /** @return true when a milestone (30 min, 1 h, ...) was just reached. */
    public boolean tick(boolean watching, long gapMs) {
        if (gapMs > IDLE_RESET_MIN * 60_000L) { minutes = 0; idleMinutes = 0; } // TV was asleep / process frozen
        if (!watching) {
            if (++idleMinutes >= IDLE_RESET_MIN) { minutes = 0; idleMinutes = 0; }
            return false;
        }
        idleMinutes = 0;
        minutes++;
        return minutes % MILESTONE_MIN == 0;
    }

    /** 0 happy (<1 h), 1 unimpressed (<2 h), 2 angry (<3 h), 3 furious. Each hour past bedtime adds one. */
    public int mood() { return Math.min(3, minutes / 60 + lateHours); }

    public static String fmt(int min) {
        int h = min / 60, m = min % 60;
        return h == 0 ? m + " min" : m == 0 ? h + " h" : h + " h " + m + " m";
    }

    public String label() {
        String t = fmt(minutes);
        switch (mood()) {
            case 0: return "Yay! " + t + " streak!";
            case 1: return "Hmm. " + t + " streak.";
            case 2: return t + " streak. Still watching?!";
            default: return t + " streak!!! GO OUTSIDE.";
        }
    }

    // Self-check: java app/src/main/java/dev/hepta/tvcat/Streak.java
    public static void main(String[] a) {
        Streak s = new Streak();
        for (int i = 1; i < 30; i++) assert !s.tick(true, 60_000);
        assert s.tick(true, 60_000) && s.minutes == 30 && s.mood() == 0 && s.label().equals("Yay! 30 min streak!");
        for (int i = 0; i < IDLE_RESET_MIN - 1; i++) assert !s.tick(false, 60_000);
        assert s.minutes == 30 : "short idle must keep streak";
        assert !s.tick(false, 60_000) && s.minutes == 0 : "long idle resets";
        for (int i = 0; i < 90; i++) s.tick(true, 60_000);
        assert s.mood() == 1 && s.label().equals("Hmm. 1 h 30 m streak.") : s.label();
        s.lateHours = 2;
        assert s.mood() == 3 : "bedtime bumps mood";
        s.lateHours = 0;
        for (int i = 0; i < 90; i++) s.tick(true, 60_000);
        assert s.mood() == 3 && s.label().startsWith("3 h streak!!!") : s.label();
        s.tick(true, 3_600_000);
        assert s.minutes == 1 : "sleep gap resets";
        assert fmt(0).equals("0 min") && fmt(120).equals("2 h") && fmt(61).equals("1 h 1 m");
        System.out.println("Streak ok");
    }
}
