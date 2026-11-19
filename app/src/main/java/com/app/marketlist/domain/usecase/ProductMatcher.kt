package com.app.marketlist.domain.usecase

import com.app.marketlist.domain.model.Product
import java.text.Normalizer

/** Coincidencia de descripciones de ticket con productos existentes (por nombre, tolerante). */
object ProductMatcher {
    private const val JACCARD_THRESHOLD = 0.6
    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Minúsculas, sin acentos ni símbolos, espacios compactados. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return decomposed.replace(DIACRITICS, "").replace(NON_ALNUM, " ").trim()
    }

    private fun tokens(normalized: String): Set<String> =
        normalized.split(' ').filter { token -> token.length >= 3 && token.any { it.isLetter() } }.toSet()

    fun bestMatch(description: String, products: List<Product>): Product? {
        val desc = normalize(description)
        if (desc.isEmpty()) return null
        products.firstOrNull { normalize(it.name) == desc }?.let { return it }

        val descTokens = tokens(desc)
        var best: Product? = null
        var bestScore = 0.0
        if (descTokens.isNotEmpty()) {
            for (product in products) {
                val productTokens = tokens(normalize(product.name))
                if (productTokens.isEmpty()) continue
                val common = descTokens.intersect(productTokens).size
                val union = descTokens.union(productTokens).size
                val score = common.toDouble() / union
                if (score >= JACCARD_THRESHOLD && score > bestScore) {
                    best = product
                    bestScore = score
                }
            }
        }
        if (best != null) return best

        if (desc.length >= 4) {
            return products
                .filter { p ->
                    val name = normalize(p.name)
                    name.length >= 4 && (desc.contains(name) || name.contains(desc))
                }
                .maxByOrNull { normalize(it.name).length }
        }
        return null
    }
}
