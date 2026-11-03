package cl.fourpeople.aps

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PantallaUsuarios(onSalir: () -> Unit, onAuditoria: () -> Unit, onIncidentes: () -> Unit, onRestaurado: () -> Unit) {
    val contexto = LocalContext.current.applicationContext
    val alcance = rememberCoroutineScope()
    val gestionar = ControlAcceso.puede(contexto, "usuarios.gestionar")
    var busqueda by rememberSaveable { mutableStateOf("") }
    var filtro by rememberSaveable { mutableStateOf("Todos") }
    var id by rememberSaveable { mutableStateOf<String?>(null) }
    var nuevo by rememberSaveable { mutableStateOf(false) }
    var configurando by rememberSaveable { mutableStateOf(false) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var zipPendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var restauracion by rememberSaveable { mutableStateOf<String?>(null) }
    val original = remember(id) { PerfilesCompartidos.perfiles.firstOrNull { it.usuario == id } }

    fun ejecutar(tarea: suspend () -> Unit) {
        if (ocupado) return
        ocupado = true; error = null; mensaje = null
        alcance.launch {
            try { withContext(NonCancellable) { tarea() }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "No se pudo completar la operación."
            } finally { ocupado = false }
        }
    }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val archivo = zipPendiente?.let { File(contexto.cacheDir, it) }; zipPendiente = null
        if (uri != null && archivo != null) ejecutar {
            try {
                ControlAcceso.exigir(contexto, "respaldo.crear")
                withContext(Dispatchers.IO) {
                    val salida = contexto.contentResolver.openOutputStream(uri, "wt") ?: error("No se pudo abrir el destino.")
                    salida.use { out -> archivo.inputStream().use { it.copyTo(out) } }
                }
                mensaje = "Respaldo completo exportado."
            } finally { archivo.delete() }
        } else archivo?.delete()
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ejecutar {
            ControlAcceso.exigir(contexto, "respaldo.restaurar")
            val archivo = File(contexto.cacheDir, "importacion-${UUID.randomUUID()}.zip")
            try {
                withContext(Dispatchers.IO) {
                    contexto.contentResolver.openInputStream(uri)?.use { entrada -> archivo.outputStream().use { salida ->
                        val buffer = ByteArray(8192); var total = 0L
                        while (true) { val n = entrada.read(buffer); if (n == -1) break
                            total += n; require(total <= 200L * 1024 * 1024) { "El respaldo supera 200 MB." }; salida.write(buffer, 0, n)
                        }
                    } } ?: error("No se pudo abrir el respaldo.")
                }
                restauracion = archivo.name
            } catch (e: Exception) { archivo.delete(); throw e }
        }
    }
    BackHandler(enabled = nuevo || id != null || configurando) {
        if (!ocupado) { nuevo = false; id = null; configurando = false; error = null }
    }
    if ((nuevo || id != null) && gestionar) {
        EditorUsuario(original, ocupado, error, onVolver = { nuevo = false; id = null; error = null }) { propuesta -> ejecutar {
            DatosUsuarios.guardarUsuario(contexto, original, propuesta)
            nuevo = false; id = null; mensaje = "Usuario guardado."
        } }
        return
    }
    Scaffold(containerColor = FondoTi,
        bottomBar = { NavegacionTi("usuarios", onAuditoria, onIncidentes, !ocupado, {}) }) { espacio ->
        Column(Modifier.fillMaxSize().padding(espacio).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (configurando && gestionar) TextButton(onClick = { configurando = false; error = null }, enabled = !ocupado) { Text("‹ Volver") }
                Text(if (configurando || !gestionar) "Configuración y respaldos" else "Usuarios", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onSalir, enabled = !ocupado) { Text("Salir") }
            }
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            mensaje?.let { Text(it, color = VerdeTi) }
            if (configurando || !gestionar) {
                if (ControlAcceso.puede(contexto, "saldos.configurar")) {
                    var administrativos by rememberSaveable { mutableStateOf(DatosUsuarios.estado.saldos.administrativos.toString()) }
                    var feriado by rememberSaveable { mutableStateOf(DatosUsuarios.estado.saldos.feriado.toString()) }
                    Text("Saldos anuales", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("Días calendario. Los cambios se aplican al cálculo compartido de funcionario y jefatura.")
                    OutlinedTextField(administrativos, { administrativos = it }, label = { Text("Días administrativos") }, enabled = !ocupado, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(feriado, { feriado = it }, label = { Text("Días de feriado") }, enabled = !ocupado, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { ejecutar {
                        val a = administrativos.toIntOrNull() ?: error("Ingresa días administrativos enteros.")
                        val f = feriado.toIntOrNull() ?: error("Ingresa días de feriado enteros.")
                        DatosUsuarios.guardarSaldos(contexto, PoliticaSaldos(a, f)); mensaje = "Saldos actualizados."
                    } }, enabled = !ocupado) { Text("Guardar saldos") }
                }
                Text("Respaldo completo", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Incluye usuarios, permisos, saldos, documentos y sus versiones, adjuntos, solicitudes, auditoría e incidentes.")
                if (ControlAcceso.puede(contexto, "respaldo.crear")) Button(onClick = { ejecutar {
                    val archivo = RespaldoLocal.crear(contexto)
                    zipPendiente = archivo.name
                    try { exportar.launch("respaldo-aps.zip") }
                    catch (e: Exception) { archivo.delete(); zipPendiente = null; throw e }
                } }, enabled = !ocupado && zipPendiente == null, modifier = Modifier.fillMaxWidth()) { Text("Crear y exportar respaldo") }
                if (ControlAcceso.puede(contexto, "respaldo.restaurar")) OutlinedButton(onClick = {
                    try { importar.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }
                    catch (_: Exception) { error = "No se pudo abrir el selector de respaldo." }
                }, enabled = !ocupado, modifier = Modifier.fillMaxWidth()) { Text("Seleccionar respaldo para restaurar") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { nuevo = true; id = null; error = null }) { Text("+ Nuevo usuario") }
                    OutlinedButton(onClick = { configurando = true; error = null }) { Text("Configuración") }
                }
                OutlinedTextField(busqueda, { busqueda = it }, label = { Text("Buscar usuario") }, singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("Todos") + RolUsuario.entries.map { AccesoCuentas.rolTexto(it) }).forEach { rol ->
                        FilterChip(selected = filtro == rol, onClick = { filtro = rol }, label = { Text(rol) }, shape = RoundedCornerShape(24.dp))
                    }
                }
                val visibles = PerfilesCompartidos.perfiles.filter { cuenta ->
                    (filtro == "Todos" || AccesoCuentas.rolTexto(cuenta.rol) == filtro) &&
                        listOf(cuenta.nombre, cuenta.usuario, cuenta.correo, cuenta.centro).any { it.contains(busqueda.trim(), true) }
                }
                if (visibles.isEmpty()) Text("No hay usuarios con estos filtros.")
                visibles.forEach { cuenta ->
                    val activa = cuenta.activa && cuenta.usuario !in DatosSeguridad.estado.bloqueadas
                    Card(Modifier.fillMaxWidth().clickable { id = cuenta.usuario; error = null }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(44.dp).background(Color(0xFFDDEFEA), CircleShape), contentAlignment = Alignment.Center) {
                                Text(cuenta.nombre.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }, color = VerdeTi, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(cuenta.nombre, fontWeight = FontWeight.Bold, color = if (activa) Color(0xFF17241F) else Color.Gray)
                                Text("${cuenta.usuario} · ${cuenta.centro}", fontSize = 13.sp)
                                Text(if (cuenta.usuario in DatosSeguridad.estado.bloqueadas) "Bloqueada por incidente" else if (cuenta.activa) "Activa" else "Inactiva", fontSize = 12.sp, color = if (activa) VerdeTi else Color.Gray)
                            }
                            Text(AccesoCuentas.rolTexto(cuenta.rol), fontSize = 12.sp, color = VerdeTi)
                        }
                    }
                }
                Text("${visibles.size} de ${PerfilesCompartidos.perfiles.size} usuarios · datos ficticios", color = Color(0xFF566861))
            }
        }
    }
    restauracion?.let { nombre ->
        AlertDialog(onDismissRequest = { if (!ocupado) { File(contexto.cacheDir, nombre).delete(); restauracion = null } },
            title = { Text("Restaurar respaldo") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se reemplazarán los datos actuales y sus adjuntos por el contenido del respaldo. Después tendrás que iniciar sesión nuevamente.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } },
            confirmButton = { TextButton(onClick = { ejecutar {
                val archivo = File(contexto.cacheDir, nombre)
                try { RespaldoLocal.restaurar(contexto, archivo); onRestaurado() }
                finally { restauracion = null; archivo.delete() }
            } }, enabled = !ocupado) { Text("Restaurar y cerrar sesión") } },
            dismissButton = { TextButton(onClick = { File(contexto.cacheDir, nombre).delete(); restauracion = null }, enabled = !ocupado) { Text("Cancelar") } })
    }
}

@Composable
private fun EditorUsuario(original: PerfilUsuario?, ocupado: Boolean, error: String?, onVolver: () -> Unit, onGuardar: (PerfilUsuario) -> Unit) {
    val contexto = LocalContext.current
    var usuario by rememberSaveable { mutableStateOf(original?.usuario ?: "") }
    var nombre by rememberSaveable { mutableStateOf(original?.nombre ?: "") }
    var correo by rememberSaveable { mutableStateOf(original?.correo ?: "") }
    var rol by rememberSaveable { mutableStateOf(original?.rol?.name ?: RolUsuario.FUNCIONARIO.name) }
    var centro by rememberSaveable { mutableStateOf(original?.centro ?: "CESFAM Norte") }
    var permisos by rememberSaveable { mutableStateOf(original?.permisos?.toList() ?: PermisosUsuario.predeterminados(RolUsuario.FUNCIONARIO).toList()) }
    var activa by rememberSaveable { mutableStateOf(original?.activa ?: true) }
    val propia = original?.usuario == ControlAcceso.perfil(contexto)?.usuario
    val bloqueada = original?.usuario in DatosSeguridad.estado.bloqueadas
    Scaffold(containerColor = FondoTi,
        bottomBar = { Surface(color = Color.White) {
            Button(onClick = { onGuardar(PerfilUsuario(usuario.trim(), nombre, RolUsuario.valueOf(rol), centro,
                original?.run, permisos.toSet(), correo, activa)) }, enabled = !ocupado,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 52.dp)) { Text("Guardar cambios") }
        } }) { espacio ->
        Column(Modifier.fillMaxSize().padding(espacio).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onVolver, enabled = !ocupado) { Text("‹ Volver a usuarios") }
            Text(if (original == null) "Nuevo usuario" else "Editar usuario", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario de acceso") }, enabled = !ocupado && original == null, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre completo") }, enabled = !ocupado, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(correo, { correo = it }, label = { Text("Correo electrónico") }, enabled = !ocupado, modifier = Modifier.fillMaxWidth())
            SelectorTi("Rol", AccesoCuentas.rolTexto(RolUsuario.valueOf(rol)), RolUsuario.entries.map { AccesoCuentas.rolTexto(it) }, Modifier.fillMaxWidth(), !ocupado && !propia) { elegido ->
                rol = RolUsuario.entries.first { AccesoCuentas.rolTexto(it) == elegido }.name
                permisos = PermisosUsuario.predeterminados(RolUsuario.valueOf(rol)).toList()
            }
            SelectorTi("Centro", centro, PermisosUsuario.centros, Modifier.fillMaxWidth(), !ocupado) { centro = it }
            Text("Permisos del rol", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(12.dp)) {
                    PermisosUsuario.disponibles(RolUsuario.valueOf(rol)).forEach { permiso ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(PermisosUsuario.nombres.getValue(permiso), Modifier.weight(1f))
                            Switch(checked = permiso in permisos, enabled = !ocupado && !propia, onCheckedChange = { marcar ->
                                permisos = if (marcar) permisos + permiso else permisos - permiso
                            }, modifier = Modifier.semantics { contentDescription = PermisosUsuario.nombres.getValue(permiso) })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Cuenta activa", fontWeight = FontWeight.Bold); Text("Desactivar impide el acceso.", fontSize = 13.sp) }
                Switch(modifier = Modifier.semantics { contentDescription = "Cuenta activa" }, checked = activa && !bloqueada, onCheckedChange = { activa = it }, enabled = !ocupado && !propia && !bloqueada)
            }
            if (bloqueada) Text("Bloqueada por incidente. Reactívala desde Incidentes.")
            if (propia) Text("Tu rol, tus permisos y la activación de tu cuenta se conservan para mantener el acceso de administración.")
            if (original == null) Text("Acceso de demostración con la contraseña del MVP: Contraseña123#", fontSize = 13.sp)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (ocupado) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
