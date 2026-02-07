package su.SkrinVex.ofox.data.api

import su.SkrinVex.ofox.BuildConfig

object ApiConfig {
    const val BASE_URL = "https://api.skrinvex.su/ofox/"
    
    // Включить логирование только в debug
    val ENABLE_LOGGING = BuildConfig.DEBUG
    
    // Таймауты для разных сборок
    val CONNECT_TIMEOUT = if (BuildConfig.DEBUG) 10L else 30L
    val READ_TIMEOUT = if (BuildConfig.DEBUG) 10L else 30L
}
