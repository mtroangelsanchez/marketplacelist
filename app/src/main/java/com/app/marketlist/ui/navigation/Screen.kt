package com.app.marketlist.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")

    object ProductDetail : Screen("product/{productId}") {
        const val ARG_PRODUCT_ID = "productId"
        fun createRoute(productId: Long) = "product/$productId"
    }

    object ProductForm : Screen("product_form?productId={productId}&barcode={barcode}") {
        const val ARG_PRODUCT_ID = "productId"
        const val ARG_BARCODE = "barcode"
        const val RESULT_SCANNED_BARCODE = "scanned_barcode"
        fun createRoute(productId: Long? = null, barcode: String? = null): String {
            val params = buildList {
                if (productId != null) add("productId=$productId")
                if (!barcode.isNullOrBlank()) add("barcode=$barcode")
            }
            return if (params.isEmpty()) "product_form" else "product_form?" + params.joinToString("&")
        }
    }

    object StoreList : Screen("stores")

    object StoreLists : Screen("store_lists/{storeId}") {
        const val ARG_STORE_ID = "storeId"
        fun createRoute(storeId: Long) = "store_lists/$storeId"
    }
    object ListDetail : Screen("list_detail/{listId}?storeId={storeId}") {
        const val ARG_LIST_ID = "listId"
        const val ARG_STORE_ID = "storeId"
        fun createRoute(listId: Long, storeId: Long? = null): String =
            if (storeId == null) "list_detail/$listId" else "list_detail/$listId?storeId=$storeId"
    }
    object MonthlyListHistory : Screen("monthly_lists")

    object TicketScan : Screen("ticket_scan")
    object ListSheet : Screen("list_sheet")

    object Scanner : Screen("scanner?forResult={forResult}") {
        const val ARG_FOR_RESULT = "forResult"
        fun createRoute(forResult: Boolean = false) = "scanner?forResult=$forResult"
    }

    object PriceHistory : Screen("price_history/{productId}") {
        const val ARG_PRODUCT_ID = "productId"
        fun createRoute(productId: Long) = "price_history/$productId"
    }

    object Export : Screen("export/{listId}") {
        const val ARG_LIST_ID = "listId"
        fun createRoute(listId: Long) = "export/$listId"
    }
}
