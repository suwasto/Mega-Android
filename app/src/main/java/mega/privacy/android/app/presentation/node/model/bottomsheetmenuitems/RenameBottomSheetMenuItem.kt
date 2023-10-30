package mega.privacy.android.app.presentation.node.model.bottomsheetmenuitems

import androidx.compose.runtime.Composable
import mega.privacy.android.app.presentation.node.model.menuaction.RenameMenuAction
import mega.privacy.android.core.ui.controls.lists.MenuActionListTile
import mega.privacy.android.core.ui.model.MenuAction
import mega.privacy.android.core.ui.model.MenuActionWithIcon
import mega.privacy.android.domain.entity.node.TypedNode
import javax.inject.Inject

/**
 * Rename bottom sheet menu item
 */
class RenameBottomSheetMenuItem @Inject constructor() :
    NodeBottomSheetMenuItem<MenuActionWithIcon> {
    override fun shouldDisplay() = true

    override val menuAction: RenameMenuAction = RenameMenuAction(220)
    override val groupId: Int
        get() = 8

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
                onActionClicked = { it(menuAction) },
            )
        }
}