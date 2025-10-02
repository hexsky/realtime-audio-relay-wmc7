package com.example.realtimeaudiorelay
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.Socket
import kotlin.concurrent.thread

class SimpleTcpAudioClient(val host: String, val port: Int = 50007) : Thread() {
    private var running = true
    private var audioTrack: AudioTrack? = null
    private var outputStream: OutputStream? = null
    override fun run() {
        try {
            val socket = Socket(host, port)
            outputStream = socket.getOutputStream()

            val input = BufferedInputStream(socket.getInputStream())
            // read header line (until '\n')
            val headerSb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b == -1 || b == '\n'.code) break
                headerSb.append(b.toChar())
            }
            val header = JSONObject(headerSb.toString())
            val sampleRate = header.getInt("sample_rate")
            val channels = header.getInt("channels")
            val sampleWidth = header.getInt("sample_width")


            val channelConfig =
                if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4,
                AudioTrack.MODE_STREAM
            )
            audioTrack.play()


            val buffer = ByteArray(4096)
            while (running) {
                val read = input.read(buffer)
                if (read <= 0) break
                var written = 0
                while (written < read) {
                    val w = audioTrack.write(buffer, written, read - written)
                    if (w <= 0) break
                    written += w
                }
            }


            audioTrack.stop()
            audioTrack.release()
            socket.close()
        } catch (e: Exception) {
            Log.e("TcpAudioClient", "error: ${e.message}")
        }
    }

    fun pausePlayback() {
        audioTrack?.pause()
        sendCommand("PAUSE\n")
    }

    fun resumePlayback() {
        audioTrack?.play()
        sendCommand("PLAY\n")
    }

    private fun sendCommand(command: String) {
        // Perintah harus dikirim di thread terpisah
        thread {
            try {
                outputStream?.write(command.toByteArray(Charsets.UTF_8))
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e("TcpAudioClient", "Failed to send command: $command", e)
            }
        }
    }

    fun shutdown() {
        running = false
        // Tutup socket agar thread berhenti
        try {
            outputStream?.close()
        } catch (e: Exception) { /* abaikan */ }
    }
}