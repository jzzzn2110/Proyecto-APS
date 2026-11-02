package cl.fourpeople.aps

import android.content.Context
import java.io.File
import java.io.IOException

/** Estado de sesión mínimo: nunca guarda la contraseña y se limpia al cerrar sesión. */
object SesionLocal {
    private const val PREFS = "sesion_local"
    fun guardar(contexto: Context, usuario: String, rol: String) = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putString("usuario", usuario).putString("rol", rol).apply()
    fun limpiar(contexto: Context) = contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}
