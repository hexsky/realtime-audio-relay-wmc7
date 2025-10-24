# Panduan Penggunaan (Usage Guide)
Untuk menjalankan proyek ini, ikuti langkah-langkah persiapan di sisi server (laptop) dan client (smartphone).

# Server (Laptop)

1. Instalasi Dependensi: Pastikan Anda memiliki Python 3, pydub, dan ffmpeg yang terinstal di sistem Anda.
    - pip install pydub
2. Hubungkan laptop Anda ke jaringan Wi-Fi. Smartphone client harus berada di jaringan yang sama.
3. Dapatkan Alamat IP Laptop
  - Buka Command Prompt (Windows) atau Terminal (macOS/Linux) dan jalankan perintah yang sesuai untuk menemukan alamat IP lokal Anda.
      - Untuk Windows: ipconfig
      - Untuk macOS atau Linux: ifconfig atau ip addr show
  - Cari dan catat IPv4 Address Anda (contoh: 192.168.1.10).
5. Jalankan Server: Buka terminal di dalam folder server/ dan jalankan skrip stream_server.py dengan path ke file audio yang ingin Anda putar.
    - python stream_server_udp_multicast.py "path/lengkap/ke/musik.mp3"
    - Server akan berjalan dan siap menerima koneksi.

# Client (Smartphone)

1. Instalasi Aplikasi: Pasang (install) file APK RealtimeAudioRelay.apk atau app-debug.apk di smartphone Android Anda.
2. Hubungkan TWS/Earphone Bluetooth: Pastikan TWS Anda sudah terhubung dengan smartphone melalui menu Bluetooth.
3. Koneksi Jaringan: Hubungkan smartphone ke jaringan Wi-Fi yang sama dengan laptop.
4. Menjalankan Streaming: Buka aplikasi RealtimeAudioRelay di smartphone Anda.
5. Pada kolom input, masukkan alamat IPv4 Address laptop yang sudah Anda catat sebelumnya.
6. Tekan tombol "Connect".
7. Setelah terhubung, audio siap di putar.
8. Ketik enter pada server di laptop setelah terhubung, untuk memutar audio.
9. Untuk berhenti, tekan tombol "Disconnect".
