package com.example.detectorgas

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

// Simula el Arduino (MQ-2 + servo + ventilador) cuando no hay HC-05 conectado.
// Genera la misma trama que el Arduino: GAS, EST, VAL, FAN, y obedece los mismos comandos.
object Simulador {
    private val handler = Handler(Looper.getMainLooper())
    private const val UMBRAL = 350
    private const val HISTERESIS = 30

    var activo = false
    var datos = mapOf<String, String>()
    var alRecibir: (() -> Unit)? = null

    private var gas = 120.0
    private var fuga = false
    private var alerta = false
    private var valvulaAbierta = true
    private var fan = 0
    private var inicio = 0L

    fun iniciar() {
        if (activo) return
        activo = true
        inicio = System.currentTimeMillis()
        handler.post(tick)
    }

    fun detener() {
        activo = false
        handler.removeCallbacks(tick)
    }

    // Botón "Simular fuga" (para la demostración)
    fun simularFuga() { fuga = true }

    // Mismos comandos que el Arduino: FAN:0..100, VAL:ABRIR, VAL:CERRAR
    fun enviar(cmd: String) {
        when {
            cmd.startsWith("FAN:") -> fan = (cmd.substring(4).toIntOrNull() ?: 0).coerceIn(0, 100)
            cmd == "VAL:CERRAR" -> valvulaAbierta = false
            cmd == "VAL:ABRIR" -> if (!alerta) valvulaAbierta = true   // fail-safe
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!activo) return
            val calentando = System.currentTimeMillis() - inicio < 5_000   // 5 s de "calentamiento"

            // La fuga sube el gas; con la válvula cerrada se corta y el ventilador lo baja más rápido
            if (fuga && valvulaAbierta) gas += 35 + Random.nextInt(20)
            else gas -= 4 + fan * 0.25
            if (!valvulaAbierta) fuga = false
            if (gas > 900) gas = 900.0
            gas = maxOf(gas, 100.0 + Random.nextInt(40))

            if (!calentando) {
                if (!alerta && gas >= UMBRAL) {
                    alerta = true
                    valvulaAbierta = false   // fail-safe: corta el gas
                    fan = 100                // ventila al máximo
                } else if (alerta && gas < UMBRAL - HISTERESIS) {
                    alerta = false           // la válvula sigue cerrada hasta que el admin la abra
                }
            }

            datos = mapOf(
                "GAS" to gas.toInt().toString(),
                "EST" to if (calentando) "CALENTANDO" else if (alerta) "ALERTA" else "NORMAL",
                "VAL" to if (valvulaAbierta) "ABIERTA" else "CERRADA",
                "FAN" to fan.toString()
            )
            BaseDatos.bd.registrar(datos)
            alRecibir?.invoke()
            handler.postDelayed(this, 500)
        }
    }
}
