# stream_server_udp_multicast.py (Multi-client UDP Streaming)
import socket
import time
from pydub import AudioSegment
import sys
import struct
import os
import threading
from typing import Set, Tuple

HOST = '0.0.0.0'
PORT = 50007
PAYLOAD_SIZE = 1020

class MulticastUdpServer:
    def __init__(self, mp3_path: str):
        self.mp3_path = mp3_path
        self.clients: Set[Tuple[str, int]] = set()
        self.clients_lock = threading.Lock()
        self.audio_data = None
        self.total_bytes = 0
        self.is_streaming = False
        self.should_stream = False
        self.socket = None
        self.stream_thread = None
        self.broadcast_enabled = False  # Flag to enable auto-broadcast for new clients
        
    def load_audio(self):
        """Load audio file once"""
        try:
            audio = AudioSegment.from_file(self.mp3_path)
            audio = audio.set_frame_rate(44100).set_sample_width(2).set_channels(2)
            self.audio_data = audio.raw_data
            self.total_bytes = len(self.audio_data)
            print(f"Audio loaded: {os.path.basename(self.mp3_path)} ({self.total_bytes} bytes)", flush=True)
            return True
        except Exception as e:
            print(f"Error loading audio file: {e}", flush=True)
            return False
    
    def create_socket(self):
        """Create and bind UDP socket"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.socket.bind((HOST, PORT))
            self.socket.settimeout(0.1)  # Non-blocking with timeout
            print(f"Server UDP listening on {HOST}:{PORT}", flush=True)
            return True
        except OSError as e:
            print(f"Error binding socket: {e}. Port might be in use.", flush=True)
            return False
    
    def handle_client_messages(self):
        """Handle incoming client messages (START, STOP)"""
        while True:
            try:
                data, client_address = self.socket.recvfrom(1024)
                message = data.decode('utf-8').strip()
                
                if message == 'START':
                    with self.clients_lock:
                        is_new_client = client_address not in self.clients
                        if is_new_client:
                            self.clients.add(client_address)
                            print(f"Client connected: {client_address} (Total: {len(self.clients)})", flush=True)
                            
                            # Auto-broadcast to new client if broadcast is enabled
                            if self.broadcast_enabled and not self.is_streaming:
                                print(f"Auto-starting broadcast for new client: {client_address}", flush=True)
                                self.start_broadcast_for_client(client_address)
                        else:
                            print(f"Client reconnected: {client_address}", flush=True)
                
                elif message == 'STOP':
                    with self.clients_lock:
                        if client_address in self.clients:
                            self.clients.remove(client_address)
                            print(f"Client disconnected: {client_address} (Total: {len(self.clients)})", flush=True)
                
            except socket.timeout:
                continue
            except Exception as e:
                print(f"Error handling client message: {e}", flush=True)
    
    def broadcast_audio(self, target_clients=None):
        """Broadcast audio to all connected clients or specific clients"""
        self.is_streaming = True
        
        if target_clients:
            print(f"Starting audio broadcast to {len(target_clients)} client(s)...", flush=True)
        else:
            print("Starting audio broadcast to all clients...", flush=True)
            
        sequence_number = 0
        bytes_sent = 0
        
        while bytes_sent < self.total_bytes:
            chunk_start = bytes_sent
            chunk_end = chunk_start + PAYLOAD_SIZE
            chunk = self.audio_data[chunk_start:chunk_end]
            
            if not chunk:
                break
            
            # Prepare packet
            seq_num_bytes = struct.pack('>I', sequence_number)
            packet_data = seq_num_bytes + chunk
            
            # Send to target clients or all clients
            if target_clients:
                # Send to specific clients only
                for client_address in target_clients:
                    try:
                        self.socket.sendto(packet_data, client_address)
                    except Exception as e:
                        print(f"Error sending to {client_address}: {e}", flush=True)
            else:
                # Send to all clients
                with self.clients_lock:
                    clients_copy = self.clients.copy()
                    disconnected_clients = set()
                    
                    for client_address in clients_copy:
                        try:
                            self.socket.sendto(packet_data, client_address)
                        except Exception as e:
                            print(f"Error sending to {client_address}: {e}", flush=True)
                            disconnected_clients.add(client_address)
                    
                    # Remove disconnected clients
                    for client in disconnected_clients:
                        self.clients.discard(client)
                        print(f"Removed disconnected client: {client}", flush=True)
            
            bytes_sent += len(chunk)
            sequence_number += 1
            
            # No sleep for fast streaming like the working version
        
        print(f"Audio broadcast complete. Sent {bytes_sent} bytes in {sequence_number} packets.", flush=True)
        self.is_streaming = False
    
    def start_broadcast_for_client(self, client_address):
        """Start broadcast for a specific client in a separate thread"""
        def broadcast_thread():
            self.broadcast_audio(target_clients=[client_address])
        
        thread = threading.Thread(target=broadcast_thread, daemon=True)
        thread.start()
    
    def streaming_loop(self):
        """Stream audio when manually triggered"""
        with self.clients_lock:
            if len(self.clients) == 0:
                print("No clients connected. Streaming cancelled.", flush=True)
                self.should_stream = False
                return
        
        # Stream once, then stop
        self.broadcast_audio()
        self.should_stream = False
    
    def wait_for_enter(self):
        """Wait for user to press Enter to enable broadcasting"""
        try:
            input("Press Enter to enable broadcasting (or Ctrl+C to quit)...\n")
            
            with self.clients_lock:
                if len(self.clients) == 0:
                    print("No clients connected yet. Broadcast will start automatically when clients connect.", flush=True)
                else:
                    print(f"Broadcasting to {len(self.clients)} connected client(s)...", flush=True)
                    # Broadcast to all currently connected clients
                    self.should_stream = True
                    if self.stream_thread is None or not self.stream_thread.is_alive():
                        self.stream_thread = threading.Thread(target=self.streaming_loop, daemon=True)
                        self.stream_thread.start()
            
            # Enable auto-broadcast for new clients
            self.broadcast_enabled = True
            print("Broadcast mode enabled. New clients will automatically receive broadcasts.", flush=True)
            
            # Keep server running
            while True:
                time.sleep(1)
                    
        except EOFError:
            # Handle when input is not available
            time.sleep(1)
    
    def run(self):
        """Main server loop"""
        if not self.load_audio():
            return
        
        if not self.create_socket():
            return
        
        # Start client message handler in separate thread
        message_thread = threading.Thread(target=self.handle_client_messages, daemon=True)
        message_thread.start()
        
        print("Server ready. Waiting for clients to connect...", flush=True)
        print("Press Enter to enable broadcast mode.", flush=True)
        print("After Enter is pressed, each new client will automatically receive a broadcast.", flush=True)
        
        try:
            # Wait for Enter key to start broadcasting
            self.wait_for_enter()
                    
        except KeyboardInterrupt:
            print("\n\nServer shutting down...", flush=True)
        finally:
            if self.socket:
                self.socket.close()
            print("Server stopped.", flush=True)

def main():
    if len(sys.argv) < 2:
        print('Usage: python stream_server_udp_multicast.py path/to/file.mp3')
        sys.exit(1)
    
    server = MulticastUdpServer(sys.argv[1])
    server.run()

if __name__ == '__main__':
    main()