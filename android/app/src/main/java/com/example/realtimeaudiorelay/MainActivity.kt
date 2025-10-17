package com.example.realtimeaudiorelay // Ganti dengan package name Anda

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.*
// Hapus import java.util.concurrent.TimeUnit jika tidak dipakai

class MainActivity : AppCompatActivity() {

    private var audioClient: UdpAudioClient? = null
    private lateinit var etServerIp: EditText
    private lateinit var btnConnect: Button
    // Hapus deklarasi UI player jika tidak dipakai lagi untuk UDP
    // private lateinit var btnPlayPause: ImageButton
    private lateinit var tvStatus: TextView
    // private lateinit var playerControls: View
    // private lateinit var seekBar: SeekBar
    // private lateinit var tvCurrentTime: TextView
    // private lateinit var tvTotalDuration: TextView

    // ▼▼▼ State variable BARU ▼▼▼
    private var isClientRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Pastikan layout ini sesuai

        // Inisialisasi view dasar
        etServerIp = findViewById(R.id.etServerIp)
        btnConnect = findViewById(R.id.btnConnect)
        tvStatus = findViewById(R.id.tvStatus)
        // Inisialisasi playerControls jika masih ada di layout
        // playerControls = findViewById(R.id.playerControls)


        // Setup listener HANYA untuk Connect/Disconnect
        btnConnect.setOnClickListener {
            if (!isClientRunning) { // <-- Gunakan flag
                startStreaming()
            } else {
                stopStreaming()
            }
        }
    }

    private fun startStreaming() {
        val hostIp = etServerIp.text.toString()
        if (hostIp.isBlank()) {
            Toast.makeText(this, "Alamat IP tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        audioClient = UdpAudioClient(this, hostIp)
        audioClient?.start()
        isClientRunning = true // <-- Set flag
        // updateUiOnConnect() // Panggil fungsi update UI yang sesuai
        // Tampilkan status connected
        etServerIp.isEnabled = false
        btnConnect.text = "Disconnect"
        tvStatus.text = "Status: Connected"
        // playerControls.visibility = View.VISIBLE // Tampilkan UI player jika ada
    }

    private fun stopStreaming() {
        audioClient?.shutdown()
        isClientRunning = false // <-- Reset flag
        audioClient = null
        // updateUiOnDisconnect() // Panggil fungsi update UI yang sesuai
        // Tampilkan status disconnected
        etServerIp.isEnabled = true
        btnConnect.text = "Connect"
        tvStatus.text = "Status: Disconnected"
        // playerControls.visibility = View.GONE // Sembunyikan UI player jika ada
    }

    // Fungsi update UI bisa disederhanakan jika UI player tidak aktif
    // Hapus atau komentari fungsi onDurationReceived, onProgressUpdate, onCompletion,
    // togglePlayPause, formatMillis, setupSeekBarListener, updatePlayPauseButton
    // Hapus juga implementasi PlaybackProgressListener dari deklarasi class

    override fun onDestroy() {
        super.onDestroy()
        // Pastikan client dihentikan saat Activity dihancurkan
        if (isClientRunning) {
            stopStreaming()
        }
    }
}