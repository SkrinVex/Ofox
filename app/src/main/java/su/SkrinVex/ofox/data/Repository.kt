package su.SkrinVex.ofox.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Repository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val prefs = context.getSharedPreferences("ofox_prefs", Context.MODE_PRIVATE)

    suspend fun login(email: String, password: String): User? = withContext(Dispatchers.IO) {
        db.userDao().login(email, password)?.also {
            prefs.edit().putInt("user_id", it.id).apply()
        }
    }

    suspend fun register(email: String, password: String, name: String): User? = withContext(Dispatchers.IO) {
        val userId = db.userDao().register(User(email = email, password = password, name = name))
        db.userDao().getUser(userId.toInt())?.also {
            prefs.edit().putInt("user_id", it.id).apply()
        }
    }

    fun isLoggedIn(): Boolean = prefs.getInt("user_id", -1) != -1

    fun getCurrentUserId(): Int = prefs.getInt("user_id", -1)

    suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId != -1) db.userDao().getUser(userId) else null
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    suspend fun updateUser(user: User) = withContext(Dispatchers.IO) {
        db.userDao().updateUser(user)
    }

    suspend fun getAllPosts(): List<Post> = withContext(Dispatchers.IO) {
        db.postDao().getAllPosts()
    }

    suspend fun createPost(content: String, type: String = "TEXT") = withContext(Dispatchers.IO) {
        val user = getCurrentUser() ?: return@withContext
        db.postDao().insertPost(
            Post(
                authorId = user.id,
                authorName = user.name,
                content = content,
                timestamp = System.currentTimeMillis(),
                type = type
            )
        )
    }

    suspend fun toggleLike(post: Post) = withContext(Dispatchers.IO) {
        val newLiked = !post.isLiked
        val newLikes = if (newLiked) post.likes + 1 else post.likes - 1
        db.postDao().updateLikes(post.id, newLikes, newLiked)
    }

    suspend fun sharePost(post: Post) = withContext(Dispatchers.IO) {
        db.postDao().updateShares(post.id, post.shares + 1)
    }

    suspend fun getAllChats(): List<Chat> = withContext(Dispatchers.IO) {
        db.chatDao().getAllChats()
    }

    suspend fun getMessages(chatId: Int): List<Message> = withContext(Dispatchers.IO) {
        db.messageDao().getMessages(chatId)
    }

    suspend fun sendMessage(chatId: Int, text: String, isFromMe: Boolean) = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        db.messageDao().insertMessage(
            Message(chatId = chatId, text = text, timestamp = timestamp, isFromMe = isFromMe)
        )
        db.chatDao().updateChat(chatId, text, timestamp)
    }

    suspend fun getAllDiscoveries(): List<Discovery> = withContext(Dispatchers.IO) {
        db.discoveryDao().getAllDiscoveries()
    }

    suspend fun toggleJoinDiscovery(discovery: Discovery) = withContext(Dispatchers.IO) {
        val newJoined = !discovery.isJoined
        val newParticipants = if (newJoined) discovery.participants + 1 else discovery.participants - 1
        db.discoveryDao().updateJoinStatus(discovery.id, newJoined, newParticipants)
    }

    suspend fun createDiscovery(title: String, description: String, category: String, colorHex: String) = withContext(Dispatchers.IO) {
        db.discoveryDao().insertDiscovery(
            Discovery(
                id = 0,
                title = title,
                description = description,
                category = category,
                participants = 1,
                colorHex = colorHex,
                isJoined = true
            )
        )
    }

    suspend fun initializeSampleData() = withContext(Dispatchers.IO) {
        if (db.postDao().getAllPosts().isEmpty()) {
            val samplePosts = listOf(
                Post(0, 1, "Komari", "🌟 Сегодня особенный день! Запустил новый проект и чувствую невероятный прилив энергии.", 42, 12, 8, System.currentTimeMillis() - 7200000, "MOOD"),
                Post(0, 2, "Елена", "Что лучше для изучения Android разработки?", 28, 15, 3, System.currentTimeMillis() - 14400000, "POLL"),
                Post(0, 3, "Андрей", "\"Код - это поэзия, которую понимают машины\" - неизвестный автор", 35, 8, 12, System.currentTimeMillis() - 21600000, "QUOTE")
            )
            samplePosts.forEach { db.postDao().insertPost(it) }
        }

        if (db.chatDao().getAllChats().isEmpty()) {
            val sampleChats = listOf(
                Chat(0, "Алексей", "Привет! Как дела?", System.currentTimeMillis() - 3600000),
                Chat(0, "Мария", "Увидимся завтра", System.currentTimeMillis() - 7200000),
                Chat(0, "Группа разработчиков", "Новый релиз готов", System.currentTimeMillis() - 10800000)
            )
            sampleChats.forEach { db.chatDao().insertChat(it) }
        }

        if (db.discoveryDao().getAllDiscoveries().isEmpty()) {
            val sampleDiscoveries = listOf(
                Discovery(0, "Coding Challenge", "Решай задачи по программированию", "Программирование", 1247, "FF4CAF50"),
                Discovery(0, "Tech Meetup", "Встреча разработчиков в твоем городе", "События", 89, "FF2196F3"),
                Discovery(0, "Open Source", "Найди проект для вклада", "Проекты", 567, "FFFF9800")
            )
            sampleDiscoveries.forEach { db.discoveryDao().insertDiscovery(it) }
        }
    }
}
