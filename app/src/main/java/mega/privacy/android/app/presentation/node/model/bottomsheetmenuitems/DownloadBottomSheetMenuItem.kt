package mega.privacy.android.app.presentation.node.model.bottomsheetmenuitems

import androidx.compose.runtime.Composable
import mega.privacy.android.app.presentation.node.model.menuaction.DownloadMenuAction
import mega.privacy.android.core.ui.controls.lists.MenuActionListTile
import mega.privacy.android.core.ui.model.MenuAction
import mega.privacy.android.core.ui.model.MenuActionWithIcon
import mega.privacy.android.domain.entity.node.TypedNode
import javax.inject.Inject

/**
 * Download bottom sheet menu item
 *
 * @param menuAction [DownloadMenuAction]
 */
class DownloadBottomSheetMenuItem @Inject constructor(
    override val menuAction: DownloadMenuAction,
) : NodeBottomSheetMenuItem<MenuActionWithIcon> {
    override fun shouldDisplay() = true

    override fun menuAction(
        selectedNode: TypedNode,
        showDivider: Boolean,
    ): @Composable ((MenuAction) -> Unit) -> Unit =
        {
            MenuActionListTile(
                text = menuAction.getDescription(),
                icon = menuAction.getIconPainter(),
                addSeparator = showDivider,
                isDestructive = false,
                onActionClicked = { it(menuAction) }
            )
        }

    override val groupId: Int
        get() = 6
}