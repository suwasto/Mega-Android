package mega.privacy.android.app.meeting.fragments

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Display
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import mega.privacy.android.app.R
import mega.privacy.android.app.components.twemoji.EmojiEditText
import mega.privacy.android.app.databinding.FragmentMeetingInfoBinding
import mega.privacy.android.app.lollipop.controllers.ChatController
import mega.privacy.android.app.meeting.activity.MeetingActivityViewModel
import mega.privacy.android.app.meeting.adapter.Participant
import mega.privacy.android.app.modalbottomsheet.BaseBottomSheetDialogFragment
import mega.privacy.android.app.utils.ChatUtil
import mega.privacy.android.app.utils.ColorUtils.getThemeColor
import mega.privacy.android.app.utils.Constants
import mega.privacy.android.app.utils.LogUtil
import mega.privacy.android.app.utils.Util
import mega.privacy.android.app.utils.Util.showSnackbar
import nz.mega.sdk.MegaChatApiJava
import nz.mega.sdk.MegaChatError
import nz.mega.sdk.MegaChatRequest
import nz.mega.sdk.MegaChatRequestListenerInterface

class MeetingInfoBottomSheetDialogFragment : BaseBottomSheetDialogFragment(),
    MegaChatRequestListenerInterface {
    private lateinit var binding: FragmentMeetingInfoBinding
    private val inMeetingViewModel by lazy { (parentFragment as InMeetingFragment).inMeetingViewModel }
    private val shareViewModel: MeetingActivityViewModel by activityViewModels()
    private var changeTitleDialog: AlertDialog? = null
    private var chatTitle: String = ""
    private var chatLink: String = ""

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)

        binding = FragmentMeetingInfoBinding.inflate(LayoutInflater.from(context), null, false)
        dialog.setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        initView()
    }

    private fun initView() {
        inMeetingViewModel.chatTitle.observe(this) {
            chatTitle = it
            binding.meetingName.text = it
        }
        inMeetingViewModel.participants.observe(this) { participants ->
            binding.participantSize.text =
                requireContext().getString(R.string.participants_chat_label) + ": " + (participants.size + 1)
            binding.moderatorName.text =
                requireContext().getString(R.string.administrator_permission_label_participants_panel) + ": " + getModeratorList(
                    participants
                )
        }

        shareViewModel.meetingLinkLiveData.observe(this) { link ->
            if (link.isNotEmpty()) {
                binding.copyLink.isVisible = true
                binding.copyLink.text = link
                chatLink = link
                binding.copyLink.setOnClickListener { copyLink() }
            }
        }

        binding.edit.isVisible = inMeetingViewModel.isModerator()
        binding.shareLink.isVisible = inMeetingViewModel.isLinkVisible()
        binding.invite.isVisible = inMeetingViewModel.isLinkVisible()

        listenAction(binding.shareLink) { (parentFragment as InMeetingFragment).onShareLink() }
        listenAction(binding.invite) { (parentFragment as InMeetingFragment).onInviteParticipants() }
        listenAction(binding.copyLink) { copyLink() }
        listenAction(binding.edit) { showRenameGroupDialog() }
    }

    /**
     * After execute the action, close the item dialog
     */
    private fun listenAction(view: View, action: () -> Unit) {
        view.setOnClickListener {
            action()
            dismiss()
        }
    }

    fun getModeratorList(participants: MutableList<Participant>): String {
        var nameList = if (inMeetingViewModel.isModerator()) {
            ChatController(context).myFullName
        } else {
            ""
        }

        participants.filter { it.isModerator }
            .map { it.name }.forEach {
                if (it.isNotEmpty())
                    nameList = "$nameList, $it"
            }

        return nameList
    }

    fun copyLink() {
        LogUtil.logDebug("copyLink")
        if (chatLink != null) {
            val clipboard =
                requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Copied Text", chatLink)
            clipboard.setPrimaryClip(clip)
            showSnackbar(requireContext(), getString(R.string.chat_link_copied_clipboard))
        } else {
            showSnackbar(requireContext(), getString(R.string.general_text_error))
        }
    }

    fun showRenameGroupDialog() {
        val activity = requireActivity()
        val layout = LinearLayout(requireActivity())
        layout.orientation = LinearLayout.VERTICAL
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

        val display: Display = requireActivity().windowManager.defaultDisplay
        outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)

        params.setMargins(
            Util.scaleWidthPx(20, outMetrics),
            Util.scaleHeightPx(16, outMetrics),
            Util.scaleWidthPx(17, outMetrics),
            0
        )

        val input = EmojiEditText(requireActivity())
        layout.addView(input, params)
        input.setOnLongClickListener { v: View? -> false }
        input.setSingleLine()
        input.setSelectAllOnFocus(true)
        input.setTextColor(getThemeColor(requireActivity(), android.R.attr.textColorSecondary))
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        input.setEmojiSize(Util.dp2px(Constants.EMOJI_SIZE.toFloat(), outMetrics))
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.inputType = InputType.TYPE_CLASS_TEXT
        val maxAllowed = ChatUtil.getMaxAllowed(chatTitle)
        input.filters = arrayOf<InputFilter>(LengthFilter(maxAllowed))
        input.setText(chatTitle)
        val builder = MaterialAlertDialogBuilder(requireContext())
        input.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                changeTitle(input)
            } else {
                LogUtil.logDebug("Other IME$actionId")
            }
            false
        }
        input.setImeActionLabel(getString(R.string.context_rename), EditorInfo.IME_ACTION_DONE)
        builder.setTitle(R.string.context_rename)
            .setPositiveButton(getString(R.string.context_rename), null)
            .setNegativeButton(android.R.string.cancel, null)
            .setView(layout)
            .setOnDismissListener { dialog: DialogInterface? ->
                Util.hideKeyboard(
                    activity,
                    InputMethodManager.HIDE_NOT_ALWAYS
                )
            }
        changeTitleDialog = builder.create()
        changeTitleDialog?.apply {
            show()
            getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener { changeTitle(input) }
        }
    }

    private fun changeTitle(input: EmojiEditText) {
        val title = input.text.toString()
        if (title == "" || title.isEmpty() || title.trim { it <= ' ' }.isEmpty()) {
            LogUtil.logWarning("Input is empty")
            input.error = getString(R.string.invalid_string)
            input.requestFocus()
        } else if (!ChatUtil.isAllowedTitle(title)) {
            LogUtil.logWarning("Title is too long")
            input.error = getString(R.string.title_long)
            input.requestFocus()
        } else {
            LogUtil.logDebug("Positive button pressed - change title")
            inMeetingViewModel.changeMeetingRoom(title, this)
            changeTitleDialog?.dismiss()
        }
    }


    companion object {
        fun newInstance(): MeetingInfoBottomSheetDialogFragment {
            return MeetingInfoBottomSheetDialogFragment()
        }
    }

    override fun onRequestStart(api: MegaChatApiJava?, request: MegaChatRequest?) {
    }

    override fun onRequestUpdate(api: MegaChatApiJava?, request: MegaChatRequest?) {
    }

    override fun onRequestFinish(
        api: MegaChatApiJava?,
        request: MegaChatRequest?,
        e: MegaChatError?
    ) {
    }

    override fun onRequestTemporaryError(
        api: MegaChatApiJava?,
        request: MegaChatRequest?,
        e: MegaChatError?
    ) {
    }
}