# stream_server.py
import socket
import json
from pydub import AudioSegment
import time
import selectors

HOST = '0.0.0.0'
PORT = 50007
CHUNK_MS = 100  # Kirim dalam potongan 100ms

def mp3_to_pcm_chunks(mp3_path, chunk_ms=100):
    audio = AudioSegment.from_file(mp3_path)
    audio = audio.set_frame_rate(44100).set_sample_width(2).set_channels(2)
    
    total_ms = len(audio)
    for start in range(0, total_ms, chunk_ms):
        chunk = audio[start:start+chunk_ms]
        yield chunk.raw_data

def serve(mp3_path):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((HOST, PORT))
    s.listen(1)
    print(f"Streaming server listening on {HOST}:{PORT}")

    while True:
        conn, addr = s.accept()
        print('Client connected:', addr)
        
        is_paused = False
        audio_generator = mp3_to_pcm_chunks(mp3_path, CHUNK_MS)

        # Kirim header
        header = json.dumps({
            'sample_rate': 44100, 'channels': 2, 'sample_width': 2
        }) + '\n'
        conn.sendall(header.encode('utf-8'))
        
        # Buat koneksi non-blocking untuk bisa menerima perintah
        conn.setblocking(False)

        try:
            while True:
                # Cek apakah ada perintah masuk dari client
                try:
                    command = conn.recv(1024).decode('utf-8').strip()
                    if command == 'PAUSE':
                        is_paused = True
                        print("-> Server: PAUSED")
                    elif command == 'PLAY':
                        is_paused = False
                        print("-> Server: PLAYING")
                except BlockingIOError:
                    # Tidak ada perintah, lanjutkan
                    pass

                if not is_paused:
                    try:
                        chunk = next(audio_generator)
                        conn.sendall(chunk)
                    except StopIteration:
                        print("End of audio file.")
                        break
                    except BlockingIOError:
                        # Buffer client penuh, tunggu sejenak
                        time.sleep(0.05)


                # Atur kecepatan streaming agar sesuai realtime
                time.sleep(CHUNK_MS / 1000.0)

        except (BrokenPipeError, ConnectionResetError):
            print('Client disconnected')
        finally:
            conn.close()

if __name__ == '__main__':
    import sys
    if len(sys.argv) < 2:
        print('Usage: python stream_server.py path/to/file.mp3')
        sys.exit(1)
    serve(sys.argv[1])