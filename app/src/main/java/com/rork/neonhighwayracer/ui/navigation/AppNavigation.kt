package com.rork.neonhighwayracer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rork.neonhighwayracer.ui.screens.GameScreen
import com.rork.neonhighwayracer.ui.screens.HomeScreen

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onStartGame = {
                    navController.navigate("game") {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable("game") {
            GameScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
