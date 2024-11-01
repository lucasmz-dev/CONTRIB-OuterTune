package com.zionhuang.innertube

import com.zionhuang.innertube.models.AccountInfo
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.Artist
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.BrowseEndpoint
import com.zionhuang.innertube.models.GridRenderer
import com.zionhuang.innertube.models.MusicCarouselShelfRenderer
import com.zionhuang.innertube.models.MusicShelfRenderer
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SearchSuggestions
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.zionhuang.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import com.zionhuang.innertube.models.YouTubeClient.Companion.TVHTML5
import com.zionhuang.innertube.models.YouTubeClient.Companion.WEB
import com.zionhuang.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.zionhuang.innertube.models.YouTubeLocale
import com.zionhuang.innertube.models.getContinuation
import com.zionhuang.innertube.models.response.AccountMenuResponse
import com.zionhuang.innertube.models.response.BrowseResponse
import com.zionhuang.innertube.models.response.CreatePlaylistResponse
import com.zionhuang.innertube.models.response.GetQueueResponse
import com.zionhuang.innertube.models.response.GetSearchSuggestionsResponse
import com.zionhuang.innertube.models.response.GetTranscriptResponse
import com.zionhuang.innertube.models.response.NextResponse
import com.zionhuang.innertube.models.response.PipedResponse
import com.zionhuang.innertube.models.response.PlayerResponse
import com.zionhuang.innertube.models.response.SearchResponse
import com.zionhuang.innertube.pages.AlbumPage
import com.zionhuang.innertube.pages.ArtistItemsContinuationPage
import com.zionhuang.innertube.pages.ArtistItemsPage
import com.zionhuang.innertube.pages.ArtistPage
import com.zionhuang.innertube.pages.BrowseResult
import com.zionhuang.innertube.pages.ExplorePage
import com.zionhuang.innertube.pages.HistoryPage
import com.zionhuang.innertube.pages.LibraryContinuationPage
import com.zionhuang.innertube.pages.LibraryPage
import com.zionhuang.innertube.pages.MoodAndGenres
import com.zionhuang.innertube.pages.NewReleaseAlbumPage
import com.zionhuang.innertube.pages.NextPage
import com.zionhuang.innertube.pages.NextResult
import com.zionhuang.innertube.pages.PlaylistContinuationPage
import com.zionhuang.innertube.pages.PlaylistPage
import com.zionhuang.innertube.pages.RelatedPage
import com.zionhuang.innertube.pages.SearchPage
import com.zionhuang.innertube.pages.SearchResult
import com.zionhuang.innertube.pages.SearchSuggestionPage
import com.zionhuang.innertube.pages.SearchSummary
import com.zionhuang.innertube.pages.SearchSummaryPage
import com.zionhuang.innertube.utils.isPrivateId
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.Proxy
import kotlin.random.Random

/**
 * Parse useful data with [InnerTube] sending requests.
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
object YouTube {
    private val innerTube = InnerTube()

    var locale: YouTubeLocale
        get() = innerTube.locale
        set(value) {
            innerTube.locale = value
        }
    var visitorData: String
        get() = innerTube.visitorData
        set(value) {
            innerTube.visitorData = value
        }
    var cookie: String?
        get() = innerTube.cookie
        set(value) {
            innerTube.cookie = value
        }
    var proxy: Proxy?
        get() = innerTube.proxy
        set(value) {
            innerTube.proxy = value
        }

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = runCatching {
        val response = innerTube.getSearchSuggestions(WEB_REMIX, query).body<GetSearchSuggestionsResponse>()
        SearchSuggestions(
            queries = response.contents?.getOrNull(0)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull { content ->
                content.searchSuggestionRenderer?.suggestion?.runs?.joinToString(separator = "") { it.text }
            }.orEmpty(),
            recommendedItems = response.contents?.getOrNull(1)?.searchSuggestionsSectionRenderer?.contents?.mapNotNull {
                it.musicResponsiveListItemRenderer?.let { renderer ->
                    SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                }
            }.orEmpty()
        )
    }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> = runCatching {
        val response = innerTube.search(WEB_REMIX, query).body<SearchResponse>()
        SearchSummaryPage(
            summaries = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { it ->
                if (it.musicCardShelfRenderer != null)
                    SearchSummary(
                        title = it.musicCardShelfRenderer.header.musicCardShelfHeaderBasicRenderer.title.runs?.firstOrNull()?.text ?: return@mapNotNull null,
                        items = listOfNotNull(SearchSummaryPage.fromMusicCardShelfRenderer(it.musicCardShelfRenderer))
                            .plus(
                                it.musicCardShelfRenderer.contents
                                    ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                    ?.mapNotNull(SearchSummaryPage.Companion::fromMusicResponsiveListItemRenderer)
                                    .orEmpty()
                            )
                            .distinctBy { it.id }
                            .ifEmpty { null } ?: return@mapNotNull null
                    )
                else
                    SearchSummary(
                        title = it.musicShelfRenderer?.title?.runs?.firstOrNull()?.text ?: return@mapNotNull null,
                        items = it.musicShelfRenderer.contents
                            ?.mapNotNull {
                                SearchSummaryPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                            }
                            ?.distinctBy { it.id }
                            ?.ifEmpty { null } ?: return@mapNotNull null
                    )
            }!!
        )
    }

    suspend fun search(query: String, filter: SearchFilter): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, query, filter.value).body<SearchResponse>()
        SearchResult(
            items = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.lastOrNull()
                ?.musicShelfRenderer?.contents?.mapNotNull {
                    SearchPage.toYTItem(it.musicResponsiveListItemRenderer)
                }.orEmpty(),
            continuation = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.lastOrNull()
                ?.musicShelfRenderer?.continuations?.getContinuation()
        )
    }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> = runCatching {
        val response = innerTube.search(WEB_REMIX, continuation = continuation).body<SearchResponse>()
        SearchResult(
            items = response.continuationContents?.musicShelfContinuation?.contents
                ?.mapNotNull {
                    SearchPage.toYTItem(it.musicResponsiveListItemRenderer)
                }!!,
            continuation = response.continuationContents.musicShelfContinuation.continuations?.getContinuation()
        )
    }

    suspend fun album(browseId: String): Result<AlbumPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId, setLogin = isPrivateId(browseId)).body<BrowseResponse>()

        val album = AlbumItem(
            browseId = browseId,
            playlistId = AlbumPage.getPlaylistId(response)!!,
            title = AlbumPage.getTitle(response)!!,
            artists = AlbumPage.getArtists(response),
            year = AlbumPage.getYear(response),
            thumbnail = AlbumPage.getThumbnail(response)!!
        )
        val songs = AlbumPage.getSongs(response, album)
        AlbumPage(album, songs)
    }

    suspend fun albumSongs(playlistId: String): Result<List<SongItem>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, "VL$playlistId").body<BrowseResponse>()

        val contents =
            response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                ?.musicPlaylistShelfRenderer?.contents ?:
            response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents

        val songs = contents?.mapNotNull {
            AlbumPage.getSong(it.musicResponsiveListItemRenderer)
        }
        songs!!
    }

    suspend fun artist(browseId: String): Result<ArtistPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId).body<BrowseResponse>()

        ArtistPage(
            artist = ArtistItem(
                id = browseId,
                title = response.header?.musicImmersiveHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                thumbnail = response.header?.musicImmersiveHeaderRenderer?.thumbnail?.musicThumbnailRenderer?.getThumbnailUrl()
                    ?: response.header?.musicVisualHeaderRenderer?.foregroundThumbnail?.musicThumbnailRenderer?.getThumbnailUrl()!!,
                channelId = response.header?.musicImmersiveHeaderRenderer?.subscriptionButton?.subscribeButtonRenderer?.channelId!!,
                playEndpoint = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicShelfRenderer
                    ?.contents?.firstOrNull()?.musicResponsiveListItemRenderer?.overlay?.musicItemThumbnailOverlayRenderer
                    ?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                shuffleEndpoint = response.header.musicImmersiveHeaderRenderer.playButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint,
                radioEndpoint = response.header.musicImmersiveHeaderRenderer.startRadioButton?.buttonRenderer?.navigationEndpoint?.watchEndpoint
            ),
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull(ArtistPage::fromSectionListRendererContent)!!,
            description = response.header.musicImmersiveHeaderRenderer.description?.runs?.firstOrNull()?.text
        )
    }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        val gridRenderer = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.gridRenderer
        if (gridRenderer != null) {
            ArtistItemsPage(
                title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = gridRenderer.items.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { renderer ->
                        ArtistItemsPage.fromMusicTwoRowItemRenderer(renderer)
                    }
                },
                continuation = null
            )
        } else {
            ArtistItemsPage(
                title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text!!,
                items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                    ?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                        ArtistItemsPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                    }!!,
                continuation = response.contents.singleColumnBrowseResultsRenderer.tabs.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                    ?.musicPlaylistShelfRenderer?.continuations?.getContinuation()
            )
        }
    }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, continuation = continuation).body<BrowseResponse>()
        ArtistItemsContinuationPage(
            items = response.continuationContents?.musicPlaylistShelfContinuation?.contents?.mapNotNull {
                ArtistItemsContinuationPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
            }!!,
            continuation = response.continuationContents.musicPlaylistShelfContinuation.continuations?.getContinuation()
        )
    }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "VL$playlistId",
            setLogin = true
        ).body<BrowseResponse>()

        if (response.header != null)
            playlistOld(playlistId, response)
        else
            playlistNew(playlistId, response)
    }

    private fun playlistOld(playlistId: String, response: BrowseResponse): PlaylistPage {
        val header = response.header?.musicDetailHeaderRenderer ?: response.header?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicDetailHeaderRenderer!!
        val editable = response.header?.musicEditablePlaylistDetailHeaderRenderer != null

        return PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = header.title.runs?.firstOrNull()?.text!!,
                author = header.subtitle.runs?.getOrNull(2)?.let {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                },
                songCountText = header.secondSubtitle.runs?.firstOrNull()?.text,
                thumbnail = header.thumbnail.croppedSquareThumbnailRenderer?.getThumbnailUrl()!!,
                playEndpoint =  response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                    ?.musicPlaylistShelfRenderer?.contents?.firstOrNull()?.musicResponsiveListItemRenderer
                    ?.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?.playNavigationEndpoint?.watchEndpoint,
                shuffleEndpoint = header.menu.menuRenderer.topLevelButtons?.firstOrNull()?.buttonRenderer?.navigationEndpoint?.watchPlaylistEndpoint!!,
                radioEndpoint = header.menu.menuRenderer.items.find {
                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint!!,
                isEditable = editable
            ),
            songs = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                ?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                }!!,
            songsContinuation = response.contents.singleColumnBrowseResultsRenderer.tabs.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                ?.musicPlaylistShelfRenderer?.continuations?.getContinuation(),
            continuation = response.contents.singleColumnBrowseResultsRenderer.tabs.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.continuations?.getContinuation()
        )
    }

    private fun playlistNew(playlistId: String, response: BrowseResponse): PlaylistPage {
        val header = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.musicResponsiveHeaderRenderer
            ?: response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                ?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer

        val editable = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicEditablePlaylistDetailHeaderRenderer != null

        return PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = header?.title?.runs?.firstOrNull()?.text!!,
                author = header.straplineTextOne?.runs?.firstOrNull()?.let {
                    Artist(
                        name = it.text,
                        id = it.navigationEndpoint?.browseEndpoint?.browseId
                    )
                },
                songCountText = header.secondSubtitle?.runs?.firstOrNull()?.text,
                thumbnail = response.background?.musicThumbnailRenderer?.getThumbnailUrl(),
                playEndpoint = header.buttons.getOrNull(1)?.musicPlayButtonRenderer
                    ?.playNavigationEndpoint?.watchEndpoint,
                shuffleEndpoint = header.buttons.getOrNull(2)?.menuRenderer?.items?.find {
                    it.menuNavigationItemRenderer?.icon?.iconType == "MUSIC_SHUFFLE"
                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                radioEndpoint = header.buttons.getOrNull(2)?.menuRenderer?.items?.find {
                    it.menuNavigationItemRenderer?.icon?.iconType == "MIX"
                }?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint,
                isEditable = editable
            ),
            songs = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
                ?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.mapNotNull {
                    PlaylistPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
                }!!,
            songsContinuation = response.contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer
                .contents.firstOrNull()?.musicPlaylistShelfRenderer?.continuations?.getContinuation(),
            continuation = response.contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer
                .continuations?.getContinuation()
        )
    }

    suspend fun playlistContinuation(continuation: String) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()
        PlaylistContinuationPage(
            songs = response.continuationContents?.musicPlaylistShelfContinuation?.contents?.mapNotNull {
                PlaylistPage.fromMusicResponsiveListItemRenderer(it.musicResponsiveListItemRenderer)
            }!!,
            continuation = response.continuationContents.musicPlaylistShelfContinuation.continuations?.getContinuation()
        )
    }

    suspend fun explore(): Result<ExplorePage> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_explore").body<BrowseResponse>()
        ExplorePage(
            newReleaseAlbums = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_new_releases_albums"
            }?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicTwoRowItemRenderer }
                ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer).orEmpty(),
            moodAndGenres = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.find {
                it.musicCarouselShelfRenderer?.header?.musicCarouselShelfBasicHeaderRenderer?.moreContentButton?.buttonRenderer?.navigationEndpoint?.browseEndpoint?.browseId == "FEmusic_moods_and_genres"
            }?.musicCarouselShelfRenderer?.contents
                ?.mapNotNull { it.musicNavigationButtonRenderer }
                ?.mapNotNull(MoodAndGenres.Companion::fromMusicNavigationButtonRenderer)
                .orEmpty()
        )
    }

    suspend fun newReleaseAlbums(): Result<List<AlbumItem>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_new_releases_albums").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()?.gridRenderer?.items
            ?.mapNotNull { it.musicTwoRowItemRenderer }
            ?.mapNotNull(NewReleaseAlbumPage::fromMusicTwoRowItemRenderer)
            .orEmpty()
    }

    suspend fun moodAndGenres(): Result<List<MoodAndGenres>> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = "FEmusic_moods_and_genres").body<BrowseResponse>()
        response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents!!
            .mapNotNull(MoodAndGenres.Companion::fromSectionListRendererContent)
    }

    suspend fun browse(browseId: String, params: String?): Result<BrowseResult> = runCatching {
        val response = innerTube.browse(WEB_REMIX, browseId = browseId, params = params).body<BrowseResponse>()
        BrowseResult(
            title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
            items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { content ->
                when {
                    content.gridRenderer != null -> {
                        BrowseResult.Item(
                            title = content.gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.gridRenderer.items
                                .mapNotNull(GridRenderer.Item::musicTwoRowItemRenderer)
                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }

                    content.musicCarouselShelfRenderer != null -> {
                        BrowseResult.Item(
                            title = content.musicCarouselShelfRenderer.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            items = content.musicCarouselShelfRenderer.contents
                                .mapNotNull(MusicCarouselShelfRenderer.Content::musicTwoRowItemRenderer)
                                .mapNotNull(RelatedPage.Companion::fromMusicTwoRowItemRenderer)
                        )
                    }

                    else -> null
                }
            }.orEmpty()
        )
    }

    suspend fun library(browseId: String, tabIndex: Int = 0) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = browseId,
            setLogin = true
        ).body<BrowseResponse>()

        val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs

        val contents = if (tabs != null && tabs.size >= tabIndex) {
                tabs[tabIndex].tabRenderer.content?.sectionListRenderer?.contents?.firstOrNull()
            }
            else {
                null
            }

        when {
            contents?.gridRenderer != null -> {
                LibraryPage(
                    items = contents.gridRenderer.items
                        .mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                    continuation = contents.gridRenderer.continuations?.firstOrNull()?.
                        nextContinuationData?.continuation
                )
            }

            else -> { // contents?.musicShelfRenderer != null
                LibraryPage(
                    items = contents?.musicShelfRenderer?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfRenderer.continuations?.firstOrNull()?.
                        nextContinuationData?.continuation
                )
            }
        }
    }

    suspend fun libraryContinuation(continuation: String) = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.continuationContents

        when {
            contents?.gridContinuation != null -> {
                LibraryContinuationPage(
                    items = contents.gridContinuation.items
                        .mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) },
                    continuation = contents.gridContinuation.continuations?.firstOrNull()?.
                        nextContinuationData?.continuation
                )
            }

            else -> { // contents?.musicShelfContinuation != null
                LibraryContinuationPage(
                    items = contents?.musicShelfContinuation?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfContinuation.continuations?.firstOrNull()?.
                        nextContinuationData?.continuation
                )
            }
        }
    }

    suspend fun libraryRecentActivity(): Result<LibraryPage> = runCatching {
        val continuation = LibraryFilter.FILTER_RECENT_ACTIVITY.value

        val response = innerTube.browse(
            client = WEB_REMIX,
            browseContinuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val items = response.continuationContents?.sectionListContinuation?.contents?.firstOrNull()
            ?.gridRenderer?.items!!.mapNotNull {
                it.musicTwoRowItemRenderer?.let { renderer ->
                    LibraryPage.fromMusicTwoRowItemRenderer(renderer)
                }
            }.toMutableList()

        /*
         * We need to fetch the artist page when accessing the library because it allows to have
         * a proper playEndpoint, which is needed to correctly report the playing indicator in
         * the home page.
         *
         * Despite this, we need to use the old thumbnail because it's the proper format for a
         * square picture, which is what we need.
         */
        items.forEachIndexed { index, item ->
            if (item is ArtistItem)
                items[index] = artist(item.id).getOrNull()?.artist!!.copy(thumbnail = item.thumbnail)
        }

        LibraryPage(
            items = items,
            continuation = null
        )
    }

    suspend fun musicHistory() = runCatching {
        val response = innerTube.browse(
            client = WEB_REMIX,
            browseId = "FEmusic_history",
            setLogin = true
        ).body<BrowseResponse>()

        HistoryPage(
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull {
                    it.musicShelfRenderer?.let { musicShelfRenderer ->
                        HistoryPage.fromMusicShelfRenderer(musicShelfRenderer)
                    }
                }
        )
    }

    suspend fun likeVideo(videoId: String, like: Boolean) = runCatching {
        if (like)
            innerTube.likeVideo(WEB_REMIX, videoId)
        else
            innerTube.unlikeVideo(WEB_REMIX, videoId)
    }

    suspend fun likePlaylist(playlistId: String, like: Boolean) = runCatching {
        if (like)
            innerTube.likePlaylist(WEB_REMIX, playlistId)
        else
            innerTube.unlikePlaylist(WEB_REMIX, playlistId)
    }

    suspend fun subscribeChannel(channelId: String, subscribe: Boolean) = runCatching {
        if (subscribe)
            innerTube.subscribeChannel(WEB_REMIX, channelId)
        else
            innerTube.unsubscribeChannel(WEB_REMIX, channelId)
    }

    suspend fun getChannelId(browseId: String): String {
        YouTube.artist(browseId).onSuccess {
            return it.artist.channelId!!
        }
        return ""
    }

    suspend fun addToPlaylist(playlistId: String, videoId: String) = runCatching {
        innerTube.addToPlaylist(WEB_REMIX, playlistId, videoId)
    }

    suspend fun addPlaylistToPlaylist(playlistId: String, addPlaylistId: String) = runCatching {
        innerTube.addPlaylistToPlaylist(WEB_REMIX, playlistId, addPlaylistId)
    }

    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) = runCatching {
        innerTube.removeFromPlaylist(WEB_REMIX, playlistId, videoId, setVideoId)
    }

    suspend fun moveSongPlaylist(playlistId: String, setVideoId: String, successorSetVideoId: String) = runCatching {
        innerTube.moveSongPlaylist(WEB_REMIX, playlistId, setVideoId, successorSetVideoId)
    }

    suspend fun createPlaylist(title: String) = runCatching {
        innerTube.createPlaylist(WEB_REMIX, title).body<CreatePlaylistResponse>().playlistId
    }

    suspend fun renamePlaylist(playlistId: String, name: String) = runCatching {
        innerTube.renamePlaylist(WEB_REMIX, playlistId, name)
    }

    suspend fun deletePlaylist(playlistId: String) = runCatching {
        innerTube.deletePlaylist(WEB_REMIX, playlistId)
    }

    suspend fun player(videoId: String, playlistId: String? = null, registerPlayback: Boolean = true): Result<PlayerResponse> = runCatching {
        val playerResponse = innerTube.player(ANDROID_MUSIC, videoId, playlistId).body<PlayerResponse>()

        if (playerResponse.playabilityStatus.status == "OK") {
            if (registerPlayback)
                registerPlayback(playlistId, playerResponse.playbackTracking?.videostatsPlaybackUrl?.baseUrl!!)

            return@runCatching playerResponse
        }

        val safePlayerResponse = innerTube.player(TVHTML5, videoId, playlistId).body<PlayerResponse>()
        if (safePlayerResponse.playabilityStatus.status != "OK") {
            return@runCatching playerResponse
        }
        val audioStreams = innerTube.pipedStreams(videoId).body<PipedResponse>().audioStreams
        safePlayerResponse.copy(
            streamingData = safePlayerResponse.streamingData?.copy(
                adaptiveFormats = safePlayerResponse.streamingData.adaptiveFormats.mapNotNull { adaptiveFormat ->
                    audioStreams.find { it.bitrate == adaptiveFormat.bitrate }?.let {
                        adaptiveFormat.copy(
                            url = it.url
                        )
                    }
                }
            )
        )
    }

    suspend fun registerPlayback(playlistId: String? = null, playbackTracking: String) {
        val cpn = (1..16).map {
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"[Random.Default.nextInt(
                0,
                64
            )]
        }.joinToString("")

        val playbackUrl = playbackTracking.replace(
            "https://s.youtube.com",
            "https://music.youtube.com",
        )

        innerTube.registerPlayback(
            url = playbackUrl,
            playlistId = playlistId,
            cpn = cpn
        )
    }

    suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult> = runCatching {
        val response = innerTube.next(
            WEB_REMIX,
            endpoint.videoId,
            endpoint.playlistId,
            endpoint.playlistSetVideoId,
            endpoint.index,
            endpoint.params,
            continuation).body<NextResponse>()
        val playlistPanelRenderer = response.continuationContents?.playlistPanelContinuation
            ?: response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer
                .watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer
                ?.content?.playlistPanelRenderer!!
        val watchEndpointResponse = response.currentVideoEndpoint?.watchEndpoint
        val title = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer
                .watchNextTabbedResultsRenderer.tabs[0].tabRenderer.content?.musicQueueRenderer
                ?.header?.musicQueueHeaderRenderer?.subtitle?.runs?.firstOrNull()?.text
        // load automix items
        playlistPanelRenderer.contents.lastOrNull()?.automixPreviewVideoRenderer?.content?.automixPlaylistVideoRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.let { watchPlaylistEndpoint ->
            return@runCatching next(watchPlaylistEndpoint).getOrThrow().let { result ->
                result.copy(
                    title = title,
                    items = playlistPanelRenderer.contents.mapNotNull {
                        it.playlistPanelVideoRenderer?.let { renderer ->
                            NextPage.fromPlaylistPanelVideoRenderer(renderer)
                        }
                    } + result.items,
                    lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
                    relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
                    currentIndex = playlistPanelRenderer.currentIndex,
                    endpoint = watchPlaylistEndpoint
                )
            }
        }
        NextResult(
            title = title,
            items = playlistPanelRenderer.contents.mapNotNull {
                it.playlistPanelVideoRenderer?.let(NextPage::fromPlaylistPanelVideoRenderer)
            },
            currentIndex = watchEndpointResponse?.index,
            lyricsEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint,
            relatedEndpoint = response.contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer.watchNextTabbedResultsRenderer.tabs.getOrNull(2)?.tabRenderer?.endpoint?.browseEndpoint,
            continuation = playlistPanelRenderer.continuations?.getContinuation(),
            endpoint = endpoint
        )
    }

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId, endpoint.params).body<BrowseResponse>()
        response.contents?.sectionListRenderer?.contents?.firstOrNull()?.musicDescriptionShelfRenderer?.description?.runs?.firstOrNull()?.text
    }

    suspend fun related(endpoint: BrowseEndpoint) = runCatching {
        val response = innerTube.browse(WEB_REMIX, endpoint.browseId).body<BrowseResponse>()
        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()
        response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
            sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
                when (val item = content.musicResponsiveListItemRenderer?.let(RelatedPage.Companion::fromMusicResponsiveListItemRenderer)
                    ?: content.musicTwoRowItemRenderer?.let(RelatedPage.Companion::fromMusicTwoRowItemRenderer)) {
                    is SongItem -> if (content.musicResponsiveListItemRenderer?.overlay
                            ?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchEndpoint?.watchEndpointMusicSupportedConfigs
                            ?.watchEndpointMusicConfig?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                    ) songs.add(item)

                    is AlbumItem -> albums.add(item)
                    is ArtistItem -> artists.add(item)
                    is PlaylistItem -> playlists.add(item)
                    null -> {}
                }
            }
        }
        RelatedPage(songs, albums, artists, playlists)
    }

    suspend fun queue(videoIds: List<String>? = null, playlistId: String? = null): Result<List<SongItem>> = runCatching {
        if (videoIds != null) {
            assert(videoIds.size <= MAX_GET_QUEUE_SIZE) // Max video limit
        }
        innerTube.getQueue(WEB_REMIX, videoIds, playlistId).body<GetQueueResponse>().queueDatas
            .mapNotNull {
                it.content.playlistPanelVideoRenderer?.let { renderer ->
                    NextPage.fromPlaylistPanelVideoRenderer(renderer)
                }
            }
    }

    suspend fun transcript(videoId: String): Result<String> = runCatching {
        val response = innerTube.getTranscript(WEB, videoId).body<GetTranscriptResponse>()
        response.actions?.firstOrNull()?.updateEngagementPanelAction?.content?.transcriptRenderer?.body?.transcriptBodyRenderer?.cueGroups?.joinToString(separator = "\n") { group ->
            val time = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.startOffsetMs
            val text = group.transcriptCueGroupRenderer.cues[0].transcriptCueRenderer.cue.simpleText
                .trim('♪')
                .trim(' ')
            "[%02d:%02d.%03d]$text".format(time / 60000, (time / 1000) % 60, time % 1000)
        }!!
    }

    suspend fun visitorData(): Result<String> = runCatching {
        Json.parseToJsonElement(innerTube.getSwJsData().bodyAsText().substring(5))
            .jsonArray[0]
            .jsonArray[2]
            .jsonArray.first { (it as? JsonPrimitive)?.content?.startsWith(VISITOR_DATA_PREFIX) == true }
            .jsonPrimitive.content
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        innerTube.accountMenu(WEB_REMIX).body<AccountMenuResponse>()
            .actions[0].openPopupAction.popup.multiPageMenuRenderer
            .header?.activeAccountHeaderRenderer
            ?.toAccountInfo()!!
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val FILTER_SONG = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val FILTER_VIDEO = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ALBUM = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_ARTIST = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val FILTER_FEATURED_PLAYLIST = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val FILTER_COMMUNITY_PLAYLIST = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
        }
    }

    @JvmInline
    value class LibraryFilter(val value: String) {
        companion object {
            val FILTER_RECENT_ACTIVITY = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCaEFCb0FZQg%3D%3D")
            val FILTER_RECENTLY_PLAYED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpYnJhcnlfbGFuZGluZxoQZ2dNR0tnUUlCUkFCb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_ALPHABETICAL = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBUkFBb0FZQg%3D%3D")
            val FILTER_PLAYLISTS_RECENTLY_SAVED = LibraryFilter("4qmFsgIrEhdGRW11c2ljX2xpa2VkX3BsYXlsaXN0cxoQZ2dNR0tnUUlBQkFCb0FZQg%3D%3D")
        }
    }

    const val MAX_GET_QUEUE_SIZE = 1000

    private const val VISITOR_DATA_PREFIX = "Cgt"

    const val DEFAULT_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"
}
