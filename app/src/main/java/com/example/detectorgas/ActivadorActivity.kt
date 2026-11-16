package com.example.detectorgas

import android.os.Bundle
import android.widget.Button
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ActivadorActivity : AppCompatActivity() {

    // Con el HC-05 conectado se manda al Arduino; si no, al simulador
    private fun datos() = if (Bt.activo) Bt.datos else Simulador.datos
    private fun enviar(cmd: String) = if (Bt.activo) Bt.enviar(cmd) else Simulador.enviar(cmd)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LoginActivity.rol != "ADMIN") { finish(); return }   // solo administrador
        setContentView(R.layout.activity_activador)

        val txtModo = findViewById<TextView>(R.id.txtModo)
        val swVentilador = findViewById<CompoundButton>(R.id.swVentilador)
        val seekVelocidad = findViewById<SeekBar>(R.id.seekVelocidad)
        val swValvula = findViewById<CompoundButton>(R.id.swValvula)

        txtModo.text = if (Bt.activo) "Control directo por Bluetooth" else "Modo demostración (simulador)"

        // Estado inicial según lo que informa el Arduino
        val fan = datos()["FAN"]?.toIntOrNull() ?: 0
        swVentilador.isChecked = fan > 0
        if (fan > 0) seekVelocidad.progress = fan
        swValvula.isChecked = datos()["VAL"] == "ABIERTA"

        swVentilador.setOnCheckedChangeListener { _, encendido ->
            enviar("FAN:" + if (encendido) seekVelocidad.progress else 0)
        }

        seekVelocidad.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) {}
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {
                if (swVentilador.isChecked) enviar("FAN:${seekVelocidad.progress}")
            }
        })

        swValvula.setOnCheckedChangeListener { sw, abierta ->
            if (abierta && datos()["EST"] == "ALERTA") {   // bloqueo: no se abre el gas con fuga
                Toast.makeText(this, "No se puede abrir con alerta de gas", Toast.LENGTH_LONG).show()
                sw.isChecked = false
            } else {
                enviar(if (abierta) "VAL:ABRIR" else "VAL:CERRAR")
            }
        }

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
    }
}
