package su.SkrinVex.ofox.data.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketClient(private val context: Context) {
    private var webSocket: WebSocket? = null
    private val apiClient = ApiClient.getInstance(context)
    private var shouldReconnect = true
    
    private val _events = MutableStateFlow<WSEvent?>(null)
    val events: StateFlow<WSEvent?> = _events
    
    suspend fun connect() {
        val token = apiClient.getToken() ?: return
        
        val client = OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url("${ApiConfig.WS_URL}?token=$token")
            .build()
        
        val wsUrl = "${ApiConfig.WS_URL}?token=$token"
        Log.d("WebSocket", "Connecting to: $wsUrl")
        
        shouldReconnect = true
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected successfully")
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Message received: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.getString("type")
                    
                    when (type) {
                        "badge_update" -> {
                            val userId = json.getInt("userId")
                            val badgesArray = json.getJSONArray("badges")
                            val badges = (0 until badgesArray.length()).map { i ->
                                val obj = badgesArray.getJSONObject(i)
                                Badge(obj.getString("badge_type"), obj.getString("description"))
                            }
                            _events.value = WSEvent.BadgeUpdate(userId, badges)
                        }
                        "post_update" -> {
                            val postId = json.getInt("postId")
                            val likes = json.optInt("likes", -1)
                            val shares = json.optInt("shares", -1)
                            _events.value = WSEvent.PostUpdate(postId, likes, shares)
                        }
                        "new_post" -> {
                            val postId = json.getInt("postId")
                            _events.value = WSEvent.NewPost(postId)
                        }
                        "new_message" -> {
                            val chatId = json.getInt("chatId")
                            val message = json.getString("message")
                            val timestamp = json.getLong("timestamp")
                            _events.value = WSEvent.NewMessage(
                                chatId, message, timestamp,
                                senderId = json.optInt("senderId", 0),
                                senderName = json.optString("senderName", ""),
                                senderAvatarUrl = json.optString("senderAvatarUrl").takeIf { it.isNotBlank() },
                                messageType = json.optString("messageType", "text"),
                                replyToId = json.optInt("replyToId", -1).takeIf { it != -1 },
                                replyToText = json.optString("replyToText").takeIf { it.isNotBlank() },
                                replyToSenderName = json.optString("replyToSenderName").takeIf { it.isNotBlank() }
                            )
                        }
                        "chat_update" -> {
                            val chatId = json.getInt("chatId")
                            val lastMessage = json.getString("lastMessage")
                            val timestamp = json.getLong("timestamp")
                            _events.value = WSEvent.ChatUpdate(chatId, lastMessage, timestamp)
                            Log.d("WebSocket", "Chat update event: chatId=$chatId")
                        }
                        "NEW_COMMENT" -> {
                            val postId = json.getInt("postId")
                            val commentObj = json.getJSONObject("comment")
                            val badgesArray = commentObj.optJSONArray("author_badges")
                            val badges = if (badgesArray != null) {
                                (0 until badgesArray.length()).map { i ->
                                    val obj = badgesArray.getJSONObject(i)
                                    su.SkrinVex.ofox.data.api.models.BadgeResponse(
                                        badge_type = obj.getString("badge_type"),
                                        description = obj.getString("description")
                                    )
                                }
                            } else emptyList()
                            
                            val comment = su.SkrinVex.ofox.data.api.models.CommentResponse(
                                id = commentObj.getInt("id"),
                                post_id = commentObj.getInt("post_id"),
                                author_id = commentObj.getInt("author_id"),
                                author_name = commentObj.getString("author_name"),
                                author_badges = badges,
                                author_avatar_url = commentObj.optString("author_avatar_url").takeIf { it.isNotBlank() },
                                content = commentObj.getString("content"),
                                created_at = commentObj.getString("created_at"),
                                reply_to_id = commentObj.optInt("reply_to_id", -1).takeIf { it != -1 },
                                reply_to_author_name = commentObj.optString("reply_to_author_name").takeIf { it.isNotBlank() && it != "null" },
                                is_pinned = commentObj.optBoolean("is_pinned", false)
                            )
                            _events.value = WSEvent.NewComment(postId, comment)
                            Log.d("WebSocket", "New comment event: postId=$postId")
                        }
                        "DELETE_COMMENT" -> {
                            val postId = json.getInt("postId")
                            val commentId = json.getInt("commentId")
                            _events.value = WSEvent.DeleteComment(postId, commentId)
                            Log.d("WebSocket", "Delete comment event: postId=$postId, commentId=$commentId")
                        }
                        "typing" -> {
                            val chatId = json.getInt("chatId")
                            val userId = json.getInt("userId")
                            val userName = json.getString("userName")
                            _events.value = WSEvent.Typing(chatId, userId, userName)
                        }
                        "user_online" -> {
                            _events.value = WSEvent.UserOnline(json.getInt("userId"))
                        }
                        "user_offline" -> {
                            _events.value = WSEvent.UserOffline(json.getInt("userId"))
                        }
                        "chat_read" -> {
                            _events.value = WSEvent.ChatRead(json.getInt("chatId"))
                        }
                        "new_follower" -> {
                            _events.value = WSEvent.NewFollower(
                                actorId = json.getInt("actorId"),
                                actorName = json.optString("actorName", ""),
                                actorAvatarUrl = json.optString("actorAvatarUrl").takeIf { it.isNotBlank() && it != "null" }
                            )
                        }
                        "discovery_message" -> {
                            val chatId = json.getInt("chatId")
                            val message = json.getString("message")
                            val timestamp = json.getLong("timestamp")
                            _events.value = WSEvent.DiscoveryMessage(
                                chatId, message, timestamp,
                                senderId = json.optInt("senderId", 0),
                                senderName = json.optString("senderName", ""),
                                senderAvatarUrl = json.optString("senderAvatarUrl").takeIf { it.isNotBlank() },
                                messageType = json.optString("messageType", "text"),
                                replyToId = json.optInt("replyToId", -1).takeIf { it != -1 },
                                replyToText = json.optString("replyToText").takeIf { it.isNotBlank() },
                                replyToSenderName = json.optString("replyToSenderName").takeIf { it.isNotBlank() }
                            )
                        }
                        "comment_reply" -> {
                            val postId = json.getInt("postId")
                            _events.value = WSEvent.CommentReply(postId)
                            Log.d("WebSocket", "Comment reply event: postId=$postId")
                        }
                        "post_comment" -> {
                            val postId = json.getInt("postId")
                            _events.value = WSEvent.PostComment(postId)
                            Log.d("WebSocket", "Post comment event: postId=$postId")
                        }
                        "warning" -> {
                            val data = json.getJSONObject("data")
                            _events.value = WSEvent.Warning(
                                id = data.getInt("id"),
                                reason = data.getString("reason"),
                                warningNumber = data.getInt("warningNumber"),
                                totalWarnings = data.getInt("totalWarnings")
                            )
                            Log.d("WebSocket", "Warning event received")
                        }
                        "ban" -> {
                            val data = json.getJSONObject("data")
                            _events.value = WSEvent.Ban(
                                reason = data.getString("reason"),
                                expiresAt = if (data.isNull("expiresAt")) null else data.getString("expiresAt")
                            )
                            Log.d("WebSocket", "Ban event received")
                        }
                        "content_deleted" -> {
                            val data = json.getJSONObject("data")
                            _events.value = WSEvent.ContentDeleted(
                                contentType = data.getString("contentType"),
                                contentId = data.getInt("contentId"),
                                reason = data.getString("reason")
                            )
                            Log.d("WebSocket", "Content deleted event received")
                        }
                        "message_reaction" -> {
                            val chatId = json.getInt("chatId")
                            val messageId = json.getInt("messageId")
                            val arr = json.getJSONArray("reactions")
                            val reactions = (0 until arr.length()).map { i ->
                                val obj = arr.getJSONObject(i)
                                val idsArr = obj.getJSONArray("user_ids")
                                su.SkrinVex.ofox.data.api.models.MessageReaction(
                                    emoji = obj.getString("emoji"),
                                    user_ids = (0 until idsArr.length()).map { idsArr.getInt(it) },
                                    count = obj.getInt("count")
                                )
                            }
                            _events.value = WSEvent.MessageReaction(chatId, messageId, reactions)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Error parsing message", e)
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Connection failed: ${t.message}", t)
                Log.e("WebSocket", "Response: ${response?.code} ${response?.message}")
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closing: $code $reason")
                webSocket.close(1000, null)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocket", "Closed: $code $reason")
            }
        })
    }
    
    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
    }
    
    companion object {
        @Volatile
        private var INSTANCE: WebSocketClient? = null
        
        fun getInstance(context: Context): WebSocketClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebSocketClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

sealed class WSEvent {
    data class BadgeUpdate(val userId: Int, val badges: List<Badge>) : WSEvent()
    data class PostUpdate(val postId: Int, val likes: Int, val shares: Int) : WSEvent()
    data class NewPost(val postId: Int) : WSEvent()
    data class NewMessage(val chatId: Int, val message: String, val timestamp: Long, val senderId: Int = 0, val senderName: String = "", val senderAvatarUrl: String? = null, val messageType: String = "text", val replyToId: Int? = null, val replyToText: String? = null, val replyToSenderName: String? = null) : WSEvent()
    data class ChatUpdate(val chatId: Int, val lastMessage: String, val timestamp: Long) : WSEvent()
    data class NewComment(val postId: Int, val comment: su.SkrinVex.ofox.data.api.models.CommentResponse) : WSEvent()
    data class DeleteComment(val postId: Int, val commentId: Int) : WSEvent()
    data class Warning(val id: Int, val reason: String, val warningNumber: Int, val totalWarnings: Int) : WSEvent()
    data class Ban(val reason: String, val expiresAt: String?) : WSEvent()
    data class ContentDeleted(val contentType: String, val contentId: Int, val reason: String) : WSEvent()
    data class CommentReply(val postId: Int) : WSEvent()
    data class PostComment(val postId: Int) : WSEvent()
    data class Typing(val chatId: Int, val userId: Int, val userName: String) : WSEvent()
    data class UserOnline(val userId: Int) : WSEvent()
    data class UserOffline(val userId: Int) : WSEvent()
    data class ChatRead(val chatId: Int) : WSEvent()
    data class NewFollower(val actorId: Int, val actorName: String, val actorAvatarUrl: String?) : WSEvent()
    data class DiscoveryMessage(val chatId: Int, val message: String, val timestamp: Long, val senderId: Int = 0, val senderName: String = "", val senderAvatarUrl: String? = null, val messageType: String = "text", val replyToId: Int? = null, val replyToText: String? = null, val replyToSenderName: String? = null) : WSEvent()
    data class MessageReaction(val chatId: Int, val messageId: Int, val reactions: List<su.SkrinVex.ofox.data.api.models.MessageReaction>) : WSEvent()
}

data class Badge(
    val type: String,
    val description: String
)
