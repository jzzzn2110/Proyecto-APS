package cl.fourpeople.aps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PantallaJefatura(
    perfil: String,
    onSalir: () -> Unit
) {
    // Permite acceder al almacenamiento del teléfono.
    val contexto = LocalContext.current.applicationContext

    // Permite guardar los cambios desde los botones.
    val alcance = rememberCoroutineScope()

    // Guarda el identificador de la solicitud abierta.
    var seleccion by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    // Respuesta que escribe Jefatura.
    var respuesta by rememberSaveable {
        mutableStateOf("")
    }

    // Cambia entre las solicitudes pendientes y todas.
    var soloPendientes by rememberSaveable {
        mutableStateOf(true)
    }

    // Evita pulsar los botones varias veces mientras guarda.
    var ocupado by remember {
        mutableStateOf(false)
    }

    // Muestra los problemas de validación o guardado.
    var error by remember {
        mutableStateOf<String?>(null)
    }

    // Usa las solicitudes compartidas con el funcionario.
    val solicitudes = DatosSolicitudes.estado.solicitudes
    val solicitud = solicitudes.firstOrNull {
        it.id == seleccion
    }

    // true aprueba la solicitud; false la rechaza.
    fun resolver(aprobar: Boolean) {
        val original = solicitud ?: return
        if (ocupado) return

        ocupado = true
        error = null

        alcance.launch {
            try {
                // Completa el guardado si se recrea la pantalla.
                withContext(NonCancellable) {
                    DatosSolicitudes.resolver(
                        contexto = contexto,
                        perfil = perfil,
                        id = original.id,
                        aprobar = aprobar,
                        respuesta = respuesta
                    )
                }

                // Cierra el detalle después de guardar.
                seleccion = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "No se pudo resolver la solicitud."
            } finally {
                ocupado = false
            }
        }
    }

    Scaffold { espacio ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(espacio)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Jefatura · solicitudes",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Text(DatosAdministracion.estado.expediente.centro)

            OutlinedButton(
                onClick = onSalir,
                enabled = !ocupado
            ) {
                Text("Cerrar sesión")
            }

            Text(
                "Expediente ficticio: " +
                        DatosAdministracion.estado.expediente.nombre
            )

            // Muestra los saldos del año actual.
            listOf(
                TipoSolicitud.ADMINISTRATIVO,
                TipoSolicitud.FERIADO
            ).forEach { tipo ->
                val ano = LocalDate.now().year

                // Descuenta solamente las solicitudes aprobadas.
                val sinConsumir = ReglasSolicitudes.saldo(
                    solicitudes,
                    tipo,
                    ano,
                    reservarPendientes = false
                )

                // También reserva los días de solicitudes pendientes.
                val libres = ReglasSolicitudes.saldo(
                    solicitudes,
                    tipo,
                    ano,
                    reservarPendientes = true
                )

                Text(
                    "${tipo.texto} ($ano): " +
                            "${textoDias(sinConsumir)} días sin consumir · " +
                            "${textoDias(libres)} libres considerando reservas"
                )
            }

            // Al pulsarlo, cambia el filtro de la lista.
            FilterChip(
                selected = soloPendientes,
                onClick = {
                    soloPendientes = !soloPendientes
                },
                label = {
                    Text(
                        if (soloPendientes) {
                            "En revisión"
                        } else {
                            "Todas las solicitudes"
                        }
                    )
                }
            )

            val visibles = solicitudes.filter {
                !soloPendientes ||
                        it.estado == EstadoSolicitud.EN_REVISION
            }

            if (visibles.isEmpty()) {
                Text("No hay solicitudes en esta vista.")
            }

            // Muestra una tarjeta por cada solicitud.
            visibles.forEach { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.tipo.texto,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            "${fechaSolicitud(item.desde)} al " +
                                    "${fechaSolicitud(item.hasta)} · " +
                                    "${textoDias(item.dias)} días"
                        )

                        EtiquetaSolicitud(item.estado)

                        Button(
                            onClick = {
                                seleccion = item.id
                                respuesta = ""
                                error = null
                            },
                            enabled = !ocupado
                        ) {
                            Text("Abrir solicitud")
                        }
                    }
                }
            }

            Text(
                "Saldos de demostración por días calendario. " +
                        "Sin conexión a un servidor."
            )
        }
    }

    // Abre el detalle cuando se selecciona una solicitud.
    solicitud?.let { item ->
        AlertDialog(
            onDismissRequest = {
                if (!ocupado) seleccion = null
            },
            title = {
                Text(item.tipo.texto)
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EtiquetaSolicitud(item.estado)

                    Text("Folio: ${item.id}")

                    Text(
                        "${fechaSolicitud(item.desde)} al " +
                                "${fechaSolicitud(item.hasta)} · " +
                                item.jornada.texto
                    )

                    Text("Motivo: ${item.motivo}")

                    // Usa el año de esta solicitud para calcular su saldo.
                    val ano = LocalDate.parse(item.desde).year

                    if (
                        item.tipo == TipoSolicitud.ADMINISTRATIVO ||
                        item.tipo == TipoSolicitud.FERIADO
                    ) {
                        val saldo = ReglasSolicitudes.saldo(
                            solicitudes,
                            item.tipo,
                            ano
                        )

                        Text(
                            "Saldo del año $ano con reservas: " +
                                    "${textoDias(saldo)} días"
                        )
                    }

                    // Muestra el adjunto si la solicitud tiene uno.
                    if (item.archivoUri.isNotBlank()) {
                        VistaPreviaDocumento(item.archivoUri)
                    }

                    // Solo las solicitudes pendientes se pueden resolver.
                    if (item.estado == EstadoSolicitud.EN_REVISION) {
                        OutlinedTextField(
                            value = respuesta,
                            onValueChange = {
                                respuesta = it
                            },
                            label = {
                                Text("Respuesta de Jefatura")
                            },
                            enabled = !ocupado
                        )

                        Text(
                            "Respuesta obligatoria: entre 10 y 500 caracteres."
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { resolver(true) },
                                enabled = !ocupado
                            ) {
                                Text("Aprobar")
                            }

                            OutlinedButton(
                                onClick = { resolver(false) },
                                enabled = !ocupado
                            ) {
                                Text("Rechazar")
                            }
                        }
                    } else {
                        // Las solicitudes resueltas muestran su respuesta.
                        Text(item.respuesta)

                        if (item.revisadaPor.isNotBlank()) {
                            Text(
                                "Revisada por: ${item.revisadaPor}\n" +
                                        "Fecha y hora: ${item.revisadaEn}"
                            )
                        }
                    }

                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (ocupado) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        seleccion = null
                    },
                    enabled = !ocupado
                ) {
                    Text("Cerrar")
                }
            }
        )
    }
}