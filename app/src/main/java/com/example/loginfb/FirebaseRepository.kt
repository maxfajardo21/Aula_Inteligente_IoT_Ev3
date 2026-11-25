package com.example.loginfb

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LecturaAula(
    val id: String = "",
    val temperatura: Float = 0f,
    val gas: Int = 0,
    val luz: Int = 0,
    val presencia: Boolean = false,
    val extractor: Boolean = false,
    val ventana: Boolean = false,
    val buzzer: Boolean = false,
    val alerta: String = "NORMAL",
    val fechaHora: String = "",
    val timestamp: Long = 0L,
    val usuarioEmail: String = ""
)

object FirebaseRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val coleccionLecturas = db.collection("lecturas_aula")

    suspend fun login(correo: String, pass: String): Boolean {
        return try {
            auth.signInWithEmailAndPassword(correo.trim(), pass).await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Error en login: ${e.message}", e)
            false
        }
    }

    suspend fun register(correo: String, pass: String): Boolean {
        return try {
            auth.createUserWithEmailAndPassword(correo.trim(), pass).await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseAuth", "Error en register: ${e.message}", e)
            false
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun guardarLectura(
        temp: Float,
        gas: Int,
        luz: Int,
        presencia: Boolean,
        extractor: Boolean,
        ventana: Boolean,
        buzzer: Boolean,
        alerta: String
    ) {
        val ahora = System.currentTimeMillis()
        val formato = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val fechaTexto = formato.format(Date(ahora))
        val correoUsuario = auth.currentUser?.email ?: "anonimo@inacap.cl"

        val datos = hashMapOf(
            "temperatura" to temp,
            "gas" to gas,
            "luz" to luz,
            "presencia" to presencia,
            "extractor" to extractor,
            "ventana" to ventana,
            "buzzer" to buzzer,
            "alerta" to alerta,
            "fechaHora" to fechaTexto,
            "timestamp" to ahora,
            "usuarioEmail" to correoUsuario
        )

        coleccionLecturas.add(datos)
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error guardando lectura: ${e.message}", e)
            }
    }

    fun escucharHistorial(onCambio: (List<LecturaAula>) -> Unit) {
        coleccionLecturas
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val lista = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(LecturaAula::class.java)?.copy(id = doc.id)
                }
                onCambio(lista)
            }
    }
}