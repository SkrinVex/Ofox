package su.SkrinVex.ofox.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Repository(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val prefs = context.getSharedPreferences("ofox_prefs", Context.MODE_PRIVATE)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            initSampleUsers()
        }
    }

    private suspend fun initSampleUsers() {
        val users = listOf(
            User(id = 1, email = "komari@ofox.su", password = "123", name = "Komari", bio = "Разработчик OFOX"),
            User(id = 2, email = "elena@ofox.su", password = "123", name = "Елена", bio = "Дизайнер и художник"),
            User(id = 3, email = "ivan@ofox.su", password = "123", name = "Иван", bio = "Программист")
        )
        users.forEach { user ->
            if (db.userDao().getUser(user.id) == null) {
                try {
                    db.userDao().insertUser(user)
                } catch (e: Exception) {
                    // User already exists
                }
            }
        }
    }

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

    suspend fun getUserById(userId: Int): User? = withContext(Dispatchers.IO) {
        db.userDao().getUser(userId)
    }

    suspend fun getPostsByUser(userId: Int): List<Post> = withContext(Dispatchers.IO) {
        db.postDao().getPostsByUser(userId)
    }

    suspend fun isSubscribed(userId: Int): Boolean = withContext(Dispatchers.IO) {
        prefs.getStringSet("subscriptions", emptySet())?.contains(userId.toString()) ?: false
    }

    suspend fun getSubscribersCount(userId: Int): Int {
        val baseCount = when(userId) {
            1 -> 150
            2 -> 473
            3 -> 89
            else -> 10
        }
        val key = "subs_count_$userId"
        return prefs.getInt(key, baseCount)
    }

    suspend fun toggleSubscription(userId: Int) = withContext(Dispatchers.IO) {
        val subs = prefs.getStringSet("subscriptions", emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = "subs_count_$userId"
        val currentCount = getSubscribersCount(userId)
        
        if (subs.contains(userId.toString())) {
            subs.remove(userId.toString())
            prefs.edit().putInt(key, currentCount - 1).apply()
        } else {
            subs.add(userId.toString())
            prefs.edit().putInt(key, currentCount + 1).apply()
        }
        prefs.edit().putStringSet("subscriptions", subs).apply()
    }

    suspend fun getSubscriptions(): List<User> = withContext(Dispatchers.IO) {
        val subs = prefs.getStringSet("subscriptions", emptySet()) ?: emptySet()
        subs.mapNotNull { db.userDao().getUser(it.toIntOrNull() ?: 0) }
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

    suspend fun createPost(
        content: String, 
        type: String = "TEXT",
        discoveryId: Int = 0,
        discoveryTitle: String = "",
        discoveryColor: String = ""
    ) = withContext(Dispatchers.IO) {
        val user = getCurrentUser() ?: return@withContext
        db.postDao().insertPost(
            Post(
                authorId = user.id,
                authorName = user.name,
                content = content,
                timestamp = System.currentTimeMillis(),
                type = type,
                discoveryId = discoveryId,
                discoveryTitle = discoveryTitle,
                discoveryColor = discoveryColor
            )
        )
    }

    suspend fun createPoll(question: String, options: String, votes: String) = withContext(Dispatchers.IO) {
        val user = getCurrentUser() ?: return@withContext
        db.postDao().insertPost(
            Post(
                authorId = user.id,
                authorName = user.name,
                content = question,
                timestamp = System.currentTimeMillis(),
                type = "POLL",
                pollOptions = options,
                pollVotes = votes
            )
        )
    }

    suspend fun voteOnPoll(postId: Int, optionIndex: Int) = withContext(Dispatchers.IO) {
        val post = db.postDao().getPostById(postId) ?: return@withContext
        val votes = post.pollVotes.split(",").map { it.toIntOrNull() ?: 0 }.toMutableList()
        votes[optionIndex] = votes[optionIndex] + 1
        db.postDao().updatePollVote(postId, votes.joinToString(","), optionIndex)
    }

    suspend fun toggleLike(post: Post) = withContext(Dispatchers.IO) {
        val newLiked = !post.isLiked
        val newLikes = if (newLiked) post.likes + 1 else post.likes - 1
        db.postDao().updateLikes(post.id, newLikes, newLiked)
    }

    suspend fun sharePost(post: Post) = withContext(Dispatchers.IO) {
        db.postDao().updateShares(post.id, post.shares + 1)
    }

    suspend fun deletePost(postId: Int) = withContext(Dispatchers.IO) {
        db.postDao().deletePost(postId)
    }

    suspend fun getAllChats(): List<Chat> = withContext(Dispatchers.IO) {
        db.chatDao().getAllChats()
    }

    suspend fun createChat(userId: Int, userName: String) = withContext(Dispatchers.IO) {
        db.chatDao().insertChat(
            Chat(
                id = 0,
                name = userName,
                lastMessage = "Начните общение",
                timestamp = System.currentTimeMillis()
            )
        )
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

    suspend fun getDiscoveryById(id: Int): Discovery? = withContext(Dispatchers.IO) {
        db.discoveryDao().getDiscoveryById(id)
    }

    suspend fun getUserContributionToDiscovery(discoveryId: Int): Int = withContext(Dispatchers.IO) {
        db.postDao().getPostsByDiscovery(discoveryId).size
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
                Post(0, 2, "Елена", "Что лучше для изучения Android разработки?", 28, 15, 3, System.currentTimeMillis() - 14400000, "POLL", pollOptions = "Kotlin,Java,Flutter,React Native", pollVotes = "45,23,18,14", userVote = -1),
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
