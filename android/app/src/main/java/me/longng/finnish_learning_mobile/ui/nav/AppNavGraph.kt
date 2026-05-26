package me.longng.finnish_learning_mobile.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.toRoute

/**
 * Top-level navigation graph.
 */
@Composable
fun AppNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Route.Landing,
    ) {
        composable<Route.Landing> { Placeholder("Landing") }
        composable<Route.Login> { Placeholder("Login") }
        composable<Route.Register> { Placeholder("Register") }
        composable<Route.Dashboard> { Placeholder("Dashboard") }
        composable<Route.Portfolio> { Placeholder("Portfolio") }

        composable<Route.Topic> { entry ->
            val args: Route.Topic = entry.toRoute()
            Placeholder("Topic ${args.topicId}")
        }
        composable<Route.Quiz> { entry ->
            val args: Route.Quiz = entry.toRoute()
            Placeholder("Quiz ${args.topicId}")
        }
    }
}

@Composable
private fun Placeholder(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = label)
    }
}