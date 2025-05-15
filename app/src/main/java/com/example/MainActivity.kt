package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.AuthScreen
import com.example.ui.AuthViewModel
import com.example.ui.ProductScreen
import com.example.ui.ProductViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(application)
    }
    private val productViewModel: ProductViewModel by viewModels {
        ProductViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

                    if (isLoggedIn) {
                        ProductScreen(
                            productViewModel = productViewModel,
                            authViewModel = authViewModel
                        )
                    } else {
                        AuthScreen(
                            viewModel = authViewModel
                        )
                    }
                }
            }
        }
    }
}
