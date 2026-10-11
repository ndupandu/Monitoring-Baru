# Monitoring Client (APK)

Aplikasi Android yang membungkus dashboard monitoring. Fitur:

* Layar izin sebelum login: Lokasi dan Notifikasi (pop-up izin bawaan Android).
* GPS diambil lewat Android, jadi tombol "Lokasi saya" di halaman Peta berfungsi walau dashboard memakai HTTP.
* Notifikasi "Konsol selesai": hanya muncul bila aplikasi sedang TIDAK dibuka. Kalau aplikasi dibuka lagi sebelum
  perintah selesai, tidak ada notifikasi.
* Link luar (Google Maps / Navigasi) dibuka di aplikasinya masing-masing.

## Cara build lewat GitHub

1. Buat repository baru di GitHub (boleh private), lalu unggah seluruh isi folder ini (termasuk folder `.github`).
2. Di repo: **Settings > Secrets and variables > Actions > tab Variables > New repository variable**
   * Name: `SERVER_URL`
   * Value: alamat dashboard, mis. `http://192.168.1.10:8080/` atau `https://monitor.domainmu.com/`
3. Buka tab **Actions > Build APK > Run workflow**. Setelah selesai (sekitar 3-5 menit), unduh **monitoring-client-apk**
   dari bagian *Artifacts*, ekstrak zip-nya, lalu pasang file `.apk` di HP (izinkan "Pasang dari sumber ini").

## Tanda tangan tetap (disarankan)

Tanpa langkah ini APK memakai kunci debug yang bisa berbeda tiap build, sehingga HP menolak memperbarui aplikasi di atas
versi lama ("aplikasi gagal dipasang"). Buat keystore sekali saja:

    keytool -genkeypair -v -keystore release.jks -alias monitoring -keyalg RSA -keysize 2048 -validity 10000
    base64 -w0 release.jks     # salin hasilnya

Tambahkan empat **Secrets** (Settings > Secrets and variables > Actions > Secrets):

| Nama | Isi |
| --- | --- |
| `KEYSTORE_B64` | hasil `base64 -w0 release.jks` |
| `KEYSTORE_PASSWORD` | sandi keystore |
| `KEY_ALIAS` | `monitoring` |
| `KEY_PASSWORD` | sandi kunci |

Simpan file `release.jks` dengan aman (jangan di-commit). Kalau hilang, aplikasi tidak bisa diperbarui di atas versi lama.

## Catatan

* Notifikasi bekerja untuk perintah yang dikirim dari halaman Konsol / WiFi / Registrasi di dalam aplikasi.
  Saat aplikasi masuk ke latar belakang dan masih ada perintah berjalan, Android menampilkan notifikasi tetap
  "Menunggu perintah konsol selesai" (syarat Android untuk layanan latar belakang) lalu diganti notifikasi hasil.
  Pemantauan berhenti otomatis setelah 8 menit.
* Aplikasi membuka `SERVER_URL` apa adanya. Ganti nilainya lalu build ulang bila alamat dashboard berubah.
