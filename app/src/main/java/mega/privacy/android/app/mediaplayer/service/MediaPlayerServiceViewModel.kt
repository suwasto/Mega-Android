package mega.privacy.android.app.mediaplayer.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.source.ShuffleOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.addTo
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import mega.privacy.android.app.MegaOffline
import mega.privacy.android.app.MimeTypeList
import mega.privacy.android.app.R
import mega.privacy.android.app.constants.SettingsConstants.KEY_AUDIO_BACKGROUND_PLAY_ENABLED
import mega.privacy.android.app.constants.SettingsConstants.KEY_AUDIO_REPEAT_MODE
import mega.privacy.android.app.constants.SettingsConstants.KEY_AUDIO_SHUFFLE_ENABLED
import mega.privacy.android.app.constants.SettingsConstants.KEY_VIDEO_REPEAT_MODE
import mega.privacy.android.app.mediaplayer.gateway.PlayerServiceViewModelGateway
import mega.privacy.android.app.mediaplayer.mapper.PlaylistItemMapper
import mega.privacy.android.app.mediaplayer.model.MediaPlaySources
import mega.privacy.android.app.mediaplayer.model.RepeatToggleMode
import mega.privacy.android.app.mediaplayer.playlist.PlaylistItem
import mega.privacy.android.app.mediaplayer.playlist.finalizeItem
import mega.privacy.android.app.mediaplayer.playlist.updateNodeName
import mega.privacy.android.app.search.callback.SearchCallback
import mega.privacy.android.app.usecase.GetGlobalTransferUseCase
import mega.privacy.android.app.usecase.GetGlobalTransferUseCase.Result
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.Constants.AUDIO_BROWSE_ADAPTER
import mega.privacy.android.app.utils.Constants.CONTACT_FILE_ADAPTER
import mega.privacy.android.app.utils.Constants.FILE_BROWSER_ADAPTER
import mega.privacy.android.app.utils.Constants.FILE_LINK_ADAPTER
import mega.privacy.android.app.utils.Constants.FOLDER_LINK_ADAPTER
import mega.privacy.android.app.utils.Constants.FROM_CHAT
import mega.privacy.android.app.utils.Constants.INBOX_ADAPTER
import mega.privacy.android.app.utils.Constants.INCOMING_SHARES_ADAPTER
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_ADAPTER_TYPE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_ARRAY_OFFLINE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_CONTACT_EMAIL
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_FILE_NAME
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_FROM_DOWNLOAD_SERVICE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_HANDLE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_HANDLES_NODES_SEARCH
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_IS_PLAYLIST
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_NEED_STOP_HTTP_SERVER
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_ORDER_GET_CHILDREN
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_PARENT_NODE_HANDLE
import mega.privacy.android.app.utils.Constants.INTENT_EXTRA_KEY_REBUILD_PLAYLIST
import mega.privacy.android.app.utils.Constants.INVALID_SIZE
import mega.privacy.android.app.utils.Constants.INVALID_VALUE
import mega.privacy.android.app.utils.Constants.LINKS_ADAPTER
import mega.privacy.android.app.utils.Constants.NODE_HANDLES
import mega.privacy.android.app.utils.Constants.OFFLINE_ADAPTER
import mega.privacy.android.app.utils.Constants.OUTGOING_SHARES_ADAPTER
import mega.privacy.android.app.utils.Constants.PHOTO_SYNC_ADAPTER
import mega.privacy.android.app.utils.Constants.RECENTS_ADAPTER
import mega.privacy.android.app.utils.Constants.RECENTS_BUCKET_ADAPTER
import mega.privacy.android.app.utils.Constants.RUBBISH_BIN_ADAPTER
import mega.privacy.android.app.utils.Constants.SEARCH_BY_ADAPTER
import mega.privacy.android.app.utils.Constants.VIDEO_BROWSE_ADAPTER
import mega.privacy.android.app.utils.Constants.ZIP_ADAPTER
import mega.privacy.android.app.utils.FileUtil.JPG_EXTENSION
import mega.privacy.android.app.utils.FileUtil.getDownloadLocation
import mega.privacy.android.app.utils.FileUtil.getUriForFile
import mega.privacy.android.app.utils.FileUtil.isFileAvailable
import mega.privacy.android.app.utils.MegaNodeUtil.isInRootLinksLevel
import mega.privacy.android.app.utils.OfflineUtils.getOfflineFile
import mega.privacy.android.app.utils.OfflineUtils.getOfflineFolderName
import mega.privacy.android.app.utils.StringResourcesUtils.getString
import mega.privacy.android.app.utils.TextUtil
import mega.privacy.android.app.utils.ThumbnailUtils.getThumbFolder
import mega.privacy.android.app.utils.wrapper.GetOfflineThumbnailFileWrapper
import mega.privacy.android.data.mapper.SortOrderIntMapper
import mega.privacy.android.domain.entity.SortOrder
import mega.privacy.android.domain.entity.getDuration
import mega.privacy.android.domain.entity.node.TypedFileNode
import mega.privacy.android.domain.entity.node.TypedNode
import mega.privacy.android.domain.qualifier.ApplicationScope
import mega.privacy.android.domain.qualifier.IoDispatcher
import mega.privacy.android.domain.repository.MediaPlayerRepository
import mega.privacy.android.domain.usecase.MonitorConnectivity
import nz.mega.sdk.MegaApiJava.INVALID_HANDLE
import nz.mega.sdk.MegaCancelToken
import nz.mega.sdk.MegaError
import nz.mega.sdk.MegaTransfer
import org.jetbrains.anko.defaultSharedPreferences
import timber.log.Timber
import java.io.File
import java.util.Collections
import javax.inject.Inject

/**
 * A class containing audio player service logic, because using ViewModel in Service
 * is not the standard scenario, so this class is actually not a subclass of ViewModel.
 */
class MediaPlayerServiceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaPlayerRepository: MediaPlayerRepository,
    private val offlineThumbnailFileWrapper: GetOfflineThumbnailFileWrapper,
    private val getGlobalTransferUseCase: GetGlobalTransferUseCase,
    @ApplicationScope private val sharingScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val sortOrderIntMapper: SortOrderIntMapper,
    private val playlistItemMapper: PlaylistItemMapper,
    private val monitorConnectivity: MonitorConnectivity,
) : PlayerServiceViewModelGateway, ExposedShuffleOrder.ShuffleChangeListener, SearchCallback.Data {
    private val compositeDisposable = CompositeDisposable()

    private val preferences = context.defaultSharedPreferences
    private var backgroundPlayEnabled =
        preferences.getBoolean(KEY_AUDIO_BACKGROUND_PLAY_ENABLED, true)
    private var shuffleEnabled = preferences.getBoolean(KEY_AUDIO_SHUFFLE_ENABLED, false)
    private var videoRepeatToggleMode =
        convertToRepeatToggleMode(preferences.getInt(KEY_VIDEO_REPEAT_MODE,
            RepeatToggleMode.REPEAT_NONE.ordinal))
    private var audioRepeatToggleMode =
        convertToRepeatToggleMode(preferences.getInt(KEY_AUDIO_REPEAT_MODE,
            RepeatToggleMode.REPEAT_NONE.ordinal))

    private var currentIntent: Intent? = null

    private val playerSource = MutableLiveData<MediaPlaySources>()

    private val mediaItemToRemove = MutableSharedFlow<Int>()

    private val nodeNameUpdate = MutableLiveData<String>()

    private val playingThumbnail = MutableLiveData<File>()

    private val playlist = MutableLiveData<Pair<List<PlaylistItem>, Int>>()

    private val playlistTitle = MutableLiveData<String>()

    private val retry = MutableLiveData<Boolean>()

    private val error = MutableLiveData<Int>()

    private var actionMode = MutableLiveData<Boolean>()

    private val itemsSelectedCount = MutableLiveData<Int>()

    private var mediaPlayback = MutableLiveData<Boolean>()

    private val playlistItems = mutableListOf<PlaylistItem>()

    private val itemsSelectedMap = mutableMapOf<Long, PlaylistItem>()

    private var playlistSearchQuery: String? = null

    private var shuffleOrder: ShuffleOrder = ExposedShuffleOrder(0, this)

    private var playingHandle = INVALID_HANDLE

    private var paused = false

    private var isAudioPlayer = false

    private var playerRetry = 0

    private var needStopStreamingServer = false

    private var playSourceChanged: MutableList<MediaItem> = mutableListOf()
    private var playingPosition = 0

    private var cancelToken: MegaCancelToken? = null

    init {
        itemsSelectedCount.value = 0
        setupTransferListener()
    }

    override fun setPaused(paused: Boolean, currentPosition: Long?) {
        this.paused = paused
        mediaPlayback.value = paused
    }

    override suspend fun buildPlayerSource(intent: Intent?): Boolean {
        if (intent == null || !intent.getBooleanExtra(INTENT_EXTRA_KEY_REBUILD_PLAYLIST, true)) {
            retry.value = false
            return false
        }

        val type = intent.getIntExtra(INTENT_EXTRA_KEY_ADAPTER_TYPE, INVALID_VALUE)
        val uri = intent.data

        if (type == INVALID_VALUE || uri == null) {
            retry.value = false
            return false
        }

        val samePlaylist = isSamePlaylist(type, intent)
        currentIntent = intent

        val firstPlayHandle = intent.getLongExtra(INTENT_EXTRA_KEY_HANDLE, INVALID_HANDLE)
        if (firstPlayHandle == INVALID_HANDLE) {
            retry.value = false
            return false
        }

        val firstPlayNodeName = intent.getStringExtra(INTENT_EXTRA_KEY_FILE_NAME)
        if (firstPlayNodeName == null) {
            retry.value = false
            return false
        }

        // Because the same instance will be used if user creates another audio playlist,
        // so if we need stop streaming server in previous creation, we still need
        // stop it even if the new creation indicates we don't need to stop it,
        // otherwise the streaming server won't be stopped at the end.
        needStopStreamingServer = needStopStreamingServer || intent.getBooleanExtra(
            INTENT_EXTRA_KEY_NEED_STOP_HTTP_SERVER, false
        )

        playerRetry = 0
        isAudioPlayer = MimeTypeList.typeForName(firstPlayNodeName).isAudio

        var displayNodeNameFirst = type != OFFLINE_ADAPTER || !isAudioPlayer
        if (samePlaylist && firstPlayHandle == playingHandle) {
            // if we are already playing this music, then the metadata is already
            // in LiveData (_metadata of AudioPlayerService), we don't need (and shouldn't)
            // emit node name.
            displayNodeNameFirst = false
        }

        val firstPlayUri = if (type == FOLDER_LINK_ADAPTER) {
            if (isMegaApiFolder(type)) {
                mediaPlayerRepository.getLocalLinkFromMegaApiFolder(firstPlayHandle)
            } else {
                mediaPlayerRepository.getLocalLinkFromMegaApi(firstPlayHandle)
            }?.let { url ->
                Uri.parse(url)
            }
        } else {
            uri
        }

        if (firstPlayUri == null) {
            retry.value = false
            return false
        }

        val mediaItem = MediaItem.Builder()
            .setUri(firstPlayUri)
            .setMediaId(firstPlayHandle.toString())
            .build()
        playerSource.value = MediaPlaySources(
            listOf(mediaItem),
            // we will emit a single item list at first, and the current playing item
            // will always be at index 0 in that single item list.
            if (samePlaylist && firstPlayHandle == playingHandle) 0 else INVALID_VALUE,
            if (displayNodeNameFirst) firstPlayNodeName else null
        )

        if (intent.getBooleanExtra(INTENT_EXTRA_KEY_IS_PLAYLIST, true)) {
            if (type != OFFLINE_ADAPTER && type != ZIP_ADAPTER) {
                needStopStreamingServer =
                    needStopStreamingServer || setupStreamingServer(type)
            }
            sharingScope.launch(ioDispatcher) {
                when (type) {
                    OFFLINE_ADAPTER -> {
                        playlistTitle.postValue(getOfflineFolderName(context, firstPlayHandle))
                        buildPlaylistFromOfflineNodes(intent, firstPlayHandle)
                    }
                    AUDIO_BROWSE_ADAPTER -> {
                        playlistTitle.postValue(getString(R.string.upload_to_audio))
                        buildPlaySourcesByTypedNodes(type = type,
                            typedNodes = mediaPlayerRepository.getAudioNodes(
                                getSortOrderFromIntent(intent)
                            ),
                            firstPlayHandle = firstPlayHandle)
                    }
                    VIDEO_BROWSE_ADAPTER -> {
                        playlistTitle.postValue(getString(R.string.sortby_type_video_first))
                        buildPlaySourcesByTypedNodes(type = type,
                            typedNodes = mediaPlayerRepository.getVideoNodes(
                                getSortOrderFromIntent(intent)
                            ),
                            firstPlayHandle = firstPlayHandle)
                    }
                    FILE_BROWSER_ADAPTER,
                    RUBBISH_BIN_ADAPTER,
                    INBOX_ADAPTER,
                    LINKS_ADAPTER,
                    INCOMING_SHARES_ADAPTER,
                    OUTGOING_SHARES_ADAPTER,
                    CONTACT_FILE_ADAPTER,
                    -> {
                        val parentHandle = intent.getLongExtra(
                            INTENT_EXTRA_KEY_PARENT_NODE_HANDLE,
                            INVALID_HANDLE
                        )
                        val order = getSortOrderFromIntent(intent)

                        if (isInRootLinksLevel(type, parentHandle)) {
                            playlistTitle.postValue(getString(R.string.tab_links_shares))
                            buildPlaySourcesByTypedNodes(type = type,
                                typedNodes = mediaPlayerRepository.getNodesFromPublicLinks(
                                    isAudio = isAudioPlayer,
                                    order = order
                                ),
                                firstPlayHandle = firstPlayHandle)
                            return@launch
                        }

                        if (type == INCOMING_SHARES_ADAPTER && parentHandle == INVALID_HANDLE) {
                            playlistTitle.postValue(getString(R.string.tab_incoming_shares))
                            buildPlaySourcesByTypedNodes(type = type,
                                typedNodes = mediaPlayerRepository.getNodesFromPublicLinks(
                                    isAudio = isAudioPlayer,
                                    order = order
                                ),
                                firstPlayHandle = firstPlayHandle)
                            return@launch
                        }

                        if (type == OUTGOING_SHARES_ADAPTER && parentHandle == INVALID_HANDLE) {
                            playlistTitle.postValue(getString(R.string.tab_outgoing_shares))
                            buildPlaySourcesByTypedNodes(type = type,
                                typedNodes = mediaPlayerRepository.getNodesFromOutShares(
                                    isAudio = isAudioPlayer,
                                    lastHandle = INVALID_HANDLE,
                                    order = order
                                ),
                                firstPlayHandle = firstPlayHandle)
                            return@launch
                        }

                        if (type == CONTACT_FILE_ADAPTER && parentHandle == INVALID_HANDLE) {
                            intent.getStringExtra(INTENT_EXTRA_KEY_CONTACT_EMAIL)
                                ?.let { email ->
                                    mediaPlayerRepository.getNodesByEmail(isAudioPlayer, email)
                                        ?.let { nodes ->
                                            mediaPlayerRepository.getUserNameByEmail(email)?.let {
                                                getString(R.string.title_incoming_shares_with_explorer)
                                                    .let { sharesTitle ->
                                                        playlistTitle.postValue("$sharesTitle $it")
                                                    }
                                            }
                                            buildPlaySourcesByTypedNodes(type = type,
                                                typedNodes = nodes,
                                                firstPlayHandle = firstPlayHandle)
                                        }
                                }
                            return@launch
                        }

                        if (parentHandle == INVALID_HANDLE) {
                            when (type) {
                                RUBBISH_BIN_ADAPTER -> mediaPlayerRepository.getRubbishNode()
                                INBOX_ADAPTER -> mediaPlayerRepository.getInboxNode()
                                else -> mediaPlayerRepository.getRootNode()
                            }
                        } else {
                            mediaPlayerRepository.getParentNodeByHandle(parentHandle)
                        }?.let { parent ->
                            if (parentHandle == INVALID_HANDLE) {
                                getString(
                                    when (type) {
                                        RUBBISH_BIN_ADAPTER -> R.string.section_rubbish_bin
                                        INBOX_ADAPTER -> R.string.home_side_menu_backups_title
                                        else -> R.string.section_cloud_drive
                                    }
                                )
                            } else {
                                parent.name
                            }.let { title ->
                                playlistTitle.postValue(title)
                            }
                            mediaPlayerRepository.getChildrenByParentHandle(
                                isAudio = isAudioPlayer,
                                parentHandle = parent.id.id,
                                order = getSortOrderFromIntent(intent))?.let { children ->
                                buildPlaySourcesByTypedNodes(type, children, firstPlayHandle)
                            }
                        }
                    }
                    RECENTS_ADAPTER, RECENTS_BUCKET_ADAPTER -> {
                        playlistTitle.postValue(getString(R.string.section_recents))
                        intent.getLongArrayExtra(NODE_HANDLES)?.let { handles ->
                            buildPlaylistFromHandles(type = type,
                                handles = handles.toList(),
                                firstPlayHandle = firstPlayHandle)
                        }
                    }
                    FOLDER_LINK_ADAPTER -> {
                        val parentHandle = intent.getLongExtra(
                            INTENT_EXTRA_KEY_PARENT_NODE_HANDLE,
                            INVALID_HANDLE
                        )
                        val order = getSortOrderFromIntent(intent)

                        (if (parentHandle == INVALID_HANDLE) {
                            mediaPlayerRepository.megaApiFolderGetRootNode()
                        } else {
                            mediaPlayerRepository.megaApiFolderGetParentNode(parentHandle)
                        })?.let { parent ->
                            playlistTitle.postValue(parent.name)

                            mediaPlayerRepository.megaApiFolderGetChildrenByParentHandle(
                                isAudioPlayer, parent.id.id, order)?.let { children ->
                                buildPlaySourcesByTypedNodes(type, children, firstPlayHandle)
                            }
                        }
                    }
                    ZIP_ADAPTER -> {
                        intent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY)
                            ?.let { zipPath ->
                                playlistTitle.postValue(File(zipPath).parentFile?.name
                                    ?: "")
                                File(zipPath).parentFile?.listFiles()?.let { files ->
                                    buildPlaySourcesByFiles(files = files.asList(),
                                        firstPlayHandle = firstPlayHandle)
                                }
                            }
                    }
                    SEARCH_BY_ADAPTER -> {
                        intent.getLongArrayExtra(INTENT_EXTRA_KEY_HANDLES_NODES_SEARCH)
                            ?.let { handles ->
                                buildPlaylistFromHandles(type = type,
                                    handles = handles.toList(),
                                    firstPlayHandle = firstPlayHandle)
                            }
                    }
                }
                postPlayingThumbnail()
            }
        } else {
            playlistItems.clear()

            val node: TypedFileNode? =
                mediaPlayerRepository.getTypedNodeByHandle(firstPlayHandle) as? TypedFileNode
            val thumbnail = when {
                type == OFFLINE_ADAPTER -> {
                    offlineThumbnailFileWrapper.getThumbnailFile(context,
                        firstPlayHandle.toString())
                }
                node == null -> {
                    null
                }
                else -> {
                    File(getThumbFolder(context), node.base64Id.plus(JPG_EXTENSION))
                }
            }

            playlistItemMapper(firstPlayHandle,
                firstPlayNodeName,
                thumbnail,
                0,
                TYPE_PLAYING,
                node?.size ?: INVALID_SIZE,
                node?.type?.getDuration() ?: 0)
                .let { playlistItem ->
                    playlistItems.add(playlistItem)
                }

            recreateAndUpdatePlaylistItems()

            if (thumbnail != null && !thumbnail.exists() && node != null) {
                mediaPlayerRepository.getThumbnail(isAudioPlayer,
                    node.id.id,
                    thumbnail.absolutePath
                ) { nodeHandle ->
                    if (nodeHandle == playingHandle) {
                        postPlayingThumbnail()
                    }
                }
            } else {
                postPlayingThumbnail()
            }
        }

        return true
    }

    /**
     * Build play sources by node OfflineNodes
     *
     * @param intent Intent
     * @param firstPlayHandle the index of first playing item
     */
    private fun buildPlaylistFromOfflineNodes(
        intent: Intent,
        firstPlayHandle: Long,
    ) {
        with(intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getParcelableArrayListExtra(INTENT_EXTRA_KEY_ARRAY_OFFLINE, MegaOffline::class.java)
            } else {
                @Suppress("DEPRECATION")
                getParcelableArrayListExtra(INTENT_EXTRA_KEY_ARRAY_OFFLINE)
            }
        }?.let { offlineFiles ->
            playlistItems.clear()

            val mediaItems = mutableListOf<MediaItem>()
            var firstPlayIndex = 0

            offlineFiles.filter {
                getOfflineFile(context, it).let { file ->
                    isFileAvailable(file) && file.isFile && filterByNodeName(it.name)
                }
            }.mapIndexed { currentIndex, megaOffline ->
                mediaItems.add(
                    mediaItemFromFile(getOfflineFile(context, megaOffline), megaOffline.handle)
                )
                if (megaOffline.handle.toLong() == firstPlayHandle) {
                    firstPlayIndex = currentIndex
                }

                playlistItemMapper(megaOffline.handle.toLong(),
                    megaOffline.name,
                    offlineThumbnailFileWrapper.getThumbnailFile(context, megaOffline),
                    currentIndex,
                    TYPE_NEXT,
                    megaOffline.getSize(context),
                    0)
                    .let { playlistItem ->
                        playlistItems.add(playlistItem)
                    }
            }

            updatePlaySources(mediaItems, playlistItems, firstPlayIndex)
        }
    }

    /**
     * Build play sources by node TypedNodes
     *
     * @param type adapter type
     * @param typedNodes [TypedNode] list
     * @param firstPlayHandle the index of first playing item
     */
    private suspend fun buildPlaySourcesByTypedNodes(
        type: Int,
        typedNodes: List<TypedNode>,
        firstPlayHandle: Long,
    ) {
        playlistItems.clear()

        val mediaItems = ArrayList<MediaItem>()
        var firstPlayIndex = 0

        val nodesWithoutThumbnail = ArrayList<Pair<Long, File>>()

        typedNodes.mapIndexed { currentIndex, typedNode ->
            if (typedNode is TypedFileNode) {
                mediaPlayerRepository.getLocalFilePath(typedNode).let { localPath ->
                    if (localPath != null && isLocalFile(typedNode, localPath)) {
                        mediaItemFromFile(File(localPath), typedNode.id.id.toString())
                    } else {
                        val url = if (isMegaApiFolder(type)) {
                            mediaPlayerRepository.getLocalLinkFromMegaApiFolder(typedNode.id.id)
                        } else {
                            mediaPlayerRepository.getLocalLinkFromMegaApi(typedNode.id.id)
                        }
                        if (url == null) {
                            null
                        } else {
                            MediaItem.Builder()
                                .setUri(Uri.parse(url))
                                .setMediaId(typedNode.id.id.toString())
                                .build()
                        }
                    }?.let {
                        mediaItems.add(it)
                    }
                }

                if (typedNode.id.id == firstPlayHandle) {
                    firstPlayIndex = currentIndex
                }
                val thumbnail = typedNode.thumbnailPath?.let { path ->
                    File(path)
                }

                playlistItemMapper(typedNode.id.id,
                    typedNode.name,
                    thumbnail,
                    currentIndex,
                    TYPE_NEXT,
                    typedNode.size,
                    typedNode.type.getDuration())
                    .let { playlistItem ->
                        playlistItems.add(playlistItem)
                    }

                if (thumbnail != null && !thumbnail.exists()) {
                    nodesWithoutThumbnail.add(Pair(typedNode.id.id, thumbnail))
                }
            }
        }

        if (nodesWithoutThumbnail.isNotEmpty() && monitorConnectivity().value) {
            sharingScope.launch(ioDispatcher) {
                nodesWithoutThumbnail.map {
                    mediaPlayerRepository.getThumbnail(
                        isMegaApiFolder = isMegaApiFolder(type),
                        it.first,
                        it.second.absolutePath
                    ) { nodeHandle ->
                        if (nodeHandle == playingHandle) {
                            postPlayingThumbnail()
                        }
                    }
                }
            }
        }
        updatePlaySources(mediaItems, playlistItems, firstPlayIndex)
    }

    /**
     * Build play sources by node handles
     *
     * @param type adapter type
     * @param handles node handles
     * @param firstPlayHandle the index of first playing item
     */
    private suspend fun buildPlaylistFromHandles(
        type: Int,
        handles: List<Long>,
        firstPlayHandle: Long,
    ) {
        buildPlaySourcesByTypedNodes(type = type,
            typedNodes = mediaPlayerRepository.getNodesByHandles(isAudioPlayer, handles),
            firstPlayHandle = firstPlayHandle)
    }

    /**
     * Build play sources by files
     *
     * @param files media files
     * @param firstPlayHandle the index of first playing item
     */
    private fun buildPlaySourcesByFiles(
        files: List<File>,
        firstPlayHandle: Long,
    ) {
        playlistItems.clear()

        val mediaItems = ArrayList<MediaItem>()
        var firstPlayIndex = 0

        files.filter {
            it.isFile && filterByNodeName(it.name)
        }.mapIndexed { currentIndex, file ->
            mediaItems.add(mediaItemFromFile(file, file.name.hashCode().toString()))

            if (file.name.hashCode().toLong() == firstPlayHandle) {
                firstPlayIndex = currentIndex
            }

            playlistItemMapper(file.name.hashCode().toLong(),
                file.name,
                null,
                currentIndex,
                TYPE_NEXT,
                file.length(),
                0)
                .let { playlistItem ->
                    playlistItems.add(playlistItem)
                }
        }
        updatePlaySources(mediaItems, playlistItems, firstPlayIndex)
    }

    /**
     * Update play sources for media player and playlist
     *
     * @param mediaItems media items
     * @param items playlist items
     * @param firstPlayIndex the index of first playing item
     */
    private fun updatePlaySources(
        mediaItems: List<MediaItem>,
        items: List<PlaylistItem>,
        firstPlayIndex: Int,
    ) {
        if (mediaItems.isNotEmpty() && items.isNotEmpty()) {
            playerSource.postValue(MediaPlaySources(mediaItems, firstPlayIndex, null))
            recreateAndUpdatePlaylistItems(items = items)
        }
    }

    /**
     * Setup transfer listener
     */
    private fun setupTransferListener() {
        getGlobalTransferUseCase.get()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .filter { it is Result.OnTransferTemporaryError && it.transfer != null }
            .subscribeBy(
                onNext = { event ->
                    val errorEvent = event as Result.OnTransferTemporaryError
                    errorEvent.transfer?.run {
                        onTransferTemporaryError(this, errorEvent.error)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribeBy(onError = { Timber.e(it) })
                            .addTo(compositeDisposable)
                    }
                },
                onError = { Timber.e(it) }
            )
            .addTo(compositeDisposable)
    }

    override fun onPlayerError() {
        playerRetry++
        retry.value = playerRetry <= MAX_RETRY
    }

    /**
     * Check if the new intent would create the same playlist as current one.
     *
     * @param type new adapter type
     * @param intent new intent
     * @return if the new intent would create the same playlist as current one
     */
    private fun isSamePlaylist(type: Int, intent: Intent): Boolean {
        val oldIntent = currentIntent ?: return false
        val oldType = oldIntent.getIntExtra(INTENT_EXTRA_KEY_ADAPTER_TYPE, INVALID_VALUE)

        if (
            intent.getBooleanExtra(INTENT_EXTRA_KEY_FROM_DOWNLOAD_SERVICE, false)
            && oldIntent.getBooleanExtra(INTENT_EXTRA_KEY_FROM_DOWNLOAD_SERVICE, false)
        ) {
            return true
        }

        when (type) {
            OFFLINE_ADAPTER -> {
                val oldDir = oldIntent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY)
                    ?: return false
                val newDir =
                    intent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY) ?: return false
                return oldDir == newDir
            }
            AUDIO_BROWSE_ADAPTER,
            VIDEO_BROWSE_ADAPTER,
            FROM_CHAT,
            FILE_LINK_ADAPTER,
            PHOTO_SYNC_ADAPTER,
            -> {
                return oldType == type
            }
            FILE_BROWSER_ADAPTER,
            RUBBISH_BIN_ADAPTER,
            INBOX_ADAPTER,
            LINKS_ADAPTER,
            INCOMING_SHARES_ADAPTER,
            OUTGOING_SHARES_ADAPTER,
            CONTACT_FILE_ADAPTER,
            FOLDER_LINK_ADAPTER,
            -> {
                val oldParentHandle = oldIntent.getLongExtra(
                    INTENT_EXTRA_KEY_PARENT_NODE_HANDLE,
                    INVALID_HANDLE
                )
                val newParentHandle = intent.getLongExtra(
                    INTENT_EXTRA_KEY_PARENT_NODE_HANDLE,
                    INVALID_HANDLE
                )
                return oldType == type && oldParentHandle == newParentHandle
            }
            RECENTS_ADAPTER, RECENTS_BUCKET_ADAPTER -> {
                val oldHandles = oldIntent.getLongArrayExtra(NODE_HANDLES) ?: return false
                val newHandles = intent.getLongArrayExtra(NODE_HANDLES) ?: return false
                return oldHandles.contentEquals(newHandles)
            }
            ZIP_ADAPTER -> {
                val oldZipPath = oldIntent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY)
                    ?: return false
                val newZipPath =
                    intent.getStringExtra(INTENT_EXTRA_KEY_OFFLINE_PATH_DIRECTORY) ?: return false
                return oldZipPath == newZipPath
            }
            SEARCH_BY_ADAPTER -> {
                val oldHandles = oldIntent.getLongArrayExtra(INTENT_EXTRA_KEY_HANDLES_NODES_SEARCH)
                    ?: return false
                val newHandles =
                    intent.getLongArrayExtra(INTENT_EXTRA_KEY_HANDLES_NODES_SEARCH) ?: return false
                return oldHandles.contentEquals(newHandles)
            }
            else -> {
                return false
            }
        }
    }

    private fun filterByNodeName(name: String): Boolean =
        MimeTypeList.typeForName(name).let { mime ->
            if (isAudioPlayer) {
                mime.isAudio && !mime.isAudioNotSupported
            } else {
                mime.isVideo && mime.isVideoReproducible && !mime.isVideoNotSupported
            }
        }

    private fun getSortOrderFromIntent(intent: Intent): Int {
        val order =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(
                    INTENT_EXTRA_KEY_ORDER_GET_CHILDREN,
                    SortOrder::class.java) ?: SortOrder.ORDER_DEFAULT_ASC
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(
                    INTENT_EXTRA_KEY_ORDER_GET_CHILDREN) as SortOrder?
                    ?: SortOrder.ORDER_DEFAULT_ASC
            }
        return sortOrderIntMapper(order)
    }

    override fun initNewSearch(): MegaCancelToken {
        cancelSearch()
        return MegaCancelToken.createInstance()
    }

    override fun cancelSearch() {
        cancelToken?.cancel()
    }

    private fun mediaItemFromFile(file: File, handle: String): MediaItem =
        MediaItem.Builder()
            .setUri(getUriForFile(context, file))
            .setMediaId(handle)
            .build()


    private fun postPlayingThumbnail() =
        playlistItems.firstOrNull { (nodeHandle) ->
            nodeHandle == playingHandle
        }?.thumbnail?.let {
            playingThumbnail.postValue(it)
        }

    /**
     * Recreate and update playlist items
     *
     * @param items playlist items
     * @param isScroll true is scroll to target position, otherwise is false.
     * @param isBuildPlaySources true is building play sources, otherwise is false.
     */
    private fun recreateAndUpdatePlaylistItems(
        items: List<PlaylistItem> = playlistItems,
        isScroll: Boolean = true,
        isBuildPlaySources: Boolean = true,
    ) {
        sharingScope.launch(ioDispatcher) {
            Timber.d("recreateAndUpdatePlaylistItems ${items.size} items")
            if (items.isEmpty()) {
                return@launch
            }

            playingPosition = items.indexOfFirst { (nodeHandle) ->
                nodeHandle == playingHandle
            }.takeIf { index ->
                index in items.indices
            } ?: 0

            val recreatedItems = mutableListOf<PlaylistItem>()
            // Adjust whether need to build play sources again to avoid playlist is reordered everytime playlist items updated
            if (isBuildPlaySources && shuffleEnabled && shuffleOrder.length == items.size) {
                recreatedItems.add(items[playingPosition])

                var newPlayingIndex = 0

                var index = shuffleOrder.getPreviousIndex(playingPosition)
                while (index != C.INDEX_UNSET) {
                    recreatedItems.add(0, items[index])
                    index = shuffleOrder.getPreviousIndex(index)
                    newPlayingIndex++
                }

                index = shuffleOrder.getNextIndex(playingPosition)
                while (index != C.INDEX_UNSET) {
                    recreatedItems.add(items[index])
                    index = shuffleOrder.getNextIndex(index)
                }

                playingPosition = newPlayingIndex
            } else {
                recreatedItems.addAll(items)
            }

            val searchQuery = playlistSearchQuery
            if (!TextUtil.isTextEmpty(searchQuery)) {
                filterPlaylistItems(recreatedItems, searchQuery ?: return@launch)
                return@launch
            }
            for ((index, item) in recreatedItems.withIndex()) {
                val type = when {
                    index < playingPosition -> TYPE_PREVIOUS
                    playingPosition == index -> TYPE_PLAYING
                    else -> TYPE_NEXT
                }
                recreatedItems[index] =
                    item.finalizeItem(
                        index = index,
                        type = type,
                        isSelected = item.isSelected,
                        duration = item.duration,
                    )
            }
            val hasPrevious = playingPosition > 0
            var scrollPosition = playingPosition
            if (hasPrevious) {
                recreatedItems[0] = recreatedItems[0].copy(headerIsVisible = true)
            }
            recreatedItems[playingPosition] =
                recreatedItems[playingPosition].copy(headerIsVisible = true)

            Timber.d("recreateAndUpdatePlaylistItems post ${recreatedItems.size} items")
            if (!isScroll) {
                scrollPosition = -1
            }
            playlist.postValue(Pair(recreatedItems, scrollPosition))
        }
    }

    private fun filterPlaylistItems(items: List<PlaylistItem>, filter: String) {
        if (items.isEmpty()) return

        val filteredItems = ArrayList<PlaylistItem>()
        items.forEachIndexed { index, item ->
            if (item.nodeName.contains(filter, true)) {
                // Filter only affects displayed playlist, it doesn't affect what
                // ExoPlayer is playing, so we still need use the index before filter.
                filteredItems.add(item.finalizeItem(index, TYPE_PREVIOUS))
            }
        }

        playlist.postValue(Pair(filteredItems, 0))
    }

    override fun searchQueryUpdate(newText: String?) {
        playlistSearchQuery = newText
        playlist.value?.first?.let {
            recreateAndUpdatePlaylistItems(items = it, isBuildPlaySources = false)
        }
    }

    override fun getCurrentIntent() = currentIntent

    override fun getCurrentPlayingHandle() = playingHandle

    override fun setCurrentPlayingHandle(handle: Long) {
        playingHandle = handle
        playlist.value?.first?.let { items ->
            playingPosition = items.indexOfFirst { (nodeHandle) ->
                nodeHandle == handle
            }.takeIf { index ->
                index in items.indices
            } ?: 0
            recreateAndUpdatePlaylistItems(items = items, isBuildPlaySources = false)
        }
        postPlayingThumbnail()
    }

    override fun getPlaylistItem(handle: String?): PlaylistItem? =
        handle?.let {
            playlistItems.firstOrNull { (nodeHandle) ->
                nodeHandle == handle.toLong()
            }
        }

    override fun getPlayingThumbnail() = playingThumbnail

    override fun playerSourceUpdate() = playerSource.asFlow()

    override fun mediaItemToRemoveUpdate() = mediaItemToRemove

    override fun nodeNameUpdate() = nodeNameUpdate.asFlow()

    override fun retryUpdate() = retry.asFlow()

    override fun playlistUpdate() = playlist.asFlow()

    override fun mediaPlaybackUpdate() = mediaPlayback.asFlow()

    override fun errorUpdate() = error.asFlow()

    override fun playlistTitleUpdate() = playlistTitle.asFlow()

    override fun itemsSelectedCountUpdate() = itemsSelectedCount.asFlow()

    override fun actionModeUpdate() = actionMode.asFlow()

    override fun removeItem(handle: Long) {
        initPlayerSourceChanged()
        val newItems = removeSingleItem(handle)
        newItems?.let {
            resetRetryState()
            recreateAndUpdatePlaylistItems(items = it)
        } ?: let {
            playlist.value = Pair(emptyList(), 0)
            error.postValue(MegaError.API_ENOENT)
        }
    }

    private fun removeSingleItem(handle: Long): List<PlaylistItem>? =
        playlist.value?.first?.let { items ->
            val newItems = items.toMutableList()
            items.indexOfFirst { (nodeHandle) ->
                nodeHandle == handle
            }.takeIf { index ->
                index in playlistItems.indices
            }?.let { index ->
                sharingScope.launch {
                    mediaItemToRemove.emit(index)
                }
                newItems.removeIf { (nodeHandle) ->
                    nodeHandle == handle
                }
                playlistItems.removeIf { (nodeHandle) ->
                    nodeHandle == handle
                }
                playSourceChanged.removeIf { mediaItem ->
                    mediaItem.mediaId.toLong() == handle
                }
            }
            newItems
        }

    override fun removeAllSelectedItems() {
        if (itemsSelectedMap.isNotEmpty()) {
            itemsSelectedMap.forEach {
                removeSingleItem(it.value.nodeHandle)?.let { newItems ->
                    playlist.value = Pair(newItems, playingPosition)
                }
            }
            itemsSelectedMap.clear()
            itemsSelectedCount.value = itemsSelectedMap.size
            actionMode.value = false
        }
    }

    override fun itemSelected(handle: Long) {
        playlistItems.indexOfFirst { (nodeHandle) ->
            nodeHandle == handle
        }.takeIf { index ->
            index in playlistItems.indices
        }?.let { selectedIndex ->
            playlistItems[selectedIndex].let { item ->
                val isSelected = !item.isSelected
                playlistItems[selectedIndex] = item.copy(isSelected = isSelected)
                if (playlistItems[selectedIndex].isSelected) {
                    itemsSelectedMap[handle] = item
                } else {
                    itemsSelectedMap.remove(handle)
                }
                itemsSelectedCount.value = itemsSelectedMap.size
                // Refresh the playlist
                recreateAndUpdatePlaylistItems(isScroll = false)
            }
        }
    }

    override fun clearSelections() {
        mutableListOf<PlaylistItem>().let { items ->
            items.addAll(playlistItems.map { item ->
                item.copy(isSelected = false)
            }).apply {
                playlistItems.clear()
                playlistItems.addAll(items)
            }
        }
        itemsSelectedMap.clear()
        actionMode.value = false
        recreateAndUpdatePlaylistItems()
    }

    override fun setActionMode(isActionMode: Boolean) {
        actionMode.value = isActionMode
        if (isActionMode) {
            recreateAndUpdatePlaylistItems(isScroll = false)
        }
    }

    override fun resetRetryState() {
        playerRetry = 0
        retry.value = true
    }

    override fun updateItemName(handle: Long, newName: String) {
        for ((index, item) in playlistItems.withIndex()) {
            if (item.nodeHandle == handle) {
                val newItem = playlistItems[index].updateNodeName(newName)
                playlistItems[index] = newItem
                recreateAndUpdatePlaylistItems()
                if (handle == playingHandle) {
                    nodeNameUpdate.postValue(newName)
                }
                return
            }
        }
    }

    override fun getPlaylistItems() = playlist.value?.first

    override fun isAudioPlayer() = isAudioPlayer

    override fun setAudioPlayer(isAudioPlayer: Boolean) {
        this.isAudioPlayer = isAudioPlayer
    }

    override fun backgroundPlayEnabled() = backgroundPlayEnabled

    override fun toggleBackgroundPlay(isEnable: Boolean): Boolean {
        backgroundPlayEnabled = isEnable
        preferences.edit()
            .putBoolean(KEY_AUDIO_BACKGROUND_PLAY_ENABLED, backgroundPlayEnabled)
            .apply()

        return backgroundPlayEnabled
    }

    override fun shuffleEnabled(): Boolean = shuffleEnabled

    override fun getShuffleOrder() = shuffleOrder

    override fun setShuffleEnabled(enabled: Boolean) {
        shuffleEnabled = enabled
        preferences.edit()
            .putBoolean(KEY_AUDIO_SHUFFLE_ENABLED, shuffleEnabled)
            .apply()
        if (!enabled) {
            recreateAndUpdatePlaylistItems()
        }
    }

    override fun newShuffleOrder(): ShuffleOrder {
        shuffleOrder = ExposedShuffleOrder(playlistItems.size, this)
        return shuffleOrder
    }

    override fun audioRepeatToggleMode() = audioRepeatToggleMode

    override fun videoRepeatToggleMode() = videoRepeatToggleMode

    override fun setAudioRepeatMode(repeatToggleMode: RepeatToggleMode) {
        audioRepeatToggleMode = repeatToggleMode
        preferences.edit().putInt(KEY_AUDIO_REPEAT_MODE, repeatToggleMode.ordinal).apply()
    }

    override fun setVideoRepeatMode(repeatToggleMode: RepeatToggleMode) {
        videoRepeatToggleMode = repeatToggleMode
        preferences.edit().putInt(KEY_VIDEO_REPEAT_MODE, repeatToggleMode.ordinal).apply()
    }

    private fun convertToRepeatToggleMode(ordinal: Int): RepeatToggleMode =
        when (ordinal) {
            RepeatToggleMode.REPEAT_NONE.ordinal -> RepeatToggleMode.REPEAT_NONE
            RepeatToggleMode.REPEAT_ONE.ordinal -> RepeatToggleMode.REPEAT_ONE
            else -> RepeatToggleMode.REPEAT_ALL
        }

    override fun clear() {
        sharingScope.launch {
            compositeDisposable.dispose()

            if (needStopStreamingServer) {
                mediaPlayerRepository.megaApiHttpServerStop()
                mediaPlayerRepository.megaApiFolderHttpServerStop()
            }
        }
    }

    private suspend fun isMegaApiFolder(type: Int) =
        type == FOLDER_LINK_ADAPTER && mediaPlayerRepository.credentialsIsNull()

    override fun swapItems(current: Int, target: Int) {
        Collections.swap(playlistItems, current, target)
        // Keep the index for swap items to keep the play order is correct
        val index = playlistItems[current].index
        playlistItems[current] = playlistItems[current].copy(index = playlistItems[target].index)
        playlistItems[target] = playlistItems[target].copy(index = index)

        initPlayerSourceChanged()
        // Swap the items of play source
        Collections.swap(playSourceChanged, current, target)
    }

    override fun getIndexFromPlaylistItems(item: PlaylistItem): Int? =
        playlist.value?.first?.let { items ->
            items.indexOfFirst {
                it.nodeName == item.nodeName
            }.takeIf { index ->
                index in items.indices
            }
        }

    override fun updatePlaySource() {
        val newPlayerSource = mutableListOf<MediaItem>()
        newPlayerSource.addAll(playSourceChanged)
        playerSource.value?.run {
            playerSource.value =
                copy(mediaItems = newPlayerSource, newIndexForCurrentItem = playingPosition)
            playSourceChanged.clear()
        }
    }

    override fun isPaused() = paused

    override fun getPlayingPosition(): Int = playingPosition

    override fun scrollToPlayingPosition() {
        playlist.value?.first?.let {
            recreateAndUpdatePlaylistItems(items = it, isBuildPlaySources = false)
        }
    }

    override fun isActionMode() = actionMode.value

    private fun initPlayerSourceChanged() {
        if (playSourceChanged.isEmpty()) {
            // Get the play source
            playerSource.value?.run {
                playSourceChanged.addAll(mediaItems)
            }
        }
    }

    override fun onShuffleChanged(newShuffle: ShuffleOrder) {
        shuffleOrder = newShuffle
        if (shuffleOrder.length != 0 && shuffleOrder.length == playlistItems.size) {
            recreateAndUpdatePlaylistItems()
        }
    }

    private fun onTransferTemporaryError(transfer: MegaTransfer, e: MegaError): Completable =
        Completable.fromAction {
            if (transfer.nodeHandle != playingHandle) {
                return@fromAction
            }

            if ((e.errorCode == MegaError.API_EOVERQUOTA && !transfer.isForeignOverquota && e.value != 0L)
                || e.errorCode == MegaError.API_EBLOCKED
            ) {
                error.value = e.errorCode
            }
        }

    private suspend fun setupStreamingServer(type: Int): Boolean {
        val memoryInfo = ActivityManager.MemoryInfo()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(memoryInfo)

        with(mediaPlayerRepository) {
            if (isMegaApiFolder(type)) {
                if (megaApiFolderHttpServerIsRunning() != 0) {
                    return false
                }
                megaApiFolderHttpServerStart()
                megaApiFolderHttpServerSetMaxBufferSize(
                    bufferSize = if (memoryInfo.totalMem > Constants.BUFFER_COMP)
                        Constants.MAX_BUFFER_32MB
                    else
                        Constants.MAX_BUFFER_16MB
                )
            } else {
                if (megaApiHttpServerIsRunning() != 0) {
                    return false
                }
                megaApiHttpServerStart()
                megaApiHttpServerSetMaxBufferSize(
                    bufferSize = if (memoryInfo.totalMem > Constants.BUFFER_COMP)
                        Constants.MAX_BUFFER_32MB
                    else
                        Constants.MAX_BUFFER_16MB
                )
            }
        }
        return true
    }

    private suspend fun isLocalFile(node: TypedFileNode, localPath: String?): Boolean =
        node.fingerprint.let { fingerprint ->
            localPath != null &&
                    (isOnMegaDownloads(node) || (fingerprint != null
                            && fingerprint == mediaPlayerRepository.getFingerprint(localPath)))
        }

    private fun isOnMegaDownloads(node: TypedFileNode): Boolean =
        File(getDownloadLocation(), node.name).let { file ->
            isFileAvailable(file) && file.length() == node.size
        }

    companion object {
        private const val MAX_RETRY = 6

        /**
         * The previous type of media item
         */
        const val TYPE_PREVIOUS = 1

        /**
         * The playing type playing media item
         */
        const val TYPE_PLAYING = 2

        /**
         * The next type next media item
         */
        const val TYPE_NEXT = 3

        /**
         * Clear saved audio player settings.
         *
         * @param context Android context
         */
        @JvmStatic
        fun clearSettings(context: Context) {
            context.defaultSharedPreferences.edit()
                .remove(KEY_AUDIO_BACKGROUND_PLAY_ENABLED)
                .remove(KEY_AUDIO_SHUFFLE_ENABLED)
                .remove(KEY_AUDIO_REPEAT_MODE)
                .apply()
        }
    }
}
