package cl.fourpeople.aps

/** Contratos comunes para que cada pantalla consulte el mismo usuario y centro. */
enum class RolUsuario { FUNCIONARIO, ADMINISTRATIVO, JEFATURA, TI }

data class PerfilUsuario(
    val usuario: String,
    val nombre: String,
    val rol: RolUsuario,
    val centro: String,
    val run: String? = null,
    val permisos: Set<String> = emptySet(),
    val correo: String = "$usuario@demo.invalid",
    val activa: Boolean = true
) {
    fun puede(permiso: String) = permiso in permisos
}

object PerfilesCompartidos {
    val iniciales = listOf(
        PerfilUsuario("c.rojas", "Camila Rojas Soto", RolUsuario.FUNCIONARIO, "CESFAM Norte", "DEMO-CAMILA", setOf("documentos.propios", "solicitudes.propias")),
        PerfilUsuario("administrativo", "Administrativo APS", RolUsuario.ADMINISTRATIVO, "CESFAM Norte", permisos = PermisosUsuario.predeterminados(RolUsuario.ADMINISTRATIVO)),
        PerfilUsuario("d.herrera", "Diego Herrera", RolUsuario.TI, "Nivel central", permisos = PermisosUsuario.predeterminados(RolUsuario.TI)),
        PerfilUsuario("jefatura", "Jefatura CESFAM Norte", RolUsuario.JEFATURA, "CESFAM Norte", permisos = setOf("solicitudes.resolver", "expedientes.leer"))
    )
    val perfiles: List<PerfilUsuario> get() = DatosUsuarios.estado.usuarios
    fun buscar(usuario: String, rol: String) = perfiles.firstOrNull {
        it.usuario == usuario && it.rol.name.replace('_', ' ').equals(rol.replace(" / Admin", "").uppercase(), true)
    }
}
