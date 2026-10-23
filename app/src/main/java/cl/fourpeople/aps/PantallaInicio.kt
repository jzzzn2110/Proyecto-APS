package cl.fourpeople.aps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VerdeInicio = Color(0xFF115D55)
private val FondoInicio = Color(0xFFF5F7F4)
private val GrisInicio = Color(0xFF6E7772)

@Composable
fun PantallaInicio(onCerrarSesion: () -> Unit) {
    Scaffold(
        containerColor = FondoInicio,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val opciones = listOf(
                    "Inicio",
                    "Documentos",
                    "Solicitudes",
                    "Avisos"
                )

                val iconos = listOf(
                    android.R.drawable.ic_menu_view,
                    android.R.drawable.ic_menu_agenda,
                    android.R.drawable.ic_menu_today,
                    android.R.drawable.ic_dialog_info
                )

                opciones.forEachIndexed { indice, titulo ->
                    NavigationBarItem(
                        selected = indice == 0,
                        enabled = indice == 0,
                        onClick = {},
                        icon = {
                            Icon(
                                painterResource(iconos[indice]),
                                contentDescription = null
                            )
                        },
                        label = {
                            Text(titulo, fontSize = 10.sp)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VerdeInicio,
                            selectedTextColor = VerdeInicio,
                            indicatorColor = Color(0xFFDDEFE6)
                        )
                    )
                }
            }
        }
    ) { espacio ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(espacio)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Color(0xFFDDEFE6),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "CR",
                        color = VerdeInicio,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Hola, Camila",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Funcionaria · CESFAM Norte",
                        color = GrisInicio,
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = onCerrarSesion) {
                    Icon(
                        painterResource(
                            android.R.drawable.ic_menu_close_clear_cancel
                        ),
                        contentDescription = "Cerrar sesión",
                        tint = VerdeInicio
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResumenInicio(
                    "1",
                    "Documento pendiente",
                    Color(0xFF8B5200),
                    Modifier.weight(1f)
                )

                ResumenInicio(
                    "1",
                    "Documento rechazado",
                    Color(0xFFA52D26),
                    Modifier.weight(1f)
                )

                ResumenInicio(
                    "1",
                    "Solicitud en curso",
                    Color(0xFF24518A),
                    Modifier.weight(1f)
                )
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF9E1DD)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "1 documento rechazado",
                        color = Color(0xFFA52D26),
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Certificado curso IAAS · revisa el motivo",
                        color = Color(0xFFA52D26),
                        fontSize = 12.sp
                    )
                }
            }

            AccesoInicio(
                titulo = "Cargar documento",
                detalle = "Escanea con la cámara o sube un archivo",
                icono = android.R.drawable.ic_menu_upload,
                destacado = true
            )

            AccesoInicio(
                titulo = "Nueva solicitud",
                detalle = "Permisos, feriado legal y otros trámites",
                icono = android.R.drawable.ic_menu_today
            )

            Text(
                "Últimos movimientos",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                MovimientoInicio(
                    titulo = "Anexo de contrato 2026",
                    detalle = "Documento · hoy",
                    estado = "Pendiente",
                    fondo = Color(0xFFFFEED0),
                    color = Color(0xFF8B5200)
                )

                HorizontalDivider(
                    color = Color(0xFFE5E9E5)
                )

                MovimientoInicio(
                    titulo = "Permiso administrativo",
                    detalle = "Solicitud · 26 sep",
                    estado = "En revisión",
                    fondo = Color(0xFFDDE8F7),
                    color = Color(0xFF24518A)
                )
            }
        }
    }
}

@Composable
private fun ResumenInicio(
    numero: String,
    titulo: String,
    color: Color,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 112.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                numero,
                color = color,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                titulo,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = GrisInicio
            )
        }
    }
}

@Composable
private fun AccesoInicio(
    titulo: String,
    detalle: String,
    icono: Int,
    destacado: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (destacado) {
                VerdeInicio
            } else {
                Color.White
            }
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                painterResource(icono),
                contentDescription = null,
                tint = if (destacado) Color.White else VerdeInicio,
                modifier = Modifier.size(30.dp)
            )

            Column(Modifier.weight(1f)) {
                Text(
                    titulo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = if (destacado) {
                        Color.White
                    } else {
                        Color(0xFF18231F)
                    }
                )

                Text(
                    detalle,
                    fontSize = 12.sp,
                    color = if (destacado) {
                        Color.White
                    } else {
                        GrisInicio
                    }
                )
            }
        }
    }
}

@Composable
private fun MovimientoInicio(
    titulo: String,
    detalle: String,
    estado: String,
    fondo: Color,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                titulo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )

            Text(
                detalle,
                color = GrisInicio,
                fontSize = 12.sp
            )
        }

        Text(
            estado,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    fondo,
                    RoundedCornerShape(20.dp)
                )
                .padding(
                    horizontal = 9.dp,
                    vertical = 5.dp
                )
        )
    }
}