package cl.fourpeople.aps

import org.junit.Assert.*
import org.junit.Test

class UsuariosTest {
    private fun cuenta() = PerfilUsuario("demo.admin", "Administrativo de prueba", RolUsuario.ADMINISTRATIVO,
        "CESFAM Norte", permisos = PermisosUsuario.predeterminados(RolUsuario.ADMINISTRATIVO))

    @Test fun cadaPermisoSeEvaluaSinDependerSoloDelRol() {
        val original = cuenta()
        assertTrue(PermisosUsuario.permite(original, "documentos.revisar", emptySet(), "CESFAM Norte"))
        assertFalse(PermisosUsuario.permite(original.copy(permisos = original.permisos - "documentos.revisar"), "documentos.revisar", emptySet(), "CESFAM Norte"))
        assertFalse(PermisosUsuario.permite(original, "documentos.descargar", emptySet(), "CESFAM Norte"))
        assertTrue(PermisosUsuario.permite(original.copy(permisos = original.permisos + "documentos.descargar"), "documentos.descargar", emptySet(), "CESFAM Norte"))
    }
    @Test fun centroPropietarioBloqueoYActivacionRestringenElAcceso() {
        val u = cuenta()
        assertFalse(PermisosUsuario.permite(u, "expedientes.leer", emptySet(), "CESFAM Sur"))
        assertFalse(PermisosUsuario.permite(u, "expedientes.leer", emptySet(), propietario = "c.rojas"))
        assertFalse(PermisosUsuario.permite(u.copy(activa = false), "expedientes.leer", emptySet()))
        assertFalse(PermisosUsuario.permite(u, "expedientes.leer", setOf(u.usuario)))
        assertFalse(PermisosUsuario.permite(null, "expedientes.leer", emptySet()))
    }
    @Test fun rolNoPuedeRecibirPermisosDeAdministracionTi() {
        assertThrows(IllegalArgumentException::class.java) { PermisosUsuario.validar(cuenta().copy(permisos = setOf("usuarios.gestionar"))) }
        PermisosUsuario.validar(cuenta())
    }
    @Test fun validaNombreUsuarioCorreoYCentro() {
        listOf(cuenta().copy(usuario = "../fuera"), cuenta().copy(nombre = ""), cuenta().copy(correo = "sin correo"),
            cuenta().copy(correo = "a b@demo.invalid"), cuenta().copy(centro = "Desconocido")).forEach {
            assertThrows(IllegalArgumentException::class.java) { PermisosUsuario.validar(it) }
        }
    }
    @Test fun politicasDeSaldoValidanRangosSinAceptarValoresInvalidos() {
        PoliticaSaldos(0, 0).validar(); PoliticaSaldos(30, 365).validar()
        listOf(PoliticaSaldos(-1, 15), PoliticaSaldos(31, 15), PoliticaSaldos(6, 366)).forEach {
            assertThrows(IllegalArgumentException::class.java) { it.validar() }
        }
    }
    @Test fun cuentasDesactivadasNoPasanLaValidacionDePermisos() {
        assertFalse(PermisosUsuario.permite(cuenta().copy(activa = false), "documentos.revisar", emptySet()))
    }
}
