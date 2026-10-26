package cl.fourpeople.aps

import org.json.JSONObject

// Convierte los datos entre objetos Kotlin y JSON para guardarlos.
object CodecAdministracion {
    // Kotlin -> JSON: prepara los campos que irán al archivo.
    fun escribir(estado: EstadoAdministracion): JSONObject = JSONObject().apply {
        put("expediente", JSONObject().apply {
            val e = estado.expediente
            put("nombre", e.nombre); put("run", e.run); put("centro", e.centro)
            put("cargo", e.cargo); put("contrato", e.contrato); put("ingreso", e.ingreso)
        })
    }

    // JSON -> Kotlin: recupera los campos del archivo.
    fun leer(raiz: JSONObject?): EstadoAdministracion {
        // Si falta un campo antiguo, usa el dato inicial de demostración.
        val base = ExpedienteLaboral()
        val e = raiz?.optJSONObject("expediente") ?: return EstadoAdministracion()
        return EstadoAdministracion(expediente = ExpedienteLaboral(
            e.optString("nombre", base.nombre), e.optString("run", base.run),
            e.optString("centro", base.centro), e.optString("cargo", base.cargo),
            e.optString("contrato", base.contrato), e.optString("ingreso", base.ingreso)))
    }
}