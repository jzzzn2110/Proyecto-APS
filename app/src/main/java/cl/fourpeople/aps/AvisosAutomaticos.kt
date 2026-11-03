package cl.fourpeople.aps

import java.time.LocalDate

/** Genera avisos derivados sin escribir duplicados: la identidad depende del estado vigente. */
object AvisosAutomaticos {
    fun vigentes(hoy: LocalDate = LocalDate.now()): List<AvisoFuncionario> =
        avisosFuncionario(hoy).distinctBy { it.id }

    fun idsNuevos(hoy: LocalDate, leidos: Set<String>): Set<String> =
        vigentes(hoy).asSequence().map { it.id }.filterNot(leidos::contains).toSet()
}
