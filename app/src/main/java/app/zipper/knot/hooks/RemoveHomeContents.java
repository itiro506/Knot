package app.zipper.knot.hooks;

import android.view.View;
import android.view.ViewGroup;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class RemoveHomeContents implements BaseHook {

  private static int targetRecId = 0;
  private static boolean isSetupDone = false;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    XposedHelpers.findAndHookMethod(
        cfg.main.mainActivity, lpparam.classLoader, "onResume",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
            if (!isSetupDone) {
              android.app.Activity host =
                  (android.app.Activity)param.thisObject;
              LineVersion.Config c = LineVersion.get();
              targetRecId = host.getResources().getIdentifier(
                  c.home.resRecommendation, "id", c.linePkg);
              isSetupDone = true;
            }
          }
        });

    XposedHelpers.findAndHookMethod(
        View.class, "onAttachedToWindow", new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
            View target = (View)param.thisObject;
            int currentId = target.getId();
            if (currentId == View.NO_ID || targetRecId == 0)
              return;

            if (currentId == targetRecId) {
              if (SettingsStore.get(config.removeHomeRecommendations.key,
                                    config.removeHomeRecommendations.enabled)) {
                target.setVisibility(View.GONE);
                ViewGroup.LayoutParams params = target.getLayoutParams();
                if (params != null && params.height != 0) {
                  params.height = 0;
                  target.setLayoutParams(params);
                }
              }
            }
          }
        });
  }
}
