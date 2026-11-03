package cl.fourpeople.aps

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class EventoAuditoria(
    val id: String = UUID.randomUUID().toString(),
    val fecha: String = LocalDateTime.now().toString(),
    val usuario: String,
    val rol: String,
    val entidadId: String,
    val entidad: String,
    val accion: String,
    val resultado: String = "Correcto",
    val centro: String,
    val categoria: String,
    val titular: String,
    val run: String,
    val detalle: String = ""
)

data class FiltroAuditoria(
    val busqueda: String = "",
    val centro: String = "Todos",
    val categoria: String = "Todos",
    val accion: String = "Todas",
    val periodo: String = "Últimos 7 días"
) {
    fun aplicar(eventos: List<EventoAuditoria>, hoy: LocalDate = LocalDate.now()): List<EventoAuditoria> {
        val dias = when (periodo) { "Hoy" -> 1L; "Últimos 7 días" -> 7L; "Últimos 30 días" -> 30L; else -> null }
        return eventos.filter { e ->
            val fecha = LocalDateTime.parse(e.fecha).toLocalDate()
            (centro == "Todos" || e.centro == centro) &&
                (categoria == "Todos" || e.categoria == categoria) &&
                (accion == "Todas" || e.accion == accion) &&
                (dias == null || (!fecha.isBefore(hoy.minusDays(dias - 1)) && !fecha.isAfter(hoy))) &&
                listOf(e.usuario, e.titular, e.run, e.entidad, e.entidadId).any {
                    it.contains(busqueda.trim(), ignoreCase = true)
                }
        }.sortedByDescending { it.fecha }
    }
}

object CsvAuditoria {
    private fun celda(valor: String): String {
        // Evita que una hoja de cálculo interprete datos del usuario como fórmulas.
        val seguro = if (valor.trimStart().firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r', '\n')) "'" + valor else valor
        return "\"" + seguro.replace("\"", "\"\"") + "\""
    }
    fun generar(eventos: List<EventoAuditoria>): String = "\uFEFF" +
        (listOf(listOf("ID", "Fecha", "Usuario", "Rol", "Entidad ID", "Entidad", "Acción", "Resultado", "Centro", "Tipo", "Titular", "RUN ficticio", "Detalle")) +
            eventos.map { listOf(it.id, it.fecha, it.usuario, it.rol, it.entidadId, it.entidad, it.accion, it.resultado, it.centro, it.categoria, it.titular, it.run, it.detalle) })
            .joinToString("\r\n", postfix = "\r\n") { fila -> fila.joinToString(",") { celda(it) } }
}
