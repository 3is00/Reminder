package com.myself.waterreminder;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "Helper")
public class HelperPlugin extends Plugin {

  private void go(Intent i) {
    try {
      i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      getContext().startActivity(i);
    } catch (Exception e) {
    }
  }

  private Uri pkg() {
    return Uri.parse("package:" + getContext().getPackageName());
  }

  @PluginMethod
  public void status(PluginCall call) {
    Context c = getContext();
    JSObject r = new JSObject();
    PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
    r.put("battery", pm.isIgnoringBatteryOptimizations(c.getPackageName()));
    boolean ex = true;
    if (Build.VERSION.SDK_INT >= 31) {
      AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
      ex = am.canScheduleExactAlarms();
    }
    r.put("exact", ex);
    call.resolve(r);
  }

  @PluginMethod
  public void askBattery(PluginCall call) {
    go(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg()));
    call.resolve();
  }

  @PluginMethod
  public void askExact(PluginCall call) {
    if (Build.VERSION.SDK_INT >= 31) {
      go(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg()));
    }
    call.resolve();
  }

  @PluginMethod
  public void appSettings(PluginCall call) {
    go(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg()));
    call.resolve();
  }

  @PluginMethod
  public void pickSound(PluginCall call) {
    SharedPreferences sp = getContext().getSharedPreferences("snd", Context.MODE_PRIVATE);
    Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
    i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL);
    i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
    i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
    i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختار صوت التذكير");
    String cur = sp.getString("uri", null);
    Uri exist = cur != null ? Uri.parse(cur) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, exist);
    startActivityForResult(call, i, "soundResult");
  }

  @ActivityCallback
  private void soundResult(PluginCall call, ActivityResult result) {
    if (call == null) return;
    Intent data = result.getData();
    if (result.getResultCode() != Activity.RESULT_OK || data == null) {
      JSObject c = new JSObject();
      c.put("cancelled", true);
      call.resolve(c);
      return;
    }
    Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
    if (uri == null) {
      uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }
    String title = "";
    try {
      Ringtone rt = RingtoneManager.getRingtone(getContext(), uri);
      if (rt != null) title = rt.getTitle(getContext());
    } catch (Exception e) {
    }
    SharedPreferences sp = getContext().getSharedPreferences("snd", Context.MODE_PRIVATE);
    String old = sp.getString("ch", null);
    String id = "water_" + System.currentTimeMillis();
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationManager nm = (NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE);
      if (old != null) nm.deleteNotificationChannel(old);
      AudioAttributes aa = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
      NotificationChannel ch = new NotificationChannel(id, "تذكير الماية", NotificationManager.IMPORTANCE_HIGH);
      ch.setSound(uri, aa);
      ch.enableVibration(true);
      ch.setVibrationPattern(new long[]{0, 800, 400, 800, 400, 800});
      ch.setLockscreenVisibility(1);
      nm.createNotificationChannel(ch);
    }
    sp.edit().putString("ch", id).putString("uri", uri.toString()).apply();
    JSObject r = new JSObject();
    r.put("channelId", id);
    r.put("title", title);
    call.resolve(r);
  }
}
