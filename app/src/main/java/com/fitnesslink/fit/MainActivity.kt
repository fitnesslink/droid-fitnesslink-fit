package com.fitnesslink.fit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fitnesslink.fit.ui.navigation.AppNavigation
import com.fitnesslink.fit.ui.theme.FitnessLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FitnessLinkTheme {
                AppNavigation()
            }
        }
    }
}
