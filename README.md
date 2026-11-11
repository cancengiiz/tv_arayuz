# Seans

Google TV / Android TV projektörler için reklamsız, sade bir ana ekran.
Arayüz `app/src/main/assets/index.html` içindeki web sayfasıdır. Android tarafı onu tam ekran gösterir, kurulu uygulamaları listeler ve başlatır.

Xming Page One (Google TV) için hazırlandı. Diğer Google TV cihazlarında da çalışması beklenir.

## APK nasıl oluşur

`main` dalına her gönderimde GitHub Actions APK'yı derler:

- **Actions** sekmesinde `seans-apk` olarak indirilebilir.
- **Releases** altında `son-surum` yayınına `seans.apk` olarak eklenir.

Projektörden doğrudan indirme adresi (repo herkese açıksa):

```
https://github.com/cancengiiz/tv_arayuz/releases/download/son-surum/seans.apk
```

## Projektöre kurulum

1. **Geliştirici seçeneklerini aç:** Ayarlar → Sistem → Hakkında → *Android TV OS derlemesi* satırına 7 kez tıkla.
2. **Downloader uygulamasını kur:** Play Store'dan *Downloader* (AFTVnews).
3. **Bilinmeyen kaynak izni:** Ayarlar → Uygulamalar → Güvenlik ve kısıtlamalar → Bilinmeyen kaynaklar → *Downloader*'ı aç.
4. **APK'yı indir ve kur:** Downloader'a yukarıdaki adresi yaz, indir, *Yükle*.
5. **Seans'ı aç:** Uygulamalar listesinde *Seans* görünür.

## Home tuşunun Seans'ı açması

**Yöntem A — Erişilebilirlik (kolay, geri alınabilir)**
Seans → Ayarlar → *Home tuşuyla aç* → Erişilebilirlik ekranında *Seans: Home tuşuyla aç* servisini etkinleştir.
Home'a basınca Google TV ana ekranı bir an görünür, ardından Seans öne gelir. Servis ekrandaki içeriği okumaz; yalnızca Google TV ana ekranının açıldığını algılar.
Google TV güncellemelerinden sonra izin kapanabilir. O durumda yeniden aç.

**Yöntem B — ADB ile Google TV ana ekranını kapatmak (kesin, ileri seviye)**

```
adb connect PROJEKTOR_IP
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

Geri almak için:

```
adb shell pm enable com.google.android.apps.tv.launcherx
```

Bu yöntemle kumandadaki bazı kısayol tuşları (YouTube, Netflix gibi) çalışmayabilir. Önce A'yı dene.

## Arka plan fotoğrafları

Fotoğraflar GitHub'a konmaz, projektörde durur. Seans ilk açılışta fotoğraflara erişim izni ister.

- Projektörde **Seans** adlı bir klasör varsa yalnızca oradaki fotoğraflar kullanılır, yoksa projektördeki tüm fotoğraflar.
- Fotoğrafları aktarmanın en kolay yolu: telefona ve projektöre **Send Files to TV** uygulamasını kurup fotoğrafları telefondan göndermek.
- Yeni fotoğraf eklediğinde Seans'a dönmen yeterli, liste kendiliğinden yenilenir. Ayarlardaki "Fotoğrafları yenile" de aynı işi yapar.
- Dikey ve kare fotoğraflar sağda çerçeveli gösterilir, yatay fotoğraflar tam ekran.

## Kumanda

| Tuş | İşlev |
|---|---|
| ← → | Alt şeritte gez |
| OK | Aç |
| Menü | Ayarlar · "Tüm uygulamalar"da: alt şeride ekle / çıkar |
| Geri | Paneli kapat · ana ekranda: ekran koruyucu |

## Geliştirme

Arayüzü tarayıcıda denemek için `app/src/main/assets/index.html` dosyasını açman yeterli. Klavyedeki ok tuşları, Enter, M ve Esc kumandanın yerine geçer.
Köprü (`window.SeansNative`): `getApps()`, `launch(pkg)`, `openSystemSettings()`, `openAccessibilitySettings()`.
