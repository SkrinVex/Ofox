package su.SkrinVex.ofox.data

import android.content.Context
import android.util.Patterns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.SkrinVex.ofox.data.api.ApiClient
import su.SkrinVex.ofox.data.api.models.*
import java.text.SimpleDateFormat
import java.util.*

class Repository(private val context: Context) {
    private val apiClient = ApiClient.getInstance(context)
    private val db = AppDatabase.getDatabase(context)
    private val prefs = context.getSharedPreferences("ofox_prefs", Context.MODE_PRIVATE)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            apiClient.getToken()
        }
    }

    // Auth
    suspend fun login(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            if (!isValidEmail(email)) return@withContext Result.failure(Exception("Неверный формат email"))
            if (password.length < 3) return@withContext Result.failure(Exception("Пароль слишком короткий"))
            
            val response = apiClient.api.login(LoginRequest(email, password))
            apiClient.saveToken(response.token)
            prefs.edit().putInt("user_id", response.user.id).apply()
            
            val user = response.user.toUser()
            db.userDao().insertUser(user)
            Result.success(user)
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                401 -> Result.failure(Exception("Неверный email или пароль"))
                else -> Result.failure(Exception("Ошибка сервера"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка подключения к серверу"))
        }
    }

    suspend fun register(email: String, password: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!isValidEmail(email)) return@withContext Result.failure(Exception("Неверный формат email"))
            if (password.length < 6) return@withContext Result.failure(Exception("Пароль должен быть минимум 6 символов"))
            if (name.isBlank()) return@withContext Result.failure(Exception("Имя не может быть пустым"))
            
            val response = apiClient.api.register(RegisterRequest(email, password, name))
            Result.success(response.message)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val errorMessage = try {
                org.json.JSONObject(errorBody ?: "{}").getString("error")
            } catch (_: Exception) {
                "Ошибка сервера"
            }
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка подключения к серверу"))
        }
    }

    suspend fun verifyCode(email: String, code: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.verifyCode(VerifyRequest(email, code))
            apiClient.saveToken(response.token)
            prefs.edit().putInt("user_id", response.user.id).apply()
            
            val user = response.user.toUser()
            db.userDao().insertUser(user)
            Result.success(user)
        } catch (e: retrofit2.HttpException) {
            when (e.code()) {
                400 -> Result.failure(Exception("Неверный или истёкший код"))
                else -> Result.failure(Exception("Ошибка сервера"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка подключения к серверу"))
        }
    }

    fun isLoggedIn(): Boolean = prefs.getInt("user_id", -1) != -1
    fun getCurrentUserId(): Int = prefs.getInt("user_id", -1)

    suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId == -1) return@withContext null
        try {
            val response = apiClient.api.getProfile()
            val user = response.toUser()
            db.userDao().insertUser(user)
            user
        } catch (e: Exception) {
            db.userDao().getUser(userId)
        }
    }

    suspend fun getUserById(userId: Int): User? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.getUserById(userId)
            val user = response.toUser()
            db.userDao().insertUser(user)
            user
        } catch (e: Exception) {
            db.userDao().getUser(userId)
        }
    }

    suspend fun getUserBadges(userId: Int): List<su.SkrinVex.ofox.data.api.models.BadgeResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.getUserById(userId)
            response.badges ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateUser(name: String, bio: String): User? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.updateProfile(mapOf("name" to name, "bio" to bio))
            val user = response.toUser()
            db.userDao().updateUser(user)
            user
        } catch (e: Exception) {
            null
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            apiClient.clearToken()
            prefs.edit().clear().apply()
            db.clearAllTables()
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to logout", e)
        }
    }

    suspend fun syncData() = withContext(Dispatchers.IO) {
        try {
            // Синхронизация постов
            val posts = apiClient.api.getPosts().map { it.toPost() }
            db.postDao().getAllPosts().forEach { db.postDao().deletePost(it.id) }
            posts.forEach { db.postDao().insertPost(it) }
            
            // Синхронизация открытий
            val discoveries = apiClient.api.getDiscoveries().map { it.toDiscovery() }
            discoveries.forEach { db.discoveryDao().insertDiscovery(it) }
            
            // Синхронизация чатов
            val chats = apiClient.api.getChats().map { it.toChat() }
            chats.forEach { db.chatDao().insertChat(it) }
        } catch (e: Exception) {
            // Игнорируем ошибки синхронизации
        }
    }

    // Posts with cache and pagination
    suspend fun getAllPosts(limit: Int = 20, offset: Int = 0): List<Post> = withContext(Dispatchers.IO) {
        try {
            val posts = apiClient.api.getPosts(limit = limit, offset = offset).map { it.toPost() }
            if (offset == 0) {
                // Очищаем кэш только при первой загрузке
                db.postDao().deleteAllPosts()
            }
            posts.forEach { 
                try {
                    db.postDao().insertPost(it)
                } catch (e: Exception) {
                    android.util.Log.e("Repository", "Error inserting post ${it.id}", e)
                }
            }
            posts
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Error fetching posts from API", e)
            if (offset == 0) {
                try {
                    db.postDao().getAllPosts()
                } catch (dbError: Exception) {
                    android.util.Log.e("Repository", "Error fetching posts from DB", dbError)
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    suspend fun getPostsByUser(userId: Int): List<Post> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getPosts(userId = userId).map { it.toPost() }
        } catch (e: Exception) {
            db.postDao().getPostsByUser(userId)
        }
    }

    suspend fun getPostsByDiscovery(discoveryId: Int): List<Post> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getPosts(discoveryId = discoveryId).map { it.toPost() }
        } catch (e: Exception) {
            db.postDao().getPostsByDiscovery(discoveryId)
        }
    }

    suspend fun createPost(content: String, type: String = "TEXT", discoveryId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("Repository", "Creating post: content=$content, type=$type, discoveryId=$discoveryId")
            val response = apiClient.api.createPost(PostRequest(content, type, discoveryId = discoveryId))
            android.util.Log.d("Repository", "Post created: $response")
            val post = response.toPost()
            db.postDao().insertPost(post)
            post
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to create post", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun createPoll(question: String, options: List<String>, discoveryId: Int? = null) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("Repository", "Creating poll: question=$question, options=$options, discoveryId=$discoveryId")
            val response = apiClient.api.createPost(PostRequest(question, "POLL", options, discoveryId))
            android.util.Log.d("Repository", "Poll created: $response")
            val post = response.toPost()
            db.postDao().insertPost(post)
            post
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to create poll", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun toggleLike(post: Post): Post? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.toggleLike(post.id)
            val updatedPost = response.toPost()
            db.postDao().updateLikes(updatedPost.id, updatedPost.likes, updatedPost.isLiked)
            updatedPost
        } catch (e: Exception) {
            val newLiked = !post.isLiked
            val newLikes = if (newLiked) post.likes + 1 else post.likes - 1
            db.postDao().updateLikes(post.id, newLikes, newLiked)
            post.copy(likes = newLikes, isLiked = newLiked)
        }
    }

    suspend fun sharePost(post: Post): Post? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.sharePost(post.id)
            val updatedPost = response.toPost()
            db.postDao().updateShares(updatedPost.id, updatedPost.shares)
            updatedPost
        } catch (e: Exception) {
            val newShares = post.shares + 1
            db.postDao().updateShares(post.id, newShares)
            post.copy(shares = newShares)
        }
    }

    suspend fun voteOnPoll(postId: Int, optionIndex: Int): Post? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.voteOnPoll(postId, mapOf("optionIndex" to optionIndex))
            val updatedPost = response.toPost()
            db.postDao().updatePollVote(updatedPost.id, updatedPost.pollVotes, optionIndex)
            updatedPost
        } catch (e: Exception) {
            val post = db.postDao().getPostById(postId) ?: return@withContext null
            val votes = post.pollVotes.split(",").map { it.toIntOrNull() ?: 0 }.toMutableList()
            if (optionIndex < votes.size) {
                votes[optionIndex]++
                val newVotes = votes.joinToString(",")
                db.postDao().updatePollVote(postId, newVotes, optionIndex)
                post.copy(pollVotes = newVotes, userVote = optionIndex)
            } else post
        }
    }

    suspend fun deletePost(postId: Int) = withContext(Dispatchers.IO) {
        try {
            apiClient.api.deletePost(postId)
            db.postDao().deletePost(postId)
        } catch (e: Exception) {
            db.postDao().deletePost(postId)
        }
    }

    // Discoveries
    suspend fun getAllDiscoveries(): List<Discovery> = withContext(Dispatchers.IO) {
        try {
            val discoveries = apiClient.api.getDiscoveries().map { it.toDiscovery() }
            android.util.Log.d("Repository", "Fetched ${discoveries.size} discoveries from API")
            
            // Очищаем старый кэш
            val cached = db.discoveryDao().getAllDiscoveries()
            android.util.Log.d("Repository", "Cached discoveries: ${cached.size}")
            
            // Вставляем новые
            discoveries.forEach { 
                android.util.Log.d("Repository", "Inserting discovery: $it")
                db.discoveryDao().insertDiscovery(it) 
            }
            discoveries
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to fetch discoveries from API", e)
            e.printStackTrace()
            val cached = db.discoveryDao().getAllDiscoveries()
            android.util.Log.d("Repository", "Returning ${cached.size} cached discoveries")
            cached
        }
    }

    suspend fun toggleJoinDiscovery(discovery: Discovery): Discovery? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.toggleJoinDiscovery(discovery.id)
            val updated = response.toDiscovery()
            db.discoveryDao().updateJoinStatus(updated.id, updated.isJoined, updated.participants)
            updated
        } catch (e: Exception) {
            val newJoined = !discovery.isJoined
            val newParticipants = if (newJoined) discovery.participants + 1 else discovery.participants - 1
            db.discoveryDao().updateJoinStatus(discovery.id, newJoined, newParticipants)
            discovery.copy(isJoined = newJoined, participants = newParticipants)
        }
    }

    suspend fun getDiscoveryById(id: Int): Discovery? = withContext(Dispatchers.IO) {
        db.discoveryDao().getDiscoveryById(id)
    }

    suspend fun getUserContributionToDiscovery(discoveryId: Int): Int = withContext(Dispatchers.IO) {
        db.postDao().getPostsByDiscovery(discoveryId).size
    }

    suspend fun createDiscovery(title: String, description: String, category: String, colorHex: String): Discovery? = withContext(Dispatchers.IO) {
        try {
            // Убираем FF (альфа-канал) и # если есть
            var cleanColor = if (colorHex.length == 8) colorHex.substring(2) else colorHex
            if (cleanColor.startsWith("#")) cleanColor = cleanColor.substring(1)
            
            android.util.Log.d("Repository", "Creating discovery: title=$title, category=$category, colorHex=$cleanColor")
            val response = apiClient.api.createDiscovery(mapOf(
                "title" to title,
                "description" to description,
                "category" to category,
                "colorHex" to cleanColor
            ))
            android.util.Log.d("Repository", "Discovery created: $response")
            val discovery = response.toDiscovery()
            db.discoveryDao().insertDiscovery(discovery)
            discovery
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to create discovery", e)
            e.printStackTrace()
            null
        }
    }

    // Chats
    suspend fun getAllChats(): List<Chat> = withContext(Dispatchers.IO) {
        try {
            val chats = apiClient.api.getChats().map { it.toChat() }
            android.util.Log.d("Repository", "Loaded ${chats.size} chats")
            chats.forEach { chat ->
                android.util.Log.d("Repository", "Chat: ${chat.name}, badges: ${chat.userBadges}")
                db.chatDao().insertChat(chat)
            }
            chats
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to load chats from API", e)
            db.chatDao().getAllChats()
        }
    }

    suspend fun getMessages(chatId: Int): List<Message> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            val messages = apiClient.api.getMessages(chatId).map { it.toMessage(currentUserId) }
            messages.forEach { db.messageDao().insertMessage(it) }
            messages
        } catch (e: Exception) {
            db.messageDao().getMessages(chatId)
        }
    }

    suspend fun sendMessage(chatId: Int, text: String): Message? = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            val response = apiClient.api.sendMessage(chatId, SendMessageRequest(text))
            val message = response.toMessage(currentUserId)
            db.messageDao().insertMessage(message)
            db.chatDao().updateChat(chatId, text, message.timestamp)
            message
        } catch (e: Exception) {
            val timestamp = System.currentTimeMillis()
            val message = Message(0, chatId, text, timestamp, true)
            db.messageDao().insertMessage(message)
            db.chatDao().updateChat(chatId, text, timestamp)
            message
        }
    }

    // Subscriptions
    suspend fun isSubscribed(userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.isSubscribed(userId)
            response["subscribed"] ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun toggleSubscription(userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.toggleSubscription(userId)
            response["subscribed"] ?: false
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to toggle subscription", e)
            false
        }
    }

    suspend fun getMutualFriends(): List<User> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getMutualFriends().map { it.toUser() }
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to get mutual friends", e)
            emptyList()
        }
    }

    suspend fun getSubscribersCount(userId: Int): Int = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.getSubscribersCount(userId)
            response["count"] ?: 0
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to get subscribers count", e)
            0
        }
    }

    suspend fun getSubscriptions(): List<User> = withContext(Dispatchers.IO) {
        emptyList()
    }
    
    suspend fun getAppInfo(): String = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getAppInfo().content
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to load app info", e)
            "# Информация недоступна\n\nНе удалось загрузить информацию о приложении. Проверьте подключение к интернету."
        }
    }

    suspend fun createChat(userId: Int, userName: String): Long? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("Repository", "Creating chat with user $userId ($userName)")
            val response = apiClient.api.createChat(CreateChatRequest(userName, listOf(userId)))
            android.util.Log.d("Repository", "Chat created: $response")
            
            val userBadges = response.other_user_badges?.let { badges ->
                org.json.JSONArray(badges.map { badge ->
                    org.json.JSONObject().apply {
                        put("badge_type", badge.badge_type)
                        put("description", badge.description)
                    }
                }).toString()
            } ?: ""
            
            val chat = Chat(
                id = response.id,
                name = response.other_user_name,
                lastMessage = response.last_message,
                timestamp = parseTimestamp(response.updated_at),
                userId = response.other_user_id,
                userBadges = userBadges
            )
            db.chatDao().insertChat(chat)
            response.id.toLong()
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to create chat", e)
            e.printStackTrace()
            null
        }
    }

    // Helpers
    private fun isValidEmail(email: String): Boolean = Patterns.EMAIL_ADDRESS.matcher(email).matches()

    private fun UserResponse.toUser() = User(id, email, "", name, bio)

    private fun PostResponse.toPost() = Post(
        id = id,
        authorId = author_id,
        authorName = author_name ?: "Unknown",
        content = content ?: "",
        likes = likes ?: 0,
        shares = shares ?: 0,
        timestamp = created_timestamp ?: parseTimestamp(created_at),
        type = type ?: "TEXT",
        isLiked = is_liked ?: false,
        pollOptions = poll_options?.joinToString(",") ?: "",
        pollVotes = poll_votes?.joinToString(",") ?: "",
        userVote = user_vote ?: -1,
        discoveryId = discovery_id ?: 0,
        discoveryTitle = discovery_title ?: "",
        discoveryColor = if (discovery_color.isNullOrBlank()) "" else if (discovery_color.startsWith("#")) discovery_color else "#$discovery_color",
        authorBadges = author_badges?.let { badges ->
            org.json.JSONArray(badges.map { 
                org.json.JSONObject().apply {
                    put("badge_type", it.badge_type)
                    put("description", it.description)
                }
            }).toString()
        } ?: ""
    )

    private fun DiscoveryResponse.toDiscovery(): Discovery {
        val cleanColor = color_hex?.let { 
            if (it.startsWith("#")) it else "#$it" 
        } ?: "#4CAF50"
        
        val discovery = Discovery(
            id = id,
            title = title,
            description = description,
            category = category,
            participants = participants,
            colorHex = cleanColor,
            isJoined = is_joined,
            creatorName = creator_name ?: "Unknown"
        )
        android.util.Log.d("Repository", "Mapped discovery: $discovery")
        return discovery
    }

    private fun ChatResponse.toChat(): Chat {
        android.util.Log.d("Repository", "Converting ChatResponse: id=$id, name=$name, other_user_name=$other_user_name, badges=${other_user_badges?.size ?: 0}")
        return Chat(
            id = id,
            name = other_user_name,
            lastMessage = last_message,
            timestamp = parseTimestamp(updated_at),
            userId = other_user_id,
            userBadges = other_user_badges?.let { badges ->
                val json = org.json.JSONArray(badges.map { badge ->
                    org.json.JSONObject().apply {
                        put("badge_type", badge.badge_type)
                        put("description", badge.description)
                    }
                }).toString()
                android.util.Log.d("Repository", "Badges JSON: $json")
                json
            } ?: ""
        )
    }

    private fun MessageResponse.toMessage(currentUserId: Int) = Message(
        id = id,
        chatId = chat_id,
        text = text,
        timestamp = parseTimestamp(created_at),
        isFromMe = sender_id == currentUserId,
        senderId = sender_id
    )

    private fun parseTimestamp(dateStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            format.timeZone = java.util.TimeZone.getTimeZone("UTC")
            format.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to parse timestamp: $dateStr", e)
            System.currentTimeMillis()
        }
    }
}
