package cl.fourpeople.aps

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AuditoriaTest {
    private fun evento(id: String = "1", fecha: String = "2026-11-02T10:00:00",
                       centro: String = "CESFAM Norte", categoria: String = "Anexo",
                       accion: String = "Cargó", nombre: String = "Anexo de contrato") =
        EventoAuditoria(id = id, fecha = fecha, usuario = "c.rojas", rol = "Funcionario",
            entidadId = "DOC-1", entidad = nombre, accion = accion, centro = centro,
            categoria = categoria, titular = "Camila Rojas", run = "DEMO-CAMILA")
    private val hoy = LocalDate.of(2026, 11, 2)

    @Test fun filtrosCombinadosRespetanCentroTipoAccionYBusqueda() {
        val lista = listOf(evento(), evento("2", centro = "CESFAM Sur"), evento("3", accion = "Vio"),
            evento("4", categoria = "Contrato"))
        val filtro = FiltroAuditoria("demo-camila", "CESFAM Norte", "Anexo", "Cargó")
        assertEquals(listOf("1"), filtro.aplicar(lista, hoy).map { it.id })
        assertTrue(filtro.copy(busqueda = "sin coincidencias").aplicar(lista, hoy).isEmpty())
    }
    @Test fun sieteDiasIncluyeLimiteYExcluyeFechasFuturas() {
        val lista = listOf(evento("1", "2026-10-27T00:00:00"), evento("2", "2026-10-26T23:59:59"),
            evento("3", "2026-11-03T00:00:00"), evento("4"))
        assertEquals(listOf("4", "1"), FiltroAuditoria().aplicar(lista, hoy).map { it.id })
    }
    @Test fun hoyFiltraSoloLaFechaActual() {
        assertEquals(listOf("1"), FiltroAuditoria(periodo = "Hoy").aplicar(
            listOf(evento(), evento("2", "2026-11-01T23:59:59")), hoy).map { it.id })
    }
    @Test fun exportaSoloLaSeleccionFiltrada() {
        val seleccion = FiltroAuditoria(centro = "CESFAM Norte").aplicar(
            listOf(evento(nombre = "Visible"), evento("2", centro = "Sur", nombre = "Oculto")), hoy)
        val csv = CsvAuditoria.generar(seleccion)
        assertTrue(csv.contains("Visible"))
        assertFalse(csv.contains("Oculto"))
        assertEquals(3, csv.split("\r\n").size)
    }
    @Test fun csvEscapaComillasComasSaltosYFormulas() {
        val csv = CsvAuditoria.generar(listOf(evento(nombre = "=SUM(1,2)\n\"dato\"")))
        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("\"'=SUM(1,2)\n\"\"dato\"\"\""))
    }
    @Test fun csvVacioMantieneCabeceras() {
        val csv = CsvAuditoria.generar(emptyList())
        assertTrue(csv.contains("Entidad ID"))
        assertEquals(2, csv.split("\r\n").size)
    }
}
