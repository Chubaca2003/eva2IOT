package com.example.detectorgas

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.UUID

// Conexión con el HC-05, compartida por todas las pantallas
object Bt {
    private val SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private var socket: BluetoothSocket? = null
    private val main = Handler(Looper.getMainLooper())

    var activo = false                    // true mientras el usuario quiere estar conectado
    var ultimoDato = 0L                   // hora del último mensaje del Arduino
    var datos = mapOf<String, String>()   // GAS, EST, VAL, FAN
    var alRecibir: (() -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun conectar(d: BluetoothDevice, listo: (Boolean) -> Unit) {
        try { socket?.close() } catch (e: IOException) { }   // cierra una conexión anterior
        Thread {
            try {
                socket = d.createRfcommSocketToServiceRecord(SPP)
                socket!!.connect()
            } catch (e: IOException) {
                main.post { listo(false) }
                return@Thread
            }
            activo = true
            ultimoDato = System.currentTimeMillis()
            main.post { listo(true) }

            // Lee cada línea: "GAS:412;EST:ALERTA;VAL:CERRADA;FAN:100"
            try {
                socket!!.inputStream.bufferedReader().forEachLine { linea ->
                    val m = linea.trim().split(";").map { it.split(":") }
                        .filter { it.size == 2 }.associate { it[0] to it[1] }
                    if (m.containsKey("GAS")) {          // ignora mensajes mal formados
                        datos = m
                        ultimoDato = System.currentTimeMillis()
                        main.post {
                            BaseDatos.bd.registrar(m)    // guarda en el historial (SQLite)
                            alRecibir?.invoke()
                        }
                    }
                }
            } catch (e: IOException) { }
        }.start()
    }

    fun enviar(cmd: String) {
        try { socket?.outputStream?.write("$cmd\n".toByteArray()) } catch (e: IOException) { }
    }

    fun desconectar() {
        activo = false
        try { socket?.close() } catch (e: IOException) { }
    }
}