package tv.seans.launcher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.WebResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Arayüzü https://seans.local/ sanal adresinden sunar:
 *   /…               → assets klasöründeki dosyalar
 *   /photos/photos.js → projektördeki fotoğrafların listesi (window.SEANS_PHOTOS)
 *   /local/{id}       → fotoğrafın küçültülmüş, doğru yöne çevrilmiş kopyası
 * Fotoğraflar cihazdan çıkmaz. "Seans" adlı bir klasör varsa yalnızca oradakiler, yoksa tüm fotoğraflar kullanılır.
 */
class LocalContent {

    static final String HOST = "seans.local";
    static final String START_URL = "https://" + HOST + "/index.html";
    private static final int MAX_EDGE = 1600;
    private static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
            "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};

    private final Context ctx;

    LocalContent(Context context) {
        this.ctx = context.getApplicationContext();
    }

    static String photoPermission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    boolean hasPermission() {
        return ctx.checkSelfPermission(photoPermission()) == PackageManager.PERMISSION_GRANTED;
    }

    WebResourceResponse handle(Uri uri) {
        if (uri == null || !HOST.equals(uri.getHost())) return null;
        String path = uri.getPath();
        if (path == null || path.equals("/") || path.isEmpty()) path = "/index.html";
        try {
            if (path.equals("/photos/photos.js")) {
                return text("application/javascript", "window.SEANS_PHOTOS = " + photosJson() + ";\n");
            }
            if (path.startsWith("/local/")) {
                return image(Long.parseLong(path.substring(7)));
            }
            String asset = path.substring(1);
            String mime = mime(asset);
            return new WebResourceResponse(mime, mime.startsWith("image/") ? null : "utf-8", ctx.getAssets().open(asset));
        } catch (Exception e) {
            return notFound();
        }
    }

    /* ---------------- Fotoğraf listesi ---------------- */

    private static class Photo {
        long id;
        String path;
        long taken;
    }

    private List<Photo> query() {
        List<Photo> all = new ArrayList<>();
        if (!hasPermission()) return all;
        String[] proj = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN
        };
        try (Cursor c = ctx.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                proj, null, null, MediaStore.Images.Media.DATE_TAKEN + " ASC")) {
            if (c == null) return all;
            while (c.moveToNext()) {
                Photo p = new Photo();
                p.id = c.getLong(0);
                p.path = c.getString(1);
                p.taken = c.getLong(2);
                all.add(p);
            }
        } catch (Exception ignored) {
            return all;
        }
        List<Photo> seans = new ArrayList<>();
        for (Photo p : all) {
            if (p.path != null && p.path.toLowerCase(Locale.ROOT).contains("/seans/")) seans.add(p);
        }
        return seans.isEmpty() ? all : seans;
    }

    int photoCount() {
        return query().size();
    }

    private String photosJson() {
        JSONArray arr = new JSONArray();
        for (Photo p : query()) {
            try {
                Uri u = uriOf(p.id);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
                    BitmapFactory.decodeStream(in, null, o);
                }
                if (o.outWidth <= 0 || o.outHeight <= 0) continue;
                int rot = 0;
                String exifDate = null;
                try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
                    if (in != null) {
                        ExifInterface ex = new ExifInterface(in);
                        rot = degrees(ex.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
                        exifDate = ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
                    }
                } catch (Exception ignored) { }

                int w = o.outWidth, h = o.outHeight;
                if (rot == 90 || rot == 270) { int t = w; w = h; h = t; }
                float s = Math.min(1f, (float) MAX_EDGE / Math.max(w, h));

                JSONObject j = new JSONObject();
                j.put("src", "/local/" + p.id);
                j.put("w", Math.round(w * s));
                j.put("h", Math.round(h * s));
                j.put("caption", caption(exifDate, p.taken));
                arr.put(j);
            } catch (Exception ignored) { }
        }
        return arr.toString();
    }

    private static String caption(String exifDate, long takenMs) {
        Calendar cal = Calendar.getInstance();
        boolean ok = false;
        if (exifDate != null) {
            try {
                cal.setTime(new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(exifDate));
                ok = true;
            } catch (Exception ignored) { }
        }
        if (!ok && takenMs > 0) {
            cal.setTimeInMillis(takenMs);
            ok = true;
        }
        if (!ok) return "";
        return cal.get(Calendar.DAY_OF_MONTH) + " " + MONTHS[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.YEAR);
    }

    private static int degrees(int orientation) {
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: return 90;
            case ExifInterface.ORIENTATION_ROTATE_180: return 180;
            case ExifInterface.ORIENTATION_ROTATE_270: return 270;
            default: return 0;
        }
    }

    private static Uri uriOf(long id) {
        return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
    }

    /* ---------------- Fotoğraf dosyası (küçültülmüş kopya, önbellekte) ---------------- */

    private WebResourceResponse image(long id) throws Exception {
        File out = new File(ctx.getCacheDir(), "ph_" + id + ".jpg");
        if (!out.exists()) makeScaled(id, out);
        return new WebResourceResponse("image/jpeg", null, new FileInputStream(out));
    }

    private synchronized void makeScaled(long id, File out) throws Exception {
        if (out.exists()) return;
        Uri u = uriOf(id);

        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
            BitmapFactory.decodeStream(in, null, b);
        }
        int sample = 1;
        while (Math.max(b.outWidth, b.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2;

        int rot = 0;
        try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
            if (in != null) rot = degrees(new ExifInterface(in).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL));
        } catch (Exception ignored) { }

        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = ctx.getContentResolver().openInputStream(u)) {
            bmp = BitmapFactory.decodeStream(in, null, d);
        }
        if (bmp == null) throw new Exception("decode failed");

        float s = Math.min(1f, (float) MAX_EDGE / Math.max(bmp.getWidth(), bmp.getHeight()));
        Matrix m = new Matrix();
        m.postScale(s, s);
        if (rot != 0) m.postRotate(rot);
        Bitmap fin = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        if (fin != bmp) bmp.recycle();

        File tmp = new File(out.getPath() + ".tmp");
        try (FileOutputStream fo = new FileOutputStream(tmp)) {
            fin.compress(Bitmap.CompressFormat.JPEG, 85, fo);
        }
        fin.recycle();
        if (!tmp.renameTo(out)) throw new Exception("rename failed");
    }

    /* ---------------- Yardımcılar ---------------- */

    private static WebResourceResponse text(String mime, String body) {
        WebResourceResponse r = new WebResourceResponse(mime, "utf-8",
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        HashMap<String, String> h = new HashMap<>();
        h.put("Cache-Control", "no-store");
        r.setResponseHeaders(h);
        return r;
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found",
                Collections.<String, String>emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private static String mime(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".html")) return "text/html";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
