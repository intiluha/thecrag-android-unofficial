package org.thecragunofficial.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.thecragunofficial.android.domain.WORLD_ID
import org.thecragunofficial.android.ui.browse.AreaScreen
import org.thecragunofficial.android.ui.route.RouteScreen
import org.thecragunofficial.android.ui.search.SearchScreen
import org.thecragunofficial.android.ui.theme.TheCragTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TheCragTheme { AppNav() } }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "area/$WORLD_ID") {
        composable("area/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: WORLD_ID
            AreaScreen(
                areaId = id,
                onOpenArea = { nav.navigate("area/$it") },
                onOpenRoute = { nav.navigate("route/$it") },
                onSearch = { nav.navigate("search") },
                onBack = if (nav.previousBackStackEntry != null) ({ nav.popBackStack() }) else null,
            )
        }
        composable("route/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            RouteScreen(routeId = id, onOpenArea = { nav.navigate("area/$it") }, onBack = { nav.popBackStack() })
        }
        composable("search") {
            SearchScreen(
                onOpenArea = { nav.navigate("area/$it") },
                onOpenRoute = { nav.navigate("route/$it") },
                onBack = { nav.popBackStack() },
            )
        }
    }
}
