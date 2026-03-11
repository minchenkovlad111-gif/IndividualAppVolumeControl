package com.duda.appvolumecontrol.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.service.PlaybackControllerService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = "App Volume Control is running."
            textSize = 18f
            setPadding(32, 48, 32, 48)
        }
        setContentView(textView)

        startForegroundService(Intent(this, PlaybackControllerService::class.java))
    }
}
