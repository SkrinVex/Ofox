package su.SkrinVex.ofox.data.api

import su.SkrinVex.ofox.BuildConfig

object ApiConfig {
    private const val PROD_URL = "https://api.skrinvex.su/ofox/"
    private const val DEV_URL = "https://dev.api.skrinvex.su/ofox/"

    val BASE_URL get() = if (BuildConfig.DEBUG) DEV_URL else PROD_URL
    val WS_URL get() = if (BuildConfig.DEBUG) "wss://dev.api.skrinvex.su/ofox/ws" else "wss://api.skrinvex.su/ofox/ws"
    
    // Включить логирование только в debug
    val ENABLE_LOGGING = BuildConfig.DEBUG
    
    // Таймауты для разных сборок
    val CONNECT_TIMEOUT = if (BuildConfig.DEBUG) 10L else 30L
    val READ_TIMEOUT = if (BuildConfig.DEBUG) 10L else 30L
}
