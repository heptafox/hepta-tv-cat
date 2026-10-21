#!/bin/sh
# Install TV Cat on an Android TV over Wi-Fi. Needs only `adb` (platform-tools), not Android Studio.
# Usage: ./install.sh <tv-ip> [path/to/apk]
set -e
IP=${1:?usage: ./install.sh <tv-ip> [path/to/apk]}
APK=${2:-hepta-tv-cat.apk}
TV="$IP:5555"
PKG=dev.hepta.tvcat

adb connect "$TV"   # first time: accept the "Allow USB debugging?" prompt on the TV
adb -s "$TV" install -r "$APK"
adb -s "$TV" shell appops set $PKG SYSTEM_ALERT_WINDOW allow   # draw the cat over other apps
adb -s "$TV" shell appops set $PKG GET_USAGE_STATS allow       # detect the foreground app
adb -s "$TV" shell cmd notification allow_listener $PKG/.NotifListener   # optional: show TV notifications in the bubble
adb -s "$TV" shell am start -n $PKG/.SettingsActivity
echo "Done. TV Cat is running."
