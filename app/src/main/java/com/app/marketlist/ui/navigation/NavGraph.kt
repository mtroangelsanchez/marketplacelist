package com.app.marketlist.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.app.marketlist.ui.screens.export.ExportScreen
import com.app.marketlist.ui.screens.home.HomeScreen
import com.app.marketlist.ui.screens.listsheet.ListSheetScreen
import com.app.marketlist.ui.screens.monthlylist.ListDetailScreen
import com.app.marketlist.ui.screens.monthlylist.MonthlyListHistoryScreen
import com.app.marketlist.ui.screens.pricehistory.PriceHistoryScreen
import com.app.marketlist.ui.screens.product.ProductDetailScreen
import com.app.marketlist.ui.screens.product.ProductFormScreen
import com.app.marketlist.ui.screens.scanner.ScannerScreen
import com.app.marketlist.ui.screens.store.StoreListScreen
import com.app.marketlist.ui.screens.store.StoreListsScreen
import com.app.marketlist.ui.screens.ticket.TicketScanScreen

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onProductClick = { navController.navigate(Screen.ProductDetail.createRoute(it)) },
                onAddProduct = { navController.navigate(Screen.ProductForm.createRoute()) }
            )
        }

        composable(
            route = Screen.ProductDetail.route,
            arguments = listOf(navArgument(Screen.ProductDetail.ARG_PRODUCT_ID) { type = NavType.LongType })
        ) {
            ProductDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Screen.ProductForm.createRoute(productId = it)) },
                onOpenHistory = { navController.navigate(Screen.PriceHistory.createRoute(it)) }
            )
        }

        composable(
            route = Screen.ProductForm.route,
            arguments = listOf(
                navArgument(Screen.ProductForm.ARG_PRODUCT_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument(Screen.ProductForm.ARG_BARCODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            ProductFormScreen(
                onBack = { navController.popBackStack() },
                onScan = { navController.navigate(Screen.Scanner.createRoute(forResult = true)) }
            )
        }

        composable(Screen.StoreList.route) {
            StoreListScreen(onStoreClick = { navController.navigate(Screen.StoreLists.createRoute(it)) })
        }
        composable(
            route = Screen.StoreLists.route,
            arguments = listOf(navArgument(Screen.StoreLists.ARG_STORE_ID) { type = NavType.LongType })
        ) { entry ->
            val storeId = entry.arguments?.getLong(Screen.StoreLists.ARG_STORE_ID) ?: -1L
            StoreListsScreen(
                onBack = { navController.popBackStack() },
                onListClick = { navController.navigate(Screen.ListDetail.createRoute(it, storeId)) }
            )
        }
        composable(
            route = Screen.ListDetail.route,
            arguments = listOf(
                navArgument(Screen.ListDetail.ARG_LIST_ID) { type = NavType.LongType },
                navArgument(Screen.ListDetail.ARG_STORE_ID) {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            ListDetailScreen(
                onBack = { navController.popBackStack() },
                onProductClick = { navController.navigate(Screen.ProductDetail.createRoute(it)) }
            )
        }

        composable(Screen.MonthlyListHistory.route) {
            MonthlyListHistoryScreen(
                onExport = { navController.navigate(Screen.Export.createRoute(it)) },
                onListClick = { navController.navigate(Screen.ListDetail.createRoute(it)) },
                onNewProduct = { navController.navigate(Screen.ProductForm.createRoute()) },
                onProductClick = { navController.navigate(Screen.ProductDetail.createRoute(it)) },
                onScanTicket = { navController.navigate(Screen.TicketScan.route) },
                onScanList = { navController.navigate(Screen.ListSheet.route) }
            )
        }

        composable(Screen.ListSheet.route) {
            ListSheetScreen(
                onBack = { navController.popBackStack() },
                onSaved = { listId ->
                    navController.navigate(Screen.ListDetail.createRoute(listId)) {
                        popUpTo(Screen.ListSheet.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.TicketScan.route) {
            TicketScanScreen(
                onBack = { navController.popBackStack() },
                onSaved = { listId, storeId ->
                    navController.navigate(Screen.ListDetail.createRoute(listId, storeId)) {
                        popUpTo(Screen.TicketScan.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.Scanner.route,
            arguments = listOf(
                navArgument(Screen.Scanner.ARG_FOR_RESULT) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            val forResult = entry.arguments?.getBoolean(Screen.Scanner.ARG_FOR_RESULT) ?: false
            ScannerScreen(
                onBack = if (forResult) ({ navController.popBackStack() }) else null,
                onProductFound = { productId ->
                    navController.navigate(Screen.ProductDetail.createRoute(productId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onNewProduct = { barcode ->
                    navController.navigate(Screen.ProductForm.createRoute(barcode = barcode)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBarcodeResult = { barcode ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(Screen.ProductForm.RESULT_SCANNED_BARCODE, barcode)
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.PriceHistory.route,
            arguments = listOf(navArgument(Screen.PriceHistory.ARG_PRODUCT_ID) { type = NavType.LongType })
        ) {
            PriceHistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Export.route,
            arguments = listOf(navArgument(Screen.Export.ARG_LIST_ID) { type = NavType.LongType })
        ) {
            ExportScreen(onBack = { navController.popBackStack() })
        }
    }
}
