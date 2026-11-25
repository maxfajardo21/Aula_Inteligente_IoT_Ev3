package com.example.loginfb.vistas

import android.content.Context
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException

@Composable
fun RestablecerContrasena(navController: NavHostController, auth: FirebaseAuth) {
    var email by rememberSaveable { mutableStateOf("") }
    var nuevaPassword by rememberSaveable { mutableStateOf("") }
    var verificarPassword by rememberSaveable { mutableStateOf("") }
    var cargando by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp), contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Restablecer Contraseña",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Correo Electronico") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = nuevaPassword,
                onValueChange = { nuevaPassword = it },
                label = { Text("Nueva contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = verificarPassword,
                onValueChange = { verificarPassword = it },
                label = { Text("Verificar contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                enabled = !cargando,
                onClick = {
                    restablecer(
                        email.trim(), nuevaPassword, verificarPassword, auth, context,
                        navController
                    ) { cargando = it }
                }) {
                Text("Restablecer")
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { navController.popBackStack() }) {
                Text("Volver a Iniciar Sesión")
            }
        }
    }
}

private fun restablecer(
    email: String,
    nuevaPassword: String,
    verificarPassword: String,
    auth: FirebaseAuth,
    context: Context,
    navController: NavHostController,
    setCargando: (Boolean) -> Unit
) {
    if (email.isEmpty() || nuevaPassword.isEmpty() || verificarPassword.isEmpty()) {
        Toast.makeText(context, "Complete todos los campos", Toast.LENGTH_SHORT).show()
        return
    }
    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        Toast.makeText(context, "Correo no válido", Toast.LENGTH_SHORT).show()
        return
    }
    if (nuevaPassword.length < 6) {
        Toast.makeText(context, "La contraseña debe tener al menos 6 caracteres", Toast.LENGTH_SHORT).show()
        return
    }
    if (nuevaPassword != verificarPassword) {
        Toast.makeText(context, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
        return
    }

    setCargando(true)
    auth.sendPasswordResetEmail(email)
        .addOnCompleteListener { task ->
            setCargando(false)
            if (task.isSuccessful) {
                Toast.makeText(
                    context,
                    "Te enviamos un correo para confirmar el cambio",
                    Toast.LENGTH_LONG
                ).show()
                navController.popBackStack()
            } else if (task.exception is FirebaseAuthInvalidUserException) {
                Toast.makeText(context, "Ese correo no está registrado", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "No se pudo completar la solicitud", Toast.LENGTH_SHORT).show()
            }
        }
}
