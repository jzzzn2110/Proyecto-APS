package cl.fourpeople.aps

import java.time.LocalDateTime
import java.util.UUID

enum class EstadoIncidente(val texto: String) { ABIERTO("Abierto"), EN_REVISION("En revisión"), CERRADO("Cerrado") }
enum class PrioridadIncidente(val texto: String) { ALTA("Alta"), MEDIA("Media"), BAJA("Baja") }

data class SeguimientoIncidente(
    val fecha: String,
    val responsable: String,
    val accion: String,
    val observacion: String
)

data class IncidenteSeguridad(
    val id: String = UUID.randomUUID().toString(),
    val titulo: String,
    val descripcion: String,
    val usuario: String,
    val prioridad: PrioridadIncidente,
    val eventosIds: Set<String> = emptySet(),
    val estado: EstadoIncidente = EstadoIncidente.ABIERTO,
    val fecha: String = LocalDateTime.now().toString(),
    val seguimiento: List<SeguimientoIncidente> = emptyList()
)

data class EstadoSeguridad(
    val incidentes: List<IncidenteSeguridad> = emptyList(),
    val bloqueadas: Set<String> = emptySet(),
    val auditoria: List<EventoAuditoria> = emptyList()
)

object AccesoCuentas {
    fun rolTexto(rol: RolUsuario) = when (rol) {
        RolUsuario.FUNCIONARIO -> "Funcionario"
        RolUsuario.ADMINISTRATIVO -> "Administrativo"
        RolUsuario.JEFATURA -> "Jefatura"
        RolUsuario.TI -> "TI / Admin"
    }

    fun errorIngreso(usuario: String, clave: String, rol: String, bloqueadas: Set<String>): String? {
        if (usuario.isBlank() || clave.isBlank()) return "Completa el usuario y la contraseña."
        val cuenta = PerfilesCompartidos.perfiles.firstOrNull {
            it.usuario == usuario.trim() && rolTexto(it.rol) == rol
        }
        if (cuenta == null || clave != "Contraseña123#") return "Usuario o contraseña incorrectos."
        return if (!cuenta.activa || cuenta.usuario in bloqueadas) "Cuenta bloqueada. Contacta al administrador de TI." else null
    }
}

object ReglasIncidentes {
    fun validar(titulo: String, descripcion: String, usuario: String, ids: Set<String>, eventos: List<EventoAuditoria>) {
        require(titulo.trim().length in 3..120) { "El título debe tener entre 3 y 120 caracteres." }
        require(descripcion.trim().length in 10..1500) { "La descripción debe tener entre 10 y 1500 caracteres." }
        require(PerfilesCompartidos.perfiles.any { it.usuario == usuario }) { "Selecciona una cuenta válida." }
        val porId = eventos.associateBy { it.id }
        require(ids.all { porId[it]?.usuario == usuario }) { "Los eventos deben pertenecer a la cuenta seleccionada." }
    }

    fun validarTransicion(actual: EstadoIncidente, siguiente: EstadoIncidente, observacion: String) {
        require((actual == EstadoIncidente.ABIERTO && siguiente == EstadoIncidente.EN_REVISION) ||
            (actual == EstadoIncidente.EN_REVISION && siguiente == EstadoIncidente.CERRADO)) {
            "El incidente debe pasar de Abierto a En revisión y después a Cerrado."
        }
        require(observacion.trim().length in 10..1000) { "La observación debe tener entre 10 y 1000 caracteres." }
    }
}
