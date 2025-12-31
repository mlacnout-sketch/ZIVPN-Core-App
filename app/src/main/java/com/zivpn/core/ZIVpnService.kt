package com.zivpn.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.IOException

class ZIVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tun2SocksProcess: Process? = null

    companion object {
        const val CHANNEL_ID = "ZIVPN_CHANNEL"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }

        val serverIp = intent?.getStringExtra("EXTRA_SERVER_IP") ?: "202.10.48.173"
        val password = intent?.getStringExtra("EXTRA_PASSWORD") ?: "asd63"

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ZIVPN Core")
            .setContentText("Running on $serverIp")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        
        startForeground(1, notification)

        Thread {
            startVpn(serverIp, password)
        }.start()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "ZIVPN Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startVpn(serverIp: String, password: String) {
        Log.d("ZIVPN", "Starting VPN Service...")

        // 1. Siapkan binary
        BinaryManager.copyAssets(this)
        
        // 2. Jalankan Core (Hysteria + LoadBalancer)
        BinaryManager.startCore(this, serverIp, password)

        // 3. Buat Interface VPN (TUN)
        val builder = Builder()
        builder.setSession("ZIVPN Core")
        builder.addAddress("10.0.1.1", 24)
        builder.addRoute("0.0.0.0", 0)
        builder.setMtu(1500)
        
        // Penting: Kecualikan aplikasi sendiri agar tidak loop (terutama jika connect ke server Hysteria langsung)
        // Jika server IP (202.10.48.173) diakses, jangan lewat VPN
        // Atau protect socket. Tapi binary external (libuz) tidak bisa diprotect via Java `protect()`.
        // Solusi terbaik: Tambahkan route khusus untuk IP server agar bypass VPN.
        // builder.addRoute("202.10.48.173", 32) -> Tidak didukung "exclude route" di API lama, 
        // tapi jika kita addRoute 0.0.0.0/0, semua kena. 
        // Trik: Biasanya libuz akan bind ke physical interface, tapi lebih aman jika kita exclude package kita sendiri
        // jika libuz berjalan dengan UID app ini.
        try {
            builder.addDisallowedApplication(packageName) 
        } catch (e: Exception) {
            Log.e("ZIVPN", "Failed to exclude app", e)
        }

        vpnInterface = builder.establish()

        if (vpnInterface != null) {
            Log.d("ZIVPN", "VPN Interface created: ${vpnInterface!!.fd}")
            startTun2Socks(vpnInterface!!.fd)
        } else {
            Log.e("ZIVPN", "Failed to establish VPN")
            stopSelf()
        }
    }

    private fun startTun2Socks(tunFd: Int) {
        val binDir = filesDir.absolutePath
        val tun2socksPath = "$binDir/tun2socks"
        val proxyUrl = "socks5://127.0.0.1:7777"
        
        Log.d("ZIVPN", "Starting Tun2Socks using JNI...")
        
        // Pass FD directly using JNI to avoid leakage issues
        val pid = BinaryManager.startTun2Socks(tunFd, tun2socksPath, proxyUrl)
        
        if (pid > 0) {
            Log.d("ZIVPN", "Tun2Socks started with PID: $pid")
            // Note: We don't have a Process object to destroy later, 
            // but we can kill it by PID or just rely on stopCore() cleaning up 
            // (though stopCore currently only kills Java Process objects).
            // Better: Add PID to BinaryManager's tracking if possible, or killall in stopCore.
        } else {
            Log.e("ZIVPN", "Failed to start Tun2Socks via JNI")
        }
    }

    private fun stopVpn() {
        BinaryManager.stopCore()
        // Kill tun2socks manually since we started it via JNI fork
        try {
            Runtime.getRuntime().exec("killall tun2socks")
        } catch (e: Exception) {}
        
        tun2SocksProcess?.destroy()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {}
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}