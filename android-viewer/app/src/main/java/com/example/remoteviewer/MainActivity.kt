package com.example.remoteviewer

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import okio.ByteString

class MainActivity : AppCompatActivity() {

    private lateinit var screenView: ImageView
    private lateinit var ipInput: EditText
    private lateinit var connectBtn: Button
    private lateinit var statusText: TextView

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var remoteW = 1920f
    private var remoteH = 1080f
    private var imgLeft = 0f
    private var imgTop = 0f
    private var imgWidth = 1f
    private var imgHeight = 1f
    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f
    private var longPressFired = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        screenView = findViewById(R.id.screenView)
        ipInput = findViewById(R.id.ipInput)
        connectBtn = findViewById(R.id.connectBtn)
        statusText = findViewById(R.id.statusText)
        connectBtn.setOnClickListener { toggleConnection() }
        setupTouchListener()
    }

    private fun toggleConnection() {
        if (webSocket != null) disconnect() else connect()
    }

    private fun connect() {
        var addr = ipInput.text.toString().trim()
        if (addr.isEmpty()) { statusText.text = "Masukkan IP:PORT"; return }
        if (!addr.startsWith("ws://") && !addr.startsWith("wss://")) addr = "ws://" + addr
        val request = Request.Builder().url(addr).build()
        statusText.text = "Connecting..."
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                runOnUiThread {
                    statusText.text = "Connected"
                    connectBtn.text = "Disconnect"
                }
            }
           override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    runOnUiThread {
                        screenView.setImageBitmap(bmp)
                        updateImageBounds()
                    }
                }
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e("WS", "Failure: " + t.message)
                runOnUiThread {
                    statusText.text = "Error: " + t.message
                    connectBtn.text = "Connect"
                }
                webSocket = null
            }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                runOnUiThread {
                    statusText.text = "Disconnected"
                    connectBtn.text = "Connect"
                }
                webSocket = null
            }
        })
    }

    private fun disconnect() {
        webSocket?.close(1000, "bye")
        webSocket = null
        statusText.text = "Disconnected"
        connectBtn.text = "Connect"
    }

    private fun updateImageBounds() {
        val drawable = screenView.drawable ?: return
        val vw = screenView.width.toFloat()
        val vh = screenView.height.toFloat()
        val iw = drawable.intrinsicWidth.toFloat()
        val ih = drawable.intrinsicHeight.toFloat()
        if (iw <= 0f || ih <= 0f) return
        val scale = minOf(vw / iw, vh / ih)
        imgWidth = iw * scale
        imgHeight = ih * scale
        imgLeft = (vw - imgWidth) / 2f
        imgTop = (vh - imgHeight) / 2f
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        screenView.setOnTouchListener { _, event ->
            if (webSocket == null) return@setOnTouchListener false
            updateImageBounds()
            val xImg = (event.x - imgLeft) / imgWidth
            val yImg = (event.y - imgTop) / imgHeight
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    downX = event.x
                    downY = event.y
                    longPressFired = false
                    screenView.postDelayed({
                        if (!longPressFired && System.currentTimeMillis() - downTime >= 600) {
                            longPressFired = true
                            sendRightClick()
                        }
                    }, 600)
                    if (inImage(xImg, yImg)) sendMove((xImg * remoteW).toInt(), (yImg * remoteH).toInt())
                }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(event.x - downX) > 20 || Math.abs(event.y - downY) > 20) longPressFired = true
                    if (inImage(xImg, yImg)) sendMove((xImg * remoteW).toInt(), (yImg * remoteH).toInt())
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - downTime
                    val moved = Math.abs(event.x - downX) > 20 || Math.abs(event.y - downY) > 20
                    if (!longPressFired && !moved && duration < 600) {
                        if (inImage(xImg, yImg)) sendMove((xImg * remoteW).toInt(), (yImg * remoteH).toInt())
                        sendClick()
                    }
                    longPressFired = false
                }
            }
            true
        }
    }

    private fun inImage(xImg: Float, yImg: Float) = xImg in 0f..1f && yImg in 0f..1f

    private fun sendJson(obj: JSONObject) { webSocket?.send(obj.toString()) }

    private fun sendMove(x: Int, y: Int) {
        sendJson(JSONObject().apply {
            put("type", "mouse")
            put("x", x)
            put("y", y)
        })
    }

    private fun sendClick() {
        sendJson(JSONObject().apply {
            put("type", "click")
            put("button", "left")
        })
    }

    private fun sendRightClick() {
        sendJson(JSONObject().apply { put("type", "right_click") })
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }
}
