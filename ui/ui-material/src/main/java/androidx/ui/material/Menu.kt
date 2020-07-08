/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.material

import android.util.DisplayMetrics
import androidx.animation.FloatPropKey
import androidx.animation.LinearOutSlowInEasing
import androidx.animation.transitionDefinition
import androidx.animation.tween
import androidx.compose.Composable
import androidx.compose.Immutable
import androidx.compose.getValue
import androidx.compose.remember
import androidx.compose.setValue
import androidx.compose.state
import androidx.ui.animation.Transition
import androidx.ui.core.ContextAmbient
import androidx.ui.core.DensityAmbient
import androidx.ui.core.DrawLayerModifier
import androidx.ui.core.LayoutDirection
import androidx.ui.core.Modifier
import androidx.ui.core.Popup
import androidx.ui.core.PopupPositionProvider
import androidx.ui.core.TransformOrigin
import androidx.ui.unit.Position
import androidx.ui.foundation.Box
import androidx.ui.foundation.ContentGravity
import androidx.ui.foundation.ProvideTextStyle
import androidx.ui.foundation.VerticalScroller
import androidx.ui.foundation.clickable
import androidx.ui.layout.ColumnScope
import androidx.ui.layout.ExperimentalLayout
import androidx.ui.layout.IntrinsicSize
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.padding
import androidx.ui.layout.preferredSizeIn
import androidx.ui.layout.preferredWidth
import androidx.ui.material.ripple.RippleIndication
import androidx.ui.unit.Density
import androidx.ui.unit.IntBounds
import androidx.ui.unit.IntOffset
import androidx.ui.unit.IntSize
import androidx.ui.unit.dp
import androidx.ui.unit.height
import androidx.ui.unit.width
import kotlin.math.max
import kotlin.math.min

/**
 * A Material Design [dropdown menu](https://material.io/components/menus#dropdown-menu).
 *
 * The menu has a [toggle], which is the element generating the menu. For example, this can be
 * an icon which, when tapped, triggers the menu.
 * The content of the [DropdownMenu] can be [DropdownMenuItem]s, as well as custom content.
 * [DropdownMenuItem] can be used to achieve items as defined by the Material Design spec.
 * [onDismissRequest] will be called when the menu should close - for example when there is a
 * tap outside the menu, or when the back key is pressed.
 * The menu will do a best effort to be fully visible on screen. It will try to expand
 * horizontally, depending on layout direction, to the end of the [toggle], then to the start of
 * the [toggle], and then screen end-aligned. Vertically, it will try to expand to the bottom
 * of the [toggle], then from the top of the [toggle], and then screen top-aligned. A
 * [dropdownOffset] can be provided to adjust the positioning of the menu for cases when the
 * layout bounds of the [toggle] do not coincide with its visual bounds.
 *
 * Example usage:
 * @sample androidx.ui.material.samples.MenuSample
 *
 * @param toggle The element generating the menu
 * @param expanded Whether the menu is currently open or dismissed
 * @param onDismissRequest Called when the menu should be dismiss
 * @param toggleModifier The modifier to be applied to the toggle
 * @param dropdownOffset Offset to be added to the position of the menu
 * @param dropdownModifier Modifier to be applied to the menu content
 */
@Composable
fun DropdownMenu(
    toggle: @Composable () -> Unit,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    toggleModifier: Modifier = Modifier,
    dropdownOffset: Position = Position(0.dp, 0.dp),
    dropdownModifier: Modifier = Modifier,
    dropdownContent: @Composable ColumnScope.() -> Unit
) {
    var visibleMenu by state { expanded }
    if (expanded) visibleMenu = true

    Box(toggleModifier) {
        toggle()

        if (visibleMenu) {
            var transformOrigin by state { TransformOrigin.Center }
            val density = DensityAmbient.current
            val popupPositionProvider = DropdownMenuPositionProvider(
                dropdownOffset,
                density,
                ContextAmbient.current.resources.displayMetrics
            ) { parentBounds, menuBounds ->
                transformOrigin = calculateTransformOrigin(parentBounds, menuBounds, density)
            }

            Popup(
                isFocusable = true,
                onDismissRequest = onDismissRequest,
                popupPositionProvider = popupPositionProvider
            ) {
                Transition(
                    definition = DropdownMenuOpenCloseTransition,
                    initState = !expanded,
                    toState = expanded,
                    onStateChangeFinished = {
                        visibleMenu = it
                    }
                ) { state ->
                    val drawLayer = remember {
                        MenuDrawLayerModifier(
                            { state[Scale] },
                            { state[Alpha] },
                            { transformOrigin }
                        )
                    }
                    Card(
                        modifier = drawLayer
                            // MenuVerticalMargin corresponds to the one Material row margin
                            // required between the menu and the display edges. The
                            // MenuElevationInset is needed for drawing the elevation,
                            // otherwise it is clipped. TODO(popam): remove it after b/156890315
                            .padding(MenuElevationInset, MenuVerticalMargin),
                        elevation = MenuElevation
                    ) {
                        @OptIn(ExperimentalLayout::class)
                        VerticalScroller(
                            modifier = dropdownModifier
                                .padding(vertical = DropdownMenuVerticalPadding)
                                .preferredWidth(IntrinsicSize.Max),
                            children = dropdownContent
                        )
                    }
                }
            }
        }
    }
}

/**
 * A dropdown menu item, as defined by the Material Design spec.
 *
 * Example usage:
 * @sample androidx.ui.material.samples.MenuSample
 *
 * @param onClick Called when the menu item was clicked
 * @param modifier The modifier to be applied to the menu item
 * @param enabled Controls the enabled state of the menu item - when `false`, the menu item
 * will not be clickable and [onClick] will not be invoked
 */
@Composable
fun DropdownMenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    // TODO(popam, b/156911853): investigate replacing this Box with ListItem
    Box(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick, indication = RippleIndication(true))
            .fillMaxWidth()
            // Preferred min and max width used during the intrinsic measurement.
            .preferredSizeIn(
                minWidth = DropdownMenuItemDefaultMinWidth,
                maxWidth = DropdownMenuItemDefaultMaxWidth,
                minHeight = DropdownMenuItemDefaultMinHeight
            )
            .padding(horizontal = DropdownMenuHorizontalPadding),
        gravity = ContentGravity.CenterStart
    ) {
        // TODO(popam, b/156912039): update emphasis if the menu item is disabled
        val typography = MaterialTheme.typography
        val emphasisLevels = EmphasisAmbient.current
        ProvideTextStyle(typography.subtitle1) {
            ProvideEmphasis(
                if (enabled) emphasisLevels.high else emphasisLevels.disabled,
                content
            )
        }
    }
}

// Size constants.
private val MenuElevation = 8.dp
internal val MenuElevationInset = 32.dp
private val MenuVerticalMargin = 32.dp
private val DropdownMenuHorizontalPadding = 16.dp
internal val DropdownMenuVerticalPadding = 8.dp
private val DropdownMenuItemDefaultMinWidth = 112.dp
private val DropdownMenuItemDefaultMaxWidth = 280.dp
private val DropdownMenuItemDefaultMinHeight = 48.dp

// Menu open/close animation.
private val Scale = FloatPropKey()
private val Alpha = FloatPropKey()
internal const val InTransitionDuration = 120
internal const val OutTransitionDuration = 75

private val DropdownMenuOpenCloseTransition = transitionDefinition {
    state(false) {
        // Menu is dismissed.
        this[Scale] = 0.8f
        this[Alpha] = 0f
    }
    state(true) {
        // Menu is expanded.
        this[Scale] = 1f
        this[Alpha] = 1f
    }
    transition(false, true) {
        // Dismissed to expanded.
        Scale using tween(
            durationMillis = InTransitionDuration,
            easing = LinearOutSlowInEasing
        )
        Alpha using tween(
            durationMillis = 30
        )
    }
    transition(true, false) {
        // Expanded to dismissed.
        Scale using tween(
            durationMillis = 1,
            delayMillis = OutTransitionDuration - 1
        )
        Alpha using tween(
            durationMillis = OutTransitionDuration
        )
    }
}

private class MenuDrawLayerModifier(
    val scaleProvider: () -> Float,
    val alphaProvider: () -> Float,
    val transformOriginProvider: () -> TransformOrigin
) : DrawLayerModifier {
    override val scaleX: Float get() = scaleProvider()
    override val scaleY: Float get() = scaleProvider()
    override val alpha: Float get() = alphaProvider()
    override val transformOrigin: TransformOrigin get() = transformOriginProvider()
    override val clip: Boolean = true
}

private fun calculateTransformOrigin(
    parentBounds: IntBounds,
    menuBounds: IntBounds,
    density: Density
): TransformOrigin {
    val inset = with(density) { MenuElevationInset.toIntPx() }
    val realMenuBounds = IntBounds(
        menuBounds.left + inset,
        menuBounds.top + inset,
        menuBounds.right - inset,
        menuBounds.bottom - inset
    )
    val pivotX = when {
        realMenuBounds.left >= parentBounds.right -> 0
        realMenuBounds.right <= parentBounds.left -> 1
        else -> {
            val intersectionCenter =
                (max(parentBounds.left, realMenuBounds.left) +
                        min(parentBounds.right, realMenuBounds.right)) / 2
            (intersectionCenter + inset - menuBounds.left) / menuBounds.width
        }
    }
    val pivotY = when {
        realMenuBounds.top >= parentBounds.bottom -> 0
        realMenuBounds.bottom <= parentBounds.top -> 1
        else -> {
            val intersectionCenter =
                (max(parentBounds.top, realMenuBounds.top) +
                        min(parentBounds.bottom, realMenuBounds.bottom)) / 2
            (intersectionCenter + inset - menuBounds.top) / menuBounds.height
        }
    }
    return TransformOrigin(pivotX.toFloat(), pivotY.toFloat())
}

// Menu positioning.

/**
 * Calculates the position of a Material [DropdownMenu].
 */
// TODO(popam): Investigate if this can/should consider the app window size rather than screen size
@Immutable
internal data class DropdownMenuPositionProvider(
    val contentOffset: Position,
    val density: Density,
    val displayMetrics: DisplayMetrics,
    val onPositionCalculated: (IntBounds, IntBounds) -> Unit = { _, _ -> }
) : PopupPositionProvider {
    override fun calculatePosition(
        parentLayoutBounds: IntBounds,
        layoutDirection: LayoutDirection,
        popupSize: IntSize
    ): IntOffset {
        // The min margin above and below the menu, relative to the screen.
        val verticalMargin = with(density) { MenuVerticalMargin.toIntPx() }
        // The padding inset that accommodates elevation, needs to be taken into account.
        val inset = with(density) { MenuElevationInset.toIntPx() }
        val realPopupWidth = popupSize.width - inset * 2
        val realPopupHeight = popupSize.height - inset * 2
        val contentOffsetX = with(density) { contentOffset.x.toIntPx() }
        val contentOffsetY = with(density) { contentOffset.y.toIntPx() }

        // Compute horizontal position.
        val toRight = parentLayoutBounds.right + contentOffsetX
        val toLeft = parentLayoutBounds.left - contentOffsetX - realPopupWidth
        val toDisplayRight = displayMetrics.widthPixels - realPopupWidth
        val toDisplayLeft = 0
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            sequenceOf(toRight, toLeft, toDisplayRight)
        } else {
            sequenceOf(toLeft, toRight, toDisplayLeft)
        }.firstOrNull {
            it >= 0 && it + realPopupWidth <= displayMetrics.widthPixels
        } ?: toLeft

        // Compute vertical position.
        val toBottom = parentLayoutBounds.bottom + contentOffsetY
        val toTop = parentLayoutBounds.top - contentOffsetY - realPopupHeight
        val toCenter = parentLayoutBounds.top - realPopupHeight / 2
        val toDisplayBottom = displayMetrics.heightPixels - realPopupHeight - verticalMargin
        val y = sequenceOf(toBottom, toTop, toCenter, toDisplayBottom).firstOrNull {
            it >= verticalMargin &&
                    it + realPopupHeight <= displayMetrics.heightPixels - verticalMargin
        } ?: toTop

        onPositionCalculated(
            parentLayoutBounds,
            IntBounds(x - inset, y - inset, x + inset + realPopupWidth, y + inset + realPopupHeight)
        )
        return IntOffset(x - inset, y - inset)
    }
}
