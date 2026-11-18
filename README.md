# LitedirtVM — Mesin Virtual & Container Android 10 (API 29)

**LitedirtVM** adalah aplikasi host Android (`minSdk = 29`, Kotlin + Jetpack Compose + Material 3) yang dirancang menyerupai VMOS untuk mengelola dan menjalankan instance **Android 10 (API 29)** secara terisolasi di dalam aplikasi host.

---

## 1. Pilihan Arsitektur Engine: PRoot/Chroot Container vs QEMU

LitedirtVM mendukung dua profil arsitektur yang dapat dipilih per instance:

1. **PRoot / Chroot Container (`PROOT_CONTAINER` — Direkomendasikan)**
   - **Cara kerja:** Menjalankan rootfs Android 10 di atas kernel Linux perangkat host menggunakan isolasi direktori (`filesDir/virtualos/instances/<id>/rootfs/`) dan translasi *syscall* (`proot`).
   - **Kelebihan:** Ringan, hemat baterai, dan tidak memerlukan emulasi instruksi CPU saat ABI guest cocok dengan ABI host (`arm64-v8a` / `x86_64`).
2. **QEMU System Emulation (`QEMU_SYSTEM`)**
   - **Cara kerja:** Emulasi perangkat keras ARM64/x86_64 terpisah dengan *virtual framebuffer* melalui port VNC lokal (`127.0.0.1:<vncPort>`).
   - **Batasan:** Tanpa akselerasi hardware `/dev/kvm`, QEMU harus memakai translasi TCG (*software emulation*) yang sangat lambat pada Android komersial non-root.

---

## 2. Batasan Teknis Jujur Tanpa Akses Sistem / Kernel Host

Agar transparan dan akurat pada perangkat Android 10+ standar (non-root):

- **`chroot()` & `mount()` Asli:** Panggilan syscall `chroot()` dan `mount()` membutuhkan kapabilitas kernel `CAP_SYS_ADMIN` (root host). Aplikasi sandbox biasa tidak bisa melakukan mount partisi `system.img`/`ext4` secara langsung ke VFS kernel.
- **Pembatasan `W^X` & SELinux (`targetSdk >= 29`):** Android melarang eksekusi biner (`execve`) yang diunduh dari direktori data aplikasi (`filesDir`). Seluruh komponen native/kode yang dieksekusi di host harus dibundel di dalam APK/AAB ter-sign.
- **Akses `/dev/kvm`:** Pada mayoritas perangkat Android komersial, node `/dev/kvm` tidak tersedia atau diblokir oleh kebijakan SELinux vendor.
- **Binder IPC & SurfaceFlinger Terpisah:** Menjalankan framework Android AOSP penuh (*Zygote + SurfaceFlinger + AudioFlinger*) membutuhkan virtualisasi Binder/Ashmem di level kernel atau daemon host istimewa. Pada perangkat non-root tanpa biner server VNC eksternal, LitedirtVM menjalankan **Guest Session Engine Android 10 terisolasi** pada rootfs instance (`build.prop`, `VirtualShared`, shell `su` virtual, dan kontainer metadata APK/XAPK).
- **Jendela Mengambang (Floating Window):** Untuk mematuhi kebijakan izin Google Play, LitedirtVM menyediakan **Jendela Mengambang In-App** (dapat digeser & diubah ukurannya) serta **Android System Picture-in-Picture (PiP)** resmi tanpa meminta izin overlay `SYSTEM_ALERT_WINDOW` yang sensitif.

---

## 3. Struktur Proyek & Modul

```text
app/src/main/java/com/example/
├── MainActivity.kt                  # Entry point edge-to-edge + Picture-in-Picture
├── di/
│   └── AppContainer.kt              # Dependency container bersama UI & Service
├── data/
│   ├── model/VirtualModels.kt       # Entity Room, konfigurasi VM, telemetri, status Shizuku
│   ├── local/
│   │   ├── VirtualOsDatabase.kt     # Room Database + DAO
│   │   └── AppPreferences.kt        # DataStore (Tema Gelap/Terang, FGS, active ID)
│   └── repository/VirtualOsRepository.kt
├── rom/
│   └── RomImageManager.kt           # Manajer ROM khusus Android 10 (API 29) + SHA-256
├── engine/
│   ├── ContainerEngineConfig.kt     # Pembentuk argumen PRoot/QEMU & dokumentasi arsitektur
│   ├── SystemTelemetrySampler.kt    # Pemantau RAM, CPU, & Storage real-time
│   └── VirtualRuntimeEngine.kt      # Orkestrasi multi-instance, shell tamu, VNC probe, clipboard
├── instance/
│   └── InstanceManager.kt           # Buat, kloning, hapus, ekspor/impor bundle .vos
├── renderer/
│   ├── VirtualScreenRenderer.kt     # SurfaceView + layar virtual Android 10 + navigasi + rotasi
│   └── FloatingVirtualWindow.kt     # Jendela mengambang in-app (geser/ubah ukuran/PiP)
├── installer/
│   ├── ApkXapkInstaller.kt          # Parser & installer APK/XAPK ke /data/app/ virtual
│   └── SharedStorageBridge.kt       # Berbagi berkas dua arah Host <-> /sdcard/VirtualShared
├── privilege/
│   └── HostPrivilegeManager.kt      # Integrasi Shizuku API, deteksi su host & /dev/kvm, fallback
├── service/
│   └── VirtualOsForegroundService.kt# Layanan latar depan (dataSync) + notifikasi status
└── ui/
    ├── VirtualOsViewModel.kt        # State management MVVM (StateFlow)
    ├── VirtualOsApp.kt              # Navigasi adaptif (NavigationBar / NavigationRail)
    ├── theme/                       # Material 3 Dark/Light + Space Grotesk / Plus Jakarta / JetBrains Mono
    └── screens/
        ├── VmDisplayScreen.kt       # Layar VM, switcher multi-instance, rotasi, fullscreen
        ├── InstancesScreen.kt       # Manajer multi-instance (buat, kloning, ekspor/impor)
        ├── InstanceSettingsScreen.kt# Pengaturan resolusi, DPI, spoof IMEI/model, mock GPS, NAT/Bridge
        ├── AppsAndFilesScreen.kt    # Import APK/XAPK & Berbagi Berkas 2-Arah
        └── SystemAndRomScreen.kt    # ROM Android 10, Telemetri, Shizuku/Root, Tema, Batasan
```

---

## 4. Cara Build & Uji

1. **Build Debug APK via Gradle:**
   ```bash
   gradle :app:assembleDebug
   ```
2. **Jalankan Unit & Robolectric Tests:**
   ```bash
   gradle :app:testDebugUnitTest
   ```
3. **Alur Pengujian Fitur Utama:**
   - **Tab Layar VM:** Ketuk *Boot Android 10*, buka aplikasi *Terminal* dan jalankan `su` serta `getprop` untuk memverifikasi `ro.build.version.sdk=29` dan dialog otorisasi Superuser.
   - **Tab Instance:** Buat instance kedua, kloning instance, ekspor ke `.vos`, lalu jalankan dua instance bersamaan dan berpindah via strip *switcher* di atas layar VM.
   - **Tab APK & Berkas:** Pilih `.apk` atau `.xapk` dari penyimpanan host via SAF untuk dipasang ke instance pilihan, serta uji salin berkas dua arah ke `/sdcard/VirtualShared`.
   - **Tab Sistem:** Cek telemetri RAM/CPU/Storage, status Shizuku/Root host, impor/unduh ROM Android 10 (API 29), dan ubah tema Gelap/Terang.
