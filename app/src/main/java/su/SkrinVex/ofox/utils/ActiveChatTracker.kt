package su.SkrinVex.ofox.utils

object ActiveChatTracker {
    @Volatile
    var activeChatId: Int? = null
}
