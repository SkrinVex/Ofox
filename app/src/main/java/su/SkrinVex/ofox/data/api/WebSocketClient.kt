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
                            _events.value = WSEvent.NewMessage(chatId, message, timestamp)
                            Log.d("WebSocket", "New message event: chatId=$chatId")
                        }
                        "chat_update" -> {
                            val chatId = json.getInt("chatId")
                            val lastMessage = json.getString("lastMessage")
                            val timestamp = json.getLong("timestamp")
                            _events.value = WSEvent.ChatUpdate(chatId, lastMessage, timestamp)
                            Log.d("WebSocket", "Chat update event: chatId=$chatId")
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
    data class NewMessage(val chatId: Int, val message: String, val timestamp: Long) : WSEvent()
    data class ChatUpdate(val chatId: Int, val lastMessage: String, val timestamp: Long) : WSEvent()
}

data class Badge(
    val type: String,
    val description: String
)
