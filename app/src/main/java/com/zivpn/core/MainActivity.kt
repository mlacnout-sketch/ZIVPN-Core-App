package com.zivpn.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {

    private val VPN_REQUEST_CODE = 0x0F
    private lateinit var etServerIp: EditText
    private lateinit var etPassword: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val statusText = findViewById<TextView>(R.id.statusText)
        
        etServerIp = findViewById(R.id.etServerIp)
        etPassword = findViewById(R.id.etPassword)

        // Load saved prefs
        val prefs = getSharedPreferences("ZIVPN_PREFS", Context.MODE_PRIVATE)
        etServerIp.setText(prefs.getString("SERVER_IP", "202.10.48.173"))
        etPassword.setText(prefs.getString("PASSWORD", "asd63"))

        btnStart.setOnClickListener {
            savePrefs()
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, VPN_REQUEST_CODE)
            } else {
                onActivityResult(VPN_REQUEST_CODE, Activity.RESULT_OK, null)
            }
            statusText.text = "Status: Starting..."
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, ZIVpnService::class.java)
            intent.action = "STOP"
            startService(intent)
            statusText.text = "Status: Stopped"
        }
    }

    private fun savePrefs() {
        val prefs = getSharedPreferences("ZIVPN_PREFS", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("SERVER_IP", etServerIp.text.toString())
            .putString("PASSWORD", etPassword.text.toString())
            .apply()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, ZIVpnService::class.java)
            intent.putExtra("EXTRA_SERVER_IP", etServerIp.text.toString())
            intent.putExtra("EXTRA_PASSWORD", etPassword.text.toString())
            startService(intent)
        }
    }
}