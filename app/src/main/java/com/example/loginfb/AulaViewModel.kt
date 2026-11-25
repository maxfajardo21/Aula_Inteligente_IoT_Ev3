package com.example.loginfb

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.loginfb.data.BluetoothClient
import com.example.loginfb.data.Dev
import com.example.loginfb.data.Lectura
import com.example.loginfb.data.UserDb
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sin

data class AulaState(
    val loggedIn: Boolean = false,
    val authError: String? = null,
    val connected: Boolean = false,
    val status: String = "Sin conexion",
    val temp: Float? = null,
    val gas: Int? = null,
    val luz: Int? = null,
    val presencia: Boolean? = null,
    val extractor: Boolean = false,
    val luces: Boolean = false,
    val ventana: Boolean = false,
    val buzzer: Boolean = false,
    val alerta: String = "NORMAL",
    val auto: Boolean = false,
    val historial: List<Lectura> = emptyList()
)

class AulaViewModel(app: Application) : AndroidViewModel(app) {
    private val localDb = UserDb(app)
    private val bt = BluetoothClient(app)
    private val _ui = MutableStateFlow(
        AulaState(loggedIn = FirebaseAuth.getInstance().currentUser != null)
    )
    val ui: StateFlow<AulaState> = _ui.asStateFlow()
    private val prefs = app.getSharedPreferences("seguridad", Context.MODE_PRIVATE)
    private var job: Job? = null
    private var lastSave = 0L

    init {
        iniciarEscuchaFirestore()
    }

    private fun iniciarEscuchaFirestore() {
        FirebaseRepository.escucharHistorial { lecturasNube ->
            val listaAdaptada = lecturasNube.map { item ->
                Lectura(
                    ts = item.timestamp,
                    temp = item.temperatura,
                    gas = item.gas,
                    luz = item.luz,
                    presencia = item.presencia
                )
            }
            _ui.update { it.copy(historial = listaAdaptada) }
        }
    }

    fun login(correo: String, pass: String, crear: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val ahora = System.currentTimeMillis()
            if (!crear) {
                val resto = prefs.getLong("bloqueo", 0L) - ahora
                if (resto > 0) {
                    _ui.update { it.copy(authError = "Demasiados intentos. Intenta de nuevo en ${resto / 1000 + 1} s.") }
                    return@launch
                }
            }
            val ok = if (crear) {
                FirebaseRepository.register(correo, pass)
            } else {
                FirebaseRepository.login(correo, pass)
            }
            var error: String? = null
            if (!ok) {
                error = if (crear) {
                    "No se pudo crear la cuenta: usa un correo nuevo y una contraseña de 6 o mas caracteres."
                } else {
                    val fallos = prefs.getInt("fallos", 0) + 1
                    if (fallos >= MAX_FALLOS) {
                        prefs.edit().putInt("fallos", 0).putLong("bloqueo", ahora + BLOQUEO_MS).apply()
                        "Demasiados intentos. Login bloqueado por ${BLOQUEO_MS / 1000} s."
                    } else {
                        prefs.edit().putInt("fallos", fallos).apply()
                        "Correo o contraseña incorrectos. Intentos restantes: ${MAX_FALLOS - fallos}."
                    }
                }
            } else {
                prefs.edit().putInt("fallos", 0).putLong("bloqueo", 0L).apply()
                iniciarEscuchaFirestore()
            }
            _ui.update { it.copy(loggedIn = ok, authError = error) }
        }
    }

    fun cargarHistorial() {
        iniciarEscuchaFirestore()
    }

    fun logout() {
        disconnect()
        FirebaseRepository.logout()
        _ui.value = AulaState(loggedIn = false)
    }

    fun pairedDevices(): List<Dev> = bt.paired()

    fun connect(dev: Dev) {
        job?.cancel()
        job = viewModelScope.launch {
            _ui.update { it.copy(status = "Conectando...") }
            try {
                bt.connectAndRead(dev.address) {
                    _ui.update { it.copy(connected = true, status = "Conectado a ${dev.name}") }
                }.collect { parse(it) }
                _ui.update { it.copy(connected = false, status = "Conexion cerrada") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(connected = false, status = "No se pudo conectar") }
            }
        }
    }

    fun startDemo() {
        job?.cancel()
        bt.close()
        job = viewModelScope.launch {
            _ui.update { it.copy(connected = true, status = "Modo demo") }
            var n = 0
            while (true) {
                val t = 27.0 + 5.0 * sin(n / 20.0)
                val luz = (450 + 300 * sin(n / 30.0)).toInt()
                val gente = (n / 25) % 3 != 0
                val s = _ui.value
                val base = String.format(Locale.US, "T:%.1f,G:%d,L:%d,P:%d", t, 120 + (n * 7) % 60 + if (t > 28.0) 150 else 0, luz, if (gente) 1 else 0)
                val auto = if (s.auto) String.format(
                    Locale.US, ",E:%d,V:%d,Z:%d",
                    if (t > 28.0) 1 else 0, if (t > 30.0) 1 else 0, if (luz < 300 && gente) 1 else 0
                ) else ""
                parse(base + auto)
                n++
                delay(1000)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        bt.close()
        _ui.update { it.copy(connected = false, status = "Sin conexion") }
    }

    fun setExtractor(on: Boolean) { _ui.update { it.copy(extractor = on) }; send(if (on) "EXT:1" else "EXT:0") }
    fun setLuces(on: Boolean) { _ui.update { it.copy(luces = on) }; send(if (on) "LUZ:1" else "LUZ:0") }
    fun setVentana(on: Boolean) { _ui.update { it.copy(ventana = on) }; send(if (on) "VEN:1" else "VEN:0") }
    fun setAuto(on: Boolean) { _ui.update { it.copy(auto = on) }; send(if (on) "AUTO:1" else "AUTO:0") }
    fun setTodo(on: Boolean) { setExtractor(on); setLuces(on) }

    private fun send(cmd: String) {
        viewModelScope.launch(Dispatchers.IO) { bt.send(cmd) }
    }

    private fun parse(line: String) {
        val m = line.trim().split(',').mapNotNull { p ->
            p.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() }
        }.toMap()
        if (m.isEmpty()) return
        _ui.update { s ->
            s.copy(
                temp = m["T"]?.toFloatOrNull() ?: s.temp,
                gas = m["G"]?.toIntOrNull() ?: s.gas,
                luz = m["L"]?.toIntOrNull() ?: s.luz,
                presencia = m["P"]?.let { it == "1" } ?: s.presencia,
                extractor = m["E"]?.let { it == "1" } ?: s.extractor,
                ventana = m["V"]?.let { it == "1" } ?: s.ventana,
                luces = m["Z"]?.let { it == "1" } ?: s.luces,
                alerta = m["A"] ?: s.alerta
            )
        }
        val now = System.currentTimeMillis()
        if (now - lastSave > 5_000) {
            lastSave = now
            val s = _ui.value
            viewModelScope.launch(Dispatchers.IO) {
                localDb.saveReading(now, s.temp, s.gas, s.luz, s.presencia)
                FirebaseRepository.guardarLectura(
                    temp = s.temp ?: 0f,
                    gas = s.gas ?: 0,
                    luz = s.luz ?: 0,
                    presencia = s.presencia ?: false,
                    extractor = s.extractor,
                    ventana = s.ventana,
                    buzzer = s.buzzer,
                    alerta = s.alerta
                )
            }
        }
    }

    override fun onCleared() { bt.close() }

    private companion object {
        const val MAX_FALLOS = 5
        const val BLOQUEO_MS = 60_000L
    }
}