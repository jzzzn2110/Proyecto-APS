package cl.fourpeople.aps

import org.junit.Assert.*
import org.junit.Test

class IncidentesTest {
    private val evento = EventoAuditoria(id = "EV-1", usuario = "c.rojas", rol = "Funcionario",
        entidadId = "DOC-1", entidad = "Contrato", accion = "Vio", centro = "CESFAM Norte",
        categoria = "Contrato", titular = "Camila Rojas", run = "DEMO-CAMILA")

    @Test fun cuentaBloqueadaNoIngresaAunqueLaClaveSeaCorrecta() {
        assertNotNull(AccesoCuentas.errorIngreso("c.rojas", "Contraseña123#", "Funcionario", setOf("c.rojas")))
        assertNull(AccesoCuentas.errorIngreso("c.rojas", "Contraseña123#", "Funcionario", emptySet()))
    }
    @Test fun bloqueoAfectaATodosLosPerfiles() {
        PerfilesCompartidos.perfiles.forEach { cuenta ->
            assertNotNull(AccesoCuentas.errorIngreso(cuenta.usuario, "Contraseña123#", AccesoCuentas.rolTexto(cuenta.rol), setOf(cuenta.usuario)))
        }
    }
    @Test fun cambiarRolOClaveNoPermiteEludirLaValidacion() {
        assertNotNull(AccesoCuentas.errorIngreso("c.rojas", "Contraseña123#", "TI / Admin", emptySet()))
        assertNotNull(AccesoCuentas.errorIngreso("d.herrera", "incorrecta", "TI / Admin", emptySet()))
        assertNull(AccesoCuentas.errorIngreso("d.herrera", "Contraseña123#", "TI / Admin", emptySet()))
    }
    @Test fun incidentePuedeCrearseConYSinEventos() {
        ReglasIncidentes.validar("Consulta inusual", "Consulta para investigación", "c.rojas", setOf("EV-1"), listOf(evento))
        ReglasIncidentes.validar("Reporte manual", "Reporte sin eventos asociados", "c.rojas", emptySet(), emptyList())
    }
    @Test fun noPermiteVincularEventoDeOtraCuentaOInexistente() {
        assertThrows(IllegalArgumentException::class.java) {
            ReglasIncidentes.validar("Consulta inusual", "Consulta para investigación", "administrativo", setOf("EV-1"), listOf(evento))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReglasIncidentes.validar("Consulta inusual", "Consulta para investigación", "c.rojas", setOf("desconocido"), listOf(evento))
        }
    }
    @Test fun validaDatosObligatoriosYCuentaExistente() {
        listOf(Triple("", "Descripción válida de prueba", "c.rojas"), Triple("Título", "corta", "c.rojas"),
            Triple("Título", "Descripción válida de prueba", "desconocido")).forEach { (titulo, descripcion, usuario) ->
            assertThrows(IllegalArgumentException::class.java) {
                ReglasIncidentes.validar(titulo, descripcion, usuario, emptySet(), emptyList())
            }
        }
    }
    @Test fun cierreRequiereRevisionYObservacion() {
        ReglasIncidentes.validarTransicion(EstadoIncidente.ABIERTO, EstadoIncidente.EN_REVISION, "Revisión de prueba iniciada")
        ReglasIncidentes.validarTransicion(EstadoIncidente.EN_REVISION, EstadoIncidente.CERRADO, "Investigación finalizada")
        assertThrows(IllegalArgumentException::class.java) {
            ReglasIncidentes.validarTransicion(EstadoIncidente.ABIERTO, EstadoIncidente.CERRADO, "Cierre directo inválido")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReglasIncidentes.validarTransicion(EstadoIncidente.EN_REVISION, EstadoIncidente.CERRADO, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReglasIncidentes.validarTransicion(EstadoIncidente.CERRADO, EstadoIncidente.EN_REVISION, "Reapertura no permitida")
        }
    }
}
