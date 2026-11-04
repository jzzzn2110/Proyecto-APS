package cl.fourpeople.aps

import android.content.Context
import android.util.AtomicFile
import androidx.compose.runtime.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object DatosUsuarios {
    var estado by mutableStateOf(EstadoUsuarios())
        private set
    private val bloqueo get() = AlmacenamientoAps.bloqueo
    internal const val ARCHIVO = "usuarios_ti.json"

    internal fun leer(raiz: JSONObject): EstadoUsuarios {
        require(raiz.getInt("formato") == 1)
        val lista = raiz.getJSONArray("usuarios")
        val usuarios = List(lista.length()) { i -> lista.getJSONObject(i).let { dato ->
            val permisos = dato.getJSONArray("permisos")
            PerfilUsuario(dato.getString("usuario"), dato.getString("nombre"), RolUsuario.valueOf(dato.getString("rol")),
                dato.getString("centro"), if (dato.isNull("run")) null else dato.getString("run"),
                List(permisos.length()) { permisos.getString(it) }.toSet(), dato.getString("correo"), dato.getBoolean("activa"))
                .also { PermisosUsuario.validar(it) }
        } }
        require(usuarios.map { it.usuario }.distinct().size == usuarios.size)
        require(usuarios.any { it.activa && it.rol == RolUsuario.TI && "usuarios.gestionar" in it.permisos }) { "Debe existir una cuenta activa de administración." }
        val saldo = raiz.getJSONObject("saldos")
        val politica = PoliticaSaldos(saldo.getInt("administrativos"), saldo.getInt("feriado")).also { it.validar() }
        return EstadoUsuarios(usuarios, politica, AuditoriaLocal.leer(raiz.optJSONArray("auditoria")))
    }

    suspend fun cargar(contexto: Context) = bloqueo.withLock {
        val nuevo = withContext(Dispatchers.IO) {
            val archivo = AtomicFile(File(contexto.filesDir, ARCHIVO))
            val entrada = try { archivo.openRead() } catch (e: FileNotFoundException) {
                if (archivo.baseFile.exists()) throw e
                return@withContext EstadoUsuarios()
            }
            try { entrada.bufferedReader(Charsets.UTF_8).use { leer(JSONObject(it.readText())) }
            } catch (e: Exception) { throw IOException("No se pudieron leer los usuarios. Los datos se conservaron.", e) }
        }
        estado = nuevo
    }

    suspend fun asegurarPersistencia(contexto: Context) = bloqueo.withLock {
        if (!File(contexto.filesDir, ARCHIVO).exists()) guardar(contexto, estado)
    }

    suspend fun guardarUsuario(contexto: Context, original: PerfilUsuario?, propuesta: PerfilUsuario) = bloqueo.withLock {
        val actor = ControlAcceso.exigir(contexto, "usuarios.gestionar")
        val nueva = propuesta.copy(nombre = propuesta.nombre.trim(), correo = propuesta.correo.trim())
        PermisosUsuario.validar(nueva)
        if (original == null) require(estado.usuarios.none { it.usuario.equals(nueva.usuario, true) }) { "El usuario ya existe." }
        else {
            require(estado.usuarios.firstOrNull { it.usuario == original.usuario } == original && nueva.usuario == original.usuario) { "La cuenta cambió o el usuario no coincide. Vuelve a abrirla." }
            if (original.usuario == actor.usuario) require(nueva.activa && nueva.rol == original.rol && nueva.permisos == original.permisos) { "No puedes quitarte el acceso de administración." }
        }
        val lista = if (original == null) estado.usuarios + nueva else estado.usuarios.map { if (it.usuario == nueva.usuario) nueva else it }
        require(lista.any { it.activa && it.usuario !in DatosSeguridad.estado.bloqueadas && it.rol == RolUsuario.TI && "usuarios.gestionar" in it.permisos }) { "Debe quedar una cuenta activa de administración." }
        val evento = EventoAuditoria(usuario = actor.usuario, rol = AccesoCuentas.rolTexto(actor.rol), entidadId = nueva.usuario,
            entidad = nueva.nombre, accion = if (original == null) "Creó usuario" else "Editó usuario", centro = nueva.centro,
            categoria = "Usuario", titular = nueva.nombre, run = nueva.run.orEmpty(),
            detalle = "${AccesoCuentas.rolTexto(nueva.rol)} · ${if (nueva.activa) "Activa" else "Inactiva"} · Permisos: ${nueva.permisos.sorted().joinToString()}")
        guardar(contexto, estado.copy(usuarios = lista, auditoria = estado.auditoria + evento))
    }

    suspend fun guardarSaldos(contexto: Context, politica: PoliticaSaldos) = bloqueo.withLock {
        val actor = ControlAcceso.exigir(contexto, "saldos.configurar")
        politica.validar()
        val evento = EventoAuditoria(usuario = actor.usuario, rol = "TI / Admin", entidadId = "POLITICA-SALDOS", entidad = "Saldos de permisos",
            accion = "Configuró saldos", centro = "Nivel central", categoria = "Configuración", titular = actor.nombre, run = "",
            detalle = "Administrativos: ${politica.administrativos}; feriado: ${politica.feriado}")
        guardar(contexto, estado.copy(saldos = politica, auditoria = estado.auditoria + evento))
    }

    private suspend fun guardar(contexto: Context, siguiente: EstadoUsuarios) {
        withContext(Dispatchers.IO) {
            val raiz = JSONObject().apply {
                put("formato", 1)
                put("saldos", JSONObject().put("administrativos", siguiente.saldos.administrativos).put("feriado", siguiente.saldos.feriado))
                put("auditoria", AuditoriaLocal.escribir(siguiente.auditoria))
                put("usuarios", JSONArray().apply { siguiente.usuarios.forEach { u -> put(JSONObject().apply {
                    put("usuario", u.usuario); put("nombre", u.nombre); put("rol", u.rol.name); put("centro", u.centro)
                    put("run", u.run ?: JSONObject.NULL); put("correo", u.correo); put("activa", u.activa); put("permisos", JSONArray(u.permisos.toList()))
                }) } })
            }
            val archivo = AtomicFile(File(contexto.filesDir, ARCHIVO))
            val salida = archivo.startWrite()
            try { salida.write(raiz.toString().toByteArray(Charsets.UTF_8)); archivo.finishWrite(salida)
            } catch (e: Exception) { archivo.failWrite(salida); throw IOException("No se pudieron guardar los usuarios o saldos.", e) }
        }
        estado = siguiente
    }
}
