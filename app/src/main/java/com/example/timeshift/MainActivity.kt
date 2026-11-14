package com.example.timeshift

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var urlBox: EditText
    private lateinit var speedLabel: TextView
    private lateinit var hoursBox: EditText

    private var speed = 1
    private var totalOffsetMs = 0L
    private var scriptHandler: ScriptHandler? = null
    private val supportsDocStart by lazy {
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    }

    private val prefs by lazy { getSharedPreferences("timeshift", Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speed = prefs.getInt("speed", 1).coerceIn(1, 100)
        totalOffsetMs = prefs.getLong("offsetMs", 0L)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // --- top bar: address + Go
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        urlBox = EditText(this).apply {
            setSingleLine()
            hint = "https://..."
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setText(prefs.getString("url", "https://hypoza.tech/farm"))
            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_GO ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER
                ) { go(); true } else false
            }
        }
        val goBtn = Button(this).apply { text = "Go"; setOnClickListener { go() } }
        top.addView(urlBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(goBtn)

        // --- web view
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                // Fallback for WebViews without document-start scripts (less reliable).
                if (!supportsDocStart) view.evaluateJavascript(ClockScript.build(speed.toDouble(), totalOffsetMs), null)
                url?.let { urlBox.setText(it) }
            }
        }
        installScript()

        // --- bottom controls
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 8, 16, 8)
        }
        speedLabel = TextView(this)
        val seek = SeekBar(this).apply {
            max = 99
            progress = speed - 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    speed = p + 1
                    updateLabel()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { applySpeed() }
            })
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hoursBox = EditText(this).apply {
            hint = "hours"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine()
        }
        val jumpBtn = Button(this).apply { text = "Jump forward"; setOnClickListener { jump() } }
        val resetBtn = Button(this).apply { text = "Reset"; setOnClickListener { reset() } }
        val showBtn = Button(this).apply { text = "Page time"; setOnClickListener { showTime() } }
        row.addView(hoursBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(jumpBtn)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(resetBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row2.addView(showBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(speedLabel)
        bottom.addView(seek)
        bottom.addView(row)
        bottom.addView(row2)

        root.addView(top)
        root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(bottom)
        setContentView(root)

        updateLabel()
        go()
    }

    private fun updateLabel() {
        val hours = totalOffsetMs / 3_600_000.0
        speedLabel.text = "Speed: ${speed}x   |   Jumped: ${"%.2f".format(hours)} h"
    }

    private fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    private fun go() {
        val url = normalizeUrl(urlBox.text.toString())
        prefs.edit().putString("url", url).apply()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(urlBox.windowToken, 0)
        webView.loadUrl(url)
    }

    /** (Re)registers the clock script so every new page load starts with the saved settings. */
    private fun installScript() {
        if (!supportsDocStart) return
        scriptHandler?.remove()
        scriptHandler = WebViewCompat.addDocumentStartJavaScript(
            webView, ClockScript.build(speed.toDouble(), totalOffsetMs), setOf("*")
        )
    }

    private fun save() {
        prefs.edit().putInt("speed", speed).putLong("offsetMs", totalOffsetMs).apply()
        installScript()
        updateLabel()
    }

    private fun applySpeed() {
        webView.evaluateJavascript("window.__tmSet && window.__tmSet($speed, 0)", null)
        save()
    }

    private fun jump() {
        val hours = hoursBox.text.toString().toDoubleOrNull()
        if (hours == null || hours <= 0.0 || hours > 24 * 365) {
            Toast.makeText(this, "Enter hours between 0 and 8760", Toast.LENGTH_SHORT).show()
            return
        }
        val ms = (hours * 3_600_000).toLong()
        totalOffsetMs += ms
        webView.evaluateJavascript("window.__tmSet && window.__tmSet($speed, $ms)", null)
        save()
    }

    private fun reset() {
        speed = 1
        totalOffsetMs = 0L
        webView.evaluateJavascript("window.__tmReset && window.__tmReset()", null)
        save()
        recreate()
    }

    private fun showTime() {
        webView.evaluateJavascript("window.__tmState ? window.__tmState() : 'not installed'") { r ->
            Toast.makeText(this, r ?: "no answer", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
