package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ThemeFreeHook implements BaseHook {
  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    if (!config.themeFree.enabled)
      return;

    ClassLoader classLoader = lpparam.classLoader;
    app.zipper.knot.LineVersion.Config cfg =
        app.zipper.knot.LineVersion.get(classLoader);
    if (cfg == null)
      return;

    try {
      XposedHelpers.findAndHookMethod(
          cfg.themeFree.productDataClass, classLoader,
          cfg.themeFree.methodSetOwnership, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              bypassOwnership(param.thisObject, cfg);
              param.setResult(true);
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      XC_MethodHook activityHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
          Object activity = param.thisObject;
          Object fObj = XposedHelpers.getObjectField(
              activity, cfg.themeFree.fieldThemeProduct);
          bypassOwnership(fObj, cfg);
        }
      };

      if (cfg.themeFree.themeDetailActivityClass != null &&
          !cfg.themeFree.themeDetailActivityClass.isEmpty() &&
          cfg.themeFree.methodThemeDetailUpdates != null) {
        for (String method : cfg.themeFree.methodThemeDetailUpdates) {
          try {
            XposedHelpers.findAndHookMethod(
                cfg.themeFree.themeDetailActivityClass, classLoader, method,
                activityHook);
          } catch (Throwable ignored) {
          }
        }
      }
    } catch (Throwable ignored) {
    }

    try {
      XposedHelpers.findAndHookMethod(
          cfg.themeFree.themeRepositoryClass, classLoader,
          cfg.themeFree.methodIsThemeOwned, cfg.themeFree.productDataClass,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
                throws Throwable {
              if (isTheme(param.args[0], cfg)) {
                param.setResult(true);
              }
            }
          });
    } catch (Throwable ignored) {
    }
  }

  private void bypassOwnership(Object fObj,
                               app.zipper.knot.LineVersion.Config cfg) {
    if (isTheme(fObj, cfg)) {
      XposedHelpers.setBooleanField(fObj, cfg.themeFree.fieldIsOwned, true);
    }
  }

  private boolean isTheme(Object fObj, app.zipper.knot.LineVersion.Config cfg) {
    if (fObj == null)
      return false;
    try {
      Object productType =
          XposedHelpers.getObjectField(fObj, cfg.themeFree.fieldProductType);
      if (productType != null) {
        String typeName = (String)XposedHelpers.callMethod(
            productType, cfg.themeFree.methodGetProductTypeName);
        return "theme".equals(typeName);
      }
    } catch (Throwable ignored) {
    }
    return false;
  }
}
