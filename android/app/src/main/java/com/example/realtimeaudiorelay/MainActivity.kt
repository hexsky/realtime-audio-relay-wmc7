package com.example.realtimeaudiorelay // Ganti dengan package name Anda

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private var audioClient: SimpleTcpAudioClient? = null
    private lateinit var etServerIp: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnPlayPause: ImageButton
    private lateinit var tvStatus: TextView

    private var isPlaying = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServerIp = findViewById(R.id.etServerIp)
        btnConnect = findViewById(R.id.btnConnect)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        tvStatus = findViewById(R.id.tvStatus)

        btnConnect.setOnClickListener {
            if (audioClient == null || !audioClient!!.isAlive) {
                startStreaming()
            } else {
                stopStreaming()
            }
        }

        btnPlayPause.setOnClickListener {
            togglePlayPause()
        }
    }

    private fun startStreaming() {
        val hostIp = etServerIp.text.toString()
        if (hostIp.isBlank()) {
            Toast.makeText(this, "Alamat IP tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        audioClient = SimpleTcpAudioClient(hostIp)
        audioClient?.start()

        updateUi(isConnected = true, isPlaying = true)
    }

    private fun stopStreaming() {
        audioClient?.shutdown()
        audioClient = null
        updateUi(isConnected = false)
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            audioClient?.pausePlayback()
        } else {
            audioClient?.resumePlayback()
        }
        isPlaying = !isPlaying
        updateUi(isConnected = true, isPlaying = isPlaying)
    }

    private fun updateUi(isConnected: Boolean, isPlaying: Boolean = false) {
        if (isConnected) {
            tvStatus.text = "Status: Connected"
            btnConnect.text = "Disconnect"
            etServerIp.isEnabled = false
            btnPlayPause.visibility = View.VISIBLE

            if (isPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        } else {
            tvStatus.text = "Status: Disconnected"
            btnConnect.text = "Connect"
            etServerIp.isEnabled = true
            btnPlayPause.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }
}