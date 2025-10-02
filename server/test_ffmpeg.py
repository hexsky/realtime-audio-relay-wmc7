from pydub import AudioSegment
import os

print("FFMPEG path (pydub thinks):", AudioSegment.converter)

# Coba load file mp3 kecil (bisa ganti dengan file kamu)
try:
    sound = AudioSegment.from_file("test.mp3", format="mp3")
    print("✅ Berhasil load MP3, durasi:", len(sound) / 1000, "detik")
except Exception as e:
    print("❌ Gagal load MP3:", e)
