package cl.fourpeople.aps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VerdeDocumentos = Color(0xFF115D55)

@Composable
fun PantallaDocumentos(onVolver: () -> Unit) {
    var mostrarCarga by rememberSaveable {
        mutableStateOf(false)
    }

    var editandoId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var busqueda by rememberSaveable {
        mutableStateOf("")
    }

    var categoria by rememberSaveable {
        mutableStateOf("Todos")
    }

    var seleccionadoId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    val seleccionado = DatosFuncionario.documentos.firstOrNull {
        it.id == seleccionadoId
    }

    val visibles = DatosFuncionario.documentos.filter {
        (categoria == "Todos" || it.categoria == categoria) &&
                it.nombre.contains(
                    busqueda.trim(),
                    ignoreCase = true
                )
    }

    if (mostrarCarga) {
        PantallaCargaDocumento(
            onVolver = {
                mostrarCarga = false
                editandoId = null
            },
            onGuardado = {
                mostrarCarga = false
                busqueda = ""
                categoria = "Todos"
                seleccionadoId = editandoId
                editandoId = null
            },
            documentoId = editandoId
        )
        return
    }

    BackHandler(onBack = onVolver)

    Scaffold(
        containerColor = Color(0xFFF5F7F4),
        bottomBar = {
            NavigationBar(
                containerColor = Color.White
            ) {
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
                        selected = indice == 1,
                        enabled = indice <= 1,
                        onClick = {
                            if (indice == 0) {
                                onVolver()
                            }
                        },
                        icon = {
                            Icon(
                                painterResource(iconos[indice]),
                                contentDescription = null
                            )
                        },
                        label = {
                            Text(
                                text = titulo,
                                fontSize = 10.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = VerdeDocumentos,
                            selectedTextColor = VerdeDocumentos,
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mis documentos",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        editandoId = null
                        mostrarCarga = true
                    }
                ) {
                    Text(
                        text = "+",
                        fontSize = 24.sp
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Camila Rojas Soto",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "RUN DEMO-CAMILA · ficticio",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )

                    HorizontalDivider()

                    Text(
                        text = "Centro: CESFAM Norte",
                        color = VerdeDocumentos
                    )

                    Text(
                        text = "Cargo: Técnico de enfermería",
                        fontSize = 13.sp
                    )

                    Text(
                        text = "Contrato: Plazo fijo",
                        fontSize = 13.sp
                    )

                    Text(
                        text = "Ingreso: 15/03/2021",
                        fontSize = 13.sp
                    )
                }
            }

            OutlinedTextField(
                value = busqueda,
                onValueChange = {
                    busqueda = it
                },
                label = {
                    Text("Buscar documento")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Todos",
                    "Contrato",
                    "Anexo",
                    "Capacitación",
                    "Resolución",
                    "Decreto",
                    "Otro"
                ).forEach { opcion ->
                    FilterChip(
                        selected = categoria == opcion,
                        onClick = {
                            categoria = opcion
                        },
                        label = {
                            Text(opcion)
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VerdeDocumentos,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (visibles.isEmpty()) {
                Text(
                    text = "No hay documentos que coincidan con la búsqueda.",
                    color = Color.Gray
                )
            }

            visibles.forEach { documento ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            seleccionadoId = documento.id
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = documento.nombre,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )

                            Text(
                                text = "${documento.fecha} · ${documento.categoria} · v${documento.version}",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }

                        EtiquetaEstado(documento.estado)
                    }
                }
            }
        }
    }

    seleccionado?.let { documento ->
        var versionVista by rememberSaveable(documento.id, documento.version) {
            mutableIntStateOf(documento.version)
        }
        val vista = if (versionVista == documento.version) {
            documento
        } else {
            documento.historial.firstOrNull { it.version == versionVista } ?: documento
        }

        AlertDialog(
            onDismissRequest = { seleccionadoId = null },
            title = { Text(documento.nombre) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Identificador: ${documento.id}", fontSize = 12.sp)
                    Text(
                        if (vista.version == documento.version) {
                            "Versión ${vista.version} · vigente"
                        } else {
                            "Versión ${vista.version} · anterior"
                        },
                        fontWeight = FontWeight.Bold
                    )

                    if (documento.historial.isNotEmpty()) {
                        Text("Historial de versiones", fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (listOf(documento) + documento.historial
                                .sortedByDescending { it.version }).forEach { version ->
                                FilterChip(
                                    selected = versionVista == version.version,
                                    onClick = { versionVista = version.version },
                                    label = {
                                        Text(
                                            if (version.version == documento.version) {
                                                "v${version.version} · vigente"
                                            } else {
                                                "v${version.version}"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    EtiquetaEstado(vista.estado)
                    key(vista.version) {
                        VistaPreviaDocumento(uri = vista.archivoUri)
                    }
                    Text("Nombre: ${vista.nombre}")
                    Text("Categoría: ${vista.categoria}")
                    Text("Fecha: ${vista.fecha}")
                    if (vista.vencimiento.isNotBlank()) {
                        Text("Vencimiento: ${vista.vencimiento}")
                    }
                    vista.horas?.let { Text("Horas de capacitación: $it") }
                    if (vista.estado == EstadoDocumento.RECHAZADO) {
                        Text("Motivo del rechazo", fontWeight = FontWeight.Bold)
                        Text(vista.motivoRechazo, color = Color(0xFFA52D26))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { seleccionadoId = null }) {
                    Text("Cerrar")
                }
            },
            dismissButton = {
                if (documento.estado == EstadoDocumento.RECHAZADO) {
                    TextButton(
                        onClick = {
                            editandoId = documento.id
                            seleccionadoId = null
                            mostrarCarga = true
                        }
                    ) {
                        Text("Corregir documento")
                    }
                }
            }
        )
    }
}

@Composable
private fun EtiquetaEstado(estado: EstadoDocumento) {
    val (fondo, texto) = when (estado) {
        EstadoDocumento.PENDIENTE ->
            Color(0xFFFFEED0) to Color(0xFF8B5200)

        EstadoDocumento.APROBADO ->
            Color(0xFFDDEFE6) to VerdeDocumentos

        EstadoDocumento.RECHAZADO ->
            Color(0xFFF9E1DD) to Color(0xFFA52D26)
    }

    Text(
        text = estado.texto,
        color = texto,
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