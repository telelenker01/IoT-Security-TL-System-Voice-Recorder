package com.tlsystem.iotsecurity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.*
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvFileName: TextView
    
    private var isRecording = false
    private var startTime: Long = 0
    private var timer: Timer? = null
    private var mediaRecorder: MediaRecorder? = null
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
        private const val MAX_RECORDING_HOURS = 6
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupButtons()
    }
    
    private fun initViews() {
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)
        tvStatus = findViewById(R.id.tv_status)
        tvTime = findViewById(R.id.tv_time)
        tvFileName = findViewById(R.id.tv_file_name)
        
        btnStop.isEnabled = false
        tvStatus.text = "Ready to Record"
    }
    
    private fun setupButtons() {
        btnStart.setOnClickListener {
            if (checkPermissions()) {
                startRecording()
            }
        }
        
        btnStop.setOnClickListener {
            stopRecording()
        }
    }
    
    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        return if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, 
                permissionsToRequest.toTypedArray(), 
                PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }
    
    private fun startRecording() {
        try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "TL_Recording_$timestamp.mp4"
                val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                val outputFile = File(storageDir, fileName)
                
                setOutputFile(outputFile.absolutePath)
                setMaxDuration(MAX_RECORDING_HOURS * 60 * 60 * 1000)
                prepare()
                start()
                
                tvFileName.text = "Recording: $fileName"
            }
            
            isRecording = true
            startTime = System.currentTimeMillis()
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            tvStatus.text = "RECORDING..."
            
            startTimer()
            startRecordingService()
            
            Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            resetUI()
        }
    }
    
    private fun startTimer() {
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    updateTimer()
                }
            }
        }, 0, 1000)
    }
    
    private fun updateTimer() {
        val elapsed = System.currentTimeMillis() - startTime
        val seconds = (elapsed / 1000) % 60
        val minutes = (elapsed / (1000 * 60)) % 60
        val hours = (elapsed / (1000 * 60 * 60)) % 24
        
        tvTime.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
        
        if (hours >= MAX_RECORDING_HOURS) {
            Toast.makeText(this, "6-hour limit reached", Toast.LENGTH_LONG).show()
            stopRecording()
        }
    }
    
    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            timer?.cancel()
            timer = null
            
            resetUI()
            
            val serviceIntent = Intent(this, TLRecordingService::class.java)
            stopService(serviceIntent)
            
            Toast.makeText(this, "Recording saved", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun resetUI() {
        isRecording = false
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        tvStatus.text = "Ready to Record"
        tvTime.text = "00:00:00"
        tvFileName.text = "No active recording"
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                startRecording()
            } else {
                Toast.makeText(this, "Permissions denied", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
    }
}
