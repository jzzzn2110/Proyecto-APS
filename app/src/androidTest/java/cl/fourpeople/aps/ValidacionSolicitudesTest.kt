package cl.fourpeople.aps

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.time.format.DateTimeParseException

class ValidacionSolicitudesTest {
    private fun raiz(creacion: String, revision: String = ""): JSONObject {
        val solicitud = JSONObject().put("id", "SOL-FECHA").put("tipo", "OTRO")
            .put("desde", "2026-11-03").put("hasta", "2026-11-03").put("jornada", "COMPLETA")
            .put("motivo", "Solicitud ficticia").put("creadaEn", creacion).put("estado", "EN_REVISION")
            .put("revisadaEn", revision)
        return JSONObject().put("formato", 1).put("solicitudes", JSONArray().put(solicitud))
    }
    @Test fun rechazaFechasDeCreacionORevisionQueRomperianLaVista() {
        assertThrows(DateTimeParseException::class.java) { DatosSolicitudes.leer(raiz("fecha inválida")) }
        assertThrows(DateTimeParseException::class.java) { DatosSolicitudes.leer(raiz("2026-11-02T10:00:00", "inválida")) }
        assertEquals(1, DatosSolicitudes.leer(raiz("2026-11-02T10:00:00")).solicitudes.size)
    }
}
