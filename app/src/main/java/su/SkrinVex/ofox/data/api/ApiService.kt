package su.SkrinVex.ofox.data.api

import retrofit2.http.*
import su.SkrinVex.ofox.data.api.models.*

interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): SimpleMessageResponse
    
    @POST("auth/verify")
    suspend fun verifyCode(@Body request: VerifyRequest): AuthResponse
    
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse
    
    @GET("auth/profile")
    suspend fun getProfile(): UserResponse
    
    @PUT("auth/profile")
    suspend fun updateProfile(@Body request: Map<String, String>): UserResponse
    
    @PUT("auth/fcm-token")
    suspend fun updateFcmToken(@Body body: Map<String, String>): SimpleMessageResponse

    @Multipart
    @POST("auth/avatar")
    suspend fun uploadAvatar(@Part avatar: okhttp3.MultipartBody.Part): AvatarUploadResponse

    @DELETE("auth/avatar")
    suspend fun deleteAvatar(): SimpleMessageResponse

    @Multipart
    @POST("auth/banner")
    suspend fun uploadBanner(@Part banner: okhttp3.MultipartBody.Part): BannerUploadResponse

    @DELETE("auth/banner")
    suspend fun deleteBanner(): SimpleMessageResponse
    
    @GET("auth/users/{id}")
    suspend fun getUserById(@Path("id") userId: Int): UserResponse
    
    @GET("posts")
    suspend fun getPosts(
        @Query("discoveryId") discoveryId: Int? = null,
        @Query("userId") userId: Int? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): List<PostResponse>
    
    @GET("posts/{id}")
    suspend fun getPostById(@Path("id") postId: Int): PostResponse
    
    @POST("posts")
    suspend fun createPost(@Body request: PostRequest): PostResponse
    
    @POST("posts/{id}/like")
    suspend fun toggleLike(@Path("id") postId: Int): PostResponse
    
    @POST("posts/{id}/share")
    suspend fun sharePost(@Path("id") postId: Int): PostResponse
    
    @POST("posts/{id}/vote")
    suspend fun voteOnPoll(@Path("id") postId: Int, @Body vote: Map<String, Int>): PostResponse
    
    @DELETE("posts/{id}")
    suspend fun deletePost(@Path("id") postId: Int)
    
    @GET("discoveries")
    suspend fun getDiscoveries(): List<DiscoveryResponse>
    
    @GET("discoveries/{id}")
    suspend fun getDiscoveryById(@Path("id") discoveryId: Int): DiscoveryResponse
    
    @POST("discoveries")
    suspend fun createDiscovery(@Body request: Map<String, String>): DiscoveryResponse
    
    @POST("discoveries/{id}/join")
    suspend fun toggleJoinDiscovery(@Path("id") discoveryId: Int): DiscoveryResponse

    @DELETE("discoveries/{id}")
    suspend fun deleteDiscovery(@Path("id") discoveryId: Int): SimpleMessageResponse
    
    @GET("badges/definitions")
    suspend fun getBadgeDefinitions(): List<BadgeDefinition>

    @GET("badges/user/{userId}")
    suspend fun getUserBadges(@Path("userId") userId: Int): Map<String, List<BadgeResponse>>

    @GET("chats")
    suspend fun getChats(): List<ChatResponse>

    @GET("chats/{chatId}")
    suspend fun getChatById(@Path("chatId") chatId: Int): ChatResponse
    
    @POST("chats")
    suspend fun createChat(@Body request: CreateChatRequest): ChatResponse
    
    @GET("chats/{chatId}/messages")
    suspend fun getMessages(
        @Path("chatId") chatId: Int,
        @Query("limit") limit: Int = 30,
        @Query("before") before: Int? = null
    ): List<MessageResponse>
    
    @POST("chats/{chatId}/messages")
    suspend fun sendMessage(@Path("chatId") chatId: Int, @Body message: SendMessageRequest): MessageResponse

    @DELETE("chats/{chatId}/messages/{messageId}")
    suspend fun deleteMessage(@Path("chatId") chatId: Int, @Path("messageId") messageId: Int): SimpleMessageResponse

    @POST("chats/{chatId}/messages/{messageId}/reactions")
    suspend fun addReaction(@Path("chatId") chatId: Int, @Path("messageId") messageId: Int, @Body request: ReactionRequest): List<MessageReaction>

    @DELETE("chats/{chatId}/messages/{messageId}/reactions/{emoji}")
    suspend fun removeReaction(@Path("chatId") chatId: Int, @Path("messageId") messageId: Int, @Path("emoji") emoji: String): List<MessageReaction>
    
    @POST("chats/{chatId}/read")
    suspend fun markChatAsRead(@Path("chatId") chatId: Int)

    @POST("chats/{chatId}/typing")
    suspend fun sendTyping(@Path("chatId") chatId: Int)

    @GET("chats/{chatId}/voice/upload")
    suspend fun getVoiceUploadUrl(@Path("chatId") chatId: Int): VoiceUploadUrlResponse

    @GET("chats/voice/play")
    suspend fun getVoicePlayUrl(@Query("key") key: String): VoicePlayUrlResponse

    @GET("chats/voice/download")
    suspend fun getVoiceDownloadUrl(
        @Query("key") key: String,
        @Query("chatName") chatName: String,
        @Query("sentAt") sentAt: Long
    ): VoiceDownloadUrlResponse

    @GET("chats/{chatId}/image/upload")
    suspend fun getChatImageUploadUrl(
        @Path("chatId") chatId: Int,
        @Query("mimeType") mimeType: String = "image/jpeg"
    ): ChatImageUploadUrlResponse

    @GET("chats/image/play")
    suspend fun getChatImagePlayUrl(@Query("key") key: String): ChatImagePlayUrlResponse

    @GET("chats/image/download")
    suspend fun getChatImageDownloadUrl(
        @Query("key") key: String,
        @Query("chatName") chatName: String,
        @Query("sentAt") sentAt: Long
    ): ChatImageDownloadUrlResponse

    @GET("chats/online/users")
    suspend fun getOnlineUsers(): Map<String, List<Int>>
    
    @GET("subscriptions/mutual")
    suspend fun getMutualFriends(): List<UserResponse>
    
    @POST("subscriptions/{userId}")
    suspend fun toggleSubscription(@Path("userId") userId: Int): Map<String, Boolean>
    
    @GET("subscriptions/{userId}/check")
    suspend fun isSubscribed(@Path("userId") userId: Int): Map<String, Boolean>
    
    @GET("subscriptions/{userId}/check-reverse")
    suspend fun isSubscribedToMe(@Path("userId") userId: Int): Map<String, Boolean>
    
    @GET("subscriptions/{userId}/subscribers/count")
    suspend fun getSubscribersCount(@Path("userId") userId: Int): Map<String, Int>
    
    @GET("posts/{postId}/comments")
    suspend fun getComments(@Path("postId") postId: Int): List<CommentResponse>
    
    @POST("posts/{postId}/comments")
    suspend fun createComment(@Path("postId") postId: Int, @Body request: CreateCommentRequest): CommentResponse
    
    @DELETE("posts/comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: Int)

    @POST("posts/{postId}/comments/{commentId}/pin")
    suspend fun pinComment(@Path("postId") postId: Int, @Path("commentId") commentId: Int): SimpleMessageResponse

    @POST("posts/{postId}/report")
    suspend fun reportPost(@Path("postId") postId: Int, @Body request: ReportRequest): SimpleMessageResponse

    @POST("posts/comments/{commentId}/report")
    suspend fun reportComment(@Path("commentId") commentId: Int, @Body request: ReportRequest): SimpleMessageResponse

    @Multipart
    @POST("posts/{postId}/images")
    suspend fun uploadPostImages(
        @Path("postId") postId: Int,
        @Part images: List<okhttp3.MultipartBody.Part>
    ): PostImagesResponse
    
    @GET("app-info")
    suspend fun getAppInfo(): AppInfoResponse
    
    @GET("comment-rules")
    suspend fun getCommentRules(): AppInfoResponse
    
    @GET("ofox-rules")
    suspend fun getOfoxRules(): AppInfoResponse
    
    @GET("privacy-policy")
    suspend fun getPrivacyPolicy(): AppInfoResponse
    
    @GET("moderation/warnings/{userId}")
    suspend fun getWarnings(@Path("userId") userId: Int): List<WarningResponse>
    
    @POST("moderation/warnings/{id}/delivered")
    suspend fun markWarningDelivered(@Path("id") warningId: Int): SimpleMessageResponse
    
    @GET("moderation/bans/{userId}")
    suspend fun getBan(@Path("userId") userId: Int): retrofit2.Response<BanResponse?>
    
    @GET("moderation/deleted-content")
    suspend fun getDeletedContent(): List<DeletedContentResponse>
    
    @POST("moderation/deleted-content/{id}/viewed")
    suspend fun markContentViewed(@Path("id") contentId: Int): SimpleMessageResponse
    
    @GET("notifications")
    suspend fun getCommentNotifications(): List<CommentNotification>

    @GET("notifications/unread-count")
    suspend fun getNotificationsUnreadCount(): Map<String, Int>

    @POST("notifications/read")
    suspend fun markNotificationsRead(): SimpleMessageResponse

    @POST("notifications/read-by-post/{postId}")
    suspend fun markNotificationsReadByPost(@Path("postId") postId: Int): SimpleMessageResponse

    @POST("notifications/delete")
    suspend fun deleteNotifications(@Body request: DeleteNotificationsRequest): SimpleMessageResponse

    // Стикеры
    @GET("stickers")
    suspend fun getUserStickers(): UserStickersResponse

    @Multipart
    @POST("stickers")
    suspend fun uploadSticker(
        @Part sticker: okhttp3.MultipartBody.Part,
        @Part packId: okhttp3.MultipartBody.Part? = null,
        @Part emoji: okhttp3.MultipartBody.Part? = null
    ): StickerItem

    @DELETE("stickers/{id}")
    suspend fun deleteSticker(@Path("id") id: Int): SimpleMessageResponse

    @POST("stickers/{stickerId}/used")
    suspend fun markStickerUsed(@Path("stickerId") stickerId: Int): SimpleMessageResponse

    @GET("stickers/packs")
    suspend fun getPublicPacks(): List<StickerPack>

    @GET("stickers/packs/my")
    suspend fun getMyPacks(): List<StickerPack>

    @GET("stickers/packs/{slug}")
    suspend fun getPackBySlug(@Path("slug") slug: String): StickerPack

    @GET("stickers/packs/by-url")
    suspend fun getPackByStickerUrl(@Query("url") url: String): StickerPack

    @POST("stickers/packs")
    suspend fun createPack(@Body request: CreatePackRequest): StickerPack

    @PUT("stickers/packs/{id}")
    suspend fun updatePack(@Path("id") id: Int, @Body request: CreatePackRequest): StickerPack

    @DELETE("stickers/packs/{id}")
    suspend fun deletePack(@Path("id") id: Int): SimpleMessageResponse

    @POST("stickers/packs/{packId}/install")
    suspend fun installPack(@Path("packId") packId: Int): SimpleMessageResponse

    @DELETE("stickers/packs/{packId}/install")
    suspend fun uninstallPack(@Path("packId") packId: Int): SimpleMessageResponse

    @GET("discoveries/{discoveryId}/chat")
    suspend fun getOrCreateDiscoveryChat(@Path("discoveryId") discoveryId: Int): ChatResponse

    @GET("discoveries/chat/{chatId}/messages")
    suspend fun getDiscoveryChatMessages(
        @Path("chatId") chatId: Int,
        @Query("limit") limit: Int = 30,
        @Query("before") before: Int? = null
    ): List<MessageResponse>

    @POST("discoveries/chat/{chatId}/messages")
    suspend fun sendDiscoveryChatMessage(
        @Path("chatId") chatId: Int,
        @Body request: SendMessageRequest
    ): MessageResponse

    @GET("discoveries/chat/{chatId}/voice/upload")
    suspend fun getDiscoveryVoiceUploadUrl(@Path("chatId") chatId: Int): VoiceUploadUrlResponse

    @GET("notification-settings")
    suspend fun getNotificationSettings(): NotificationSettingsResponse

    @PUT("notification-settings")
    suspend fun updateNotificationSettings(@Body request: UpdateNotificationSettingsRequest): SimpleMessageResponse

    @POST("notification-settings/mute/chat/{chatId}")
    suspend fun toggleChatMute(@Path("chatId") chatId: Int): Map<String, Boolean>

    @POST("notification-settings/mute/friend/{friendId}")
    suspend fun toggleFriendMute(@Path("friendId") friendId: Int): Map<String, Boolean>
    
    @POST("discoveries/{discoveryId}/achievements")
    suspend fun createAchievement(@Path("discoveryId") discoveryId: Int, @Body request: CreateAchievementRequest): AchievementResponse
    
    @GET("discoveries/{discoveryId}/achievements")
    suspend fun getAchievements(@Path("discoveryId") discoveryId: Int): List<AchievementResponse>
    
    @POST("discoveries/achievements/{achievementId}/grant")
    suspend fun grantAchievement(@Path("achievementId") achievementId: Int, @Body request: GrantAchievementRequest): SimpleMessageResponse
    
    @GET("discoveries/users/{userId}/achievements")
    suspend fun getUserAchievements(@Path("userId") userId: Int): List<AchievementResponse>
}
