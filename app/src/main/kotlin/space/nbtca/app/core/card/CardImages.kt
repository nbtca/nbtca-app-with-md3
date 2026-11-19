package space.nbtca.app.core.card

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.decode.SvgDecoder
import coil.memory.MemoryCache
import coil.request.ImageRequest
import okhttp3.OkHttpClient
import java.io.File

object CardImages {
    @Volatile private var shared: ImageLoader? = null
    fun loader(context: Context): ImageLoader = shared ?: synchronized(this) {
        shared ?: ImageLoader.Builder(context.applicationContext)
            .components { add(SvgDecoder.Factory()) }
            .memoryCache { MemoryCache.Builder(context.applicationContext).maxSizePercent(.08).build() }
            .diskCache { DiskCache.Builder().directory(File(context.applicationContext.cacheDir,"card-images")).maxSizeBytes(64L*1024*1024).build() }
            .okHttpClient(OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build())
            .build().also { shared=it }
    }
    fun request(context: Context, model: Any, cookie: String): ImageRequest {
        return ImageRequest.Builder(context).apply {
            if(model is String && model.startsWith("/media/members/")) {
                val session = CardCacheSession.fromCookies(cookie) ?: return@apply
                val key = "${session.key}:$model"
                data(CardClient.ORIGIN+model)
                addHeader("Cookie",cookie)
                memoryCacheKey(key)
                diskCacheKey(key)
            } else if(model !is String || !model.startsWith("http")) data(model)
            listener(onSuccess={_,result -> android.util.Log.d("CardImageCache", result.dataSource.name) })
        }.build()
    }
    fun clear() { shared?.memoryCache?.clear(); shared?.diskCache?.clear() }
    fun thumbnail(path: String): String {
        if(!path.startsWith("/media/members/") || path.contains(".thumb.")) return path
        val dot=path.lastIndexOf('.')
        return if(dot>path.lastIndexOf('/')) path.substring(0,dot)+".thumb"+path.substring(dot) else path
    }
}
