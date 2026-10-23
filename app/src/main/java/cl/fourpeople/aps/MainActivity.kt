package cl.fourpeople.aps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VerdeAps = Color(0xFF115D55)
private val FondoAps = Color(0xFFF5F7F4)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = VerdeAps,
                    onPrimary = Color.White,
                    background = FondoAps,
                    surface = Color.White
                )
            ) {
                var sesionIniciada by rememberSaveable {
                    mutableStateOf(false)
                }

                var pantallaActual by rememberSaveable {
                    mutableStateOf("inicio")
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FondoAps
                ) {
                    if (sesionIniciada) {
                        if (pantallaActual == "documentos") {
                            PantallaDocumentos(
                                onVolver = {
                                    pantallaActual = "inicio"
                                }
                            )
                        } else {
                            PantallaInicio(
                                onCerrarSesion = {
                                    sesionIniciada = false
                                    pantallaActual = "inicio"
                                },
                                onAbrirDocumentos = {
                                    pantallaActual = "documentos"
                                }
                            )
                        }
                    } else {
                        PantallaLogin(
                            onIngresar = {
                                pantallaActual = "inicio"
                                sesionIniciada = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PantallaLogin(onIngresar: () -> Unit) {
    var usuario by rememberSaveable {
        mutableStateOf("")
    }

    var clave by rememberSaveable {
        mutableStateOf("")
    }

    var rol by rememberSaveable {
        mutableStateOf("Funcionario")
    }

    var error by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        VerdeAps,
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "APS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "CORPORACIÓN MUNICIPAL DE QUILPUÉ · SALUD",
                color = VerdeAps,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Expediente Digital",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Tus documentos laborales en un solo lugar, " +
                        "seguros y siempre disponibles.",
                color = Color(0xFF6E7772)
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = usuario,
                onValueChange = {
                    usuario = it
                    error = null
                },
                label = {
                    Text("Usuario")
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = clave,
                onValueChange = {
                    clave = it
                    error = null
                },
                label = {
                    Text("Contraseña")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Rol simulado (solo para el MVP)",
                fontWeight = FontWeight.SemiBold
            )

            listOf(
                listOf("Funcionario", "Administrativo"),
                listOf("Jefatura", "TI / Admin")
            ).forEach { fila ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fila.forEach { opcion ->
                        FilterChip(
                            selected = rol == opcion,
                            onClick = {
                                rol = opcion
                                error = null
                            },
                            label = {
                                Text(
                                    text = opcion,
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(24.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VerdeAps,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            error?.let { mensaje ->
                Text(
                    text = mensaje,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    when {
                        usuario.isBlank() || clave.isBlank() -> {
                            error = "Completa el usuario y la contraseña."
                        }

                        rol != "Funcionario" -> {
                            error = "En esta etapa está disponible " +
                                    "el perfil Funcionario."
                        }

                        usuario.trim() == "c.rojas" &&
                                clave == "Demo1234!" -> {
                            onIngresar()
                        }

                        else -> {
                            error = "Usuario o contraseña incorrectos."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Ingresar",
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Autenticación simulada. Se utilizan " +
                        "exclusivamente datos ficticios.",
                color = Color(0xFF6E7772),
                fontSize = 12.sp
            )
        }
    }
}