package tv.seans.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Web arayüzüne window.SeansNative olarak açılan köprü. */
public class Bridge {

    private static final int ICON_SIZE = 160;
    private static volatile String cache = null;
    private final Activity activity;

    Bridge(Activity activity) {
        this.activity = activity;
    }

    static void clearCache() {
        cache = null;
    }

    /** Kurulu, başlatılabilir uygulamalar: [{pkg, label, icon}] (icon: PNG data URL) */
    @JavascriptInterface
    public String getApps() {
        String c = cache;
        if (c != null) return c;

        PackageManager pm = activity.getPackageManager();
        String self = activity.getPackageName();
        LinkedHashMap<String, ResolveInfo> found = new LinkedHashMap<>();
        String[] categories = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : categories) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String p = ri.activityInfo.packageName;
                if (!p.equals(self) && !found.containsKey(p)) found.put(p, ri);
            }
        }

        final Collator collator = Collator.getInstance(new Locale("tr", "TR"));
        List<JSONObject> list = new ArrayList<>();
        for (ResolveInfo ri : found.values()) {
            try {
                JSONObject o = new JSONObject();
                o.put("pkg", ri.activityInfo.packageName);
                o.put("label", ri.loadLabel(pm).toString());
                Drawable icon = ri.activityInfo.loadIcon(pm);
                if (icon != null) o.put("icon", toDataUrl(icon));
                list.add(o);
            } catch (Exception ignored) { }
        }
        Collections.sort(list, (a, b) -> collator.compare(a.optString("label"), b.optString("label")));

        JSONArray arr = new JSONArray();
        for (JSONObject o : list) arr.put(o);
        cache = arr.toString();
        return cache;
    }

    @JavascriptInterface
    public void launch(final String pkg) {
        activity.runOnUiThread(() -> {
            PackageManager pm = activity.getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { activity.startActivity(i); } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public void openSystemSettings() {
        start(new Intent(Settings.ACTION_SETTINGS));
    }

    @JavascriptInterface
    public void openAccessibilitySettings() {
        start(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void start(final Intent i) {
        activity.runOnUiThread(() -> {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { activity.startActivity(i); } catch (Exception ignored) { }
        });
    }

    private static String toDataUrl(Drawable d) {
        Bitmap bmp = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        d.setBounds(0, 0, ICON_SIZE, ICON_SIZE);
        d.draw(canvas);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        bmp.recycle();
        return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }
}
