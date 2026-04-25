package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StickerTrialHook implements BaseHook {
  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    if (!config.stickerTrial.enabled)
      return;

    ClassLoader classLoader = lpparam.classLoader;
    app.zipper.knot.LineVersion.Config cfg =
        app.zipper.knot.LineVersion.get(classLoader);
    if (cfg == null)
      return;

    try {
      Class<?> d0Class = XposedHelpers.findClass(
          cfg.stickerTrial.freeTrialStatusCheckerClass, classLoader);

      XC_MethodHook limitHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
          param.setResult(999);
        }
      };

      XposedHelpers.findAndHookMethod(
          d0Class, cfg.stickerTrial.methodGetLimitCount, limitHook);
      XposedHelpers.findAndHookMethod(
          d0Class, cfg.stickerTrial.methodGetLimitRemaining, limitHook);

      XC_MethodHook availabilityHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
          param.setResult(true);
        }
      };

      XposedHelpers.findAndHookMethod(
          d0Class, cfg.stickerTrial.methodCheckAvailability, availabilityHook);
      XposedHelpers.findAndHookMethod(
          d0Class, cfg.stickerTrial.methodCheckStatus, availabilityHook);

    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          cfg.stickerTrial.stickerDatabaseHelperClass, classLoader,
          cfg.stickerTrial.methodInsertSticker,
          "android.database.sqlite.SQLiteDatabase",
          cfg.stickerTrial.stickerContentValueDataClass, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              android.database.sqlite.SQLiteDatabase db =
                  (android.database.sqlite.SQLiteDatabase)param.args[0];
              Object dVar = param.args[1];
              android.content.ContentValues values =
                  (android.content.ContentValues)XposedHelpers.callMethod(
                      dVar, cfg.stickerTrial.methodGetContentValues);

              long rowId = db.insertWithOnConflict("sticker", null, values, 5);
              param.setResult(rowId != -1);
            }
          });
    } catch (Throwable ignored) {
    }
  }
}
