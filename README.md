# Remote Desktop Sederhana

Viewer Android, host PC. WebSocket-based.

## Struktur
- `pc-host/` - Server Python (jalan di PC)
- `android-viewer/` - App Android
- `.github/workflows/` - GitHub Actions build APK otomatis

## Cara Pakai

### PC Host
```
cd pc-host
py -m pip install -r requirements.txt
py server.py
```

### Android Viewer
1. Push ke GitHub
2. Buka tab Actions, tunggu build selesai
3. Download APK dari Artifacts
4. Install di HP, isi IP PC:8765, klik Connect

### Kontrol
- Tap = klik kiri
- Long press = klik kanan
- Geser = gerakkan mouse
