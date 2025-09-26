package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidSrcTo
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin

@CloudstreamPlugin
class MovierulzhdPlugin: BasePlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Movierulzhd())
        registerMainAPI(Hdmovie2())
        registerExtractorAPI(FMHD())
        registerExtractorAPI(VidSrcTo())
        registerExtractorAPI(Akamaicdn())
        registerExtractorAPI(Luluvdo())
        registerExtractorAPI(FMX())
        registerExtractorAPI(Lulust())
        registerExtractorAPI(Playonion())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(Movierulzups())
        registerExtractorAPI(Movierulz())
        registerExtractorAPI(VidStack())
    }
    companion object {
        private const val DOMAINS_URL =
            "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/domains.json"
        var cachedDomains: Domains? = null

        suspend fun getDomains(forceRefresh: Boolean = false): Domains? {
            if (cachedDomains == null || forceRefresh) {
                try {
                    cachedDomains = app.get(DOMAINS_URL).parsedSafe<Domains>()
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
            }
            return cachedDomains
        }

        data class Domains(
            @JsonProperty("movierulzhd")
            val movierulzhd: String,
            @JsonProperty("hdmovie2")
            val hdmovie2: String,
        )
    }
}
