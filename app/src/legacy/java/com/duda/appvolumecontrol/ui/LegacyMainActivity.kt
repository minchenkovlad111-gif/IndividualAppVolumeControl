package fuck.individualappaudiovolumecontrol.droidvendorssuck.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class LegacyMainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Legacy mode"
            textSize = 20f
        }
        val banner = TextView(this).apply {
            text = "Per-app volume control requires Android 8.0 (API 26)+. " +
                "This build keeps preset storage and diagnostics only."
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor("#FFF3CD"))
        }
        val helper = TextView(this).apply {
            text = "Use this build to manage presets and export/import them " +
                "so they are ready when you upgrade."
            textSize = 13f
            setPadding(0, 16, 0, 24)
        }

        val presetsButton = Button(this).apply {
            text = "Manage presets"
            setOnClickListener {
                startActivity(Intent(this@LegacyMainActivity, LegacyPresetsActivity::class.java))
            }
        }
        val diagnosticsButton = Button(this).apply {
            text = "Diagnostics"
            setOnClickListener {
                startActivity(Intent(this@LegacyMainActivity, LegacyDiagnosticsActivity::class.java))
            }
        }

        content.addView(title)
        content.addView(banner)
        content.addView(helper)
        content.addView(presetsButton)
        content.addView(diagnosticsButton)

        val scroll = ScrollView(this).apply {
            addView(content)
        }
        setContentView(scroll)
    }
}
