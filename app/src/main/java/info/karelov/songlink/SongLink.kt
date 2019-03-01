package info.karelov.songlink

import com.github.kittinunf.fuel.Fuel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.Expose
import io.reactivex.Observable

const val BASE_URL = "https://song.link/"
const val INITIAL_STATE_REGEXP = """<script id="initialState".+>\s+?(.+)\s+?</script>"""
val SERVICES = arrayOf(
    SLProvider("YANDEX_SONG", "Yandex Music"),
    SLProvider("GOOGLE_SONG", "Google Music"),
    SLProvider("ITUNES_SONG", "Apple Music"),
    SLProvider("SPOTIFY_SONG", "Spotify"),
    SLProvider("YOUTUBE_VIDEO", "Youtube"),
    SLProvider("YOUTUBE_SONG", "Youtube Music"),
    SLProvider("DEEZER_SONG", "Deezer"),
    SLProvider("PANDORA_SONG", "Pandora"),
    SLProvider("SOUNDCLOUD_SONG", "SoundCloud"),
    SLProvider("TIDAL_SONG", "Tidal"),
    SLProvider("SONGLINK", "song.link")
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
    @Expose(serialize = false, deserialize = false)
    val links: MutableList<SLLink>,
    val nodesByUniqueId: Map<String, Map<String, Any>>
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
            .flatMap { this.fillLinks(it) }
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

    private fun fillLinks(data: SL): Observable<SL> {
        val links = mutableListOf<SLLink>()

        data.songlink.nodesByUniqueId.entries
            .forEach {
                if (!it.value.containsKey("entity")) {
                    return@forEach
                }

                val entity = it.value["entity"] as String? ?: ""

                links.add(SLLink(
                    name = entity,
                    provider = entity.replace("_SONG", "").replace("_VIDEO", ""),
                    url = it.value["listenUrl"] as String? ?: ""
                ))
            }

        return Observable.just(data.copy(songlink = data.songlink.copy(links = links)))
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
            val remoteProvider = data.songlink.links.find { link -> link.name == provider.name }

            if (remoteProvider != null) {
                val newProvider = provider.copy(url = remoteProvider.url)
                services.add(newProvider)
            }
        }

        services.add(SERVICES.last().copy(url = this.url))

        return Observable.just(services.toList())
    }
}
