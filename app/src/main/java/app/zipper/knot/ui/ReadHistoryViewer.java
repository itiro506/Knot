package app.zipper.knot.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.zipper.knot.SettingsStore;
import app.zipper.knot.utils.ModuleStrings;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class ReadHistoryViewer {

  public static void show(Activity activity, String targetChatId) {
    try {
      JSONObject historyJson = SettingsStore.loadReadHistory();
      JSONArray list = historyJson.optJSONArray("read_history");

      String chatName = resolveChatName(activity, targetChatId);

      ScrollView scrollView = new ScrollView(activity);
      LinearLayout container = new LinearLayout(activity);
      container.setOrientation(LinearLayout.VERTICAL);
      container.setPadding(40, 20, 40, 40);
      scrollView.addView(container);

      boolean isDark =
          app.zipper.knot.utils.ThemeUtils.isContextDarkTheme(activity);

      TextView header = new TextView(activity);
      header.setText(
          chatName != null
              ? chatName
              : app.zipper.knot.utils.ModuleStrings.READ_HISTORY_TITLE);
      header.setTextSize(18);
      header.setTextColor(isDark ? Color.WHITE : Color.BLACK);
      header.setPadding(0, 20, 0, 30);
      container.addView(header);

      boolean found = false;
      if (list != null) {
        for (int i = list.length() - 1; i >= 0; i--) {
          JSONObject entry = list.optJSONObject(i);
          if (entry == null)
            continue;

          String entryChatId = entry.optString("chatId");
          if (targetChatId == null || targetChatId.equals(entryChatId)) {
            found = true;
            addHistoryItem(activity, container, entry, isDark);
          }
        }
      }

      if (!found) {
        TextView empty = new TextView(activity);
        empty.setText(app.zipper.knot.utils.ModuleStrings.READ_HISTORY_EMPTY);
        empty.setPadding(0, 100, 0, 100);
        empty.setGravity(Gravity.CENTER);
        empty.setTextColor(Color.GRAY);
        container.addView(empty);
      }

      int themeId = isDark ? AlertDialog.THEME_DEVICE_DEFAULT_DARK
                           : AlertDialog.THEME_DEVICE_DEFAULT_LIGHT;
      AlertDialog.Builder builder = new AlertDialog.Builder(activity, themeId);
      builder.setView(scrollView);
      builder.setPositiveButton(
          app.zipper.knot.utils.ModuleStrings.READ_HISTORY_CLOSE, null);
      if (targetChatId != null && found) {
        builder.setNeutralButton(
            app.zipper.knot.utils.ModuleStrings.READ_HISTORY_DELETE,
            (dialog, which) -> {
              new AlertDialog.Builder(activity, themeId)
                  .setTitle(app.zipper.knot.utils.ModuleStrings
                                .READ_HISTORY_DELETE_CONFIRM_TITLE)
                  .setMessage(app.zipper.knot.utils.ModuleStrings
                                  .READ_HISTORY_DELETE_CONFIRM_MSG)
                  .setPositiveButton(
                      app.zipper.knot.utils.ModuleStrings.SETTINGS_YES,
                      (d, w) -> { clearChatHistory(targetChatId); })
                  .setNegativeButton(
                      app.zipper.knot.utils.ModuleStrings.SETTINGS_CANCEL, null)
                  .show();
            });
      }
      builder.show();

    } catch (Throwable t) {
      XposedBridge.log("Knot: error: " + t.getMessage());
    }
  }

  private static void addHistoryItem(Activity activity, LinearLayout container,
                                     JSONObject entry, boolean isDark) {
    String mid = entry.optString("memberMid", "???");
    String messageText = entry.optString("messageText", "");
    long timestamp = entry.optLong("timestamp", 0);

    String name = resolveMemberName(activity, mid);

    LinearLayout card = new LinearLayout(activity);
    card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(25, 20, 25, 20);
    LinearLayout.LayoutParams cardLp =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                      ViewGroup.LayoutParams.WRAP_CONTENT);
    cardLp.setMargins(0, 5, 0, 5);
    card.setLayoutParams(cardLp);

    android.graphics.drawable.GradientDrawable gd =
        new android.graphics.drawable.GradientDrawable();
    gd.setColor(isDark ? Color.parseColor("#08FFFFFF")
                       : Color.parseColor("#08000000"));
    gd.setCornerRadius(12f);
    card.setBackground(gd);

    TextView contentText = new TextView(activity);
    contentText.setText(
        messageText != null && !messageText.isEmpty()
            ? messageText
            : app.zipper.knot.utils.ModuleStrings.READ_HISTORY_UNKNOWN_MSG);
    contentText.setTextColor(isDark ? Color.parseColor("#E0E0E0")
                                    : Color.parseColor("#333333"));
    contentText.setTextSize(17);
    contentText.setPadding(0, 0, 0, 15);
    card.addView(contentText);

    LinearLayout detailRow = new LinearLayout(activity);
    detailRow.setOrientation(LinearLayout.HORIZONTAL);
    detailRow.setGravity(Gravity.CENTER_VERTICAL);

    TextView nameText = new TextView(activity);
    nameText.setText(name != null ? name : mid);
    nameText.setTextColor(isDark ? Color.parseColor("#AAAAAA")
                                 : Color.parseColor("#666666"));
    nameText.setTextSize(15);

    LinearLayout.LayoutParams lpName =
        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                      ViewGroup.LayoutParams.WRAP_CONTENT);
    nameText.setLayoutParams(lpName);
    detailRow.addView(nameText);

    TextView timeText = new TextView(activity);
    SimpleDateFormat fmt =
        new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

    timeText.setText(timestamp > 0 ? fmt.format(new Date(timestamp)) : "");
    timeText.setTextSize(12);
    timeText.setTextColor(isDark ? Color.parseColor("#888888")
                                 : Color.parseColor("#999999"));
    timeText.setPadding(20, 0, 0, 0);
    detailRow.addView(timeText);

    card.addView(detailRow);
    container.addView(card);

    View margin = new View(activity);
    container.addView(margin, new LinearLayout.LayoutParams(
                                  ViewGroup.LayoutParams.MATCH_PARENT, 15));
  }

  private static String resolveMemberName(Activity activity, String mid) {
    if (mid == null)
      return null;
    try {
      File dbFile = activity.getDatabasePath("contact");
      if (dbFile.exists()) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
            dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor =
            db.rawQuery("SELECT profile_name FROM contacts WHERE mid = ?",
                        new String[] {mid});
        if (cursor.moveToFirst()) {
          String name = cursor.getString(0);
          cursor.close();
          db.close();
          return name;
        }
        cursor.close();
        db.close();
      }
    } catch (Throwable t) {
      XposedBridge.log("Knot: DB name resolution failed: " + t);
    }
    return null;
  }

  private static String resolveChatName(Activity activity, String chatId) {
    if (chatId == null)
      return null;
    try {
      File dbFile = activity.getDatabasePath("naver_line");
      if (dbFile.exists()) {
        SQLiteDatabase db = SQLiteDatabase.openDatabase(
            dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor cursor = db.rawQuery(
            "SELECT chat_name FROM chat_history WHERE chat_id = ? LIMIT 1",
            new String[] {chatId});
        if (cursor.moveToFirst()) {
          String name = cursor.getString(0);
          cursor.close();
          db.close();
          return name;
        }
        cursor.close();
        db.close();
      }
    } catch (Throwable t) {
    }
    return null;
  }

  private static void clearChatHistory(String chatId) {
    try {
      JSONObject historyJson = SettingsStore.loadReadHistory();
      JSONArray list = historyJson.optJSONArray("read_history");
      if (list != null) {
        JSONArray newList = new JSONArray();
        for (int i = 0; i < list.length(); i++) {
          JSONObject entry = list.optJSONObject(i);
          if (entry != null && !chatId.equals(entry.optString("chatId"))) {
            newList.put(entry);
          }
        }
        historyJson.put("read_history", newList);
        SettingsStore.saveReadHistory(historyJson);
      }
    } catch (Exception e) {
    }
  }
}
