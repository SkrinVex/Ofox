package su.SkrinVex.ofox.data

import android.content.Context
import androidx.room.*

@Database(
    entities = [User::class, Post::class, Chat::class, Message::class, Discovery::class],
    version = 7
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun postDao(): PostDao
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun discoveryDao(): DiscoveryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ofox_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val email: String,
    val password: String,
    val name: String,
    val bio: String = ""
)

@Entity(tableName = "posts")
data class Post(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val authorId: Int,
    val authorName: String,
    val content: String,
    val likes: Int = 0,
    val comments: Int = 0,
    val shares: Int = 0,
    val timestamp: Long,
    val type: String = "TEXT",
    val isLiked: Boolean = false,
    val pollOptions: String = "", // JSON array of options
    val pollVotes: String = "", // JSON array of vote counts
    val userVote: Int = -1, // -1 means not voted, otherwise index of voted option
    val discoveryId: Int = 0,
    val discoveryTitle: String = "",
    val discoveryColor: String = "",
    val authorBadges: String = "" // JSON array of badges
)

@Entity(tableName = "chats")
data class Chat(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val lastMessage: String,
    val timestamp: Long,
    val userId: Int = 0,
    val userBadges: String = ""
)

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chatId: Int,
    val text: String,
    val timestamp: Long,
    val isFromMe: Boolean,
    val senderId: Int = 0
)

@Entity(tableName = "discoveries")
data class Discovery(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val category: String,
    val participants: Int,
    val colorHex: String,
    val isJoined: Boolean = false,
    val creatorName: String = ""
)

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email AND password = :password LIMIT 1")
    suspend fun login(email: String, password: String): User?

    @Insert
    suspend fun register(user: User): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUser(id: Int): User?

    @Update
    suspend fun updateUser(user: User)
}

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY timestamp DESC")
    suspend fun getAllPosts(): List<Post>

    @Query("SELECT * FROM posts WHERE id = :postId")
    suspend fun getPostById(postId: Int): Post?

    @Query("SELECT * FROM posts WHERE authorId = :userId ORDER BY timestamp DESC")
    suspend fun getPostsByUser(userId: Int): List<Post>

    @Query("SELECT * FROM posts WHERE discoveryId = :discoveryId ORDER BY timestamp DESC")
    suspend fun getPostsByDiscovery(discoveryId: Int): List<Post>

    @Insert
    suspend fun insertPost(post: Post)

    @Query("UPDATE posts SET likes = :likes, isLiked = :isLiked WHERE id = :postId")
    suspend fun updateLikes(postId: Int, likes: Int, isLiked: Boolean)

    @Query("UPDATE posts SET shares = :shares WHERE id = :postId")
    suspend fun updateShares(postId: Int, shares: Int)

    @Query("UPDATE posts SET pollVotes = :votes, userVote = :userVote WHERE id = :postId")
    suspend fun updatePollVote(postId: Int, votes: String, userVote: Int)

    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deletePost(postId: Int)

    @Query("DELETE FROM posts")
    suspend fun deleteAllPosts()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY timestamp DESC")
    suspend fun getAllChats(): List<Chat>

    @Insert
    suspend fun insertChat(chat: Chat): Long

    @Query("UPDATE chats SET lastMessage = :message, timestamp = :timestamp WHERE id = :chatId")
    suspend fun updateChat(chatId: Int, message: String, timestamp: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessages(chatId: Int): List<Message>

    @Insert
    suspend fun insertMessage(message: Message)
}

@Dao
interface DiscoveryDao {
    @Query("SELECT * FROM discoveries ORDER BY id DESC")
    suspend fun getAllDiscoveries(): List<Discovery>

    @Query("SELECT * FROM discoveries WHERE id = :id")
    suspend fun getDiscoveryById(id: Int): Discovery?

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertDiscovery(discovery: Discovery): Long

    @Query("UPDATE discoveries SET isJoined = :isJoined, participants = :participants WHERE id = :id")
    suspend fun updateJoinStatus(id: Int, isJoined: Boolean, participants: Int)
}
