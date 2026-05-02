package su.SkrinVex.ofox

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class OfoxApp : Application(), ImageLoaderFactory {

    companion object {
        // Глобальное подключение к сервису — живёт всё время жизни приложения
        var voiceService: VoicePlayerService? = null
            private set
    }

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            voiceService = (binder as VoicePlayerService.LocalBinder).getService()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            voiceService = null
            // Переподключаемся
            bindVoiceService()
        }
    }

    override fun onCreate() {
        super.onCreate()
        bindVoiceService()
    }

    private fun bindVoiceService() {
        val intent = Intent(this, VoicePlayerService::class.java)
        startService(intent)
        bindService(intent, serviceConn, BIND_AUTO_CREATE)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150 * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .addNetworkInterceptor { chain ->
                        chain.proceed(chain.request()).newBuilder()
                            .header("Cache-Control", "max-age=86400")
                            .build()
                    }
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(150)
            .allowHardware(true)
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .build()
    }
}
