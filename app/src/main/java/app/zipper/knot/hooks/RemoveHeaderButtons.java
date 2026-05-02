package app.zipper.knot.hooks;

import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.Main;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.ArrayList;
import java.util.List;

public class RemoveHeaderButtons implements BaseHook {

  @Override
  public void hook(KnotConfig config, XC_LoadPackage.LoadPackageParam lpparam)
      throws Throwable {
    if (!config.removeAiFriendsButton.enabled &&
        !config.removeOpenChatButton.enabled)
      return;

    LineVersion.Config cfg = LineVersion.get();
    if (cfg == null || cfg.talkTabHeader.chatTabHeaderStateClass.isEmpty())
      return;

    Class<?> cls = XposedHelpers.findClass(
        cfg.talkTabHeader.chatTabHeaderStateClass, lpparam.classLoader);
    Class<?> iconTypeCls = XposedHelpers.findClass(
        cfg.talkTabHeader.iconTypeClass, lpparam.classLoader);

    Object aiFriend = safeValueOf(iconTypeCls, "AI_FRIEND");
    Object album = safeValueOf(iconTypeCls, "ALBUM");
    Object openChat = safeValueOf(iconTypeCls, "OPEN_CHAT");

    XposedBridge.hookAllConstructors(cls, new XC_MethodHook() {
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        if (!Main.options.removeAiFriendsButton.enabled &&
            !Main.options.removeOpenChatButton.enabled)
          return;
        try {
          patchState(param.thisObject, cfg, aiFriend, album, openChat);
        } catch (Exception e) {
          XposedBridge.log("Knot: RemoveHeaderButtons constructor error: " + e);
        }
      }
    });

    XposedBridge.hookAllMethods(cls, "createEndButtons", new XC_MethodHook() {
      @Override
      protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        if (!Main.options.removeAiFriendsButton.enabled &&
            !Main.options.removeOpenChatButton.enabled)
          return;
        List<?> result = (List<?>)param.getResult();
        if (result == null || result.isEmpty())
          return;
        try {
          param.setResult(
              filterButtons(result, cfg, aiFriend, album, openChat));
        } catch (Exception e) {
          XposedBridge.log(
              "Knot: RemoveHeaderButtons createEndButtons error: " + e);
        }
      }
    });
  }

  private static void patchState(Object instance, LineVersion.Config cfg,
                                 Object aiFriend, Object album, Object openChat)
      throws Exception {
    Object iconState = XposedHelpers.getObjectField(
        instance, cfg.talkTabHeader.iconListStateField);
    List<?> icons = (List<?>)XposedHelpers.callMethod(iconState, "getValue");
    if (icons != null)
      XposedHelpers.callMethod(iconState, "setValue",
                               filterIcons(icons, aiFriend, album, openChat));

    Object btnState = XposedHelpers.getObjectField(
        instance, cfg.talkTabHeader.buttonListStateField);
    List<?> buttons = (List<?>)XposedHelpers.callMethod(btnState, "getValue");
    if (buttons != null)
      XposedHelpers.callMethod(
          btnState, "setValue",
          filterButtons(buttons, cfg, aiFriend, album, openChat));
  }

  private static List<Object> filterIcons(List<?> icons, Object aiFriend,
                                          Object album, Object openChat) {
    boolean removeAi = Main.options.removeAiFriendsButton.enabled;
    boolean removeOc = Main.options.removeOpenChatButton.enabled;
    List<Object> out = new ArrayList<>();
    for (Object icon : icons) {
      if (removeAi && (icon == aiFriend || icon == album))
        continue;
      if (removeOc && icon == openChat)
        continue;
      out.add(icon);
    }
    return out;
  }

  private static List<Object> filterButtons(List<?> buttons,
                                            LineVersion.Config cfg,
                                            Object aiFriend, Object album,
                                            Object openChat) throws Exception {
    boolean removeAi = Main.options.removeAiFriendsButton.enabled;
    boolean removeOc = Main.options.removeOpenChatButton.enabled;
    List<Object> out = new ArrayList<>();
    for (Object btn : buttons) {
      Object type = XposedHelpers.getObjectField(
          btn, cfg.talkTabHeader.iconTypeFieldInButton);
      if (removeAi && (type == aiFriend || type == album))
        continue;
      if (removeOc && type == openChat)
        continue;
      out.add(btn);
    }
    return out;
  }

  private static Object safeValueOf(Class<?> cls, String name) {
    try {
      return XposedHelpers.callStaticMethod(cls, "valueOf", name);
    } catch (Exception e) {
      return null;
    }
  }
}
