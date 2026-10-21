package dev.hepta.tvcat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (c.getSharedPreferences("cat", Context.MODE_PRIVATE).getBoolean("enabled", true))
            c.startForegroundService(new Intent(c, CatService.class));
    }
}
