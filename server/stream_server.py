# stream_server.py
import socket
import json
from pydub import AudioSegment
import time
import sys

HOST = '0.0.0.0'
PORT = 50007
CHUNK_MS = 100

def mp3_to_pcm_chunks(audio_segment, chunk_ms=100):
    total_ms = len(audio_segment)
    for start in range(0, total_ms, chunk_ms):
        chunk = audio_segment[start:start+chunk_ms]
        yield chunk.raw_data

def serve(mp3_path):
    try:
        audio = AudioSegment.from_file(mp3_path)
        audio = audio.set_frame_rate(44100).set_sample_width(2).set_channels(2)
        total_duration_ms = len(audio)
    except Exception as e:
        print(f"Error loading audio file: {e}")
        return

    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind((HOST, PORT))
    s.listen(1)
    print(f"Streaming server listening on {HOST}:{PORT}")

    while True:
        conn, addr = s.accept()
        print('Client connected:', addr)
        
        is_paused = False
        audio_generator = mp3_to_pcm_chunks(audio)

        header = json.dumps({
            'sample_rate': audio.frame_rate, 'channels': audio.channels,
            'sample_width': audio.sample_width, 'duration_ms': total_duration_ms
        }) + '\n'
        conn.sendall(header.encode('utf-8'))
        
        conn.setblocking(False)

        try:
            while True:
                try:
                    command_raw = conn.recv(1024)
                    # ▼▼▼ FIX 2.1: Deteksi disconnect saat pause ▼▼▼
                    if not command_raw:
                        print('Client disconnected (cleanly)')
                        break
                    # ▲▲▲ FIX 2.1 ▲▲▲
                    
                    command = command_raw.decode('utf-8').strip()
                    if command == 'PAUSE':
                        is_paused = True
                        print("-> Server: PAUSED")
                    elif command == 'PLAY':
                        is_paused = False
                        print("-> Server: PLAYING")
                    elif command.startswith('SEEK_'):
                        target_ms = int(command.split('_')[1])
                        print(f"-> Server: SEEKING to {target_ms}ms")
                        new_segment = audio[target_ms:]
                        audio_generator = mp3_to_pcm_chunks(new_segment)
                        # ▼▼▼ FIX 1.2: Jangan auto-play setelah seek ▼▼▼
                        # is_paused = False (BARIS INI DIHAPUS)
                        # ▲▲▲ FIX 1.2 ▲▲▲
                except BlockingIOError:
                    pass

                if not is_paused:
                    try:
                        chunk = next(audio_generator)
                        conn.sendall(chunk)
                    except StopIteration:
                        print("End of audio file.")
                        break
                    except BlockingIOError:
                        time.sleep(0.05)

                time.sleep(CHUNK_MS / 1000.0)
        
        # ▼▼▼ FIX 2.2: Tangani ConnectionAbortedError ▼▼▼
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            print('Client disconnected')
        # ▲▲▲ FIX 2.2 ▲▲▲
        finally:
            conn.close()

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python stream_server.py path/to/file.mp3')
        sys.exit(1)
    serve(sys.argv[1])