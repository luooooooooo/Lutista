package cn.alittlecookie.lut2photo_n

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.alittlecookie.lut2photo_n.ui.screen.MainScreen
import cn.alittlecookie.lut2photo_n.ui.theme.Lut2PhotoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Lut2PhotoTheme {
                MainScreen()
            }
        }
    }
}
