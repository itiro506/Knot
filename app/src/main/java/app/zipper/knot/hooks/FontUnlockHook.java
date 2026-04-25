package app.zipper.knot.hooks;

import android.graphics.Typeface;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;

public class FontUnlockHook implements BaseHook {
  private static Typeface customTypeface = null;
  private static boolean overrideActive = false;

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null || cfg.font.fontConfigClass.isEmpty())
      return;

    initTypeface();

    if (!overrideActive || customTypeface == null)
      return;

    XposedBridge.log("Knot: Initializing Font hooks");

    XC_MethodHook globalHook = new XC_MethodHook() {
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        param.setResult(customTypeface);
      }
    };

    XposedHelpers.findAndHookMethod(Typeface.class, "create", String.class,
                                    int.class, globalHook);
    XposedHelpers.findAndHookMethod(Typeface.class, "create", Typeface.class,
                                    int.class, globalHook);
    XposedHelpers.findAndHookMethod(Typeface.class, "defaultFromStyle",
                                    int.class, globalHook);

    XC_MethodHook textViewHook = new XC_MethodHook() {
      @Override
      protected void beforeHookedMethod(MethodHookParam param)
          throws Throwable {
        if (overrideActive && customTypeface != null) {
          if (param.args.length > 0 && param.args[0] instanceof Typeface) {
            param.args[0] = customTypeface;
          }
          try {
            if (param.thisObject instanceof android.widget.EditText) {
              XposedHelpers.callMethod(param.thisObject, "setPadding", 0, 0, 0,
                                       0);
            }
          } catch (Throwable ignored) {
          }
        }
      }
    };

    try {
      XposedHelpers.findAndHookMethod("android.widget.TextView",
                                      lpparam.classLoader, "setTypeface",
                                      Typeface.class, int.class, textViewHook);
      XposedHelpers.findAndHookMethod("android.widget.TextView",
                                      lpparam.classLoader, "setTypeface",
                                      Typeface.class, textViewHook);

      XC_MethodHook textViewConstructHook = new XC_MethodHook() {
        @Override
        protected void afterHookedMethod(MethodHookParam param)
            throws Throwable {
          if (overrideActive && customTypeface != null) {
            try {
              if (param.thisObject instanceof android.widget.EditText) {
                XposedHelpers.callMethod(param.thisObject, "setPadding", 0, 0,
                                         0, 0);
              }
            } catch (Throwable ignored) {
            }
          }
        }
      };
      XposedHelpers.findAndHookConstructor(
          "android.widget.TextView", lpparam.classLoader,
          android.content.Context.class, android.util.AttributeSet.class,
          textViewConstructHook);
      XposedHelpers.findAndHookConstructor(
          "android.widget.TextView", lpparam.classLoader,
          android.content.Context.class, android.util.AttributeSet.class,
          int.class, textViewConstructHook);
    } catch (Throwable t) {
    }

    XC_MethodHook metricsHook = new XC_MethodHook() {
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        if (!overrideActive || customTypeface == null)
          return;

        android.graphics.Paint paint = (android.graphics.Paint)param.thisObject;
        Typeface tf = paint.getTypeface();

        if (tf != null && tf != customTypeface &&
            !(paint instanceof android.text.TextPaint))
          return;

        Object metrics =
            (param.args.length > 0) ? param.args[0] : param.getResult();
        if (metrics instanceof android.graphics.Paint.FontMetricsInt) {
          android.graphics.Paint.FontMetricsInt fmi =
              (android.graphics.Paint.FontMetricsInt)metrics;
          float textSize = paint.getTextSize();
          if (textSize > 0) {
            fmi.ascent = Math.round(-textSize * 0.92f);
            fmi.descent = Math.round(textSize * 0.22f);
            fmi.top = fmi.ascent;
            fmi.bottom = fmi.descent;
            fmi.leading = 0;
          }
        }
      }
    };

    try {
      XposedHelpers.findAndHookMethod(
          android.graphics.Paint.class, "getFontMetricsInt",
          android.graphics.Paint.FontMetricsInt.class, metricsHook);
      XposedHelpers.findAndHookMethod(android.graphics.Paint.class,
                                      "getFontMetricsInt", metricsHook);
    } catch (Throwable t) {
    }

    XposedHelpers.findAndHookMethod(
        cfg.font.fontConfigClass, lpparam.classLoader,
        cfg.font.methodGetFontConfig, android.content.Context.class,
        java.util.List.class, int.class, boolean.class, int.class,
        android.os.Handler.class, cfg.font.fontCallbackClass,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
            Object callback = param.args[6];
            if (callback != null) {
              try {
                XposedHelpers.callMethod(callback, cfg.font.methodOnFontChanged,
                                         customTypeface);
              } catch (Throwable t) {
              }
            }
            param.setResult(customTypeface);
          }
        });

    XposedHelpers.findAndHookMethod(
        cfg.font.fontCallbackClass, lpparam.classLoader,
        cfg.font.methodOnFontChanged, Typeface.class, new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
            param.args[0] = customTypeface;
          }
        });

    XposedHelpers.findAndHookMethod(
        cfg.font.fontManagerClass, lpparam.classLoader,
        cfg.font.methodInitializeFont, String.class,
        android.content.Context.class, java.util.List.class, int.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param)
              throws Throwable {
            Object result = param.getResult();
            if (result != null) {
              XposedHelpers.setObjectField(result, cfg.font.fieldTypeface,
                                           customTypeface);
            }
          }
        });

    try {
      XposedHelpers.findAndHookMethod(
          cfg.font.fontSettingsClass, lpparam.classLoader,
          cfg.font.methodGetFontSettings, int.class, cfg.font.fontInjectedClass,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
              param.setResult(customTypeface);
            }
          });
    } catch (Throwable t) {
    }
  }

  private void initTypeface() {
    if (!SettingsStore.get("use_custom_font", false)) {
      overrideActive = false;
      return;
    }

    String path = SettingsStore.getString("custom_font_path", "");
    if (!path.isEmpty()) {
      File file = new File(path);
      if (file.exists() && file.canRead()) {
        try {
          customTypeface = Typeface.createFromFile(file);
          if (customTypeface != null) {
            overrideActive = true;
            XposedBridge.log("Knot: Custom font loaded: " + path);
            return;
          }
        } catch (Throwable t) {
          XposedBridge.log("Knot: Failed to load font: " + t.getMessage());
        }
      }
    }
    overrideActive = false;
  }
}
