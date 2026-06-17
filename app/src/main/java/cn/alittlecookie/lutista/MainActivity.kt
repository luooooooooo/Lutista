package cn.alittlecookie.lutista

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.alittlecookie.lutista.ui.screen.MainScreen
import cn.alittlecookie.lutista.ui.theme.LutistaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LutistaTheme {
                MainScreen()
            }
        }
    }
}
