package mega.privacy.android.app.presentation.node.model

import androidx.annotation.DrawableRes
import mega.privacy.android.app.presentation.favourites.model.mapper.getFolderIcon
import mega.privacy.android.app.presentation.node.model.mapper.getFileIcon
import mega.privacy.android.domain.entity.node.TypedFileNode
import mega.privacy.android.domain.entity.node.TypedFolderNode
import mega.privacy.android.domain.entity.node.TypedNode

/**
 * Get the icon resource associated to this [TypedNode]
 */
@DrawableRes
fun TypedNode.getIcon() = when (this) {
    is TypedFileNode -> getFileIcon(this)
    is TypedFolderNode -> getFolderIcon(this)
}