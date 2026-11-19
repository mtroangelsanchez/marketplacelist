package com.app.marketlist.ui.screens.home

import com.app.marketlist.domain.model.Product

object ProductFilter {
    /** Búsqueda parcial case-insensitive por nombre o categoría. Query en blanco devuelve todo. */
    fun apply(all: List<Product>, query: String): List<Product> {
        val term = query.trim()
        if (term.isEmpty()) return all
        return all.filter {
            it.name.contains(term, ignoreCase = true) || it.category.contains(term, ignoreCase = true)
        }
    }
}
