package com.mactso.regrowth.core

/** Immutable, Minecraft-independent configuration snapshot. */
data class MobRule(val action: String, val seconds: Double) {
    init { require(seconds.isFinite() && seconds >= 1.0) }
    fun probability(horseEating: Boolean = false): Double =
        ((if (horseEating) 20.0 else 1.0) / (seconds * 10.0)).coerceAtMost(1.0)
}

object Rules {
    private val identifier = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")
    private val actions = setOf("eat", "grow", "both", "tall", "stumble", "mushroom", "coral", "reforest", "cut")
    fun validId(value: String): Boolean = identifier.matches(value)
    fun checkedOnTick(entityId: Int, tick: Long): Boolean = (entityId and 1) != (tick and 1L).toInt()
    fun parseMobs(raw: String, warning: (String) -> Unit = {}): Map<String, MobRule> = buildMap {
        raw.split(';').filter(String::isNotBlank).forEach { entry ->
            val parts = entry.split(',').map(String::trim)
            val seconds = parts.getOrNull(2)?.toDoubleOrNull()
            val action = parts.getOrNull(1)?.lowercase()
            val validAction = action in actions || (action != null && action.isNotEmpty() && action.all { it in "chrwvtp" })
            if (parts.size != 3 || !validId(parts[0]) || seconds == null || !seconds.isFinite() || !validAction) {
                warning("Invalid mob rule: $entry")
            } else put(parts[0], MobRule(requireNotNull(action), seconds.coerceAtLeast(1.0)))
        }
    }.toMap()

    fun serializeMobs(rules: Map<String, MobRule>): String = rules.toSortedMap().entries.joinToString(";", postfix = if (rules.isEmpty()) "" else ";") {
        "${it.key},${it.value.action},${it.value.seconds}"
    }
    fun wallRadius(diameter: Int): Int = diameter.coerceIn(32, 128) / 2 - 1
    fun onPerimeter(x: Int, z: Int, radius: Int): Boolean =
        (kotlin.math.abs(x) == radius && kotlin.math.abs(z) <= radius) ||
        (kotlin.math.abs(z) == radius && kotlin.math.abs(x) <= radius)
}

/** No world/entity references; bounded and explicitly owned by one server instance. */
class BoundedCache<K, V>(private val capacity: Int, private val ttl: Long) {
    init { require(capacity > 0 && ttl > 0) }
    private data class Entry<V>(val value: V, val expires: Long)
    private val entries = LinkedHashMap<K, Entry<V>>()
    val size: Int get() = entries.size
    fun get(key: K, tick: Long, load: () -> V): V {
        val cached = entries[key]
        if (cached != null && tick < cached.expires) return cached.value
        entries.remove(key)
        val value = load()
        if (entries.size >= capacity) entries.remove(entries.keys.first())
        entries[key] = Entry(value, tick + ttl)
        return value
    }
    fun clear() = entries.clear()
}
