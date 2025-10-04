package com.example.realtimeaudiorelay // Ganti dengan package name Anda

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

    interface PlaybackProgressListener {
        fun onDurationReceived(totalMs: Int)
        fun onProgressUpdate(currentMs: Int)
        fun onCompletion()
    }

    private var running = true
    private var audioTrack: AudioTrack? = null
    private var outputStream: OutputStream? = null
    private var progressListener: PlaybackProgressListener? = null
    private var sampleRate = 44100

    // ▼▼▼ PROPERTI BARU UNTUK MENGATASI BUG SEEK ▼▼▼
    private var playbackStartOffsetMs: Long = 0
    private var framesAtStartOfSegment: Long = 0
    // ▲▲▲ PROPERTI BARU UNTUK MENGATASI BUG SEEK ▲▲▲

    fun setProgressListener(listener: PlaybackProgressListener) {
        this.progressListener = listener
    }

    override fun run() {
        try {
            val socket = Socket(host, port)
            outputStream = socket.getOutputStream()
            val input = BufferedInputStream(socket.getInputStream())

            val headerSb = StringBuilder()
            while (running) {
                val b = input.read()
                if (b == -1 || b == '\n'.code) break
                headerSb.append(b.toChar())
            }
            if (!running) return

            val header = JSONObject(headerSb.toString())
            sampleRate = header.getInt("sample_rate")
            val totalDurationMs = header.getInt("duration_ms")
            progressListener?.onDurationReceived(totalDurationMs)

            val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
            val minBuf = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT)
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC, sampleRate, channelConfig,
                AudioFormat.ENCODING_PCM_16BIT, minBuf * 4, AudioTrack.MODE_STREAM
            )
            audioTrack?.play()

            // Inisialisasi offset di awal
            playbackStartOffsetMs = 0
            framesAtStartOfSegment = 0

            // Thread untuk memantau progres
            thread {
                while (running) {
                    audioTrack?.playbackHeadPosition?.let { pos ->
                        // ▼▼▼ PERHITUNGAN PROGRES BARU ▼▼▼
                        val currentFrames = pos.toLong()
                        val framesSinceSegmentStart = currentFrames - framesAtStartOfSegment
                        val millisSinceSegmentStart = (framesSinceSegmentStart * 1000L) / sampleRate
                        val totalCurrentMs = playbackStartOffsetMs + millisSinceSegmentStart
                        progressListener?.onProgressUpdate(totalCurrentMs.toInt())
                        // ▲▲▲ PERHITUNGAN PROGRES BARU ▲▲▲
                    }
                    sleep(250) // Update progres 4x per detik
                }
            }

            val buffer = ByteArray(4096)
            while (running) {
                val read = input.read(buffer)
                if (read <= 0) break
                audioTrack?.write(buffer, 0, read)
            }

            progressListener?.onCompletion()

        } catch (e: Exception) {
            Log.e("TcpAudioClient", "Error in client thread", e)
        } finally {
            audioTrack?.stop()
            audioTrack?.release()
            try { outputStream?.close() } catch (e: Exception) {}
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

    fun seekTo(targetMs: Int) {
        // ▼▼▼ LOGIKA SEEK BARU ▼▼▼
        audioTrack?.let { track ->
            // Simpan offset baru SEBELUM mengirim perintah
            playbackStartOffsetMs = targetMs.toLong()
            // Hapus buffer audio lama
            track.flush()
            // Simpan posisi frame saat ini sebagai titik awal segmen baru
            framesAtStartOfSegment = track.playbackHeadPosition.toLong()
            // Kirim perintah ke server
            sendCommand("SEEK_$targetMs\n")
        }
        // ▲▲▲ LOGIKA SEEK BARU ▲▲▲
    }

    private fun sendCommand(command: String) {
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
        interrupt()
        try { outputStream?.close() } catch (e: Exception) {}
    }
}