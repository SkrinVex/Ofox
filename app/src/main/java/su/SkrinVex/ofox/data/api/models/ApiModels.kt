package su.SkrinVex.ofox.data.api.models

data class LoginRequest(val email: String, val password: String)

data class RegisterRequest(
    val email: String,
    val password: String,
    val name: String,
    val bio: String = ""
)

data class VerifyRequest(val email: String, val code: String)

data class SimpleMessageResponse(val message: String)

data class VersionResponse(
    val forceUpdate: Boolean,
    val minVersion: String,
    val message: String?
)

data class AuthResponse(val user: UserResponse, val token: String)

data class UserResponse(
    val id: Int,
    val email: String? = null,
    val name: String,
    val bio: String,
    val badges: List<BadgeResponse>? = null,
    val social_links: String? = null,
    val banner_color: String? = null,
    val banner_image_url: String? = null,
    val avatar_url: String? = null
)

data class BadgeResponse(
    val badge_type: String,
    val description: String
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
    val author_badges: List<BadgeResponse>?,
    val author_avatar_url: String? = null,
    val content: String?,
    val type: String?,
    val likes: Int?,
    val shares: Int?,
    val comments: Int?,
    val is_liked: Boolean?,
    val poll_options: List<String>?,
    val poll_votes: List<Int>?,
    val user_vote: Int?,
    val discovery_id: Int?,
    val discovery_title: String?,
    val discovery_color: String?,
    val is_discovery_post: Boolean?,
    val images: List<String>? = null,
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
    val creator_name: String?,
    val creator_id: Int?,
    val created_at: String?
)

data class ChatResponse(
    val id: Int,
    val name: String,
    val last_message: String,
    val updated_at: String,
    val other_user_id: Int,
    val other_user_name: String,
    val other_user_badges: List<BadgeResponse>? = null,
    val unread_count: Int = 0,
    val other_user_avatar: String? = null,
    val discovery_id: Int? = null,
    val discovery_title: String? = null
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
    val sender_badges: List<BadgeResponse>? = null,
    val sender_avatar_url: String? = null,
    val text: String,
    val message_type: String = "text",
    val created_at: String,
    val reply_to_id: Int? = null,
    val reply_to_text: String? = null,
    val reply_to_sender_name: String? = null,
    val is_read: Boolean = false,
    val reactions: Map<String, List<Int>>? = null
)

data class StickerItem(
    val id: Int,
    val user_id: Int,
    val url: String,
    val emoji: String? = "",
    val pack_id: Int? = null,
    val created_at: String? = "",
    val pack_name: String? = ""
)

data class StickerPack(
    val id: Int,
    val creator_id: Int,
    val creator_name: String = "",
    val name: String,
    val slug: String,
    val description: String = "",
    val is_public: Boolean = false,
    val sticker_count: Int = 0,
    val preview_url: String? = null,
    val stickers: List<StickerItem>? = null
)

data class UserStickersResponse(
    val packs: List<StickerPack>,
    val recent: List<StickerItem>
)

data class CreatePackRequest(
    val name: String,
    val description: String = "",
    val isPublic: Boolean = false
)

data class StickerUploadResponse(
    val id: Int,
    val url: String
)

data class SendMessageRequest(val text: String, val messageType: String = "text", val replyToId: Int? = null)

data class ReactionRequest(val emoji: String)
data class ReactionUser(val id: Int, val name: String, val avatar_url: String? = null)
data class MessageReaction(val emoji: String, val user_ids: List<Int>, val users: List<ReactionUser>? = null, val count: Int)

data class CommentResponse(
    val id: Int,
    val post_id: Int,
    val author_id: Int,
    val author_name: String,
    val author_badges: List<BadgeResponse>?,
    val author_avatar_url: String? = null,
    val content: String,
    val created_at: String,
    val reply_to_id: Int? = null,
    val reply_to_author_name: String? = null,
    val is_pinned: Boolean = false
)

data class CreateCommentRequest(
    val content: String,
    @com.google.gson.annotations.SerializedName("replyToId")
    val replyToId: Int? = null
)

data class AppInfoResponse(val content: String)

data class WarningResponse(
    val id: Int,
    val reason: String,
    val warningNumber: Int,
    val totalWarnings: Int
)

data class BanResponse(
    val reason: String,
    val expiresAt: String?
)

data class DeletedContentResponse(
    val id: Int,
    @com.google.gson.annotations.SerializedName("content_type")
    val contentType: String,
    @com.google.gson.annotations.SerializedName("content_id")
    val contentId: Int,
    val reason: String
)

data class DiscoveryChatMessage(
    val id: Int,
    val discovery_id: Int,
    val user_id: Int,
    val user_name: String,
    val message: String,
    val created_at: String
)

data class SendDiscoveryMessageRequest(val message: String)

data class AchievementResponse(
    val id: Int,
    val discovery_id: Int,
    val title: String,
    val description: String?,
    val icon: String?,
    val reward_type: String?,
    val reward_value: String?,
    val is_earned: Boolean,
    val created_at: String
)

data class CreateAchievementRequest(
    val title: String,
    val description: String,
    val icon: String,
    val rewardType: String,
    val rewardValue: String
)

data class GrantAchievementRequest(val userId: Int)

data class AvatarUploadResponse(val avatar_url: String)
data class BannerUploadResponse(val banner_image_url: String)

data class CommentNotification(
    val id: Int,
    val type: String,
    val post_id: Int?,
    val comment_id: Int?,
    val is_read: Boolean,
    val created_at: String,
    val actor_name: String,
    val actor_avatar_url: String?,
    val actor_badges: List<BadgeResponse>? = null,
    val comment_content: String?,
    val title: String?,
    val body: String?
)

data class ReportRequest(val reason: String)

data class PostImagesResponse(val images: List<String>)

data class NotificationSettingsResponse(
    val notify_post_comments: Boolean,
    val notify_friend_posts: Boolean,
    val notify_chats: Boolean,
    val notify_discovery_chats: Boolean,
    val muted_chats: List<Int>,
    val muted_friends: List<Int>
)

data class UpdateNotificationSettingsRequest(
    val notify_post_comments: Boolean,
    val notify_friend_posts: Boolean,
    val notify_chats: Boolean,
    val notify_discovery_chats: Boolean
)
data class DeleteNotificationsRequest(val ids: List<Int>, val types: List<String>)
