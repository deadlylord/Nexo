package com.stree.nexo.search

import com.stree.nexo.data.SearchItem
import com.stree.nexo.data.UsageStore
import java.text.Normalizer

object SearchEngine {

    private val accentMarks = "\\p{M}+".toRegex()
    private val separators = "[^a-z0-9]+".toRegex()
    private val stopWords = setOf(
        "the", "a", "an", "of", "for", "and", "to", "in", "on",
        "de", "del", "la", "el", "los", "las", "y", "o", "para", "con"
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(accentMarks, "")
            .lowercase()
            .trim()

    private fun words(text: String): List<String> =
        normalize(text).split(separators).filter { it.isNotBlank() }

    private fun initials(text: String): String {
        val all = words(text)
        val meaningful = all.filterNot { it in stopWords }
        val source = if (meaningful.isNotEmpty()) meaningful else all
        return source.mapNotNull { it.firstOrNull() }.joinToString("")
    }

    fun search(
        items: List<SearchItem>,
        query: String,
        usage: UsageStore,
        pinnedIds: Set<String> = emptySet()
    ): List<SearchItem> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()

        return items.asSequence()
            .mapNotNull { item ->
                score(item, normalizedQuery, usage, pinnedIds)?.let { item to it }
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .toList()
    }

    private fun score(
        item: SearchItem,
        query: String,
        usage: UsageStore,
        pinnedIds: Set<String>
    ): Int? {
        val tokens = query.split(separators).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null

        val title = normalize(item.title)
        val keywords = normalize(item.keywords)

        var total = 0
        for (token in tokens) {
            total += tokenScore(token, title, keywords) ?: return null
        }

        val useCount = usage.count(item.id).coerceAtMost(25)
        val ageMs = System.currentTimeMillis() - usage.lastUsed(item.id)
        val recency = when {
            usage.lastUsed(item.id) == 0L -> 0
            ageMs < 60L * 60L * 1000L -> 90
            ageMs < 24L * 60L * 60L * 1000L -> 60
            ageMs < 7L * 24L * 60L * 60L * 1000L -> 25
            else -> 0
        }

        return total + useCount * 14 + recency +
            if (item.id in pinnedIds) 320 else 0
    }

    private fun tokenScore(
        token: String,
        title: String,
        keywords: String
    ): Int? {
        if (title == token) return 1000
        if (title.startsWith(token)) return 860
        if (words(title).any { it.startsWith(token) }) return 780

        val titleInitials = initials(title)
        if (titleInitials.startsWith(token)) return 740

        if (title.contains(token)) return 580
        if (keywords.startsWith(token)) return 440
        if (words(keywords).any { it.startsWith(token) }) return 400
        if (keywords.contains(token)) return 340
        if (titleInitials.contains(token)) return 280
        if (token.length >= 4 && isSubsequence(token, title)) return 240

        return null
    }

    private fun isSubsequence(needle: String, haystack: String): Boolean {
        if (needle.isEmpty()) return true
        var index = 0
        for (char in haystack) {
            if (char == needle[index]) {
                index++
                if (index == needle.length) return true
            }
        }
        return false
    }
}
