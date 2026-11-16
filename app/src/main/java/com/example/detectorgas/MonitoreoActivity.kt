package com.example.detectorgas

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

// Dos fuentes de datos:
//  - HC-05 por Bluetooth (Arduino real), si se conecta.
//  - Simulador (modo demostración) mientras no haya Bluetooth.
class MonitoreoActivity : AppCompatActivity() {

    private lateinit var txtConexion: TextView
    private lateinit var txtGas: TextView
    private lateinit var txtEstado: TextView
    private lateinit var txtActuadores: TextView
    private lateinit var panel: LinearLayout
    private lateinit var btnFuga: Button
    private val handler = Handler(Looper.getMainLooper())
    private val tono = ToneGenerator(AudioManager.STREAM_ALARM, 100)
    private var parpadeo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (LoginActivity.rol.isEmpty()) {   // sin sesión no se entra
            startActivity(Intent(this, LoginActivity::class.java)); finish(); return
        }
        setContentView(R.layout.activity_monitoreo)
        txtConexion = findViewById(R.id.txtConexion)
        txtGas = findViewById(R.id.txtGas)
        txtEstado = findViewById(R.id.txtEstado)
        txtActuadores = findViewById(R.id.txtActuadores)
        panel = findViewById(R.id.panel)
        btnFuga = findViewById(R.id.btnFuga)

        findViewById<TextView>(R.id.txtSesion).text =
            "Sesión: ${LoginActivity.usuario} (${LoginActivity.rol})"

        val btnActivador = findViewById<Button>(R.id.btnActivador)
        if (LoginActivity.rol != "ADMIN") btnActivador.visibility = View.GONE   // control de acceso
        btnActivador.setOnClickListener { startActivity(Intent(this, ActivadorActivity::class.java)) }
        findViewById<Button>(R.id.btnHistorial).setOnClickListener {
            startActivity(Intent(this, HistorialActivity::class.java))
        }
        findViewById<Button>(R.id.btnConectar).setOnClickListener { conectar() }
        findViewById<Button>(R.id.btnDesconectar).setOnClickListener {
            Bt.desconectar()
            Simulador.iniciar()
        }
        btnFuga.setOnClickListener {
            Simulador.simularFuga()
            Toast.makeText(this, "Fuga simulada: mira cómo sube el gas", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnSalir).setOnClickListener {
            LoginActivity.rol = ""
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        Simulador.iniciar()
    }

    override fun onResume() {
        super.onResume()
        Bt.alRecibir = { mostrarDatos(Bt.datos, "Conectado por Bluetooth (Arduino)") }
        Simulador.alRecibir = { if (!Bt.activo) mostrarDatos(Simulador.datos, "Modo demostración (datos simulados)") }
        handler.post(vigilar)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(vigilar)
        Bt.alRecibir = null
        Simulador.alRecibir = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Simulador.detener()
        Bt.desconectar()
        tono.release()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) conectar()
    }

    @SuppressLint("MissingPermission")
    private fun conectar() {
        // Permiso obligatorio desde Android 12
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
            return
        }
        val adaptador = getSystemService(BluetoothManager::class.java)?.adapter
        if (adaptador == null || !adaptador.isEnabled) {
            Toast.makeText(this, "Activa el Bluetooth del teléfono", Toast.LENGTH_LONG).show()
            return
        }
        val lista = adaptador.bondedDevices.toList()
        AlertDialog.Builder(this)
            .setTitle("Elige el HC-05")
            .setItems(lista.map { it.name ?: it.address }.toTypedArray()) { _, i ->
                txtConexion.text = "Conectando..."
                Bt.conectar(lista[i]) { ok ->
                    if (ok) Simulador.detener()
                    txtConexion.text = if (ok) "Conectado por Bluetooth (Arduino)" else "No se pudo conectar"
                }
            }
            .setNeutralButton("Emparejar nuevo") { _, _ -> startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            .show()
    }

    // Fail-safe: cada 1 s revisa que sigan llegando datos del Arduino
    private val vigilar = object : Runnable {
        override fun run() {
            btnFuga.visibility = if (Bt.activo) View.GONE else View.VISIBLE
            if (Bt.activo && System.currentTimeMillis() - Bt.ultimoDato > 5000) {
                txtConexion.text = "SIN CONEXIÓN - sistema no verificado"
                txtEstado.text = "Revisa el detector"
                panel.setBackgroundColor(Color.GRAY)
                tono.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun mostrarDatos(d: Map<String, String>, origen: String) {
        txtConexion.text = origen
        txtGas.text = d["GAS"]
        txtActuadores.text = "Válvula: ${d["VAL"]}    Ventilador: ${d["FAN"]} %"
        if (d["EST"] == "ALERTA") {
            parpadeo = !parpadeo                         // parpadea con cada dato (~0,5 s)
            panel.setBackgroundColor(if (parpadeo) Color.RED else Color.rgb(255, 150, 150))
            txtEstado.text = "¡ALERTA DE GAS!"
            tono.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
        } else {
            panel.setBackgroundColor(Color.rgb(46, 125, 50))
            txtEstado.text = if (d["EST"] == "CALENTANDO") "Calentando sensor..." else "Normal"
        }
    }
}
