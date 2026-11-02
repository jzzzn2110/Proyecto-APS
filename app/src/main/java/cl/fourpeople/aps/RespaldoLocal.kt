package cl.fourpeople.aps

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object RespaldoLocal {
    private val nombres = listOf("expediente_funcionario.json", "solicitudes_funcionario.json", "seguridad_ti.json", DatosUsuarios.ARCHIVO)
    private const val MAX_BYTES = 200L * 1024 * 1024
    private const val PENDIENTE = "restauracion_pendiente"

    private fun eventoRespaldo(actor: PerfilUsuario, accion: String) = EventoAuditoria(
        usuario = actor.usuario, rol = "TI / Admin", entidadId = "RESPALDO-APS", entidad = "Respaldo completo",
        accion = accion, centro = "Nivel central", categoria = "Respaldo", titular = actor.nombre, run = "")

    private fun hash(archivo: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        archivo.inputStream().use { entrada -> val buffer = ByteArray(8192)
            while (true) { val n = entrada.read(buffer); if (n == -1) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun json(archivo: File) = JSONObject(archivo.readText(Charsets.UTF_8))
    private fun escribir(archivo: File, datos: ByteArray) {
        val atomico = AtomicFile(archivo); val salida = atomico.startWrite()
        try { salida.write(datos); atomico.finishWrite(salida)
        } catch (e: Exception) { atomico.failWrite(salida); throw e }
    }

    internal fun referencias(valor: Any?, accion: (JSONObject, String) -> Unit) {
        when (valor) {
            is JSONObject -> valor.keys().asSequence().toList().forEach { clave ->
                if (clave == "archivoUri" && valor.optString(clave).isNotBlank()) accion(valor, clave)
                else referencias(valor.opt(clave), accion)
            }
            is JSONArray -> (0 until valor.length()).forEach { referencias(valor.opt(it), accion) }
        }
    }
    private fun nombreAdjunto(uri: String): String {
        val direccion = Uri.parse(uri)
        val nombre = direccion.lastPathSegment.orEmpty()
        require(direccion.scheme == "file" && direccion.path?.substringBeforeLast('/')?.endsWith("/documentos") == true &&
            nombre.matches(Regex("[A-Za-z0-9._-]+")) && nombre != "." && nombre != "..") { "Referencia de adjunto inválida." }
        return nombre
    }

    suspend fun crear(contexto: Context): File {
        ControlAcceso.exigir(contexto, "respaldo.crear")
        DatosSolicitudes.asegurarPersistencia(contexto)
        DatosSeguridad.asegurarPersistencia(contexto)
        DatosUsuarios.asegurarPersistencia(contexto)
        return withContext(Dispatchers.IO) { AlmacenamientoAps.bloqueo.withLock {
            val actor = ControlAcceso.exigir(contexto, "respaldo.crear")
            val fuentes = linkedMapOf<String, File>()
            nombres.forEach { nombre ->
                val archivo = File(contexto.filesDir, nombre)
                require(archivo.isFile) { "Faltan datos para crear el respaldo." }
                fuentes[nombre] = archivo
                referencias(json(archivo)) { dato, clave ->
                    val uri = dato.getString(clave)
                    val adjunto = File(File(contexto.filesDir, "documentos"), nombreAdjunto(uri)).canonicalFile
                    require(adjunto.parentFile == File(contexto.filesDir, "documentos").canonicalFile &&
                        File(requireNotNull(Uri.parse(uri).path)).canonicalFile == adjunto && adjunto.isFile) { "Falta un archivo adjunto del expediente." }
                    fuentes["documentos/${adjunto.name}"] = adjunto
                }
            }
            require(fuentes.size <= 2000 && fuentes.values.sumOf { it.length() } <= MAX_BYTES - 1024 * 1024) { "El respaldo supera el límite de 200 MB o 2000 archivos." }
            val destino = File(contexto.cacheDir, "respaldo-aps-${UUID.randomUUID()}.zip")
            try {
                ZipOutputStream(destino.outputStream()).use { zip ->
                    fuentes.forEach { (nombre, archivo) ->
                        zip.putNextEntry(ZipEntry(nombre)); archivo.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    }
                    val manifiesto = JSONObject().put("formato", 1).put("archivos", JSONObject().apply {
                        fuentes.forEach { (nombre, archivo) -> put(nombre, hash(archivo)) }
                    })
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifiesto.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                val raiz = json(File(contexto.filesDir, nombres[0]))
                val eventos = AuditoriaLocal.leer(raiz.optJSONArray("auditoria")) + eventoRespaldo(actor, "Creó respaldo")
                raiz.put("auditoria", AuditoriaLocal.escribir(eventos))
                escribir(File(contexto.filesDir, nombres[0]), raiz.toString().toByteArray(Charsets.UTF_8))
                AuditoriaLocal.eventosDocumentales = eventos
                destino
            } catch (e: Exception) { destino.delete(); throw e }
        } }
    }

    private fun preparar(contexto: Context, respaldo: File, carpeta: File) {
        require(respaldo.isFile && respaldo.length() in 1..MAX_BYTES) { "El respaldo no es válido o supera 200 MB." }
        val vistos = mutableSetOf<String>(); var total = 0L
        ZipInputStream(respaldo.inputStream()).use { zip ->
            while (true) {
                val entrada = zip.nextEntry ?: break
                val nombre = entrada.name
                require(!entrada.isDirectory && vistos.add(nombre) && vistos.size <= 2001 &&
                    (nombre in nombres || nombre == "manifest.json" || nombre.matches(Regex("documentos/[A-Za-z0-9._-]+")))) { "El respaldo contiene una ruta o entrada no permitida." }
                val destino = File(carpeta, nombre).canonicalFile
                require(destino.toPath().startsWith(carpeta.canonicalFile.toPath())) { "Ruta fuera del respaldo." }
                destino.parentFile!!.mkdirs()
                destino.outputStream().use { salida ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer); if (n == -1) break
                        total += n; require(total <= MAX_BYTES) { "El respaldo descomprimido supera 200 MB." }
                        salida.write(buffer, 0, n)
                    }
                }
                zip.closeEntry()
            }
        }
        require(vistos.containsAll(nombres + "manifest.json")) { "El respaldo está incompleto." }
        val manifiesto = json(File(carpeta, "manifest.json"))
        require(manifiesto.getInt("formato") == 1)
        val hashes = manifiesto.getJSONObject("archivos")
        require(hashes.keys().asSequence().toSet() == vistos - "manifest.json") { "El manifiesto no coincide con el contenido." }
        hashes.keys().forEach { nombre -> require(hashes.getString(nombre) == hash(File(carpeta, nombre))) { "Un archivo del respaldo está dañado." } }
        val raices = nombres.associateWith { json(File(carpeta, it)) }
        PersistenciaDocumentos.validarRespaldo(raices.getValue(nombres[0]))
        val solicitudes = DatosSolicitudes.leer(raices.getValue(nombres[1]))
        val seguridad = DatosSeguridad.leer(raices.getValue(nombres[2]))
        val usuarios = DatosUsuarios.leer(raices.getValue(nombres[3]))
        val cuentas = usuarios.usuarios.map { it.usuario }.toSet()
        val idsAuditoria = (AuditoriaLocal.leer(raices.getValue(nombres[0]).optJSONArray("auditoria")) + solicitudes.auditoria + seguridad.auditoria + usuarios.auditoria).map { it.id }.toSet()
        require(seguridad.bloqueadas.all { it in cuentas } && seguridad.incidentes.all { it.usuario in cuentas && idsAuditoria.containsAll(it.eventosIds) }) { "El respaldo contiene referencias de seguridad incompletas." }
        require(CodecAdministracion.leer(raices.getValue(nombres[0]).optJSONObject("administracion")).expediente.usuario in cuentas) { "Falta la cuenta del titular del expediente." }
        require(usuarios.usuarios.any { it.activa && it.usuario !in seguridad.bloqueadas && it.rol == RolUsuario.TI && "usuarios.gestionar" in it.permisos }) { "El respaldo no conserva una cuenta activa de administración." }
        raices.forEach { (nombre, raiz) ->
            referencias(raiz) { dato, clave ->
                val archivo = nombreAdjunto(dato.getString(clave))
                require(File(carpeta, "documentos/$archivo").isFile) { "Falta un adjunto en el respaldo." }
                dato.put(clave, Uri.fromFile(File(contexto.filesDir, "documentos/$archivo")).toString())
            }
            File(carpeta, nombre).writeText(raiz.toString(), Charsets.UTF_8)
        }
        File(carpeta, "documentos").mkdirs()
    }

    suspend fun restaurar(contexto: Context, respaldo: File) = withContext(Dispatchers.IO) {
        AlmacenamientoAps.bloqueo.withLock {
            val actor = ControlAcceso.exigir(contexto, "respaldo.restaurar")
            recuperarBajoBloqueo(contexto)
            val temporal = File(contexto.cacheDir, "restauracion-${UUID.randomUUID()}").apply { mkdirs() }
            val pendiente = File(contexto.filesDir, PENDIENTE)
            try {
                preparar(contexto, respaldo, temporal)
                nombres.forEach { nombre ->
                    val anterior = File(contexto.filesDir, nombre)
                    val raizNueva = json(File(temporal, nombre))
                    val actuales = if (anterior.isFile) AuditoriaLocal.leer(json(anterior).optJSONArray("auditoria")) else emptyList()
                    val recuperados = AuditoriaLocal.leer(raizNueva.optJSONArray("auditoria"))
                    raizNueva.put("auditoria", AuditoriaLocal.escribir((actuales + recuperados).distinctBy { it.id }.sortedBy { it.fecha }))
                    File(temporal, nombre).writeText(raizNueva.toString(), Charsets.UTF_8)
                }
                val raiz = json(File(temporal, nombres[0]))
                raiz.put("auditoria", AuditoriaLocal.escribir(AuditoriaLocal.leer(raiz.optJSONArray("auditoria")) + eventoRespaldo(actor, "Restauró respaldo")))
                File(temporal, nombres[0]).writeText(raiz.toString(), Charsets.UTF_8)
                pendiente.mkdirs()
                val existentes = nombres.filter { File(contexto.filesDir, it).exists() }
                existentes.forEach { File(contexto.filesDir, it).copyTo(File(pendiente, it)) }
                val documentos = File(contexto.filesDir, "documentos")
                val marca = JSONObject().put("aplicada", false).put("archivos", JSONArray(existentes)).put("documentos", documentos.exists())
                escribir(File(pendiente, "estado.json"), marca.toString().toByteArray())
                if (documentos.exists()) check(documentos.renameTo(File(pendiente, "documentos"))) { "No se pudieron preparar los adjuntos actuales." }
                check(File(temporal, "documentos").renameTo(documentos)) { "No se pudieron restaurar los adjuntos." }
                nombres.forEach { escribir(File(contexto.filesDir, it), File(temporal, it).readBytes()) }
                marca.put("aplicada", true)
                escribir(File(pendiente, "estado.json"), marca.toString().toByteArray())
                SesionLocal.limpiar(contexto)
                pendiente.deleteRecursively()
            } catch (e: Exception) {
                try { recuperarBajoBloqueo(contexto) } catch (recuperacion: Exception) { e.addSuppressed(recuperacion) }
                throw IOException("No se pudo restaurar el respaldo. Se intentó conservar el estado anterior.", e)
            } finally { temporal.deleteRecursively() }
        }
    }

    suspend fun recuperarPendiente(contexto: Context) = withContext(Dispatchers.IO) {
        AlmacenamientoAps.bloqueo.withLock { recuperarBajoBloqueo(contexto) }
    }
    private fun recuperarBajoBloqueo(contexto: Context) {
        val pendiente = File(contexto.filesDir, PENDIENTE)
        if (!pendiente.exists()) return
        val atomico = AtomicFile(File(pendiente, "estado.json"))
        val marca = try { atomico.openRead().bufferedReader().use { JSONObject(it.readText()) } }
            catch (_: java.io.FileNotFoundException) { check(pendiente.deleteRecursively()); return }
        if (!marca.getBoolean("aplicada")) {
            val anteriores = marca.getJSONArray("archivos").let { datos -> List(datos.length()) { datos.getString(it) }.toSet() }
            require(nombres.containsAll(anteriores))
            nombres.forEach { nombre ->
                if (nombre in anteriores) escribir(File(contexto.filesDir, nombre), File(pendiente, nombre).readBytes())
                else AtomicFile(File(contexto.filesDir, nombre)).delete()
            }
            val documentos = File(contexto.filesDir, "documentos")
            val anterioresDocs = File(pendiente, "documentos")
            if (anterioresDocs.exists()) {
                check(!documentos.exists() || documentos.deleteRecursively())
                check(anterioresDocs.renameTo(documentos))
            } else if (!marca.getBoolean("documentos")) check(!documentos.exists() || documentos.deleteRecursively())
        }
        marca.put("aplicada", true)
        escribir(File(pendiente, "estado.json"), marca.toString().toByteArray())
        check(pendiente.deleteRecursively()) { "No se pudo completar la limpieza de la restauración." }
    }
}
