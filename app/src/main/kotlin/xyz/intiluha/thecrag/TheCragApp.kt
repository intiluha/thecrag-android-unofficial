package xyz.intiluha.thecrag

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import xyz.intiluha.thecrag.data.auth.AuthApi
import xyz.intiluha.thecrag.data.auth.AuthRepository
import xyz.intiluha.thecrag.data.logbook.LogbookRepository
import xyz.intiluha.thecrag.data.offline.FavouriteDownloader
import xyz.intiluha.thecrag.data.offline.FavouritesRepository
import xyz.intiluha.thecrag.data.offline.OfflineStore
import xyz.intiluha.thecrag.data.remote.ApiFactory
import xyz.intiluha.thecrag.data.remote.authHttp
import xyz.intiluha.thecrag.data.remote.KeyValueStore
import xyz.intiluha.thecrag.data.remote.PersistentCookieJar
import xyz.intiluha.thecrag.data.remote.TheCragApi
import xyz.intiluha.thecrag.data.repo.MapRepository
import xyz.intiluha.thecrag.data.repo.NodeRepository
import xyz.intiluha.thecrag.data.repo.RecentStore
import xyz.intiluha.thecrag.data.repo.TopoRepository
import java.io.File

/** Hand-rolled dependency graph; small enough not to need a DI framework. */
class AppGraph(app: Application) {
    private val prefs = app.getSharedPreferences("thecrag", Context.MODE_PRIVATE)
    private val store = object : KeyValueStore {
        override fun get(key: String) = prefs.getString(key, null)
        override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    }
    val cookies = PersistentCookieJar(store)
    val offline = OfflineStore(File(app.filesDir, "offline"), ApiFactory.json)
    val okHttp: OkHttpClient = ApiFactory.okHttp(cookies, app.cacheDir, BuildConfig.DEBUG, offline)
    val api: TheCragApi = ApiFactory.api(ApiFactory.retrofit(okHttp))
    val nodes = NodeRepository(api)
    val topos = TopoRepository(api)
    val map = MapRepository(api, ApiFactory.json)
    val recents = RecentStore(store, ApiFactory.json)
    // Stage 3: starred areas, pinned in the offline store and served by OfflineInterceptor.
    val favourites = FavouritesRepository(offline, FavouriteDownloader(okHttp, offline, ApiFactory.json))

    // Stage 2: the account. Separate client and cookie jar; browsing above never sees it.
    val authCookies = PersistentCookieJar(store, PersistentCookieJar.AUTH_KEY)
    val authHttp: OkHttpClient = ApiFactory.authHttp(authCookies, BuildConfig.DEBUG)
    val authApi: AuthApi = ApiFactory.retrofit(authHttp).create(AuthApi::class.java)
    val auth = AuthRepository(authApi, authCookies, store, ApiFactory.json)
    val logbook = LogbookRepository(authApi, auth, app.filesDir, ApiFactory.json)

    /** Drops everything fetched from theCrag except starred areas: in-memory copies and the HTTP disk cache. Blocking; call off the main thread. */
    fun clearCaches() {
        nodes.clear()
        topos.clear()
        okHttp.cache?.evictAll()
    }
}

class TheCragApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { graph.okHttp })) }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(256L * 1024 * 1024).build() }
            .crossfade(true)
            .build()

    companion object {
        fun graph(context: Context): AppGraph = (context.applicationContext as TheCragApp).graph
    }
}
