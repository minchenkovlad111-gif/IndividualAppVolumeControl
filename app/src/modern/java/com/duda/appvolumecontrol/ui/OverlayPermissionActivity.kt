package com.duda.appvolumecontrol.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.service.VolumeOverlayService

class OverlayPermissionActivity : AppCompatActivity() {
    private lateinit var statusView: TextView
    private lateinit var grantButton: Button
    private lateinit var showButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "Overlay permission"
            textSize = 20f
        }

        val helper = TextView(this).apply {
            text = "The volume-button overlay needs permission to draw over other apps."
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }

        statusView = TextView(this).apply {
            text = "Overlay permission: checking..."
            textSize = 14f
            setPadding(0, 0, 0, 12)
        }

        grantButton = Button(this).apply {
            text = "Grant overlay permission"
            setOnClickListener { openOverlaySettings() }
        }

        showButton = Button(this).apply {
            text = "Show overlay now"
            setOnClickListener {
                val intent = Intent(this@OverlayPermissionActivity, VolumeOverlayService::class.java)
                intent.action = VolumeOverlayService.ACTION_SHOW
                startService(intent)
            }
        }

        root.addView(header)
        root.addView(helper)
        root.addView(statusView)
        root.addView(grantButton)
        root.addView(showButton)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val canDraw = canDrawOverlays()
        statusView.text = if (canDraw) {
            "Overlay permission: granted"
        } else {
            "Overlay permission: not granted"
        }
        grantButton.isEnabled = !canDraw
        showButton.isEnabled = canDraw
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
}
