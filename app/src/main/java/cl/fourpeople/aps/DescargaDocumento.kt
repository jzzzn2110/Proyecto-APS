package cl.fourpeople.aps

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object DescargasDocumento {
    fun permitida(contexto: Context): Boolean {
        val propio = ControlAcceso.perfil(contexto)?.rol == RolUsuario.FUNCIONARIO
        return ControlAcceso.puede(contexto, "documentos.descargar", expediente = !propio, propio = propio) &&
            (!propio || ControlAcceso.puede(contexto, "documentos.propios", propio = true))
    }
    suspend fun exportar(contexto: Context, documento: DocumentoFuncionario, destino: Uri) {
        val evento = withContext(Dispatchers.IO) { AlmacenamientoAps.bloqueo.withLock {
            require(permitida(contexto)) { "Tu cuenta no tiene permiso para descargar este documento." }
            val actual = DatosFuncionario.documentos.firstOrNull { it.id == documento.id && DatosAdministracion.activo(it.id) }
                ?: error("El documento ya no está disponible.")
            require(documento in listOf(actual) + actual.historial) { "El documento cambió. Vuelve a abrirlo." }
            val direccion = Uri.parse(documento.archivoUri)
            require(direccion.scheme == "file") { "El documento no tiene archivo adjunto." }
            val archivo = File(requireNotNull(direccion.path)).canonicalFile
            require(archivo.parentFile == File(contexto.filesDir, "documentos").canonicalFile && archivo.isFile) { "No se encontró el archivo adjunto." }
            val salida = contexto.contentResolver.openOutputStream(destino, "wt") ?: error("No se pudo abrir el destino.")
            salida.use { out -> archivo.inputStream().use { it.copyTo(out) } }
            AuditoriaLocal.evento(contexto, documento.id, documento.nombre, "Descargó", documento.categoria, "Versión ${documento.version}")
        } }
        try { AuditoriaLocal.registrar(contexto, evento) }
        catch (e: Exception) { throw java.io.IOException("El archivo se exportó, pero no se pudo registrar la descarga en auditoría.", e) }
    }
}

@Composable
internal fun DescargarDocumento(documento: DocumentoFuncionario) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    if (!DescargasDocumento.permitida(contexto) || documento.archivoUri.isBlank()) return
    var mensaje by remember { mutableStateOf<String?>(null) }
    var ocupado by remember { mutableStateOf(false) }
    var pendiente by rememberSaveable { mutableStateOf<String?>(null) }
    val tipo = if (documento.archivoUri.endsWith(".pdf", true)) "application/pdf" else "application/octet-stream"
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(tipo)) { uri ->
        val clave = pendiente; pendiente = null
        if (uri != null && clave == "${documento.id}:${documento.version}") {
            ocupado = true
            alcance.launch {
                try { DescargasDocumento.exportar(contexto, documento, uri); mensaje = "Documento descargado."
                } catch (e: CancellationException) { throw e
                } catch (e: Exception) { mensaje = e.message ?: "No se pudo descargar."
                } finally { ocupado = false }
            }
        }
    }
    OutlinedButton(onClick = {
        pendiente = "${documento.id}:${documento.version}"
        try { selector.launch(documento.nombre + "." + Uri.parse(documento.archivoUri).lastPathSegment.orEmpty().substringAfterLast('.', "bin")) }
        catch (_: Exception) { pendiente = null; mensaje = "No se pudo abrir el selector de destino." }
    }, enabled = !ocupado && pendiente == null) { Text("Descargar documento") }
    mensaje?.let { Text(it) }
}
