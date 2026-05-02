package su.SkrinVex.ofox.data

import su.SkrinVex.ofox.data.api.models.BadgeDefinition

/** In-memory кэш определений бейджей, загружается с сервера при старте */
object BadgeCache {
    private var definitions: Map<String, BadgeDefinition> = emptyMap()

    fun update(defs: List<BadgeDefinition>) {
        definitions = defs.associateBy { it.badge_type }
    }

    fun get(badgeType: String): BadgeDefinition? = definitions[badgeType]

    fun all(): List<BadgeDefinition> = definitions.values.toList()
}
