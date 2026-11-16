package com.example.detectorgas

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Muestra las lecturas guardadas en SQLite (más recientes primero)
class HistorialActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)
        BaseDatos.iniciar(this)

        val lecturas = BaseDatos.bd.lecturas()
        val alertas = lecturas.count { it.est == "ALERTA" }
        val maximo = lecturas.maxOfOrNull { it.gas } ?: 0
        findViewById<TextView>(R.id.txtResumen).text =
            "Lecturas: ${lecturas.size}    Alertas: $alertas    Máximo: $maximo"

        val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        val filas = lecturas.map { "${fmt.format(Date(it.t))}     Gas: ${it.gas}     ${it.est}" }

        findViewById<ListView>(R.id.lista).adapter =
            object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, filas) {
                override fun getView(pos: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(pos, convertView, parent) as TextView
                    v.setTextColor(if (lecturas[pos].est == "ALERTA") Color.rgb(198, 40, 40) else Color.DKGRAY)
                    return v
                }
            }

        findViewById<Button>(R.id.btnVolver).setOnClickListener { finish() }
    }
}
