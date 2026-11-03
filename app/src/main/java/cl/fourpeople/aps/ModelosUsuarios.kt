package cl.fourpeople.aps

data class PoliticaSaldos(val administrativos: Int = 6, val feriado: Int = 15) {
    fun validar() { require(administrativos in 0..30 && feriado in 0..365) { "Saldos: administrativos entre 0 y 30; feriado entre 0 y 365." } }
}

data class EstadoUsuarios(
    val usuarios: List<PerfilUsuario> = PerfilesCompartidos.iniciales,
    val saldos: PoliticaSaldos = PoliticaSaldos(),
    val auditoria: List<EventoAuditoria> = emptyList()
)

object PermisosUsuario {
    val nombres = linkedMapOf(
        "documentos.propios" to "Consultar y cargar documentos propios",
        "solicitudes.propias" to "Consultar y enviar solicitudes propias",
        "expedientes.leer" to "Ver expedientes de su centro",
        "documentos.revisar" to "Validar y rechazar documentos",
        "expedientes.editar" to "Editar antecedentes laborales y metadatos",
        "documentos.baja" to "Dar de baja documentos",
        "documentos.descargar" to "Descargar documentos",
        "solicitudes.resolver" to "Resolver solicitudes del centro",
        "auditoria.leer" to "Consultar y exportar auditoría",
        "incidentes.gestionar" to "Gestionar incidentes y bloqueos",
        "usuarios.gestionar" to "Administrar usuarios y permisos",
        "saldos.configurar" to "Configurar saldos",
        "respaldo.crear" to "Crear respaldos",
        "respaldo.restaurar" to "Restaurar respaldos"
    )
    fun disponibles(rol: RolUsuario): Set<String> = when (rol) {
        RolUsuario.FUNCIONARIO -> setOf("documentos.propios", "solicitudes.propias", "documentos.descargar")
        RolUsuario.ADMINISTRATIVO -> setOf("expedientes.leer", "documentos.revisar", "expedientes.editar", "documentos.baja", "documentos.descargar")
        RolUsuario.JEFATURA -> setOf("expedientes.leer", "solicitudes.resolver")
        RolUsuario.TI -> setOf("auditoria.leer", "incidentes.gestionar", "usuarios.gestionar", "saldos.configurar", "respaldo.crear", "respaldo.restaurar")
    }
    fun predeterminados(rol: RolUsuario) = disponibles(rol) - "documentos.descargar"
    val centros = listOf("CESFAM Norte", "CESFAM Sur", "CECOSF Centro", "Nivel central")

    fun validar(cuenta: PerfilUsuario) {
        require(cuenta.usuario.matches(Regex("[a-z0-9][a-z0-9._-]{2,39}"))) { "Usuario: de 3 a 40 letras minúsculas, números, puntos o guiones." }
        require(cuenta.nombre.trim().length in 3..120) { "Nombre: entre 3 y 120 caracteres." }
        require(cuenta.correo.length <= 160 && cuenta.correo.matches(Regex("""[^\s@]+@[^\s@]+\.[^\s@]+"""))) { "Ingresa un correo válido." }
        require(cuenta.centro in centros) { "Selecciona un centro válido." }
        require(disponibles(cuenta.rol).containsAll(cuenta.permisos)) { "El rol no admite uno de los permisos seleccionados." }
    }

    fun permite(cuenta: PerfilUsuario?, permiso: String, bloqueadas: Set<String>, centro: String? = null,
                propietario: String? = null): Boolean = cuenta != null && cuenta.activa && cuenta.usuario !in bloqueadas &&
        permiso in cuenta.permisos && (centro == null || cuenta.centro == centro) &&
        (propietario == null || cuenta.usuario == propietario)
}
