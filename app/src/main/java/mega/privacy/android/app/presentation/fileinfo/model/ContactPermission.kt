package mega.privacy.android.app.presentation.fileinfo.model

import mega.privacy.android.domain.entity.contacts.ContactItem
import mega.privacy.android.domain.entity.shares.AccessPermission

/**
 * Class to join a [ContactItem] with the corresponding [AccessPermission].
 * @param contactItem [ContactItem]
 * @param accessPermission [AccessPermission]
 */
data class ContactPermission(val contactItem: ContactItem, val accessPermission: AccessPermission)