package cl.fourpeople.aps

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReglasSolicitudesTest {
    private fun solicitud(jornada: JornadaSolicitud = JornadaSolicitud.COMPLETA) = SolicitudFuncionario(
        "SOL-TEST", TipoSolicitud.ADMINISTRATIVO, "2026-11-03", "2026-11-03", jornada,
        "Solicitud ficticia de prueba", "2026-11-02T10:00:00")
    @Test fun pendienteReservaAprobadaConsumeYRechazadaLibera() {
        val pendiente = solicitud()
        assertEquals(5.0, ReglasSolicitudes.saldo(listOf(pendiente), TipoSolicitud.ADMINISTRATIVO, 2026), 0.001)
        assertEquals(6.0, ReglasSolicitudes.saldo(listOf(pendiente), TipoSolicitud.ADMINISTRATIVO, 2026, false), 0.001)
        assertEquals(5.0, ReglasSolicitudes.saldo(listOf(pendiente.copy(estado = EstadoSolicitud.APROBADA)), TipoSolicitud.ADMINISTRATIVO, 2026, false), 0.001)
        assertEquals(6.0, ReglasSolicitudes.saldo(listOf(pendiente.copy(estado = EstadoSolicitud.RECHAZADA)), TipoSolicitud.ADMINISTRATIVO, 2026), 0.001)
    }
    @Test fun rechazaPeriodosSuperpuestos() {
        assertThrows(IllegalArgumentException::class.java) {
            ReglasSolicitudes.validar(solicitud().copy(id = "OTRA"), listOf(solicitud()), LocalDate.of(2026, 11, 2))
        }
    }
    @Test fun permiteDosMediasJornadasDistintasPeroNoLaMisma() {
        val am = solicitud(JornadaSolicitud.MANANA)
        ReglasSolicitudes.validar(solicitud(JornadaSolicitud.TARDE).copy(id = "PM"), listOf(am), LocalDate.of(2026, 11, 2))
        assertThrows(IllegalArgumentException::class.java) {
            ReglasSolicitudes.validar(am.copy(id = "AM-2"), listOf(am), LocalDate.of(2026, 11, 2))
        }
    }
    @Test fun rechazaFechasPasadasYMediasJornadasDeVariosDias() {
        assertThrows(IllegalArgumentException::class.java) { ReglasSolicitudes.validar(solicitud(), emptyList(), LocalDate.of(2026, 11, 4)) }
        assertThrows(IllegalArgumentException::class.java) {
            ReglasSolicitudes.validar(solicitud(JornadaSolicitud.MANANA).copy(hasta = "2026-11-04"), emptyList(), LocalDate.of(2026, 11, 2))
        }
    }
}
