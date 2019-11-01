package info.karelov.songlink

import android.net.Uri
import com.github.kittinunf.fuel.Fuel
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.reactivex.Observable

data class Response(
    val pageUrl: String,
    val linksByPlatform: Map<String, Link>
)

data class Link(
    val url: String
)

data class Provider(
    val name: String,
    val label: String,
    val url: String = ""
)

class SongLink {
    private lateinit var providers: List<Provider>

    fun load(url: String): Observable<List<Provider>> {
        if (::providers.isInitialized) {
            return Observable.just(providers)
        }

        return this.requestData(url)
            .flatMap { this.parseJson(it) }
            .flatMap { this.getProviders(it) }
            .map { this.providers = it; it }
    }

    private fun parseJson(data: String): Observable<Response> {
        try {
            return Observable.just(Gson().fromJson(data, Response::class.java))
        } catch (error: JsonSyntaxException) {
            throw Error("Error: Couldn't decode data into SL")
        }
    }

    private fun requestData(url: String): Observable<String> {
        val builder = Uri.Builder()

        builder
            .scheme("https")
            .authority("api.song.link")
            .appendPath("v1-alpha.1")
            .appendPath("links")
            .appendQueryParameter("key", "71d7be8a-3a76-459b-b21e-8f0350374984")
            .appendQueryParameter("url", url)

        val targetUrl = builder.build().toString()

        return Observable.create { emitter ->
            Fuel.get(targetUrl).responseString { _, response, result ->
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
                    emitter.onNext(data)
                }

                emitter.onComplete()
            }
        }
    }

    private fun getProviders(response: Response): Observable<List<Provider>> {
        val providers = mutableListOf<Provider>()
        val format = String.format(
            "%s|%s|%s",
            "(?<=[A-Z])(?=[A-Z][a-z])",
            "(?<=[^A-Z])(?=[A-Z])",
            "(?<=[A-Za-z])(?=[^A-Za-z])"
        )

        for ((name, link) in response.linksByPlatform.entries) {
            providers.add(
                Provider(
                    name,
                    name
                        .capitalize()
                        .replace(format.toRegex(), " "),
                    link.url
                )
            )
        }

        providers.add(
            Provider(
                "song.link",
                "song.link",
                response.pageUrl
            )
        )

        return Observable.just(providers)
    }
}
