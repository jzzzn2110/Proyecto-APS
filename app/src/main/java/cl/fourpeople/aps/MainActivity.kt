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
import kotlinx.coroutines.CancellationException

// coloresS: se pueden cambiar estos valores para ajustar la apariencia.
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
                // Indica si se está mostrando una sesión de demostración.
                var sesionIniciada by rememberSaveable {
                    mutableStateOf(false)
                }

                // Recuerda el perfil elegido al ingresar.
                var perfilActual by rememberSaveable { mutableStateOf("Funcionario") }

                // Indica qué pantalla del funcionario se muestra.
                var pantallaActual by rememberSaveable {
                    mutableStateOf("inicio")
                }

                var documentoObjetivo by rememberSaveable { mutableStateOf<String?>(null) }
                var solicitudObjetivo by rememberSaveable { mutableStateOf<String?>(null) }
                val navegar: (String) -> Unit = { destino ->
                    documentoObjetivo = null
                    solicitudObjetivo = null
                    pantallaActual = destino
                }

                var preparado by remember {
                    mutableStateOf(false)
                }

                var errorCarga by remember {
                    mutableStateOf<String?>(null)
                }

                var intento by remember {
                    mutableIntStateOf(0)
                }

                // al iniciar carga del teléfono los documentos y las solicitudes.
                LaunchedEffect(intento) {
                    preparado = false
                    errorCarga = null

                    try {
                        val guardados = PersistenciaDocumentos.cargar(
                            applicationContext,
                            DatosFuncionario.documentos.toList()
                        )

                        DatosFuncionario.documentos.clear()
                        DatosFuncionario.documentos.addAll(guardados)

                        DatosSolicitudes.cargar(applicationContext)
                        preparado = true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        errorCarga = e.message
                            ?: "No se pudo abrir el expediente."
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FondoAps
                ) {
                    if (!preparado) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val mensaje = errorCarga

                            if (mensaje == null) {
                                CircularProgressIndicator()

                                Spacer(Modifier.height(16.dp))

                                Text("Cargando expediente…")
                            } else {
                                Text(
                                    mensaje,
                                    color = MaterialTheme.colorScheme.error
                                )

                                Spacer(Modifier.height(16.dp))

                                Button(
                                    onClick = { intento++ }
                                ) {
                                    Text("Reintentar")
                                }
                            }
                        }
                        // Administrativo y Jefatura entran a su propia pantalla.
                    } else if (sesionIniciada && perfilActual != "Funcionario") {
                        PantallaAdministrativa(perfilActual) {
                            sesionIniciada = false
                            navegar("inicio")
                        }
                    } else if (sesionIniciada) {
                        when (pantallaActual) {
                            "documentos" -> PantallaDocumentos(
                                onVolver = { navegar("inicio") },
                                onNavegar = navegar,
                                documentoInicialId = documentoObjetivo
                            )
                            "solicitudes" -> PantallaSolicitudes(
                                onNavegar = navegar,
                                onNueva = { navegar("nuevaSolicitud") },
                                solicitudInicialId = solicitudObjetivo
                            )
                            "nuevaSolicitud" -> PantallaNuevaSolicitud(
                                onVolver = { navegar("solicitudes") },
                                onEnviada = { id ->
                                    solicitudObjetivo = id
                                    pantallaActual = "solicitudEnviada"
                                }
                            )
                            "solicitudEnviada" -> PantallaSolicitudEnviada(
                                id = solicitudObjetivo,
                                onVerSolicitudes = { pantallaActual = "solicitudes" },
                                onInicio = { navegar("inicio") }
                            )
                            "avisos" -> PantallaAvisos(
                                onNavegar = navegar,
                                onAbrir = { aviso ->
                                    if (aviso.grupo == "DOCUMENTOS") {
                                        documentoObjetivo = aviso.destinoId
                                        pantallaActual = "documentos"
                                    } else {
                                        solicitudObjetivo = aviso.destinoId
                                        pantallaActual = "solicitudes"
                                    }
                                }
                            )
                            else -> PantallaInicio(
                                onCerrarSesion = {
                                    sesionIniciada = false
                                    navegar("inicio")
                                },
                                onAbrirDocumentos = { navegar("documentos") },
                                onNuevaSolicitud = { navegar("nuevaSolicitud") },
                                onNavegar = navegar
                            )
                        }
                    } else {
                        PantallaLogin(
                            // El login devuelve el perfil seleccionado
                            onIngresar = { perfil ->
                                perfilActual = perfil
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
// login de prueba: aquí están el formulario y la validación de las cuentas ficticias.
private fun PantallaLogin(onIngresar: (String) -> Unit) {
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
                label = { Text("Usuario") },
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
                label = { Text("Contraseña") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Rol simulado (solo para el MVP)",
                fontWeight = FontWeight.SemiBold
            )

            // Perfiles: estos nombres también se usan para comprobar permisos.
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

                        rol == "TI / Admin" -> {
                            error = "En esta etapa está disponible " +
                                    "Funcionario, Administrativo y Jefatura."
                        }

                        // cuenta de prueba: usuario distinto según el perfil.
                        usuario.trim() == when (rol) {
                            "Administrativo" -> "administrativo"
                            "Jefatura" -> "jefatura"
                            else -> "c.rojas"
                        } && clave == "Contraseña123#" -> {
                            onIngresar(rol)
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