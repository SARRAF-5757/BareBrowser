package io.github.sarraf5757.barebrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import io.github.sarraf5757.barebrowser.ui.BrowserScreen
import io.github.sarraf5757.barebrowser.ui.theme.BareBrowserTheme

class MainActivity : ComponentActivity() {
    // Simplified ViewModel initialization using the viewModels delegate
    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enables drawing behind system bars (status/navigation)
        enableEdgeToEdge()
        
        setContent {
            BareBrowserTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrowserScreen(viewModel = viewModel)
                }
            }
        }
    }
}
