package com.phisher98

import android.util.Base64
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.CLEARKEY_UUID
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newDrmExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.io.InputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class IndianTVPlugin : MainAPI() {
    override var lang = "hi"
    override var mainUrl = ""
    override var name = "IndianTV"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(
        TvType.Live,
    )

    private val mainUrls = listOf(
        base64Decode("aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL2t1bndhcnhzaGFzaGFuay9yb2dwbGF5X2FkZG9ucy9yZWZzL2hlYWRzL21haW4vbGl2ZXR2L2RhdGEvbXhwbGF5Lm0zdQ=="),
        base64Decode("aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL2t1bndhcnhzaGFzaGFuay9yb2dwbGF5X2FkZG9ucy9yZWZzL2hlYWRzL21haW4vbGl2ZXR2L2RhdGEveXVwcHR2Lm0zdQ=="),
        base64Decode("aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL2RybWxpdmUvZmFuY29kZS1saXZlLWV2ZW50cy9yZWZzL2hlYWRzL21haW4vZmFuY29kZS5tM3U="),
        base64Decode("aHR0cHM6Ly9saXZlLmRpbmVzaDI5LmNvbS5ucC9qaW90dnBsdXMubTN1"),
        base64Decode("aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL2t1bndhcnhzaGFzaGFuay9yb2dwbGF5X2FkZG9ucy9yZWZzL2hlYWRzL21haW4vbGl2ZXR2L2RhdGEvc29ueWxpdi5tM3U=")
    )

    // ✅ Fetch and combine all playlists
    private suspend fun fetchAllPlaylists(): Playlist {
        val allItems = mutableListOf<PlaylistItem>()
        for (url in mainUrls) {
            try {
                val content = app.get(url).text
                val playlist = IptvPlaylistParser().parseM3U(content)
                allItems.addAll(playlist.items)
            } catch (e: Exception) {
                println("Error loading $url: ${e.message}")
            }
        }
        return Playlist(allItems)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = fetchAllPlaylists()
        return newHomePageResponse(data.items.groupBy { it.attributes["group-title"] }.map { group ->
            val title = group.key ?: ""
            val show = group.value.map { channel ->
                val streamurl = channel.url.toString()
                val channelname = channel.title.toString()
                val posterurl = channel.attributes["tvg-logo"].toString()
                val nation = channel.attributes["group-title"].toString()
                val key = channel.attributes["key"].toString()
                val keyid = channel.attributes["keyid"].toString()
                newLiveSearchResponse(channelname, LoadData(streamurl, channelname, posterurl, nation, key, keyid).toJson(), TvType.Live) {
                    this.posterUrl = posterurl
                    this.lang = channel.attributes["group-title"]
                }
            }
            HomePageList(title, show, isHorizontalImages = true)
        })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val data = fetchAllPlaylists()
        return data.items.filter { it.title?.contains(query, ignoreCase = true) ?: false }.map { channel ->
            val streamurl = channel.url.toString()
            val channelname = channel.title.toString()
            val posterurl = channel.attributes["tvg-logo"].toString()
            val nation = channel.attributes["group-title"].toString()
            val key = channel.attributes["key"].toString()
            val keyid = channel.attributes["keyid"].toString()
            newLiveSearchResponse(channelname, LoadData(streamurl, channelname, posterurl, nation, key, keyid).toJson(), TvType.Live) {
                this.posterUrl = posterurl
                this.lang = channel.attributes["group-title"]
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val data = parseJson<LoadData>(url)
        return newLiveStreamLoadResponse(data.title,data.url,url)
        {
            this.posterUrl=data.poster
            this.plot=data.nation
        }
    }
    data class LoadData(
        val url: String,
        val title: String,
        val poster: String,
        val nation: String,
        val key: String,
        val keyid: String,
    )
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        if (loadData.url.contains(".m3u8"))
        {
            val headers = hashMapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 ygx/69.1 Safari/537.36"
            )
            callback.invoke(
                newExtractorLink(
                    "$name HLS",
                    name,
                    loadData.url,
                    ExtractorLinkType.M3U8,
                )
                {
                    this.quality=Qualities.P1080.value
                    this.headers=headers
                }
            )
        }
        else
        {
            val cookie=loadData.url.substringAfter("cookie=")
            val headers = hashMapOf(
                "Cookie" to cookie,
                "User-Agent" to "tv.accedo.airtel.wynk/1.97.1 (Linux;Android 11) ygx/69.1 ExoPlayerLib/2.19.1"
            )
            val key= decodeHex(loadData.key)
            val keyid=decodeHex(loadData.keyid)
            callback.invoke(
                newDrmExtractorLink(
                    "$name DASH",
                    name,
                    loadData.url,
                    ExtractorLinkType.DASH,
                    CLEARKEY_UUID
                )
                {
                    this.quality=Qualities.P1080.value
                    this.key=key
                    this.kid=keyid
                    this.headers=headers
                }
            )
        }
        return true
    }
}


data class Playlist(
    val items: List<PlaylistItem> = emptyList(),
)

data class PlaylistItem(
    val title: String? = null,
    val attributes: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val url: String? = null,
    val userAgent: String? = null,
    val key: String? = null,
    val keyid: String? = null,
)


class IptvPlaylistParser {


    /**
     * Parse M3U8 string into [Playlist]
     *
     * @param content M3U8 content string.
     * @throws PlaylistParserException if an error occurs.
     */
    fun parseM3U(content: String): Playlist {
        return parseM3U(content.byteInputStream())
    }

    /**
     * Parse M3U8 content [InputStream] into [Playlist]
     *
     * @param input Stream of input data.
     * @throws PlaylistParserException if an error occurs.
     */
    @Throws(PlaylistParserException::class)
    fun parseM3U(input: InputStream): Playlist {
        val reader = input.bufferedReader()

        if (!reader.readLine().isExtendedM3u()) {
            throw PlaylistParserException.InvalidHeader()
        }

        val playlistItems = mutableListOf<PlaylistItem>()

        var currentTitle: String? = null
        var currentAttributes: Map<String, String> = emptyMap()
        var currentUserAgent: String? = null
        var currentReferrer: String? = null
        var currentHeaders: Map<String, String> = emptyMap()
        var currentKey: String? = null
        var currentKeyId: String? = null

        reader.forEachLine { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEachLine

            when {
                // --- LICENSE KEY ---
                trimmedLine.startsWith("#KODIPROP:inputstream.adaptive.license_key=") -> {
                    val license = trimmedLine.substringAfter("=")

                    if (license.startsWith("http")) {
                        // handle URL license_key with keyid & key in query
                        val query = license.substringAfter("?", "")
                        val params = query.split("&").mapNotNull {
                            val parts = it.split("=")
                            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
                        }.toMap()

                        currentKeyId = params["keyid"]
                        currentKey = params["key"]
                    } else {
                        // fallback: keyid:key
                        val parts = license.split(":")
                        if (parts.size == 2) {
                            currentKeyId = parts[0]
                            currentKey = parts[1]
                        }
                    }
                }

                // --- CHANNEL INFO ---
                trimmedLine.startsWith(EXT_INF) -> {
                    currentTitle = trimmedLine.getTitle()
                    currentAttributes = trimmedLine.getAttributes()
                    currentUserAgent = null
                    currentReferrer = null
                    currentHeaders = emptyMap()
                }

                // --- VLC OPTS (UA / REFERRER) ---
                trimmedLine.startsWith(EXT_VLC_OPT) -> {
                    val userAgent = trimmedLine.getTagValue("http-user-agent")
                    val referrer = trimmedLine.getTagValue("http-referrer")
                    currentUserAgent = userAgent ?: currentUserAgent
                    currentReferrer = referrer ?: currentReferrer
                    if (currentReferrer != null) {
                        currentHeaders = currentHeaders + mapOf("referrer" to currentReferrer!!)
                    }
                }

                // --- EXTHTTP (JSON COOKIES etc.) ---
                trimmedLine.startsWith("#EXTHTTP:") -> {
                    val json = trimmedLine.substringAfter("#EXTHTTP:")
                    try {
                        val map = parseJson<Map<String, String>>(json)
                        currentHeaders = currentHeaders + map
                    } catch (_: Exception) {
                        // ignore bad JSON
                    }
                }

                // --- URL LINE ---
                !trimmedLine.startsWith("#") -> {
                    val url = trimmedLine.getUrl()
                    val uaParam = trimmedLine.getUrlParameter("user-agent")
                    val refParam = trimmedLine.getUrlParameter("referer")

                    val key = trimmedLine.getUrlParameter("key") ?: currentKey
                    val keyid = trimmedLine.getUrlParameter("keyid") ?: currentKeyId

                    // parse inline ||cookie=
                    val cookie = trimmedLine.substringAfter("||cookie=", "")

                    val combinedUserAgent = uaParam ?: currentUserAgent
                    val ref = refParam ?: currentReferrer

                    val urlHeaders = buildMap {
                        putAll(currentHeaders)
                        if (cookie.isNotEmpty()) put("cookie", cookie)
                        if (!ref.isNullOrEmpty()) put("referrer", ref)
                    }

                    if (currentTitle != null) {
                        val finalAttributes = currentAttributes.toMutableMap().apply {
                            if (!key.isNullOrEmpty()) put("key", key)
                            if (!keyid.isNullOrEmpty()) put("keyid", keyid)
                        }

                        playlistItems.add(
                            PlaylistItem(
                                title = currentTitle ?: "",
                                attributes = finalAttributes,
                                url = url,
                                userAgent = combinedUserAgent,
                                headers = urlHeaders,
                                key = key,
                                keyid = keyid
                            )
                        )
                    }

                    // reset state
                    currentTitle = null
                    currentAttributes = emptyMap()
                    currentUserAgent = null
                    currentReferrer = null
                    currentHeaders = emptyMap()
                    currentKey = null
                    currentKeyId = null
                }
            }
        }

        return Playlist(playlistItems)
    }
    /**
     * Replace "" (quotes) from given string.
     */
    private fun String.replaceQuotesAndTrim(): String {
        return replace("\"", "").trim()
    }

    /**
     * Check if given content is valid M3U8 playlist.
     */
    private fun String.isExtendedM3u(): Boolean = startsWith(EXT_M3U)

    /**
     * Get title of media.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result: Title
     */
    private fun String.getTitle(): String? {
        return split(",").lastOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get media url.
     *
     * Example:-
     *
     * Input:
     * ```
     * https://example.com/sample.m3u8|user-agent="Custom"
     * ```
     * Result: https://example.com/sample.m3u8
     */
    private fun String.getUrl(): String? {
        return split("|").firstOrNull()?.replaceQuotesAndTrim()
    }

    /**
     * Get url parameters.
     *
     * Example:-
     *
     * Input:
     * ```
     * http://192.54.104.122:8080/d/abcdef/video.mp4|User-Agent=Mozilla&Referer=CustomReferrer
     * ```
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "User-Agent" to "Mozilla",
     *   "Referer" to "CustomReferrer"
     * )
     * ```
     */
    /*  private fun String.getUrlParameters(): Map<String, String> {
          val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
          val headersString = replace(urlRegex, "").replaceQuotesAndTrim()
          return headersString.split("&").mapNotNull {
              val pair = it.split("=")
              if (pair.size == 2) pair.first() to pair.last() else null
          }.toMap()
      }

     */

    /**
     * Get url parameter with key.
     *
     * Example:-
     *
     * Input:
     * ```
     * http://192.54.104.122:8080/d/abcdef/video.mp4|User-Agent=Mozilla&Referer=CustomReferrer
     * ```
     * If given key is `user-agent`, then
     *
     * Result: Mozilla
     */
    private fun String.getUrlParameter(key: String): String? {
        val urlRegex = Regex("^(.*)\\|", RegexOption.IGNORE_CASE)
        val keyRegex = Regex("$key=(\\w[^&]*)", RegexOption.IGNORE_CASE)
        val paramsString = replace(urlRegex, "").replaceQuotesAndTrim()
        return keyRegex.find(paramsString)?.groups?.get(1)?.value
    }

    /**
     * Get attributes from `#EXTINF` tag as Map<String, String>.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTINF:-1 tvg-id="1234" group-title="Kids" tvg-logo="url/to/logo", Title
     * ```
     * Result will be equivalent to kotlin map:
     * ```Kotlin
     * mapOf(
     *   "tvg-id" to "1234",
     *   "group-title" to "Kids",
     *   "tvg-logo" to "url/to/logo"
     *)
     * ```
     */
    private fun String.getAttributes(): Map<String, String> {
        val extInfRegex = Regex("(#EXTINF:.?[0-9]+)", RegexOption.IGNORE_CASE)
        val attributesString = replace(extInfRegex, "").replaceQuotesAndTrim().split(",").first()
        return attributesString.split(Regex("\\s")).mapNotNull {
            val pair = it.split("=")
            if (pair.size == 2) pair.first() to pair.last()
                .replaceQuotesAndTrim() else null
        }.toMap()
    }

    /**
     * Get value from a tag.
     *
     * Example:-
     *
     * Input:
     * ```
     * #EXTVLCOPT:http-referrer=http://example.com/
     * ```
     * Result: http://example.com/
     */
    private fun String.getTagValue(key: String): String? {
        val keyRegex = Regex("$key=(.*)", RegexOption.IGNORE_CASE)
        return keyRegex.find(this)?.groups?.get(1)?.value?.replaceQuotesAndTrim()
    }

    companion object {
        const val EXT_M3U = "#EXTM3U"
        const val EXT_INF = "#EXTINF"
        const val EXT_VLC_OPT = "#EXTVLCOPT"
    }

}

/**
 * Exception thrown when an error occurs while parsing playlist.
 */
sealed class PlaylistParserException(message: String) : Exception(message) {

    /**
     * Exception thrown if given file content is not valid.
     */
    class InvalidHeader :
        PlaylistParserException("Invalid file header. Header doesn't start with #EXTM3U")

}

private fun decodeHex(hexString: String):String {
    //hexStringToByteArray
    val length = hexString.length
    val byteArray = ByteArray(length / 2)

    for (i in 0 until length step 2) {
        byteArray[i / 2] = ((Character.digit(hexString[i], 16) shl 4) +
                Character.digit(hexString[i + 1], 16)).toByte()
    }
    //byteArrayToBase64
    val base64ByteArray = Base64.encode(byteArray, Base64.NO_PADDING)
    return String(base64ByteArray, StandardCharsets.UTF_8).trim()
}
