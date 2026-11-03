package cl.fourpeople.aps

import org.json.JSONObject

// Convierte los datos entre objetos Kotlin y JSON para guardarlos.
import org.json.JSONArray
object CodecAdministracion {
    // Kotlin -> JSON: prepara los campos que irán al archivo.
    fun escribir(estado: EstadoAdministracion): JSONObject = JSONObject().apply {
        // Guarda las bajas para conservarlas al cerrar la aplicación.
        put("bajas", JSONArray().apply { estado.bajas.forEach { baja ->
            put(JSONObject().apply {
                put("documentoId", baja.documentoId); put("responsable", baja.responsable)
                put("fecha", baja.fecha); put("motivo", baja.motivo)
            })
        } })
        put("cambios", JSONArray().apply { estado.cambios.forEach { c ->
            put(JSONObject().apply {
                put("id", c.id); put("responsable", c.responsable); put("fecha", c.fecha)
                put("entidad", c.entidad); put("accion", c.accion); put("motivo", c.motivo)
                put("antes", c.antes); put("despues", c.despues)
            })
        } })
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
        // Los datos antiguos pueden no tener bajas registradas.
        val bajasJson = raiz.optJSONArray("bajas") ?: JSONArray()
        val bajas = List(bajasJson.length()) { i ->
            val b = bajasJson.getJSONObject(i)
            BajaDocumental(b.getString("documentoId"), b.getString("responsable"), b.getString("fecha"), b.getString("motivo"))
        }
        val eventos = raiz.optJSONArray("cambios") ?: JSONArray()
        val cambios = List(eventos.length()) { i ->
            val c = eventos.getJSONObject(i)
            CambioAdministrativo(c.getString("id"), c.getString("responsable"), c.getString("fecha"),
                c.getString("entidad"), c.getString("accion"), c.getString("motivo"),
                c.getString("antes"), c.getString("despues"))
        }
        return EstadoAdministracion(bajas = bajas, cambios = cambios, expediente = ExpedienteLaboral(
            e.optString("nombre", base.nombre), e.optString("run", base.run),
            e.optString("centro", base.centro), e.optString("cargo", base.cargo),
            e.optString("contrato", base.contrato), e.optString("ingreso", base.ingreso)))
    }
}
