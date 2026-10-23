package cl.fourpeople.aps

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp

@Composable
fun NavegacionFuncionario(actual: String, onNavegar: (String) -> Unit) {
    val destinos = listOf("inicio", "documentos", "solicitudes", "avisos")
    val nombres = listOf("Inicio", "Documentos", "Solicitudes", "Avisos")
    val iconos = listOf(android.R.drawable.ic_menu_view, android.R.drawable.ic_menu_agenda,
        android.R.drawable.ic_menu_today, android.R.drawable.ic_dialog_info)
    val sinLeer = avisosFuncionario().count { it.id !in DatosSolicitudes.estado.avisosLeidos }
    NavigationBar(containerColor = Color.White) {
        destinos.forEachIndexed { indice, destino ->
            NavigationBarItem(
                selected = actual == destino,
                onClick = { if (actual != destino) onNavegar(destino) },
                icon = {
                    BadgedBox(badge = {
                        if (destino == "avisos" && sinLeer > 0) {
                            Badge { androidx.compose.material3.Text(sinLeer.coerceAtMost(99).toString()) }
                        }
                    }) {
                        Icon(painterResource(iconos[indice]), contentDescription = null)
                    }
                },
                label = { Text(nombres[indice], fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Color(0xFF115D55),
                    selectedTextColor = Color(0xFF115D55),
                    indicatorColor = Color(0xFFDDEFE6)
                )
            )
        }
    }
}
