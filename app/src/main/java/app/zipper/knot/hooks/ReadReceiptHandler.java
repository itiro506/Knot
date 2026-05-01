package app.zipper.knot.hooks;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import app.zipper.knot.KnotConfig;
import app.zipper.knot.LineVersion;
import app.zipper.knot.SettingsStore;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReadReceiptHandler implements BaseHook {

  private static volatile long bypassExpiry = 0L;

  @Override
  public void hook(final KnotConfig config,
                   XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
    LineVersion.Config cfg = LineVersion.get();

    try {
      XposedBridge.hookAllMethods(
          XposedHelpers.findClass(cfg.unsend.talkServiceHookClass,
                                  lpparam.classLoader),
          cfg.unsend.methodReadBuffer, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
              try {
                if (!SettingsStore.get("record_read_history", false))
                  return;

                Object op = cfg.unsend.isOpReadScheme ? param.args[1]
                                                      : param.thisObject;
                if (op == null || op instanceof String)
                  return;

                Object type = XposedHelpers.getObjectField(
                    op, cfg.unsend.operationTypeField);
                if (type == null)
                  return;

                if (cfg.readReceipt.operationNotifiedReadName.equals(
                        type.toString())) {
                  long createdTime = XposedHelpers.getLongField(
                      op, cfg.unsend.operationCreatedTimeField);
                  String chatId = (String)XposedHelpers.getObjectField(
                      op, cfg.unsend.operationParam1Field);
                  String senderMid = (String)XposedHelpers.getObjectField(
                      op, cfg.unsend.operationParam2Field);
                  String lastMsgId = (String)XposedHelpers.getObjectField(
                      op, cfg.unsend.operationParam3Field);

                  if (chatId != null && senderMid != null &&
                      lastMsgId != null) {
                    boolean recordOthers = false;
                    String myMid = app.zipper.knot.utils.LineDBUtils.getMyMid();

                    JSONObject historyJson = SettingsStore.loadReadHistory();
                    JSONObject chats = historyJson.optJSONObject("c");
                    long maxId = -1;
                    if (chats != null) {
                      JSONObject chat = chats.optJSONObject(chatId);
                      if (chat != null) {
                        JSONObject messages = chat.optJSONObject("m");
                        if (messages != null) {
                          java.util.Iterator<String> keys = messages.keys();
                          while (keys.hasNext()) {
                            try {
                              long sid = Long.parseLong(keys.next());
                              if (sid > maxId)
                                maxId = sid;
                            } catch (NumberFormatException ignored) {
                            }
                          }
                        }
                      }
                    }

                    java.util
                        .List<app.zipper.knot.utils.LineDBUtils.MessageRecord>
                            records =
                        app.zipper.knot.utils.LineDBUtils
                            .fetchMessagesForRecording(chatId, lastMsgId, myMid,
                                                       recordOthers, maxId);

                    if (!records.isEmpty()) {
                      saveReadEvents(chatId, senderMid, records, createdTime,
                                     historyJson);
                    }
                  }
                }
              } catch (Throwable t) {
                XposedBridge.log("Knot: ReadHistory Error: " + t.getMessage());
              }
            }
          });
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to hook Operation for read history: " + t);
    }
    try {
      Class<?> thriftCls =
          lpparam.classLoader.loadClass(cfg.thrift.talkServiceClientImplClass);
      XposedBridge.hookAllMethods(
          thriftCls, cfg.thrift.v1, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
              if (param.args == null || param.args[0] == null)
                return;
              String method = param.args[0].toString();

              boolean isTarget = method.contains("sendChatChecked") ||
                                 method.contains("Checked") ||
                                 method.contains("ReadReceipt");
              if (!isTarget)
                return;

              if (!config.preventMarkAsRead.enabled ||
                  !SettingsStore.get("prevent_read_state", true))
                return;
              if (SettingsStore.get("send_mark_state", false) &&
                  bypassExpiry > System.currentTimeMillis())
                return;

              param.args[0] = "KNOT_NOP";
            }
          });
    } catch (Throwable ignored) {
    }

    try {
      Class<?> managerCls = null;
      if (cfg.readReceipt.readReceiptManagerClass != null &&
          !cfg.readReceipt.readReceiptManagerClass.isEmpty()) {
        try {
          managerCls = XposedHelpers.findClass(
              cfg.readReceipt.readReceiptManagerClass, lpparam.classLoader);
        } catch (Throwable t) {
          XposedBridge.log("Knot: Configured manager class not found: " +
                           cfg.readReceipt.readReceiptManagerClass);
        }
      }

      if (managerCls == null) {
        managerCls = app.zipper.knot.utils.DexScanner.findClass(
            lpparam.classLoader, lpparam.appInfo.sourceDir,
            c -> containsServiceReference(c) && containsMarkAsReadLogic(c));

        if (managerCls != null) {
          XposedBridge.log("Knot: Discovered manager class via scan: " +
                           managerCls.getName() +
                           " (Please update VersionConfig)");
        }
      }

      if (managerCls != null) {
        XposedBridge.hookAllMethods(
            managerCls, cfg.readReceipt.methodSendReadReceipt,
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param) {
                if (!config.preventMarkAsRead.enabled ||
                    !SettingsStore.get("prevent_read_state", true))
                  return;

                java.lang.reflect.Method m =
                    (java.lang.reflect.Method)param.method;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 3 &&
                    params[0] == long.class &&params[1] == String.class &&
                    params[2] == boolean.class) {
                  if (SettingsStore.get("send_mark_state", false) &&
                      bypassExpiry > System.currentTimeMillis())
                    return;
                  param.setResult(null);
                }
              }
            });
        XposedBridge.log("Knot: Successfully hooked manager class: " +
                         managerCls.getName());

        XposedBridge.hookAllMethods(
            managerCls, cfg.readReceipt.methodExecuteReadReceiptAsync,
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param) {
                if (config.preventMarkAsRead.enabled &&
                    SettingsStore.get("prevent_read_state", true) &&
                    SettingsStore.get("send_mark_state", false)) {
                  Class<?>[] params = ((java.lang.reflect.Method)param.method)
                                          .getParameterTypes();
                  if (params.length == 1 && params[0] == String.class)
                    bypassExpiry = System.currentTimeMillis() + 2000;
                }
              }
            });

        XposedBridge.hookAllMethods(
            managerCls, cfg.readReceipt.methodReadAll, new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param) {
                if (config.preventMarkAsRead.enabled &&
                    SettingsStore.get("prevent_read_state", true) &&
                    SettingsStore.get("send_mark_state", false)) {
                  if (((java.lang.reflect.Method)param.method)
                          .getParameterCount() == 0)
                    bypassExpiry = System.currentTimeMillis() + 2000;
                }
              }
            });
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: Manager hook error: " + t);
    }

    try {
      Class<?> talkClient =
          lpparam.classLoader.loadClass(cfg.thrift.talkServiceClientImplClass);
      XC_MethodHook sendHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
          if (config.preventMarkAsRead.enabled &&
              SettingsStore.get("prevent_read_state", true) &&
              SettingsStore.get("send_mark_state", false)) {
            bypassExpiry = System.currentTimeMillis() + 2000;
          }
        }
      };
      XposedBridge.hookAllMethods(talkClient, cfg.thrift.v1, sendHook);
      XposedBridge.hookAllMethods(talkClient, cfg.thrift.sendMessage, sendHook);
    } catch (Throwable ignored) {
    }
  }

  private static boolean containsServiceReference(Class<?> c) {
    LineVersion.Config cfg = LineVersion.get();
    for (java.lang.reflect.Field f : c.getDeclaredFields()) {
      if (cfg.thrift.talkServiceClientInterface.equals(f.getType().getName()))
        return true;
    }
    return false;
  }

  private static boolean containsMarkAsReadLogic(Class<?> c) {
    LineVersion.Config cfg = LineVersion.get();
    for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
      if (!cfg.readReceipt.methodSendReadReceipt.equals(m.getName()))
        continue;
      Class<?>[] p = m.getParameterTypes();
      if (p.length == 3 && p[0] == long.class &&p[1] == String.class &&
          p[2] == boolean.class)
        return true;
    }
    return false;
  }

  private static void saveReadEvents(
      String chatId, String readerMid,
      java.util.List<app.zipper.knot.utils.LineDBUtils.MessageRecord> records,
      long readTime, JSONObject historyJson) {
    if (records == null || records.isEmpty())
      return;

    try {
      JSONObject chats = historyJson.optJSONObject("c");
      if (chats == null) {
        chats = new JSONObject();
        historyJson.put("c", chats);
      }

      JSONObject chat = chats.optJSONObject(chatId);
      if (chat == null) {
        chat = new JSONObject();
        chats.put(chatId, chat);
      }

      JSONObject messages = chat.optJSONObject("m");
      if (messages == null) {
        messages = new JSONObject();
        chat.put("m", messages);
      }

      String readerName =
          app.zipper.knot.utils.LineDBUtils.resolveMemberName(readerMid);
      java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
          "yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
      String formattedReadTime = sdf.format(new java.util.Date(readTime));

      boolean isDirty = false;
      for (app.zipper.knot.utils.LineDBUtils.MessageRecord record : records) {
        if (!chat.has("n")) {
          chat.put("n", record.chatName);
        }

        JSONObject msgEntry = messages.optJSONObject(record.id);
        if (msgEntry == null) {
          msgEntry = new JSONObject();
          msgEntry.put("c", record.text);
          msgEntry.put("sn", record.senderName);
          msgEntry.put("sm", record.senderMid);
          msgEntry.put("ct", record.timestamp);
          msgEntry.put("r", new JSONObject());
          messages.put(record.id, msgEntry);
          isDirty = true;
        }

        JSONObject readers = msgEntry.optJSONObject("r");
        if (readers != null && !readers.has(readerMid)) {
          JSONObject rInfo = new JSONObject();
          rInfo.put("n", readerName != null ? readerName : "Unknown");
          rInfo.put("t", formattedReadTime);
          readers.put(readerMid, rInfo);
          isDirty = true;
        }
      }

      if (isDirty) {
        SettingsStore.saveReadHistory(historyJson);
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: Failed to save read events: " + t.getMessage());
    }
  }
}
