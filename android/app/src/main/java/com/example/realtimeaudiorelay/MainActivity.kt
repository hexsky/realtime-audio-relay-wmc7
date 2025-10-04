package com.example.realtimeaudiorelay // Ganti dengan package name Anda

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), SimpleTcpAudioClient.PlaybackProgressListener {

    private var audioClient: SimpleTcpAudioClient? = null
    private lateinit var etServerIp: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnPlayPause: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var playerControls: View
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalDuration: TextView

    private var isPlaying = true
    private var isSeeking = false // Flag untuk mencegah update otomatis saat user menggeser slider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inisialisasi semua view
        etServerIp = findViewById(R.id.etServerIp)
        btnConnect = findViewById(R.id.btnConnect)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        tvStatus = findViewById(R.id.tvStatus)
        playerControls = findViewById(R.id.playerControls)
        seekBar = findViewById(R.id.seekBar)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalDuration = findViewById(R.id.tvTotalDuration)

        // Setup listener
        btnConnect.setOnClickListener {
            if (audioClient == null || !audioClient!!.isAlive) startStreaming() else stopStreaming()
        }
        btnPlayPause.setOnClickListener { togglePlayPause() }
        setupSeekBarListener()
    }

    private fun startStreaming() {
        val hostIp = etServerIp.text.toString()
        if (hostIp.isBlank()) {
            Toast.makeText(this, "Alamat IP tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        audioClient = SimpleTcpAudioClient(hostIp)
        audioClient?.setProgressListener(this) // Set MainActivity sebagai listener
        audioClient?.start()

        updateUiOnConnect()
    }

    private fun stopStreaming() {
        audioClient?.shutdown()
        audioClient = null
        updateUiOnDisconnect()
    }

    private fun togglePlayPause() {
        isPlaying = !isPlaying
        if (isPlaying) audioClient?.resumePlayback() else audioClient?.pausePlayback()
        updatePlayPauseButton(isPlaying)
    }

    private fun formatMillis(millis: Int): String {
        return String.format("%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(millis.toLong()),
            TimeUnit.MILLISECONDS.toSeconds(millis.toLong()) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis.toLong()))
        )
    }

    // --- Implementasi PlaybackProgressListener ---

    override fun onDurationReceived(totalMs: Int) {
        runOnUiThread {
            seekBar.max = totalMs
            tvTotalDuration.text = formatMillis(totalMs)
        }
    }

    override fun onProgressUpdate(currentMs: Int) {
        if (!isSeeking) {
            runOnUiThread {
                seekBar.progress = currentMs
                tvCurrentTime.text = formatMillis(currentMs)
            }
        }
    }

    override fun onCompletion() {
        runOnUiThread {
            stopStreaming()
            Toast.makeText(this, "Playback Finished", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Helper untuk UI & SeekBar ---

    private fun setupSeekBarListener() {
        seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime.text = formatMillis(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                seekBar?.progress?.let {
                    audioClient?.seekTo(it)
                }
            }
        })
    }

    private fun updateUiOnConnect() {
        isPlaying = true
        etServerIp.isEnabled = false
        btnConnect.text = "Disconnect"
        tvStatus.text = "Status: Connected"
        playerControls.visibility = View.VISIBLE
        updatePlayPauseButton(true)
    }

    private fun updateUiOnDisconnect() {
        etServerIp.isEnabled = true
        btnConnect.text = "Connect"
        tvStatus.text = "Status: Disconnected"
        playerControls.visibility = View.GONE
        seekBar.progress = 0
        tvCurrentTime.text = "00:00"
        tvTotalDuration.text = "00:00"
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isPlaying) {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }
}