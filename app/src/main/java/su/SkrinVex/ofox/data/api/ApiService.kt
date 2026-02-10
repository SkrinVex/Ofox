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
    
    @GET("chats")
    suspend fun getChats(): List<ChatResponse>
    
    @POST("chats")
    suspend fun createChat(@Body request: CreateChatRequest): ChatResponse
    
    @GET("chats/{chatId}/messages")
    suspend fun getMessages(@Path("chatId") chatId: Int): List<MessageResponse>
    
    @POST("chats/{chatId}/messages")
    suspend fun sendMessage(@Path("chatId") chatId: Int, @Body message: SendMessageRequest): MessageResponse
    
    @POST("chats/{chatId}/read")
    suspend fun markChatAsRead(@Path("chatId") chatId: Int)
    
    @GET("subscriptions/mutual")
    suspend fun getMutualFriends(): List<UserResponse>
    
    @POST("subscriptions/{userId}")
    suspend fun toggleSubscription(@Path("userId") userId: Int): Map<String, Boolean>
    
    @GET("subscriptions/{userId}/check")
    suspend fun isSubscribed(@Path("userId") userId: Int): Map<String, Boolean>
    
    @GET("subscriptions/{userId}/subscribers/count")
    suspend fun getSubscribersCount(@Path("userId") userId: Int): Map<String, Int>
    
    @GET("posts/{postId}/comments")
    suspend fun getComments(@Path("postId") postId: Int): List<CommentResponse>
    
    @POST("posts/{postId}/comments")
    suspend fun createComment(@Path("postId") postId: Int, @Body request: CreateCommentRequest): CommentResponse
    
    @DELETE("posts/comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: Int)
    
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
    suspend fun getBan(@Path("userId") userId: Int): BanResponse?
    
    @GET("moderation/deleted-content")
    suspend fun getDeletedContent(): List<DeletedContentResponse>
    
    @POST("moderation/deleted-content/{id}/viewed")
    suspend fun markContentViewed(@Path("id") contentId: Int): SimpleMessageResponse
}
