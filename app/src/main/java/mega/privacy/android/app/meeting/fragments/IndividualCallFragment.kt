package mega.privacy.android.app.meeting.fragments

import android.os.Bundle
import android.util.Pair
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.jeremyliao.liveeventbus.LiveEventBus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.android.synthetic.main.individual_call_fragment.view.avatar
import kotlinx.android.synthetic.main.individual_call_fragment.view.on_hold_icon
import kotlinx.android.synthetic.main.individual_call_fragment.view.video
import kotlinx.android.synthetic.main.self_feed_floating_window_fragment.view.*
import mega.privacy.android.app.components.RoundedImageView
import mega.privacy.android.app.databinding.IndividualCallFragmentBinding
import mega.privacy.android.app.databinding.SelfFeedFloatingWindowFragmentBinding
import mega.privacy.android.app.meeting.listeners.MeetingVideoListener
import mega.privacy.android.app.meeting.listeners.RequestHiResVideoListener
import mega.privacy.android.app.utils.CallUtil
import mega.privacy.android.app.utils.CallUtil.getImageAvatarCall
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.LogUtil.logDebug
import mega.privacy.android.app.utils.LogUtil.logError
import nz.mega.sdk.MegaChatApiJava.MEGACHAT_INVALID_HANDLE
import nz.mega.sdk.MegaChatCall
import nz.mega.sdk.MegaChatSession

@AndroidEntryPoint
class IndividualCallFragment : MeetingBaseFragment() {

    private var chatId: Long? = null
    private var peerId: Long? = null
    private var clientId: Long? = null

    private var isFloatingWindow = false

    // Views
    private lateinit var vVideo: SurfaceView
    private var vAvatarLayout: RelativeLayout? = null
    private lateinit var vAvatar: RoundedImageView
    private lateinit var vOnHold: ImageView

    private val inMeetingViewModel by viewModels<InMeetingViewModel>()

    private var videoListener: MeetingVideoListener? = null

    var videoAlpha = 255

    private val remoteAVFlagsObserver =
        Observer<Pair<Long, MegaChatSession>> { callAndSession ->
            when {
                inMeetingViewModel.isOneToOneCall() && inMeetingViewModel.isSameCall(callAndSession.first) -> {
                    when {
                        isFloatingWindow -> checkItIsOnlyAudio()
                        else -> {
                            when {
                                callAndSession.second.hasVideo() -> activateVideo(
                                    this.peerId!!,
                                    this.clientId!!
                                )
                                else -> showAvatar(this.peerId!!, this.clientId!!)
                            }
                        }
                    }
                }
            }
        }

    private val localAVFlagsObserver = Observer<MegaChatCall> {
        when {
            inMeetingViewModel.isSameCall(it.callId) -> checkItIsOnlyAudio()
        }
    }

    private val callOnHoldObserver = Observer<MegaChatCall> {
        when {
            inMeetingViewModel.isSameCall(it.callId) -> checkCallOnHold(it.isOnHold)
        }
    }

    private val sessionOnHoldObserver =
        Observer<Pair<Long, MegaChatSession>> { callAndSession ->
            when {
                inMeetingViewModel.isOneToOneCall() && inMeetingViewModel.isSameCall(callAndSession.first) -> checkCallOnHold(
                    callAndSession.second.isOnHold
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            chatId = it.getLong(Constants.CHAT_ID, MEGACHAT_INVALID_HANDLE)
            peerId = it.getLong(Constants.PEER_ID, MEGACHAT_INVALID_HANDLE)
            clientId = it.getLong(Constants.CLIENT_ID, MEGACHAT_INVALID_HANDLE)
            isFloatingWindow = it.getBoolean(Constants.IS_FLOATING_WINDOW, false)
        }

        when (chatId) {
            MEGACHAT_INVALID_HANDLE -> {
                logError("Error. Chat doesn't exist")
                return
            }
            else -> {
                chatId?.let { inMeetingViewModel.setChat(it) }
                when {
                    inMeetingViewModel.getCall() == null || peerId == MEGACHAT_INVALID_HANDLE -> {
                        logError("Error. Call doesn't exist")
                        return
                    }
                    !inMeetingViewModel.isMe(peerId) && clientId == MEGACHAT_INVALID_HANDLE -> {
                        logError("Error. Client id invalid")
                        return
                    }
                    else -> initLiveEventBus()
                }
            }
        }
    }

    private fun initLiveEventBus() {
        LiveEventBus.get(Constants.EVENT_LOCAL_AVFLAGS_CHANGE, MegaChatCall::class.java)
            .observeSticky(this, localAVFlagsObserver)

        LiveEventBus.get(Constants.EVENT_CALL_ON_HOLD_CHANGE, MegaChatCall::class.java)
            .observeSticky(this, callOnHoldObserver)

        LiveEventBus.get(Constants.EVENT_REMOTE_AVFLAGS_CHANGE)
            .observeSticky(this, remoteAVFlagsObserver as Observer<Any>)

        LiveEventBus.get(Constants.EVENT_SESSION_ON_HOLD_CHANGE)
            .observeSticky(this, sessionOnHoldObserver as Observer<Any>)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        when {
            isFloatingWindow -> {
                val binding = SelfFeedFloatingWindowFragmentBinding.inflate(
                    inflater,
                    container,
                    false
                )

                binding.root.let {
                    vVideo = it.video
                    vAvatar = it.avatar
                    vAvatarLayout = it.avatar_layout
                    vOnHold = it.on_hold_icon
                }
                return binding.root
            }
            else -> {
                val binding = IndividualCallFragmentBinding.inflate(
                    inflater,
                    container,
                    false
                )

                binding.root.let {
                    vVideo = it.video
                    vAvatar = it.avatar
                    vAvatarLayout = null
                    vOnHold = it.on_hold_icon
                }
                return binding.root
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initAvatar()
        initLocalVideo()

        when {
            isFloatingWindow -> {
                if ((parentFragment as InMeetingFragment).bottomFloatingPanelViewHolder.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    view.alpha = 0f
                }

                (parentFragment as InMeetingFragment).bottomFloatingPanelViewHolder.propertyUpdaters.apply {
                    add {
                        view.alpha = 1 - it
                    }
                    add {
                        videoAlpha = ((1 - it) * 255).toInt()
                    }
                }
            }
        }
    }

    /**
     * Initialising the avatar
     */
    private fun initAvatar() {
        inMeetingViewModel.getChat()?.let {
            var avatar = getImageAvatarCall(it, peerId!!)
            when (avatar) {
                null -> {
                    avatar = CallUtil.getDefaultAvatarCall(context, peerId!!)
                }
            }

            vAvatar.setImageBitmap(avatar)
        }
    }

    /**
     * Initialising the local video
     */
    private fun initLocalVideo() {
        when {
            inMeetingViewModel.isMe(this.peerId) -> {
                inMeetingViewModel.getCall()?.let {
                    when {
                        it.hasLocalVideo() -> activateVideo(this.peerId!!, this.clientId!!)
                        else -> showAvatar(this.peerId!!, this.clientId!!)
                    }
                }
            }
        }
    }

    /**
     * Method to hide the Avatar
     */
    private fun hideAvatar(peerId: Long, clientId: Long) {
        when {
            peerId != this.peerId || clientId != this.clientId -> return
            else -> {
                vOnHold.isVisible = false
                vAvatar.alpha = 1f
                vAvatar.isVisible = false
                vAvatarLayout?.let {
                    it.isVisible = false
                }
            }
        }
    }

    /**
     * Method to control the Call on hold icon visibility
     */
    private fun showCallOnHoldIcon() {
        when {
            inMeetingViewModel.isOneToOneCall() -> {
                when {
                    inMeetingViewModel.isCallOrSessionOnHold() && !isFloatingWindow -> {
                        vOnHold.isVisible = true
                        vAvatar.alpha = 0.5f
                    }
                    else -> {
                        vOnHold.isVisible = false
                        vAvatar.alpha = 1f
                    }
                }
            }
            else -> {
                when {
                    inMeetingViewModel.isCallOnHold() -> {
                        vOnHold.isVisible = true
                        vAvatar.alpha = 0.5f
                    }
                    else -> {
                        vOnHold.isVisible = false
                        vAvatar.alpha = 1f
                    }
                }
            }
        }
    }

    /**
     * Method to check if there is a call or session on hold
     */
    private fun checkCallOnHold(isOnHold: Boolean) {
        when {
            isOnHold -> {
                //It's on hold
                when {
                    inMeetingViewModel.isOneToOneCall() && inMeetingViewModel.isMe(this.peerId) && isFloatingWindow -> {
                        closeVideo(this.peerId!!, this.clientId!!)
                        hideAvatar(this.peerId!!, this.clientId!!)
                    }
                    else ->{
                        showAvatar(this.peerId!!, this.clientId!!)
                    }
                }
            }
            else -> {
                //It is not on hold
                when {
                    inMeetingViewModel.isMe(this.peerId) -> {
                        val call = inMeetingViewModel.getCall()
                        call?.let {
                            when {
                                it.hasLocalVideo() && !inMeetingViewModel.isCallOrSessionOnHold() -> activateVideo(
                                    this.peerId!!,
                                    this.clientId!!
                                )
                                else -> {
                                    showAvatar(this.peerId!!, this.clientId!!)
                                    checkItIsOnlyAudio()
                                }
                            }
                        }
                    }
                    inMeetingViewModel.isOneToOneCall() -> {
                        val session = inMeetingViewModel.getSession(this.clientId!!)
                        session?.let {
                            when {
                                it.hasVideo() && !inMeetingViewModel.isCallOrSessionOnHold() -> activateVideo(
                                    this.peerId!!,
                                    this.clientId!!
                                )
                                else -> showAvatar(this.peerId!!, this.clientId!!)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Check if is an audio call
     */
    private fun checkItIsOnlyAudio() {
        when {
            !isFloatingWindow || !inMeetingViewModel.isOneToOneCall() -> return
            else -> when {
                inMeetingViewModel.isAudioCall() -> this.peerId?.let {
                    this.clientId?.let { it1 ->
                        hideAvatar(
                            it,
                            it1
                        )
                    }
                }
                else -> {
                    inMeetingViewModel.getCall()?.let { call ->
                        if (!call.hasLocalVideo()) {
                            vAvatar.isVisible = true
                            vAvatarLayout?.let {
                                it.isVisible = true
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Method to show the Avatar
     */
    fun showAvatar(peerId: Long, clientId: Long) {
        when {
            peerId != this.peerId || clientId != this.clientId -> return
            else -> {
                vAvatar.isVisible = true
                vAvatarLayout?.let {
                    it.isVisible = true
                }

                checkItIsOnlyAudio()
                closeVideo(peerId, clientId)
                showCallOnHoldIcon()
            }
        }

    }

    /**
     * Method for activating the video.
     */
    fun activateVideo(peerId: Long, clientId: Long) {
        when {
            peerId != this.peerId || clientId != this.clientId -> return
        }

        hideAvatar(peerId, clientId)

        when (videoListener) {
            null -> {
                when {
                    inMeetingViewModel.isMe(peerId) -> {
                        videoListener = MeetingVideoListener(
                            vVideo,
                            outMetrics,
                            MEGACHAT_INVALID_HANDLE,
                            isFloatingWindow
                        )

                        sharedModel.addLocalVideo(chatId!!, videoListener)
                    }
                    else -> {
                        videoListener = MeetingVideoListener(
                            vVideo,
                            outMetrics,
                            clientId,
                            false
                        )

                        sharedModel.addRemoteVideo(chatId!!, clientId, true, videoListener!!)
                        inMeetingViewModel.getSession(clientId)?.let {
                            if (!it.canRecvVideoHiRes()) {
                                sharedModel.requestHiResVideo(
                                    chatId!!, clientId, RequestHiResVideoListener(
                                        requireContext()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        vVideo.isVisible = true
    }

    /**
     * Method to close Video
     */
    private fun closeVideo(peerId: Long, clientId: Long) {
        when {
            peerId != this.peerId || clientId != this.clientId -> return
        }

        vVideo.isVisible = false
        removeChatVideoListener()
    }

    /**
     * Remove chat video listener
     */
    private fun removeChatVideoListener() {
        when (videoListener) {
            null -> return
            else -> {
                when {
                    inMeetingViewModel.isMe(this.peerId) -> {
                        sharedModel.removeLocalVideo(chatId!!, videoListener!!)
                    }
                    else -> {
                        val session = inMeetingViewModel.getSession(clientId!!)
                        session?.let {
                            if (it.canRecvVideoHiRes()) {
                                sharedModel.stopHiResVideo(
                                    chatId!!, clientId!!, RequestHiResVideoListener(
                                        requireContext()
                                    )
                                )
                            }
                        }

                        sharedModel.removeRemoteVideo(chatId!!, clientId!!, true, videoListener!!)
                    }
                }

                videoListener = null
            }
        }
    }

    /**
     * Method to destroy the surfaceView.
     */
    private fun removeSurfaceView() {
        vVideo.let { surfaceView ->
            if (surfaceView.parent != null && surfaceView.parent.parent != null) {
                logDebug("Removing surface view")
                (surfaceView.parent as ViewGroup).removeView(surfaceView)
            }
            surfaceView.isVisible = false
        }

        removeChatVideoListener()
    }

    companion object {

        const val TAG = "IndividualCallFragment"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param chatId Chat ID
         * @param peerId peer ID
         * @param isFloatingWindow True, if it's floating window. False, otherwise.
         * @return A new instance of fragment MeetingFragment.
         */
        @JvmStatic
        fun newInstance(chatId: Long, peerId: Long, isFloatingWindow: Boolean) =
            IndividualCallFragment().apply {
                arguments = Bundle().apply {
                    putLong(Constants.CHAT_ID, chatId)
                    putLong(Constants.PEER_ID, peerId)
                    putBoolean(Constants.IS_FLOATING_WINDOW, isFloatingWindow)
                }
            }

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param chatId Chat ID
         * @param peerId peer ID
         * @param clientId client ID
         * @return A new instance of fragment MeetingFragment.
         */
        fun newInstance(chatId: Long, peerId: Long, clientId: Long) =
            IndividualCallFragment().apply {
                arguments = Bundle().apply {
                    putLong(Constants.CHAT_ID, chatId)
                    putLong(Constants.PEER_ID, peerId)
                    putLong(Constants.CLIENT_ID, clientId)
                }
            }
    }

    override fun onResume() {
        super.onResume()

        videoListener?.let {
            it.height = 0
            it.width = 0
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        removeSurfaceView()
        vAvatar.setImageBitmap(null)
    }
}