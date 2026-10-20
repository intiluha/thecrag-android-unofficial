package org.thecragunofficial.android

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okhttp3.OkHttpClient
import org.thecragunofficial.android.data.remote.ApiFactory
import org.thecragunofficial.android.data.remote.KeyValueStore
import org.thecragunofficial.android.data.remote.PersistentCookieJar
import org.thecragunofficial.android.data.remote.TheCragApi
import org.thecragunofficial.android.data.html.TopoHtmlParser
import org.thecragunofficial.android.data.repo.MapRepository
import org.thecragunofficial.android.data.repo.NodeRepository
import org.thecragunofficial.android.data.repo.TopoRepository

/** Hand-rolled dependency graph; small enough not to need a DI framework. */
class AppGraph(app: Application) {
    private val prefs = app.getSharedPreferences("thecrag", Context.MODE_PRIVATE)
    private val store = object : KeyValueStore {
        override fun get(key: String) = prefs.getString(key, null)
        override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    }
    val cookies = PersistentCookieJar(store)
    val okHttp: OkHttpClient = ApiFactory.okHttp(cookies, app.cacheDir, BuildConfig.DEBUG)
    val api: TheCragApi = ApiFactory.api(ApiFactory.retrofit(okHttp))
    val nodes = NodeRepository(api)
    val topos = TopoRepository(api, TopoHtmlParser(ApiFactory.json))
    val map = MapRepository(api, ApiFactory.json)
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
