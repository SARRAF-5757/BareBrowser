package com.barebrowser.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.barebrowser.app.ui.BrowserScreen
import com.barebrowser.app.ui.BrowserViewModel
import com.barebrowser.app.ui.theme.BareBrowserTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BareBrowserTheme {
                val viewModel: BrowserViewModel = viewModel()
                BrowserScreen(viewModel = viewModel)
            }
        }
    }
}
