package tv.seans.launcher;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Home tuşuna basıldığında Google TV kendi ana ekranını açar.
 * Bu servis o ekranın açıldığını görünce Seans'ı öne getirir.
 * İzlenen paketler res/xml/accessibility.xml içinde (packageNames) tanımlıdır.
 * Kullanıcı Ayarlar > Erişilebilirlik > Seans'tan açar.
 */
public class HomeService extends AccessibilityService {

    private long last = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        long now = SystemClock.uptimeMillis();
        if (now - last < 700) return;
        last = now;

        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    @Override
    public void onInterrupt() { }
}
