package mega.privacy.android.domain.usecase

import mega.privacy.android.domain.repository.CameraUploadRepository
import javax.inject.Inject

/**
 * Default implementation of [SetupSecondaryFolder]
 */
class DefaultSetupSecondaryFolder @Inject constructor(
    private val cameraUploadRepository: CameraUploadRepository,
    private val resetSecondaryTimeline: ResetSecondaryTimeline,
    private val updateFolderIconBroadcast: UpdateFolderIconBroadcast,
    private val updateFolderDestinationBroadcast: UpdateFolderDestinationBroadcast,
) : SetupSecondaryFolder {
    override suspend fun invoke(secondaryHandle: Long) {
        cameraUploadRepository.setupSecondaryFolder(secondaryHandle)
            .takeIf { it != cameraUploadRepository.getInvalidHandle() }
            ?.let {
                resetSecondaryTimeline()
                cameraUploadRepository.setSecondarySyncHandle(it)
                updateFolderIconBroadcast(it, true)
                updateFolderDestinationBroadcast(it, true)
            }
    }
}
