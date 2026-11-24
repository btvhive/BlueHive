package com.example.bluehive.singleShelfComponents

import android.util.Log
import com.example.bluehive.models.MediaBrowseResponse
import com.example.bluehive.repository.MediaRecyclerViewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Centralized data loading for all media types and shelves
 */
class ShelfRepository(
    private val repo: MediaRecyclerViewData = MediaRecyclerViewData()
) {

    companion object {
        private const val TAG = "HomeSingleShelfRepository"

        /**
         * One and one: a[0], b[0], a[1], b[1]... skipping any title already
         * placed or in [exclude]; the longer list's tail follows. (Pure, tested.)
         */
        fun <T : Any> interleave(a: List<T>, b: List<T>, exclude: Set<Int>, id: (T) -> Int): List<T> {
            val used = HashSet(exclude)
            val out = ArrayList<T>(a.size + b.size)
            for (i in 0 until maxOf(a.size, b.size)) {
                a.getOrNull(i)?.let { if (used.add(id(it))) out += it }
                b.getOrNull(i)?.let { if (used.add(id(it))) out += it }
            }
            return out
        }

        fun interleave(a: List<com.example.bluehive.models.MediaItem>, b: List<com.example.bluehive.models.MediaItem>,
                       exclude: Set<Int>) = interleave(a, b, exclude) { it.tmdbId }
    }

    /**
     * Load a page of content for any media type and shelf combination
     */
    suspend fun loadPage(
        mediaType: MediaType,
        shelf: ContentShelf,
        page: Int,
        pageSize: Int,
        profileId: Int = -1
    ): MediaBrowseResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "📡 Loading: $mediaType / $shelf / page $page")

        if (mediaType != MediaType.ANIME) when (shelf) {
            ContentShelf.NEW_RELEASES -> return@withContext loadNewReleasesPage(mediaType, page, pageSize, profileId)
            ContentShelf.FRESH_PICKS  -> return@withContext loadFreshPicksPage(mediaType, page, pageSize, profileId)
            ContentShelf.POPULAR      -> return@withContext loadPopularMixPage(mediaType, null, page, pageSize, profileId)
            else -> Unit
        }
        when (mediaType) {
            MediaType.MOVIES   -> loadMoviesPage(shelf, page, pageSize, profileId)
            MediaType.TV_SHOWS -> loadTVShowsPage(shelf, page, pageSize, profileId)
            MediaType.ANIME    -> loadAnimePage(shelf, page, pageSize)
        }
    }

    /**
     * NEW RELEASES (2026-11-21), the first row: every new title, newest on the
     * left (NewContent). IF IT CANNOT LOAD, THE ROW SHOWS WHAT'S POPULAR in its
     * place rather than standing empty: the loading cover waits for the first
     * row's first page, and an empty first row would strand focus.
     */
    private suspend fun loadNewReleasesPage(mediaType: MediaType, page: Int, pageSize: Int, profileId: Int): MediaBrowseResponse {
        val items = NewContent.page(mediaType, page, pageSize)
        if (items == null) {
            if (page == 0) Log.e(TAG, "New Releases unavailable for $mediaType -- showing What's Popular in its place")
            return loadPopularMixPage(mediaType, null, page, pageSize, profileId)
        }
        return MediaBrowseResponse(
            groupSlug = "new-releases", groupName = "New Releases",
            mediaType = if (mediaType == MediaType.TV_SHOWS) "tv" else "movie",
            totalCount = -1, count = items.size, limit = pageSize, offset = page * pageSize, items = items,
        )
    }

    /**
     * WHAT'S POPULAR = popular and top rated, ONE AND ONE (2026-11-21, the
     * owner's design; the Top Rated row is gone): popular #1, top rated #1,
     * popular #2, top rated #2... A title in both lists appears once -- also
     * across pages, against the pages already built.
     *
     * [popularFirstPage] lets the loading screen's prefetched popular page be
     * used instead of fetching it again (ShelfStack's warm path).
     */
    suspend fun loadPopularMixPage(
        mediaType: MediaType,
        popularFirstPage: List<com.example.bluehive.models.MediaItem>?,
        page: Int,
        pageSize: Int,
        profileId: Int,
    ): MediaBrowseResponse {
        val popular = if (popularFirstPage != null && page == 0) null
            else if (mediaType == MediaType.TV_SHOWS) loadTVShowsPage(ContentShelf.POPULAR, page, pageSize, profileId)
            else loadMoviesPage(ContentShelf.POPULAR, page, pageSize, profileId)
        val popularItems = popularFirstPage?.takeIf { page == 0 } ?: popular!!.items
        val top = runCatching {
            if (mediaType == MediaType.TV_SHOWS) loadTVShowsPage(ContentShelf.TOP_RATED, page, pageSize, profileId)
            else loadMoviesPage(ContentShelf.TOP_RATED, page, pageSize, profileId)
        }.getOrNull()?.items.orEmpty()
        val seen = synchronized(mixSeen) {
            if (page == 0) mixSeen.remove(mediaType)
            mixSeen.getOrPut(mediaType) { HashMap() }.filterKeys { it < page }.values.flatten().toSet()
        }
        val mixed = interleave(popularItems, top, seen)
        synchronized(mixSeen) { mixSeen.getOrPut(mediaType) { HashMap() }[page] = mixed.map { it.tmdbId } }
        return MediaBrowseResponse(
            groupSlug = null, groupName = null, mediaType = if (mediaType == MediaType.TV_SHOWS) "tv" else "movie",
            totalCount = popular?.totalCount ?: -1, count = mixed.size, limit = pageSize, offset = page * pageSize,
            items = mixed,
        )
    }

    /** What's Popular pages already built, per tab: page -> ids, to keep later pages free of repeats. */
    private val mixSeen = HashMap<MediaType, HashMap<Int, List<Int>>>()

    /**
     * Fresh Picks, from one fetch (FreshPicks). IF IT CANNOT LOAD, THE ROW
     * SHOWS POPULAR instead of standing empty: it is the first row, the home
     * screen's loading cover waits for the first row's first page, and an
     * empty first row would also strand focus under the tabs.
     */
    private suspend fun loadFreshPicksPage(
        mediaType: MediaType,
        page: Int,
        pageSize: Int,
        profileId: Int,
    ): MediaBrowseResponse {
        val items = FreshPicks.page(mediaType, page, pageSize, profileId)
        if (items == null) {
            Log.e(TAG, "Fresh Picks unavailable for $mediaType -- showing Popular in its place")
            return when (mediaType) {
                MediaType.TV_SHOWS -> loadTVShowsPage(ContentShelf.POPULAR, page, pageSize, profileId)
                else -> loadMoviesPage(ContentShelf.POPULAR, page, pageSize, profileId)
            }
        }
        return MediaBrowseResponse(
            groupSlug = "fresh-picks", groupName = "Fresh Picks",
            mediaType = if (mediaType == MediaType.TV_SHOWS) "tv" else "movie",
            totalCount = FreshPicks.ROW_SIZE, count = items.size, limit = pageSize,
            offset = page * pageSize, items = items,
        )
    }

    /**
     * Load movies for a specific shelf
     */
    private suspend fun loadMoviesPage(
        shelf: ContentShelf,
        page: Int,
        pageSize: Int,
        profileId: Int = -1
    ): MediaBrowseResponse {
        // Convert -1 sentinel to null so the API omits the query param entirely.
        val resolvedProfileId = profileId.takeIf { it > 0 }
        return when (shelf) {
            // FRESH_PICKS lands here only as loadFreshPicksPage's fallback.
            ContentShelf.POPULAR, ContentShelf.FRESH_PICKS, ContentShelf.NEW_RELEASES -> repo.loadPopularMovies(page, pageSize)
            ContentShelf.TOP_RATED   -> repo.loadTopRatedMovies(page, pageSize)
            ContentShelf.NETFLIX     -> repo.loadNetflixMovies(page, pageSize, resolvedProfileId)
            ContentShelf.PRIME_VIDEO -> repo.loadPrimeVideoMovies(page, pageSize)
            ContentShelf.DISNEY_PLUS -> repo.loadDisneyPlusMovies(page, pageSize)
            ContentShelf.HULU        -> repo.loadHuluMovies(page, pageSize)
            ContentShelf.PARAMOUNT_PLUS -> repo.loadParamountPlusMovies(page, pageSize)
            ContentShelf.APPLE_TV_PLUS  -> repo.loadAppleTVPlusMovies(page, pageSize)
            ContentShelf.HBO_MAX     -> repo.loadHboMaxMovies(page, pageSize)
            ContentShelf.PEACOCK     -> repo.loadPeacockMovies(page, pageSize)
            ContentShelf.ANIME_SERIES,
            ContentShelf.ANIME_MOVIES,
            ContentShelf.ANIME_NEW_SEASON -> MediaBrowseResponse(
                groupSlug = null, groupName = null, mediaType = "movie",
                totalCount = 0, count = 0, limit = pageSize,
                offset = page * pageSize, items = emptyList()
            )
        }
    }

    /**
     * Load TV shows for a specific shelf
     */
    private suspend fun loadTVShowsPage(
        shelf: ContentShelf,
        page: Int,
        pageSize: Int,
        profileId: Int = -1
    ): MediaBrowseResponse {
        val resolvedProfileId = profileId.takeIf { it > 0 }
        return when (shelf) {
            ContentShelf.POPULAR, ContentShelf.FRESH_PICKS, ContentShelf.NEW_RELEASES -> repo.loadPopularTVShows(page, pageSize)
            ContentShelf.TOP_RATED   -> repo.loadTopRatedTVShows(page, pageSize)
            ContentShelf.NETFLIX     -> repo.loadNetflixTVShows(page, pageSize, resolvedProfileId)
            ContentShelf.PRIME_VIDEO -> repo.loadPrimeVideoTVShows(page, pageSize)
            ContentShelf.DISNEY_PLUS -> repo.loadDisneyPlusTVShows(page, pageSize)
            ContentShelf.HULU        -> repo.loadHuluTVShows(page, pageSize)
            ContentShelf.PARAMOUNT_PLUS -> repo.loadParamountPlusTVShows(page, pageSize)
            ContentShelf.APPLE_TV_PLUS  -> repo.loadAppleTVPlusTVShows(page, pageSize)
            ContentShelf.HBO_MAX     -> repo.loadHBOMaxTVShows(page, pageSize)
            ContentShelf.PEACOCK     -> repo.loadPeacockTVShows(page, pageSize)
            ContentShelf.ANIME_SERIES,
            ContentShelf.ANIME_MOVIES,
            ContentShelf.ANIME_NEW_SEASON -> MediaBrowseResponse(
                groupSlug = null, groupName = null, mediaType = "tv",
                totalCount = 0, count = 0, limit = pageSize,
                offset = page * pageSize, items = emptyList()
            )
        }
    }

    /**
     * Load anime for a specific shelf.
     * ANIME_SERIES → popular animated TV series (Animation genre, English language).
     * ANIME_MOVIES → popular animated movies  (Animation genre, English language).
     */
    private suspend fun loadAnimePage(
        shelf: ContentShelf,
        page: Int,
        pageSize: Int
    ): MediaBrowseResponse {
        return when (shelf) {
            ContentShelf.ANIME_SERIES -> repo.loadPopularAnimeSeries(page, pageSize)
            ContentShelf.ANIME_MOVIES -> repo.loadPopularAnimeMovies(page, pageSize)
            ContentShelf.ANIME_NEW_SEASON -> repo.loadNewThisSeasonAnime(page, pageSize)
            else -> {
                Log.w(TAG, "⚠️ Unknown anime shelf $shelf, returning empty")
                MediaBrowseResponse(
                    groupSlug = null,
                    groupName = null,
                    mediaType = "anime",
                    totalCount = 0,
                    count = 0,
                    limit = pageSize,
                    offset = page * pageSize,
                    items = emptyList()
                )
            }
        }
    }



    /**
     * Get the display title for a shelf based on media type
     */
    fun getShelfTitle(mediaType: MediaType, shelf: ContentShelf): String {
        if (shelf == ContentShelf.NEW_RELEASES) return "New Releases"
        if (shelf == ContentShelf.FRESH_PICKS) return "Fresh Picks"
        // Special cases where title differs by media type
        if (shelf == ContentShelf.POPULAR) {
            return when (mediaType) {
                MediaType.MOVIES -> "What's Popular"
                MediaType.TV_SHOWS -> "What's Popular"
                MediaType.ANIME -> "Popular Anime"
            }
        }

        if (shelf == ContentShelf.TOP_RATED) {
            return when (mediaType) {
                MediaType.MOVIES -> "Top Rated"
                MediaType.TV_SHOWS -> "Top Rated TV"
                MediaType.ANIME -> "Top Rated Anime"
            }
        }

        // Streaming services use same name for all media types
        return when (shelf) {
            ContentShelf.NETFLIX -> "Netflix"
            ContentShelf.PRIME_VIDEO -> "Prime Video"
            ContentShelf.DISNEY_PLUS -> "Disney+"
            ContentShelf.HULU -> "Hulu"
            ContentShelf.PARAMOUNT_PLUS -> "Paramount+"
            ContentShelf.APPLE_TV_PLUS -> "Apple TV+"
            ContentShelf.HBO_MAX -> "HBO Max"
            ContentShelf.PEACOCK -> "Peacock"
            ContentShelf.ANIME_SERIES -> "Popular Series"
            ContentShelf.ANIME_MOVIES -> "Popular Movies"
            ContentShelf.ANIME_NEW_SEASON -> "New This Season"
            else -> shelf.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}