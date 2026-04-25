package app.zipper.knot.hooks;

import android.content.res.Resources;
import android.text.SpannedString;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SafeResourceFix implements BaseHook {
  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    if (!config.safeSettingsResources.enabled)
      return;

    LineVersion.Config lineCfg = LineVersion.get();
    if (lineCfg == null || lineCfg.settings.viewHolderSwitch.isEmpty())
      return;

    final String targetClass = lineCfg.settings.viewHolderSwitch;
    final String targetMethod = lineCfg.settings.methodBindDescription;

    try {
      XposedHelpers.findAndHookMethod(
          Resources.class, "getText", int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
                throws Throwable {
              StackTraceElement[] stackTrace =
                  Thread.currentThread().getStackTrace();
              boolean found = false;
              for (int i = 0; i < Math.min(stackTrace.length, 15); i++) {
                StackTraceElement element = stackTrace[i];
                if (element.getClassName().equals(targetClass) &&
                    element.getMethodName().equals(targetMethod)) {
                  found = true;
                  break;
                }
              }

              if (found && param.getResult() instanceof String) {
                param.setResult(new SpannedString((String)param.getResult()));
              }
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to hook SafeResourceFix: " +
                       t.getMessage());
    }
  }
}
