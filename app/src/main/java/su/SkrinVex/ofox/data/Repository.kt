package su.SkrinVex.ofox.data

import android.content.Context
import android.util.Patterns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import su.SkrinVex.ofox.data.api.ApiClient
import su.SkrinVex.ofox.data.api.models.*
import java.text.SimpleDateFormat
import java.util.*

class Repository(private val context: Context) {
    private val apiClient = ApiClient.getInstance(context)
    private val db = AppDatabase.getDatabase(context)
    private val prefs = context.getSharedPreferences("ofox_prefs", Context.MODE_PRIVATE)
    private val wsClient = su.SkrinVex.ofox.data.api.WebSocketClient.getInstance(context)

    val chatsFlow: Flow<List<Chat>> = db.chatDao().getAllChatsFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            android.util.Log.d("Repository", "Init: getting token")
            apiClient.getToken()
            val loggedIn = isLoggedIn()
            android.util.Log.d("Repository", "Init: isLoggedIn=$loggedIn")
            if (loggedIn) {
                android.util.Log.d("Repository", "Init: connecting WebSocket")
                wsClient.connect()
            }
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
            su.SkrinVex.ofox.account.addOfoxAccount(context, user.name, response.token)
            Result.success(user)
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string()
            android.util.Log.e("Repository", "login HTTP ${e.code()}: $body")
            when (e.code()) {
                401 -> Result.failure(Exception("Неверный email или пароль"))
                403 -> Result.failure(Exception("Email не подтверждён"))
                else -> Result.failure(Exception("Ошибка сервера (${e.code()})"))
            }
        } catch (e: Exception) {
            android.util.Log.e("Repository", "login error", e)
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
            su.SkrinVex.ofox.account.addOfoxAccount(context, user.name, response.token)
            Result.success(user)
        } catch (e: retrofit2.HttpException) {
            val body = e.response()?.errorBody()?.string()
            android.util.Log.e("Repository", "verifyCode HTTP ${e.code()}: $body")
            when (e.code()) {
                400 -> Result.failure(Exception("Неверный или истёкший код"))
                else -> Result.failure(Exception("Ошибка сервера (${e.code()})"))
            }
        } catch (e: Exception) {
            android.util.Log.e("Repository", "verifyCode error", e)
            Result.failure(Exception("Ошибка подключения к серверу"))
        }
    }

    fun isLoggedIn(): Boolean = prefs.getInt("user_id", -1) != -1
    fun getCurrentUserId(): Int = prefs.getInt("user_id", -1)

    fun getUserFlow(userId: Int): Flow<User?> = db.userDao().getUserFlow(userId)

    suspend fun refreshUser(userId: Int) = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.getUserById(userId)
            val user = response.toUser()
            db.userDao().insertUser(user)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to refresh user $userId", e)
        }
    }

    suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        if (userId == -1) return@withContext null
        val cached = db.userDao().getUser(userId)
        // Обновляем в фоне
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiClient.api.getProfile()
                db.userDao().insertUser(response.toUser())
            } catch (_: Exception) {}
        }
        cached
    }

    suspend fun getUserById(userId: Int): User? = withContext(Dispatchers.IO) {
        val cached = db.userDao().getUser(userId)
        // Обновляем в фоне
        CoroutineScope(Dispatchers.IO).launch {
            refreshUser(userId)
        }
        cached
    }

    suspend fun getUserBadges(userId: Int): List<su.SkrinVex.ofox.data.api.models.BadgeResponse> = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.getUserById(userId)
            response.badges ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateUser(name: String, bio: String, socialLinks: String? = null, bannerColor: String? = null): User? = withContext(Dispatchers.IO) {
        try {
            val params = mutableMapOf("name" to name, "bio" to bio)
            socialLinks?.let { params["socialLinks"] = it }
            bannerColor?.let { params["bannerColor"] = it }
            
            val response = apiClient.api.updateProfile(params)
            val user = response.toUser()
            db.userDao().updateUser(user)
            user
        } catch (e: Exception) {
            android.util.Log.e("Repository", "updateUser error", e)
            null
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        try {
            su.SkrinVex.ofox.data.api.WebSocketClient.getInstance(context).disconnect()
            apiClient.clearToken()
            prefs.edit().clear().apply()
            db.clearAllTables()
            su.SkrinVex.ofox.account.removeOfoxAccount(context)
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
            val posts = apiClient.api.getPosts(limit = limit, offset = offset).map { it.toPost() }.distinctBy { it.id }
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
                    db.postDao().getAllPosts().distinctBy { it.id }
                } catch (dbError: Exception) {
                    android.util.Log.e("Repository", "Error fetching posts from DB", dbError)
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    }

    suspend fun getPostsByUser(userId: Int, limit: Int = 20, offset: Int = 0): List<Post> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getPosts(userId = userId, limit = limit, offset = offset).map { it.toPost() }
        } catch (e: Exception) {
            if (offset == 0) db.postDao().getPostsByUser(userId) else emptyList()
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
            val newLikes = response.likes ?: post.likes
            val newLiked = response.is_liked ?: !post.isLiked
            android.util.Log.d("Repository", "toggleLike response: likes=$newLikes, isLiked=$newLiked")
            db.postDao().updateLikes(post.id, newLikes, newLiked)
            post.copy(likes = newLikes, isLiked = newLiked)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "toggleLike error", e)
            val newLiked = !post.isLiked
            val newLikes = if (newLiked) post.likes + 1 else post.likes - 1
            db.postDao().updateLikes(post.id, newLikes, newLiked)
            post.copy(likes = newLikes, isLiked = newLiked)
        }
    }

    suspend fun sharePost(post: Post): Post? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.sharePost(post.id)
            val newShares = response.shares ?: post.shares + 1
            db.postDao().updateShares(post.id, newShares)
            post.copy(shares = newShares)
        } catch (e: Exception) {
            val newShares = post.shares + 1
            db.postDao().updateShares(post.id, newShares)
            post.copy(shares = newShares)
        }
    }

    suspend fun voteOnPoll(postId: Int, optionIndex: Int): Post? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.voteOnPoll(postId, mapOf("optionIndex" to optionIndex))
            val post = db.postDao().getPostById(postId) ?: return@withContext null
            val newVotes = response.poll_votes?.joinToString(",") ?: post.pollVotes
            val newUserVote = response.user_vote ?: optionIndex
            db.postDao().updatePollVote(postId, newVotes, newUserVote)
            post.copy(pollVotes = newVotes, userVote = newUserVote)
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

    suspend fun getPostById(postId: Int): Post? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.getPostById(postId)
            response.toPost()
        } catch (e: Exception) {
            android.util.Log.e("Repository", "getPostById error", e)
            null
        }
    }

    // Comments
    suspend fun getComments(postId: Int): List<CommentResponse> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getComments(postId)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "getComments error", e)
            emptyList()
        }
    }

    suspend fun createComment(postId: Int, content: String, replyToId: Int? = null): CommentResponse? = withContext(Dispatchers.IO) {
        try {
            apiClient.api.createComment(postId, CreateCommentRequest(content, replyToId))
        } catch (e: Exception) {
            android.util.Log.e("Repository", "createComment error", e)
            null
        }
    }

    suspend fun reportPost(postId: Int, reason: String) = withContext(Dispatchers.IO) {
        try {
            apiClient.api.reportPost(postId, su.SkrinVex.ofox.data.api.models.ReportRequest(reason))
            android.util.Log.d("Repository", "Reported post $postId: $reason")
        } catch (e: Exception) {
            android.util.Log.e("Repository", "reportPost error", e)
        }
    }

    suspend fun reportComment(commentId: Int, reason: String) = withContext(Dispatchers.IO) {
        try {
            apiClient.api.reportComment(commentId, su.SkrinVex.ofox.data.api.models.ReportRequest(reason))
        } catch (e: Exception) {
            android.util.Log.e("Repository", "reportComment error", e)
        }
    }

    fun hideAuthor(authorId: Int) {
        val hidden = getHiddenAuthorIds().toMutableSet()
        hidden.add(authorId)
        prefs.edit().putStringSet("hidden_authors", hidden.map { it.toString() }.toSet()).apply()
        android.util.Log.d("Repository", "Hidden author $authorId")
    }

    fun unhideAuthor(authorId: Int) {
        val hidden = getHiddenAuthorIds().toMutableSet()
        hidden.remove(authorId)
        prefs.edit().putStringSet("hidden_authors", hidden.map { it.toString() }.toSet()).apply()
        android.util.Log.d("Repository", "Unhidden author $authorId")
    }

    fun getHiddenAuthorIds(): Set<Int> {
        return prefs.getStringSet("hidden_authors", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    suspend fun uploadPostImages(postId: Int, images: List<Pair<ByteArray, String>>): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("Repository", "uploadPostImages: postId=$postId count=${images.size}")
            val parts = images.mapIndexed { i, (bytes, mimeType) ->
                val ext = when (mimeType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> "jpg"
                }
                android.util.Log.d("Repository", "  part[$i]: mimeType=$mimeType size=${bytes.size} filename=image_$i.$ext")
                val body = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                okhttp3.MultipartBody.Part.createFormData("images", "image_$i.$ext", body)
            }
            if (parts.isEmpty()) {
                android.util.Log.e("Repository", "uploadPostImages: parts is empty!")
                return@withContext Result.failure(Exception("Не удалось прочитать файлы"))
            }
            android.util.Log.d("Repository", "uploadPostImages: sending ${parts.size} parts to server...")
            val response = apiClient.api.uploadPostImages(postId, parts)
            android.util.Log.d("Repository", "uploadPostImages: success, urls=${response.images}")
            Result.success(response.images)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            android.util.Log.e("Repository", "uploadPostImages HTTP ${e.code()}: $errorBody", e)
            val msg = try { org.json.JSONObject(errorBody ?: "{}").getString("error") } catch (_: Exception) { "Ошибка загрузки фото (${e.code()})" }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            android.util.Log.e("Repository", "uploadPostImages error", e)
            Result.failure(Exception("Ошибка загрузки фото: ${e.message}"))
        }
    }

    suspend fun deleteComment(commentId: Int) = withContext(Dispatchers.IO) {
        try {
            apiClient.api.deleteComment(commentId)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "deleteComment error", e)
        }
    }

    suspend fun pinComment(postId: Int, commentId: Int) = withContext(Dispatchers.IO) {
        try {
            apiClient.api.pinComment(postId, commentId)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "pinComment error", e)
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

    suspend fun deleteDiscovery(discoveryId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            apiClient.api.deleteDiscovery(discoveryId)
            db.discoveryDao().deleteDiscovery(discoveryId)
            true
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to delete discovery", e)
            false
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
        val currentUserId = getCurrentUserId()
        db.postDao().getPostsByDiscovery(discoveryId).count { it.authorId == currentUserId }
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
            db.chatDao().deleteAllChats()
            chats.forEach { db.chatDao().insertChat(it) }
            chats
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to load chats from API", e)
            db.chatDao().getAllChats()
        }
    }

    suspend fun getChatById(chatId: Int): Chat? = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getChatById(chatId).toChat()
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to get chat $chatId", e)
            db.chatDao().getChatById(chatId)
        }
    }

    suspend fun getMessages(chatId: Int, before: Int? = null): List<Message> = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            val messages = apiClient.api.getMessages(chatId, before = before).map { it.toMessage(currentUserId) }
            // Заполняем кэш пользователей из сообщений
            messages.forEach { msg ->
                if (msg.senderId != 0 && msg.senderName.isNotBlank()) {
                    su.SkrinVex.ofox.data.UserCache.put(msg.senderId, msg.senderName, msg.senderAvatarUrl.takeIf { it.isNotBlank() })
                }
            }
            if (before == null) {
                db.messageDao().deleteMessagesByChat(chatId)
                messages.forEach { db.messageDao().insertMessage(it) }
                apiClient.api.markChatAsRead(chatId)
                db.chatDao().resetUnreadCount(chatId)
            }
            messages
        } catch (e: Exception) {
            if (before == null) db.messageDao().getMessages(chatId) else emptyList()
        }
    }

    suspend fun getOnlineUserIds(): Set<Int> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getOnlineUsers()["onlineUserIds"]?.toSet() ?: emptySet()
        } catch (_: Exception) { emptySet() }
    }

    suspend fun sendMessage(chatId: Int, text: String, replyToId: Int? = null): Message? = withContext(Dispatchers.IO) {
        try {
            val currentUserId = getCurrentUserId()
            val response = apiClient.api.sendMessage(chatId, SendMessageRequest(text, replyToId = replyToId))
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

    suspend fun isSubscribedToMe(userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("Repository", "Calling isSubscribedToMe for userId: $userId")
            val response = apiClient.api.isSubscribedToMe(userId)
            val result = response["subscribed"] ?: false
            android.util.Log.d("Repository", "isSubscribedToMe($userId): $result, response: $response")
            result
        } catch (e: Exception) {
            android.util.Log.e("Repository", "isSubscribedToMe($userId) error: ${e.message}", e)
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

    suspend fun getCommentRules(): String = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getCommentRules().content
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to load comment rules", e)
            "# Правила недоступны\n\nНе удалось загрузить правила комментариев. Проверьте подключение к интернету."
        }
    }

    suspend fun getOfoxRules(): String = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getOfoxRules().content
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to load ofox rules", e)
            "# Правила недоступны\n\nНе удалось загрузить правила Ofox. Проверьте подключение к интернету."
        }
    }

    suspend fun getPrivacyPolicy(): String = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getPrivacyPolicy().content
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to load privacy policy", e)
            "# Политика недоступна\n\nНе удалось загрузить политику конфиденциальности. Проверьте подключение к интернету."
        }
    }

    suspend fun getWarnings(): List<su.SkrinVex.ofox.data.api.models.WarningResponse> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId() ?: return@withContext emptyList()
            apiClient.api.getWarnings(userId)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to load warnings", e)
            emptyList()
        }
    }

    suspend fun markWarningDelivered(warningId: Int) = withContext(Dispatchers.IO) {
        try {
            apiClient.api.markWarningDelivered(warningId)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to mark warning delivered", e)
        }
    }

    suspend fun getBan(): su.SkrinVex.ofox.data.api.models.BanResponse? = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId() ?: return@withContext null
            val response = apiClient.api.getBan(userId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getDeletedContent(): List<su.SkrinVex.ofox.data.api.models.DeletedContentResponse> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("Repository", "Fetching deleted content...")
            val result = apiClient.api.getDeletedContent()
            android.util.Log.d("Repository", "Deleted content: ${result.size} items")
            result
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to load deleted content", e)
            emptyList()
        }
    }

    suspend fun markContentViewed(contentId: Int) = withContext(Dispatchers.IO) {
        try {
            apiClient.api.markContentViewed(contentId)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to mark content viewed", e)
        }
    }

    suspend fun uploadAvatar(imageBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val part = okhttp3.MultipartBody.Part.createFormData(
                "avatar", "avatar.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            val response = apiClient.api.uploadAvatar(part)
            val userId = getCurrentUserId()
            if (userId != -1) {
                val cached = db.userDao().getUser(userId)
                if (cached != null) db.userDao().insertUser(cached.copy(avatarUrl = response.avatar_url))
            }
            Result.success(response.avatar_url)
        } catch (e: retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val msg = try { org.json.JSONObject(errorBody ?: "{}").getString("error") } catch (_: Exception) {
                when (e.code()) {
                    413 -> "Файл слишком большой. Максимум 4 МБ"
                    415 -> "Неподдерживаемый формат изображения"
                    else -> "Ошибка сервера (${e.code()})"
                }
            }
            android.util.Log.e("Repository", "uploadAvatar error", e)
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            android.util.Log.e("Repository", "uploadAvatar error", e)
            Result.failure(Exception("Ошибка загрузки: ${e.message}"))
        }
    }

    suspend fun deleteAvatar(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.deleteAvatar()
            val userId = getCurrentUserId()
            if (userId != -1) {
                val cached = db.userDao().getUser(userId)
                if (cached != null) db.userDao().insertUser(cached.copy(avatarUrl = ""))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadBanner(imageBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        try {
            val part = okhttp3.MultipartBody.Part.createFormData(
                "banner", "banner.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            val response = apiClient.api.uploadBanner(part)
            val userId = getCurrentUserId()
            if (userId != -1) {
                val cached = db.userDao().getUser(userId)
                if (cached != null) db.userDao().insertUser(cached.copy(bannerImageUrl = response.banner_image_url))
            }
            Result.success(response.banner_image_url)
        } catch (e: retrofit2.HttpException) {
            val msg = try { org.json.JSONObject(e.response()?.errorBody()?.string() ?: "{}").getString("error") } catch (_: Exception) { "Ошибка загрузки баннера (${e.code()})" }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка загрузки баннера: ${e.message}"))
        }
    }

    suspend fun deleteBanner(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.deleteBanner()
            val userId = getCurrentUserId()
            if (userId != -1) {
                val cached = db.userDao().getUser(userId)
                if (cached != null) db.userDao().insertUser(cached.copy(bannerImageUrl = ""))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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
    private fun isValidEmail(email: String): Boolean = email.contains("@") && email.substringBefore("@").isNotEmpty() && email.substringAfter("@").contains(".")

    private fun UserResponse.toUser() = User(
        id = id, 
        email = email, 
        password = "", 
        name = name, 
        bio = bio,
        socialLinks = social_links ?: "",
        bannerColor = banner_color ?: "#4CAF50",
        bannerImageUrl = banner_image_url ?: "",
        avatarUrl = avatar_url ?: ""
    )

    private fun PostResponse.toPost() = Post(
        id = id,
        authorId = author_id,
        authorName = author_name ?: "Unknown",
        content = content ?: "",
        likes = likes ?: 0,
        comments = comments ?: 0,
        shares = shares ?: 0,
        timestamp = created_timestamp ?: parseTimestamp(created_at),
        type = type ?: "TEXT",
        isLiked = is_liked ?: false,
        pollOptions = poll_options?.joinToString("|||") ?: "",
        pollVotes = poll_votes?.joinToString(",") ?: "",
        userVote = user_vote ?: -1,
        discoveryId = discovery_id ?: 0,
        discoveryTitle = discovery_title ?: "",
        discoveryColor = if (discovery_color.isNullOrBlank()) "" else if (discovery_color.startsWith("#")) discovery_color else "#$discovery_color",
        isDiscoveryPost = is_discovery_post ?: false,
        authorBadges = author_badges?.let { badges ->
            org.json.JSONArray(badges.map { 
                org.json.JSONObject().apply {
                    put("badge_type", it.badge_type)
                    put("description", it.description)
                }
            }).toString()
        } ?: "",
        authorAvatarUrl = author_avatar_url ?: "",
        images = images?.joinToString("|||") ?: ""
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
            creatorName = creator_name ?: "Unknown",
            creatorId = creator_id ?: 0,
            createdAt = created_at?.let { parseTimestamp(it) } ?: System.currentTimeMillis()
        )
        android.util.Log.d("Repository", "Mapped discovery: $discovery")
        return discovery
    }

    private fun ChatResponse.toChat(): Chat {
        return Chat(
            id = id,
            name = if (discovery_id != null) (discovery_title ?: name) else other_user_name,
            lastMessage = last_message ?: "",
            timestamp = parseTimestamp(updated_at),
            userId = other_user_id,
            userBadges = other_user_badges?.let { badges ->
                org.json.JSONArray(badges.map { badge ->
                    org.json.JSONObject().apply {
                        put("badge_type", badge.badge_type)
                        put("description", badge.description)
                    }
                }).toString()
            } ?: "",
            unreadCount = unread_count,
            userAvatarUrl = other_user_avatar ?: "",
            discoveryId = discovery_id ?: 0,
            discoveryTitle = discovery_title ?: ""
        )
    }

    private fun MessageResponse.toMessage(currentUserId: Int) = Message(
        id = id,
        chatId = chat_id,
        text = text,
        timestamp = parseTimestamp(created_at),
        isFromMe = sender_id == currentUserId,
        senderId = sender_id,
        senderName = sender_name,
        senderAvatarUrl = sender_avatar_url ?: "",
        messageType = message_type,
        replyToId = reply_to_id,
        replyToText = reply_to_text,
        replyToSenderName = reply_to_sender_name
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

    suspend fun getCommentNotifications() = withContext(Dispatchers.IO) {
        try { apiClient.api.getCommentNotifications() } catch (e: Exception) { emptyList() }
    }

    suspend fun getNotificationsUnreadCount() = withContext(Dispatchers.IO) {
        try { apiClient.api.getNotificationsUnreadCount()["count"] ?: 0 } catch (e: Exception) { 0 }
    }

    suspend fun markNotificationsRead() = withContext(Dispatchers.IO) {
        try { apiClient.api.markNotificationsRead() } catch (_: Exception) {}
    }

    suspend fun markNotificationsReadByPost(postId: Int) = withContext(Dispatchers.IO) {
        try { apiClient.api.markNotificationsReadByPost(postId) } catch (_: Exception) {}
    }

    suspend fun deleteNotifications(ids: List<Int>, types: List<String>) = withContext(Dispatchers.IO) {
        try { apiClient.api.deleteNotifications(DeleteNotificationsRequest(ids, types)) } catch (e: Exception) {
            android.util.Log.e("Repository", "deleteNotifications error", e)
        }
    }

    suspend fun getStickers() = withContext(Dispatchers.IO) {
        try { apiClient.api.getUserStickers() } catch (e: Exception) {
            android.util.Log.e("Repository", "getStickers error", e)
            su.SkrinVex.ofox.data.api.models.UserStickersResponse(emptyList(), emptyList())
        }
    }

    suspend fun uploadSticker(uri: android.net.Uri, context: android.content.Context, packId: Int? = null, emoji: String = "") = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = stream.readBytes()
            stream.close()
            val mimeType = context.contentResolver.getType(uri) ?: "image/png"
            val body = okhttp3.RequestBody.create(mimeType.toMediaTypeOrNull(), bytes)
            val part = okhttp3.MultipartBody.Part.createFormData("sticker", "sticker.png", body)
            val packPart = packId?.let {
                okhttp3.MultipartBody.Part.createFormData("packId", it.toString())
            }
            val emojiPart = if (emoji.isNotBlank())
                okhttp3.MultipartBody.Part.createFormData("emoji", emoji) else null
            apiClient.api.uploadSticker(part, packPart, emojiPart)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "uploadSticker error", e)
            null
        }
    }

    suspend fun deleteSticker(id: Int) = withContext(Dispatchers.IO) {
        try { apiClient.api.deleteSticker(id) } catch (_: Exception) {}
    }

    suspend fun markStickerUsed(stickerId: Int) = withContext(Dispatchers.IO) {
        try { apiClient.api.markStickerUsed(stickerId) } catch (_: Exception) {}
    }

    suspend fun getPublicPacks() = withContext(Dispatchers.IO) {
        try { apiClient.api.getPublicPacks() } catch (e: Exception) { emptyList() }
    }

    suspend fun getMyPacks() = withContext(Dispatchers.IO) {
        try { apiClient.api.getMyPacks() } catch (e: Exception) { emptyList<StickerPack>() }
    }

    suspend fun getPackBySlug(slug: String) = withContext(Dispatchers.IO) {
        try { apiClient.api.getPackBySlug(slug) } catch (e: Exception) { null }
    }

    suspend fun getPackByStickerUrl(url: String) = withContext(Dispatchers.IO) {
        try { apiClient.api.getPackByStickerUrl(url) } catch (e: Exception) { null }
    }

    suspend fun createPack(name: String, description: String = "", isPublic: Boolean = false) = withContext(Dispatchers.IO) {
        try { apiClient.api.createPack(su.SkrinVex.ofox.data.api.models.CreatePackRequest(name, description, isPublic)) }
        catch (e: Exception) { android.util.Log.e("Repository", "createPack error", e); null }
    }

    suspend fun deletePack(id: Int) = withContext(Dispatchers.IO) {
        try { apiClient.api.deletePack(id) } catch (_: Exception) {}
    }

    suspend fun installPack(packId: Int) = withContext(Dispatchers.IO) {
        try { apiClient.api.installPack(packId) } catch (_: Exception) {}
    }

    suspend fun uninstallPack(packId: Int) = withContext(Dispatchers.IO) {
        try { apiClient.api.uninstallPack(packId) } catch (_: Exception) {}
    }

    suspend fun sendTyping(chatId: Int) = withContext(Dispatchers.IO) {
        try { apiClient.api.sendTyping(chatId) } catch (_: Exception) {}
    }

    suspend fun sendSticker(chatId: Int, stickerUrl: String): Message? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.sendMessage(chatId, SendMessageRequest(stickerUrl, "sticker"))
            response.toMessage(getCurrentUserId())
        } catch (e: Exception) {
            android.util.Log.e("Repository", "sendSticker error", e)
            null
        }
    }

    // Discovery features
    suspend fun getOrCreateDiscoveryChat(discoveryId: Int): Int? = withContext(Dispatchers.IO) {
        try {
            val response = apiClient.api.getOrCreateDiscoveryChat(discoveryId)
            response.id
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to get discovery chat", e)
            null
        }
    }

    suspend fun createAchievement(
        discoveryId: Int,
        title: String,
        description: String,
        icon: String,
        rewardType: String,
        rewardValue: String
    ): AchievementResponse? = withContext(Dispatchers.IO) {
        try {
            apiClient.api.createAchievement(
                discoveryId,
                CreateAchievementRequest(title, description, icon, rewardType, rewardValue)
            )
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to create achievement", e)
            null
        }
    }

    suspend fun getAchievements(discoveryId: Int): List<AchievementResponse> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getAchievements(discoveryId)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to get achievements", e)
            emptyList()
        }
    }

    suspend fun grantAchievement(achievementId: Int, userId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            apiClient.api.grantAchievement(achievementId, GrantAchievementRequest(userId))
            true
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to grant achievement", e)
            false
        }
    }

    suspend fun getUserAchievements(userId: Int): List<AchievementResponse> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getUserAchievements(userId)
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to get user achievements", e)
            emptyList()
        }
    }

    // Discovery chat messages
    suspend fun getDiscoveryChatMessages(chatId: Int, before: Int? = null): List<Message> = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getDiscoveryChatMessages(chatId, before = before).map { it.toMessage(getCurrentUserId()) }
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to get discovery chat messages", e)
            emptyList()
        }
    }

    suspend fun sendDiscoveryChatMessage(chatId: Int, text: String): Message? = withContext(Dispatchers.IO) {
        try {
            apiClient.api.sendDiscoveryChatMessage(chatId, SendMessageRequest(text)).toMessage(getCurrentUserId())
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to send discovery chat message", e)
            null
        }
    }

    // Notification settings
    suspend fun getNotificationSettings(): NotificationSettingsResponse? = withContext(Dispatchers.IO) {
        try {
            apiClient.api.getNotificationSettings()
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to get notification settings", e)
            null
        }
    }

    suspend fun updateNotificationSettings(
        notifyPostComments: Boolean,
        notifyFriendPosts: Boolean,
        notifyChats: Boolean,
        notifyDiscoveryChats: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            apiClient.api.updateNotificationSettings(
                UpdateNotificationSettingsRequest(notifyPostComments, notifyFriendPosts, notifyChats, notifyDiscoveryChats)
            )
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to update notification settings", e)
        }
    }

    suspend fun toggleChatMute(chatId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = apiClient.api.toggleChatMute(chatId)
            result["muted"] ?: false
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to toggle chat mute", e)
            false
        }
    }

    suspend fun toggleFriendMute(friendId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = apiClient.api.toggleFriendMute(friendId)
            result["muted"] ?: false
        } catch (e: Exception) {
            android.util.Log.e("Repository", "Failed to toggle friend mute", e)
            false
        }
    }
}
