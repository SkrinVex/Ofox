package su.SkrinVex.ofox.data.api.models

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val bio: String = ""
)

data class AuthResponse(val user: UserResponse, val token: String)

data class UserResponse(
    val id: Int,
    val email: String,
    val name: String,
    val bio: String
)

data class PostRequest(
    val content: String,
    val type: String = "TEXT",
    val pollOptions: List<String>? = null,
    val discoveryId: Int? = null
)

data class PostResponse(
    val id: Int,
    val author_id: Int,
    val author_name: String?,
    val content: String?,
    val type: String?,
    val likes: Int?,
    val shares: Int?,
    val is_liked: Boolean?,
    val poll_options: List<String>?,
    val poll_votes: List<Int>?,
    val user_vote: Int?,
    val discovery_id: Int?,
    val discovery_title: String?,
    val discovery_color: String?,
    val created_at: String,
    val created_timestamp: Long?
)

data class DiscoveryResponse(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val participants: Int,
    val color_hex: String?,
    val is_joined: Boolean,
    val creator_name: String?
)

data class ChatResponse(
    val id: Int,
    val name: String,
    val last_message: String,
    val updated_at: String
)

data class CreateChatRequest(
    val name: String,
    val participantIds: List<Int>
)

data class MessageResponse(
    val id: Int,
    val chat_id: Int,
    val sender_id: Int,
    val sender_name: String,
    val text: String,
    val created_at: String
)

data class SendMessageRequest(val text: String)
