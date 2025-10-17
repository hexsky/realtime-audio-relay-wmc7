# stream_server_udp.py (Non-Debug, Reconnect OK, Fast Streaming)
import socket
import time
from pydub import AudioSegment
import sys
import struct
import os

HOST = '0.0.0.0'
PORT = 50007
PAYLOAD_SIZE = 1020

def serve(mp3_path):
    # Load audio once
    try:
        audio = AudioSegment.from_file(mp3_path)
        audio = audio.set_frame_rate(44100).set_sample_width(2).set_channels(2)
        pcm_data = audio.raw_data
        total_bytes = len(pcm_data)
        print(f"Audio loaded: {os.path.basename(mp3_path)} ({total_bytes} bytes)", flush=True)
    except Exception as e:
        print(f"Error loading audio file: {e}", flush=True)
        return

    # Create socket once
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.bind((HOST, PORT))
        print(f"Server UDP listening on {HOST}:{PORT}", flush=True)
    except OSError as e:
        print(f"Error binding socket: {e}. Port might be in use.", flush=True)
        return

    # Main server loop
    while True:
        client_address = None
        print("\nWaiting for client 'START' ping...", flush=True)
        
        try:
            # Wait for client
            data, client_address = s.recvfrom(1024)
            
            if data.decode('utf-8') == 'START':
                print(f"Client connected from: {client_address}", flush=True)
                sequence_number = 0
                bytes_sent_this_session = 0
                
                # Inner streaming loop (no sleep)
                inner_loop_running = True
                while inner_loop_running and bytes_sent_this_session < total_bytes:
                    chunk_start = bytes_sent_this_session
                    chunk_end = chunk_start + PAYLOAD_SIZE
                    chunk = pcm_data[chunk_start:chunk_end]
                    
                    if not chunk:
                        inner_loop_running = False
                        continue

                    seq_num_bytes = struct.pack('>I', sequence_number)
                    packet_data = seq_num_bytes + chunk

                    try:
                        bytes_sent = s.sendto(packet_data, client_address)
                        if bytes_sent > 0:
                            bytes_sent_this_session += len(chunk)
                            sequence_number += 1
                        else:
                            print(f"Send error for {client_address}. Assuming disconnect.", flush=True)
                            inner_loop_running = False
                            continue

                        # --- NO SLEEP ---

                    # Catch specific error expected on disconnect first
                    except ConnectionResetError:
                        print(f"Client {client_address} likely disconnected (ConnectionResetError).", flush=True)
                        inner_loop_running = False
                        continue 
                    # Catch other potential socket/OS errors
                    except (socket.error, OSError) as send_err:
                        print(f"Send error for {client_address}: {send_err}.", flush=True)
                        inner_loop_running = False
                        continue
                    # Catch any other unexpected error during send
                    except Exception as inner_loop_err:
                         print(f"Unexpected error during send for {client_address}: {inner_loop_err}.", flush=True)
                         inner_loop_running = False
                         continue
                # --- End inner loop ---
                
                if bytes_sent_this_session >= total_bytes:
                    print(f"Finished streaming to {client_address}.", flush=True)
                else:
                    print(f"Streaming incomplete for {client_address}.", flush=True)

            else: # if not 'START'
                print(f"Received invalid start message from {client_address}. Ignoring.", flush=True)

        # --- Error handling for the main try block ---
        except (socket.error, OSError) as session_err:
             if client_address:
                 print(f"Socket error with {client_address}: {session_err}", flush=True)
             else:
                 print(f"Socket error while waiting for client: {session_err}", flush=True)
        except Exception as general_err:
             print(f"An unexpected error occurred: {general_err}", flush=True)
        
        # Loop automatically continues

    # Socket closing logic removed as the loop is infinite

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print('Usage: python stream_server_udp.py path/to/file.mp3')
        sys.exit(1)
    serve(sys.argv[1])