package com.example.realtimeaudiorelay

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.concurrent.PriorityBlockingQueue
import kotlin.concurrent.thread

data class AudioPacket(val seqNum: Int, val data: ByteArray) : Comparable<AudioPacket> {
    override fun compareTo(other: AudioPacket): Int {
        return this.seqNum.compareTo(other.seqNum)
    }
}

class UdpAudioClient(private val context: android.content.Context, val host: String, val port: Int = 50007) : Thread() {

    @Volatile private var running = true
    private var audioTrack: AudioTrack? = null
    private val jitterBuffer = PriorityBlockingQueue<AudioPacket>()
    private var socket: DatagramSocket? = null

    private val PAYLOAD_SIZE = 1020
    private val HEADER_SIZE = 4
    private val PACKET_SIZE = PAYLOAD_SIZE + HEADER_SIZE
    private val silenceBuffer = ByteArray(PAYLOAD_SIZE) { 0 }

    private var networkThread: Thread? = null
    private var playbackThread: Thread? = null

    // Logging Tag
    companion object {
        private const val TAG = "UdpAudioClient"
    }

    override fun run() {
        Log.d(TAG, "Main client thread starting...")
        // ... (Kode pembuatan AudioTrack tetap sama) ...
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBuf * 15)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        Log.d(TAG, "AudioTrack created.")

        playbackThread = thread(name = "PlaybackThread") { playFromBuffer() }
        networkThread = thread(name = "NetworkThread") { receivePackets() }

        audioTrack?.play()
        Log.d(TAG, "AudioTrack playing. Client initialized.")
        // Main thread bisa berhenti di sini, biarkan worker threads berjalan
    }

    private fun receivePackets() {
        Log.d(TAG, "NetworkThread started.")
        try {
            socket = DatagramSocket()
            socket?.soTimeout = 1000 // Timeout 1 detik

            val serverAddress = InetAddress.getByName(host)
            val startMsg = "START".toByteArray()
            val startPacket = DatagramPacket(startMsg, startMsg.size, serverAddress, port)
            socket!!.send(startPacket)
            Log.d(TAG, "Sent 'START' to server.")

            while (running) {
                try {
                    val buffer = ByteArray(PACKET_SIZE)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket!!.receive(packet) // <-- Blocking call

                    // Jika receive() berhasil, kita masih running
                    if (!running) break // Cek lagi setelah bangun

                    val seqNum = ByteBuffer.wrap(packet.data, 0, HEADER_SIZE).int
                    val audioData = packet.data.copyOfRange(HEADER_SIZE, packet.length)
                    jitterBuffer.add(AudioPacket(seqNum, audioData))

                } catch (e: SocketTimeoutException) {
                    // Timeout -> tidak masalah, lanjutkan loop untuk cek 'running'
                    Log.v(TAG, "Network receive timeout.") // Verbose log
                    continue
                } catch (e: SocketException) {
                    // Socket ditutup -> keluar loop
                    if (running) Log.w(TAG, "SocketException while still running? Might be closing.") else Log.d(TAG,"Socket closed, network thread stopping.")
                    break // Keluar loop jika socket ditutup
                }
            } // Akhir while(running)
        } catch (e: InterruptedException) {
            Log.d(TAG, "NetworkThread interrupted.")
        } catch (e: Exception) {
            if (running) Log.e(TAG, "NetworkThread error", e)
        } finally {
            socket?.close()
            Log.i(TAG, "NetworkThread finished.") // Info penting
        }
    }

    private fun playFromBuffer() {
        Log.d(TAG, "PlaybackThread started.")
        var expectedSeqNum = 0
        val JITTER_BUFFER_TARGET_SIZE = 100

        try {
            while (running) {
                // Initial buffering (lebih aman dengan pengecekan running)
                while (running && jitterBuffer.size < JITTER_BUFFER_TARGET_SIZE) {
                    Thread.sleep(20) // Naikkan sedikit sleep time
                }
                if (!running) break // Keluar jika dihentikan saat buffering awal

                val packet = jitterBuffer.peek()
                if (packet == null) {
                    Thread.sleep(20) // Naikkan sedikit sleep time
                    continue
                }

                // ... (Logika when untuk memproses packet tetap sama) ...
                when {
                    packet.seqNum == expectedSeqNum -> {
                        val actualPacket = jitterBuffer.poll()
                        audioTrack?.write(actualPacket.data, 0, actualPacket.data.size)
                        expectedSeqNum++
                    }
                    packet.seqNum < expectedSeqNum -> {
                        jitterBuffer.poll() // Buang paket lama
                    }
                    packet.seqNum > expectedSeqNum -> {
                        val missingCount = packet.seqNum - expectedSeqNum
                        Log.w(TAG, "Packet loss! $missingCount packets missing, starting from $expectedSeqNum")
                        repeat(missingCount) {
                            audioTrack?.write(silenceBuffer, 0, silenceBuffer.size)
                        }
                        expectedSeqNum += missingCount
                    }
                }
            } // Akhir while(running)
        } catch (e: InterruptedException) {
            Log.d(TAG, "PlaybackThread interrupted.")
        } catch (e: Exception) {
            if (running) Log.e(TAG, "PlaybackThread error", e)
        } finally {
            // Cleanup AudioTrack di thread-nya sendiri
            audioTrack?.let {
                try {
                    Log.d(TAG, "Stopping AudioTrack...")
                    if (it.playState != AudioTrack.PLAYSTATE_STOPPED) {
                        it.stop()
                    }
                    Log.d(TAG, "Releasing AudioTrack...")
                    it.release()
                    Log.d(TAG, "AudioTrack stopped and released by playback thread.")
                } catch (e: Exception) {
                    Log.w(TAG, "Error during playback thread AudioTrack cleanup", e)
                }
            }
            Log.i(TAG, "PlaybackThread finished.") // Info penting
        }
    }

    // ▼▼▼ FUNGSI SHUTDOWN PALING AGRESIF ▼▼▼
    fun shutdown() {
        // Hanya jalankan sekali
        if (!running) {
            Log.w(TAG, "Shutdown already in progress.")
            return
        }
        Log.i(TAG, "--- Shutdown initiated ---")

        // 1. Set flag running = false (SEGERA!)
        running = false
        Log.d(TAG, "Step 1: running flag set to false.")

        // 2. Interrupt kedua thread (Coba bangunkan dari sleep/blocking call)
        playbackThread?.interrupt()
        Log.d(TAG, "Step 2a: PlaybackThread interrupt requested.")
        networkThread?.interrupt() // Mungkin tidak efektif jika di receive(), tapi coba saja
        Log.d(TAG, "Step 2b: NetworkThread interrupt requested.")

        // 3. Tutup socket di thread terpisah (Cara paling pasti menghentikan receive())
        // Beri jeda *sedikit* agar interrupt() sempat diproses
        thread(name = "SocketCloserThread") {
            try {
                Thread.sleep(50) // Jeda singkat
                Log.d(TAG, "Step 3: Closing socket now...")
                val sock = socket
                socket = null // Hindari penggunaan ganda
                sock?.close() // Ini akan melempar SocketException di receive()
                Log.i(TAG, "Step 3: Socket closed by shutdown.") // Info penting
            } catch (e: InterruptedException) {
                Log.w(TAG, "SocketCloserThread interrupted.")
            } catch (e: IOException) {
                Log.e(TAG, "IOException closing socket during shutdown", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error closing socket during shutdown", e)
            }
        }
        Log.i(TAG, "--- Shutdown sequence complete. Waiting for threads to finish... ---")
    }
}