package com.app.marketlist.domain.usecase

/**
 * Detecta encabezados de departamento de un ticket ("LACTEOS", "FRUTAS Y VERDURAS---") y los
 * convierte en la categoría sugerida del producto: una de las categorías de la app cuando
 * hay equivalencia, o el nombre del departamento en formato título cuando no.
 */
object DepartmentCategory {

    private val STOP_WORDS = setOf("y", "e", "de", "del", "la", "el", "los", "las", "en", "para", "a")

    // Palabras que por sí solas nombran un departamento (no un producto): sirven para reconocer un
    // encabezado aunque la línea no termine en guiones.
    private val DEPARTMENT_WORDS = setOf(
        "abarrotes", "abarrote", "despensa", "lacteos", "lacteo", "cremeria", "carnes", "carniceria",
        "salchichoneria", "pescaderia", "mariscos", "frutas", "verduras", "legumbres", "bebidas",
        "licores", "vinos", "limpieza", "quimicos", "higiene", "tocador", "perfumeria", "farmacia",
        "panaderia", "tortilleria", "congelados", "hogar", "mascotas", "jugueteria", "papeleria",
        "electronica", "deportes", "ropa", "zapateria", "bazar", "cocina", "deli", "rosticeria",
        "botanas", "dulceria"
    )

    // Equivalencias con las categorías de la app (ProductCategories). El primer término del
    // encabezado que coincide decide la categoría.
    private val RULES: List<Pair<String, Set<String>>> = listOf(
        "Frutas y Verduras" to setOf("fruta", "frutas", "verdura", "verduras", "legumbres", "hortalizas", "produce"),
        "Carnes" to setOf(
            "carne", "carnes", "pollo", "res", "cerdo", "pescado", "pescados", "mariscos", "embutidos",
            "salchichoneria", "carniceria", "pescaderia", "aves", "charcuteria"
        ),
        "Lácteos" to setOf("lacteo", "lacteos", "cremeria", "queso", "quesos", "yogur", "yogurt", "huevo", "huevos"),
        "Bebidas" to setOf("bebida", "bebidas", "licor", "licores", "vino", "vinos", "cerveza", "cervezas", "refrescos"),
        "Limpieza" to setOf("limpieza", "quimico", "quimicos", "detergentes", "desechables"),
        "Higiene" to setOf("higiene", "tocador", "perfumeria", "farmacia", "belleza", "personal"),
        "Abarrotes" to setOf("abarrote", "abarrotes", "despensa", "enlatados", "cereales", "botanas", "dulceria")
    )

    private val ACCENTS = mapOf(
        "panaderia" to "Panadería", "tortilleria" to "Tortillería", "lacteos" to "Lácteos",
        "carniceria" to "Carnicería", "salchichoneria" to "Salchichonería", "pescaderia" to "Pescadería",
        "jugueteria" to "Juguetería", "papeleria" to "Papelería", "zapateria" to "Zapatería",
        "electronica" to "Electrónica", "rosticeria" to "Rosticería", "dulceria" to "Dulcería",
        "cremeria" to "Cremería", "perfumeria" to "Perfumería", "quimicos" to "Químicos",
        "dulces" to "Dulces", "mascotas" to "Mascotas"
    )

    private val LEADING_CODE = Regex("^\\d{4,}\\s+")
    private val TRAILING_DASHES = Regex("\\s*[-–—_=~]+[\\s\\W_]*$")

    /** Texto del encabezado sin guiones finales; null si la línea no parece un encabezado de departamento. */
    fun headerText(row: String): String? {
        var trimmed = row.trim()
        // El OCR a veces pega el código de un artículo vecino antes del encabezado con guiones.
        if (TRAILING_DASHES.containsMatchIn(trimmed)) trimmed = trimmed.replace(LEADING_CODE, "")
        if (trimmed.isEmpty() || trimmed.any { it.isDigit() }) return null
        val dashed = TRAILING_DASHES.containsMatchIn(trimmed)
        val cleaned = trimmed.replace(TRAILING_DASHES, "").replace(Regex("[^\\p{L}\\s]"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (cleaned.count { it.isLetter() } < 3) return null
        val tokens = ProductMatcher.normalize(cleaned).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > 6) return null
        if (dashed) return cleaned
        val keywords = tokens.filter { it !in STOP_WORDS }
        return if (keywords.isNotEmpty() && keywords.all { it in DEPARTMENT_WORDS }) cleaned else null
    }

    /** Categoría sugerida para un encabezado ya validado con [headerText]. */
    fun categoryFor(header: String): String {
        val tokens = ProductMatcher.normalize(header).split(' ').filter { it.isNotEmpty() }
        for (token in tokens) {
            RULES.firstOrNull { token in it.second }?.let { return it.first }
        }
        return titleCase(header, tokens)
    }

    private fun titleCase(header: String, tokens: List<String>): String {
        val words = header.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        // El OCR suele truncar el final ("FUENTE DE S---"): se descartan colas de 1 letra o conectores.
        while (words.size > 1 && (words.last().length <= 1 || ProductMatcher.normalize(words.last()) in STOP_WORDS)) {
            words.removeAt(words.lastIndex)
        }
        val result = words.mapIndexed { index, word ->
            val plain = ProductMatcher.normalize(word)
            val lower = word.lowercase()
            when {
                ACCENTS.containsKey(plain) -> ACCENTS.getValue(plain)
                index > 0 && plain in STOP_WORDS -> lower
                else -> lower.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
        return result.take(50).ifBlank { tokens.joinToString(" ") }
    }
}
