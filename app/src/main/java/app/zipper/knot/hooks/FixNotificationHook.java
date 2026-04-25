package app.zipper.knot.hooks;

import android.app.Activity;
import android.content.Context;
import android.os.PowerManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import app.zipper.knot.KnotConfig;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FixNotificationHook implements BaseHook {

  private static PowerManager.WakeLock wakeLock;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    final ClassLoader cl = lpparam.classLoader;

    try {
      XposedHelpers.findAndHookMethod(
          "jp.naver.line.android.common.lifecycle.LineLifecycleObserver", cl,
          "onStateChanged", LifecycleOwner.class, Lifecycle.Event.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!config.fixNotifications.enabled)
                return;

              Lifecycle.Event event = (Lifecycle.Event)param.args[1];

              if (event == Lifecycle.Event.ON_STOP ||
                  event == Lifecycle.Event.ON_PAUSE) {
              }
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          Activity.class, "onPause", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
                throws Throwable {
              if (!config.fixNotifications.enabled)
                return;

              Activity activity = (Activity)param.thisObject;
              if (wakeLock == null) {
                PowerManager pm = (PowerManager)activity.getSystemService(
                    Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                          "Knot:KeepAlive");
              }
              if (!wakeLock.isHeld()) {
                wakeLock.acquire(10 * 60 * 1000L); // 10 minutes
                XposedBridge.log("Knot: Socket maintenance WakeLock acquired");
              }
            }
          });

      XposedHelpers.findAndHookMethod(
          Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
                throws Throwable {
              if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }
}
