package cl.fourpeople.aps

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.sync.Mutex

object AlmacenamientoAps { val bloqueo = Mutex() }

object ControlAcceso {
    fun perfil(contexto: Context): PerfilUsuario? {
        val prefs = contexto.getSharedPreferences("sesion_local", Context.MODE_PRIVATE)
        return PerfilesCompartidos.perfiles.firstOrNull {
            it.usuario == prefs.getString("usuario", "") && AccesoCuentas.rolTexto(it.rol) == prefs.getString("rol", "")
        }
    }
    fun puede(contexto: Context, permiso: String, expediente: Boolean = false, propio: Boolean = false): Boolean {
        val datos = DatosAdministracion.estado.expediente
        val cuenta = perfil(contexto)
        if (expediente && permiso != "expedientes.leer" && cuenta?.puede("expedientes.leer") != true) return false
        return PermisosUsuario.permite(cuenta, permiso, DatosSeguridad.estado.bloqueadas,
            if (expediente || propio) datos.centro else null, if (propio) datos.usuario else null)
    }
    fun exigir(contexto: Context, permiso: String, expediente: Boolean = false, propio: Boolean = false): PerfilUsuario {
        require(puede(contexto, permiso, expediente, propio)) { "Tu cuenta no tiene permiso para esta acción o este expediente." }
        return requireNotNull(perfil(contexto))
    }
    fun avisos(contexto: Context) = avisosFuncionario().filter {
        puede(contexto, if (it.grupo == "DOCUMENTOS") "documentos.propios" else "solicitudes.propias", propio = true)
    }
}

@Composable
internal fun AccesoDenegado(onVolver: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("No tienes acceso a este expediente o a esta función.")
        Button(onClick = onVolver) { Text("Volver") }
    }
}
