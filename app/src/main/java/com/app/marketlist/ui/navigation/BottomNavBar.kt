package com.app.marketlist.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

private data class BottomItem(val label: String, val route: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem("Inicio", Screen.Home.route, Icons.Default.Home),
    BottomItem("Tiendas", Screen.StoreList.route, Icons.Default.ShoppingCart),
    BottomItem("Listas", Screen.MonthlyListHistory.route, Icons.Default.List),
    BottomItem("Escanear", Screen.Scanner.route, Icons.Default.Search)
)

/** Rutas de nivel superior en las que se muestra la barra inferior. */
val topLevelRoutes: Set<String> = bottomItems.map { it.route }.toSet()

@Composable
fun BottomNavBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    NavigationBar {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                onClick = {
                    val target = if (item.route == Screen.Scanner.route) {
                        Screen.Scanner.createRoute(false)
                    } else {
                        item.route
                    }
                    navController.navigate(target) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
