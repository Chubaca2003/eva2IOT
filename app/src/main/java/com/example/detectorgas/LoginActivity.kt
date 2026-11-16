package com.example.detectorgas

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Login contra la base local SQLite (tabla usuarios, contraseñas con hash + sal).
// Usuarios de prueba: admin / admin123 (ADMIN)  -  usuario / usuario123 (USUARIO)
class LoginActivity : AppCompatActivity() {

    companion object {
        var rol = ""
        var usuario = ""
    }

    private val prefs by lazy { getSharedPreferences("seguridad", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        BaseDatos.iniciar(this)
        rol = ""
        usuario = ""

        val etUsuario = findViewById<EditText>(R.id.etUsuario)
        val etClave = findViewById<EditText>(R.id.etClave)
        val txtMensaje = findViewById<TextView>(R.id.txtMensaje)

        findViewById<Button>(R.id.btnIngresar).setOnClickListener {
            // El bloqueo se guarda en SharedPreferences: no se salta cerrando la app
            if (System.currentTimeMillis() < prefs.getLong("bloqueo", 0)) {
                txtMensaje.text = "Bloqueado, espera 1 minuto"
                return@setOnClickListener
            }
            val u = etUsuario.text.toString().trim()
            val clave = etClave.text.toString()
            if (u.isEmpty() || clave.isEmpty()) {
                txtMensaje.text = "Ingresa usuario y contraseña"
                return@setOnClickListener
            }

            val r = BaseDatos.bd.login(u, clave)
            if (r != null) {
                prefs.edit().putInt("intentos", 0).apply()
                rol = r
                usuario = u
                startActivity(Intent(this, MonitoreoActivity::class.java))
                finish()
            } else {
                val intentos = prefs.getInt("intentos", 0) + 1
                if (intentos >= 3) {
                    prefs.edit().putLong("bloqueo", System.currentTimeMillis() + 60_000)
                        .putInt("intentos", 0).apply()
                    txtMensaje.text = "3 intentos fallidos: bloqueado 1 minuto"
                } else {
                    prefs.edit().putInt("intentos", intentos).apply()
                    txtMensaje.text = "Usuario o contraseña incorrectos (intento $intentos de 3)"
                }
            }
        }
    }
}
