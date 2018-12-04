package info.karelov.songlink

import com.github.kittinunf.fuel.Fuel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.reactivex.Observable

const val BASE_URL = "https://song.link/"
const val INITIAL_STATE_REGEXP = "<script id=\"initialState\".+>(.+)</script>"
val SERVICES = arrayOf(
    SLProvider("yandex", "Yandex Music"),
    SLProvider("google", "Google Music"),
    SLProvider("appleMusic", "Apple Music"),
    SLProvider("spotify", "Spotify"),
    SLProvider("youtube", "Youtube"),
    SLProvider("youtubeMusic", "Youtube Music"),
    SLProvider("deezer", "Deezer"),
    SLProvider("pandora", "Pandora"),
    SLProvider("soundcloud", "SoundCloud"),
    SLProvider("tidal", "Tidal"),
    SLProvider("songlink", "song.link")
)

data class SL(
    val songlink: SLData
)

data class SLResponse(
    val url: String,
    val data: String
)

data class SLData(
    val title: String,
    val artistName: String,
    val links: SLLinks
)

data class SLLinks(
    val listen: MutableList<SLLink>
)

data class SLLink(
    val name: String,
    val provider: String,
    val url: String
)

data class SLProvider(
    val name: String,
    val label: String,
    val url: String = ""
)

class SongLink {
    private lateinit var url: String
    private lateinit var providers: List<SLProvider>

    fun load(url: String): Observable<List<SLProvider>> {
        if (::providers.isInitialized) {
            return Observable.just(providers)
        }

        return this.requestData(url)
            .map { this.url = it.url; it }
            .flatMap { this.extractData(it.data) }
            .flatMap { this.parseJson(it) }
            .flatMap { this.getProviders(it) }
            .map { this.providers = it; it }
    }

    private fun parseJson(data: String): Observable<SL> {
        try {
            return Observable.just(Gson().fromJson(data, SL::class.java))
        } catch (error: JsonSyntaxException) {
            throw Error("Error: Couldn't decode data into SL")
        }
    }

    private fun requestData(url: String): Observable<SLResponse> {
        return Observable.create { emitter ->
            Fuel.get(BASE_URL + url).responseString { _, response, result ->
                val (data, error) = result

                if (error != null) {
                    if (error.response.statusCode != -1) {
                        emitter.onError(Error("Server returned ${response.statusCode} error"))
                    } else {
                        emitter.onError(error)
                    }
                } else if (data == null) {
                    emitter.onError(Error("No data returned from server"))
                } else {
                    emitter.onNext(SLResponse(response.url.toString(), data))
                }

                emitter.onComplete()
            }
        }
    }

    private fun extractData(html: String): Observable<String> {
        val regex = Regex(INITIAL_STATE_REGEXP)
        val initialState = regex.find(html)

        if (initialState == null || initialState.groups.size < 2 || initialState.groups[1]!!.value.isEmpty()) {
            throw Error("Cannot find initialState field")
        }

        return Observable.just(initialState.groups[1]!!.value)
    }

    private fun getProviders(data: SL): Observable<List<SLProvider>> {
        val services = mutableListOf<SLProvider>()

        SERVICES.forEach { provider ->
            val remoteProvider = data.songlink.links.listen.find { link -> link.name == provider.name }

            if (remoteProvider != null) {
                val newProvider = provider.copy(url = remoteProvider.url)
                services.add(newProvider)
            }
        }

        services.add(SERVICES.last().copy(url = this.url))

        return Observable.just(services.toList())
    }
}
