package cl.fourpeople.aps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
fun PantallaInicio(
    onCerrarSesion: () -> Unit,
    onAbrirDocumentos: () -> Unit,
    onNuevaSolicitud: () -> Unit,
    onNavegar: (String) -> Unit
) {
    var mostrarCarga by rememberSaveable {
        mutableStateOf(false)
    }

    val pendientes = DatosFuncionario.documentos.count {
        it.estado == EstadoDocumento.PENDIENTE
    }

    val rechazados = DatosFuncionario.documentos.count {
        it.estado == EstadoDocumento.RECHAZADO
    }

    val rechazado = DatosFuncionario.documentos.firstOrNull {
        it.estado == EstadoDocumento.RECHAZADO
    }

    if (mostrarCarga) {
        PantallaCargaDocumento(
            onVolver = {
                mostrarCarga = false
            },
            onGuardado = {
                mostrarCarga = false
                onAbrirDocumentos()
            }
        )
        return
    }

    Scaffold(
        containerColor = FondoInicio,
        bottomBar = { NavegacionFuncionario("inicio", onNavegar) }
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
                    pendientes.toString(),
                    "Documento pendiente",
                    Color(0xFF8B5200),
                    Modifier.weight(1f)
                )

                ResumenInicio(
                    rechazados.toString(),
                    "Documento rechazado",
                    Color(0xFFA52D26),
                    Modifier.weight(1f)
                )

                ResumenInicio(
                    DatosSolicitudes.estado.solicitudes.count {
                        it.estado == EstadoSolicitud.EN_REVISION
                    }.toString(),
                    "Solicitud en curso",
                    Color(0xFF24518A),
                    Modifier.weight(1f)
                )
            }

            if (rechazado != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = onAbrirDocumentos
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF9E1DD)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = if (rechazados == 1) {
                                "1 documento rechazado"
                            } else {
                                "$rechazados documentos rechazados"
                            },
                            color = Color(0xFFA52D26),
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "${rechazado.nombre} · revisa el motivo",
                            color = Color(0xFFA52D26),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            AccesoInicio(
                titulo = "Cargar documento",
                detalle = "Selecciona un PDF o una imagen",
                icono = android.R.drawable.ic_menu_upload,
                destacado = true,
                onClick = {
                    mostrarCarga = true
                }
            )

            AccesoInicio(
                titulo = "Nueva solicitud",
                detalle = "Permisos, feriado legal y otros trámites",
                icono = android.R.drawable.ic_menu_today,
                onClick = onNuevaSolicitud
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Últimos documentos",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = onAbrirDocumentos
                ) {
                    Text("Ver todos")
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                val recientes = DatosFuncionario.documentos.take(2)

                recientes.forEachIndexed { indice, documento ->
                    val (fondo, color) = when (documento.estado) {
                        EstadoDocumento.PENDIENTE ->
                            Color(0xFFFFEED0) to Color(0xFF8B5200)

                        EstadoDocumento.APROBADO ->
                            Color(0xFFDDEFE6) to VerdeInicio

                        EstadoDocumento.RECHAZADO ->
                            Color(0xFFF9E1DD) to Color(0xFFA52D26)
                    }

                    MovimientoInicio(
                        titulo = documento.nombre,
                        detalle = "${documento.fecha} · ${documento.categoria}",
                        estado = documento.estado.texto,
                        fondo = fondo,
                        color = color,
                        onClick = onAbrirDocumentos
                    )

                    if (indice < recientes.lastIndex) {
                        HorizontalDivider(
                            color = Color(0xFFE5E9E5)
                        )
                    }
                }
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
    destacado: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
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
                tint = if (destacado) {
                    Color.White
                } else {
                    VerdeInicio
                },
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
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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