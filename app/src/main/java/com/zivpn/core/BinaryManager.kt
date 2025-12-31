package com.zivpn.core

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object BinaryManager {
    private const val TAG = "ZIVPN_Bin"
    
    // Config sesuai service.sh
    // private const val SERVER_IP = "202.10.48.173" // Moved to dynamic
    // private const val PASS = "asd63" // Moved to dynamic
    private const val OBFS = "hu``hqb`c"

    private var processList = mutableListOf<Process>()

    fun copyAssets(context: Context) {
        val files = listOf("libuz", "libload", "tun2socks", "libv2ray", "libzib", "libzivpn_jni.so")
        val binDir = context.filesDir

        files.forEach { fileName ->
            try {
                val file = File(binDir, fileName)
                // Always overwrite for now to ensure update if assets changed
                context.assets.open(fileName).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file.setExecutable(true)
                Log.d(TAG, "Copied and chmod +x: $fileName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed copy $fileName", e)
            }
        }
    }
    
    // JNI External Function
    external fun startTun2Socks(tunFd: Int, path: String, proxy: String): Int

    fun startCore(context: Context, serverIp: String, password: String) {
        stopCore() // Bersihkan dulu

        val binDir = context.filesDir.absolutePath
        
        // Load JNI Library
        try {
            System.load("$binDir/libzivpn_jni.so")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load JNI lib", e)
        }
        
        val libUzPath = "$binDir/libuz"
        val libLoadPath = "$binDir/libload"

        // 1. Start 4 Hysteria Cores (Port 1080-1083)
        for (i in 0 until 4) {
            val port = 1080 + i
            val configFile = File(context.filesDir, "hysteria_$port.json")
            val configJson = """
                {
                    "server": "$serverIp:6000-19999",
                    "obfs": "$OBFS",
                    "auth": "$password",
                    "socks5": {
                        "listen": "127.0.0.1:$port"
                    },
                    "insecure": true,
                    "recvwindowconn": 131072,
                    "recvwindow": 327680
                }
            """.trimIndent()
            
            configFile.writeText(configJson)

            // Gunakan format: -s <obfs> --config <file_path>
            val cmd = listOf(libUzPath, "-s", OBFS, "--config", configFile.absolutePath)
            startProcess(cmd, "Hysteria-$port")
        }

        // Tunggu sebentar agar core siap (seperti sleep 2 di script)
        Thread.sleep(2000)

        // 2. Start Load Balancer (Port 7777)
        val loadCmd = listOf(
            libLoadPath,
            "-lport", "7777",
            "-tunnel", "127.0.0.1:1080", "127.0.0.1:1081", "127.0.0.1:1082", "127.0.0.1:1083"
        )
        startProcess(loadCmd, "LoadBalancer")
    }

    private fun startProcess(command: List<String>, name: String) {
        try {
            val pb = ProcessBuilder(command)
            // pb.environment()["LD_LIBRARY_PATH"] = "" // Removed: potentially dangerous
            val process = pb.start()
            processList.add(process)
            
            // Log output (Opsional, untuk debug)
            Thread {
                process.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Log.d(TAG, "[$name] $line")
                    }
                }
            }.start()
            
            Log.d(TAG, "Started: $name")
        } catch (e: IOException) {
            Log.e(TAG, "Failed start $name", e)
        }
    }

    fun stopCore() {
        processList.forEach { 
            try { it.destroy() } catch (e: Exception) { }
        }
        processList.clear()
        
        // Force kill binaries to ensure no leftovers
        val binaries = listOf("tun2socks", "libuz", "libload")
        binaries.forEach { bin ->
            try {
                Runtime.getRuntime().exec("killall $bin")
            } catch (e: Exception) { }
        }
        
        Log.d(TAG, "All processes stopped")
    }
}