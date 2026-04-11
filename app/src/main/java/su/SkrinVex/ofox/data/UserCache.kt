package su.SkrinVex.ofox.data

/** Простой in-memory кэш пользователей (имя + аватар) */
object UserCache {
    private val cache = mutableMapOf<Int, Pair<String, String?>>() // userId -> (name, avatarUrl)

    fun put(userId: Int, name: String, avatarUrl: String?) {
        cache[userId] = Pair(name, avatarUrl)
    }

    fun getName(userId: Int): String? = cache[userId]?.first
    fun getAvatar(userId: Int): String? = cache[userId]?.second
    fun get(userId: Int): Pair<String, String?>? = cache[userId]
}
