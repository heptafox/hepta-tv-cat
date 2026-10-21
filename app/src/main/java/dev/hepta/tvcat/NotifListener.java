package dev.hepta.tvcat;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Relays TV-side notifications into the cat's bubble. Needs `cmd notification allow_listener` via adb; unbound otherwise. */
public class NotifListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        CatService svc = CatService.me;
        if (svc == null || sbn.isOngoing() || getPackageName().equals(sbn.getPackageName())) return;
        Bundle x = sbn.getNotification().extras;
        CharSequence title = x.getCharSequence(Notification.EXTRA_TITLE), text = x.getCharSequence(Notification.EXTRA_TEXT);
        if (title == null && text == null) return;
        String msg = svc.appName(sbn.getPackageName()) + ": " + (title == null ? "" : title) + (title != null && text != null ? " — " : "") + (text == null ? "" : text);
        svc.h.post(() -> svc.say(msg, R.drawable.cat, CatService.SHOW_MS));
    }
}
