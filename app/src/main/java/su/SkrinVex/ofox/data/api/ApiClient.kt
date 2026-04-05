package su.SkrinVex.ofox.data.api

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private val Context.dataStore by preferencesDataStore(name = "auth_prefs")

class ApiClient(private val context: Context) {
    private val TOKEN_KEY = stringPreferencesKey("auth_token")
    
    private var cachedToken: String? = null
    
    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
        }
        cachedToken = token
    }
    
    suspend fun getToken(): String? {
        if (cachedToken != null) return cachedToken
        cachedToken = context.dataStore.data.map { prefs ->
            prefs[TOKEN_KEY]
        }.first()
        return cachedToken
    }
    
    suspend fun clearToken() {
        context.dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
        }
        cachedToken = null
    }
    
    private val authInterceptor = Interceptor { chain ->
        val request = chain.request().newBuilder()
        val token = cachedToken
        if (token != null) {
            request.addHeader("Authorization", "Bearer $token")
        }
        request.addHeader("X-App-Version", su.SkrinVex.ofox.BuildConfig.VERSION_NAME)
        val response = chain.proceed(request.build())
        if (response.code == 426) {
            onForceUpdate?.invoke()
        }
        response
    }

    var onForceUpdate: (() -> Unit)? = null
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (ApiConfig.ENABLE_LOGGING) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .apply {
            if (ApiConfig.ENABLE_LOGGING) {
                addInterceptor(loggingInterceptor)
            }
        }
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.READ_TIMEOUT, TimeUnit.SECONDS)
        .build()
    
    val api: ApiService = Retrofit.Builder()
        .baseUrl(ApiConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
    
    companion object {
        @Volatile
        private var INSTANCE: ApiClient? = null
        
        fun getInstance(context: Context): ApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
