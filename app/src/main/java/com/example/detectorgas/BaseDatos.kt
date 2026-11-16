package com.example.detectorgas

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.random.Random

// Base de datos local SQLite:
//   usuarios(usuario, hash, sal, rol)  -> login. La contraseña NUNCA se guarda en texto plano
//                                         (PBKDF2 + sal aleatoria por usuario)
//   lecturas(gas, est, t)              -> historial del detector
class BaseDatos private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx, "detectorgas.db", null, 1) {

    companion object {
        lateinit var bd: BaseDatos
        fun iniciar(ctx: Context) {
            if (!::bd.isInitialized) bd = BaseDatos(ctx.applicationContext)
        }

        private fun hash(clave: String, sal: ByteArray): String {
            val spec = PBEKeySpec(clave.toCharArray(), sal, 10_000, 256)
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE usuarios(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            usuario TEXT UNIQUE NOT NULL,
            hash TEXT NOT NULL,
            sal TEXT NOT NULL,
            rol TEXT NOT NULL CHECK(rol IN ('ADMIN','USUARIO')))""")
        db.execSQL("""CREATE TABLE lecturas(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            gas INTEGER NOT NULL,
            est TEXT NOT NULL,
            t INTEGER NOT NULL)""")

        // Usuarios de prueba
        crearUsuario(db, "admin", "admin123", "ADMIN")
        crearUsuario(db, "usuario", "usuario123", "USUARIO")

        // Historial de prueba: últimas 24 h, una lectura cada 30 min, con dos fugas
        val ahora = System.currentTimeMillis()
        val r = Random(7)
        for (i in 48 downTo 1) {
            val gas = when (i) {
                in 30..32 -> 380 + r.nextInt(150)   // fuga en la madrugada
                in 9..10 -> 360 + r.nextInt(90)     // fuga en la tarde
                else -> 90 + r.nextInt(80)
            }
            insertarLectura(db, gas, if (gas >= 350) "ALERTA" else "NORMAL", ahora - i * 30 * 60_000L)
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun crearUsuario(db: SQLiteDatabase, usuario: String, clave: String, rol: String) {
        val sal = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val salHex = sal.joinToString("") { "%02x".format(it) }
        db.insert("usuarios", null, ContentValues().apply {
            put("usuario", usuario); put("hash", hash(clave, sal)); put("sal", salHex); put("rol", rol)
        })
    }

    // Devuelve el rol si usuario y contraseña son correctos; null si no
    fun login(usuario: String, clave: String): String? {
        // Consulta parametrizada (?): evita inyección SQL
        readableDatabase.rawQuery("SELECT hash, sal, rol FROM usuarios WHERE usuario = ?", arrayOf(usuario)).use { c ->
            if (!c.moveToFirst()) return null
            val sal = c.getString(1).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            return if (hash(clave, sal) == c.getString(0)) c.getString(2) else null
        }
    }

    // ---------- Historial ----------

    private fun insertarLectura(db: SQLiteDatabase, gas: Int, est: String, t: Long) {
        db.insert("lecturas", null, ContentValues().apply { put("gas", gas); put("est", est); put("t", t) })
    }

    private var ultimoRegistro = 0L
    private var ultimoEstado = ""

    // Guarda una lectura cada 10 s, o al instante si cambia el estado (NORMAL -> ALERTA)
    fun registrar(m: Map<String, String>) {
        val est = m["EST"] ?: return
        val ahora = System.currentTimeMillis()
        if (ahora - ultimoRegistro > 10_000 || est != ultimoEstado) {
            ultimoRegistro = ahora
            ultimoEstado = est
            insertarLectura(writableDatabase, m["GAS"]?.toIntOrNull() ?: 0, est, ahora)
        }
    }

    data class Lectura(val gas: Int, val est: String, val t: Long)

    fun lecturas(): List<Lectura> {
        val lista = mutableListOf<Lectura>()
        readableDatabase.rawQuery("SELECT gas, est, t FROM lecturas ORDER BY t DESC LIMIT 200", null).use { c ->
            while (c.moveToNext()) lista.add(Lectura(c.getInt(0), c.getString(1), c.getLong(2)))
        }
        return lista
    }
}
