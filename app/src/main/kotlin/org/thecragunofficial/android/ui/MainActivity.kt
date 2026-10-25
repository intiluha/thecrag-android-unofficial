package org.thecragunofficial.android.ui

import android.net.Uri
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
import org.thecragunofficial.android.ui.about.AboutScreen
import org.thecragunofficial.android.ui.account.AccountScreen
import org.thecragunofficial.android.ui.logbook.LogbookScreen
import org.thecragunofficial.android.ui.browse.AreaScreen
import org.thecragunofficial.android.ui.favourites.FavouritesScreen
import org.thecragunofficial.android.ui.map.MapScreen
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
                onSearch = { ancestor, name -> nav.navigate("search?ancestor=$ancestor&name=${Uri.encode(name)}") },
                onOpenMap = { loc, zoom ->
                    nav.navigate("map/${loc?.lat ?: 20.0}/${loc?.lon ?: 0.0}/${if (loc == null) 1 else zoom}")
                },
                onAbout = { nav.navigate("about") },
                onFavourites = { nav.navigate("favourites") },
                onLogbook = { nav.navigate("logbook") },
                onAccount = { nav.navigate("account") },
                onBack = if (nav.previousBackStackEntry != null) ({ nav.popBackStack() }) else null,
            )
        }
        composable("route/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            RouteScreen(
                routeId = id,
                onOpenArea = { nav.navigate("area/$it") },
                onOpenRoute = { nav.navigate("route/$it") },
                onAccount = { nav.navigate("account") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "map/{lat}/{lon}/{zoom}",
            arguments = listOf(
                navArgument("lat") { type = NavType.StringType },
                navArgument("lon") { type = NavType.StringType },
                navArgument("zoom") { type = NavType.StringType },
            ),
        ) { entry ->
            val a = entry.arguments
            MapScreen(
                lat = a?.getString("lat")?.toDoubleOrNull() ?: 20.0,
                lon = a?.getString("lon")?.toDoubleOrNull() ?: 0.0,
                zoom = a?.getString("zoom")?.toDoubleOrNull() ?: 1.0,
                onOpenArea = { nav.navigate("area/$it") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "search?ancestor={ancestor}&name={name}",
            arguments = listOf(
                navArgument("ancestor") { type = NavType.LongType; defaultValue = WORLD_ID },
                navArgument("name") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            SearchScreen(
                scopeId = entry.arguments?.getLong("ancestor") ?: WORLD_ID,
                scopeName = entry.arguments?.getString("name"),
                onOpenArea = { nav.navigate("area/$it") },
                onOpenRoute = { nav.navigate("route/$it") },
                onBack = { nav.popBackStack() },
            )
        }
        composable("about") { AboutScreen(onBack = { nav.popBackStack() }) }
        composable("favourites") { FavouritesScreen(onOpenArea = { nav.navigate("area/$it") }, onBack = { nav.popBackStack() }) }
        composable("account") { AccountScreen(onBack = { nav.popBackStack() }, onLogbook = { nav.navigate("logbook") }) }
        composable("logbook") {
            LogbookScreen(onOpenRoute = { nav.navigate("route/$it") }, onAccount = { nav.navigate("account") }, onBack = { nav.popBackStack() })
        }
    }
}
