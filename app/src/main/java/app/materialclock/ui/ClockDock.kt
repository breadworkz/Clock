package app.materialclock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt

/**
 * The app's primary navigation: responsive floating pill dock (vertical on tablets/large displays, horizontal at the bottom on phones).
 */
@Composable
fun ClockDock(
    destinations: List<Tab>,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    showAdd: Boolean,
    addLabel: String,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    isVertical: Boolean = LocalConfiguration.current.screenWidthDp >= 600,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    if (isVertical) {
        Column(
            modifier = modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (dark) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shadowElevation = 6.dp,
                modifier = Modifier.selectableGroup(),
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    destinations.forEach { d ->
                        DockItem(tab = d, selected = d == selected, dark = dark, onClick = { onSelect(d) })
                    }

                    Box(
                        modifier = Modifier
                            .size(ITEM_SIZE)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button, onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ICON_SIZE),
                        )
                    }
                }
            }
            PlusButton(visible = showAdd, label = addLabel, isVertical = true, onClick = onAdd)
        }
    } else {
        Row(
            modifier = modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (dark) {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shadowElevation = 6.dp,
                modifier = Modifier.selectableGroup(),
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    destinations.forEach { d ->
                        DockItem(tab = d, selected = d == selected, dark = dark, onClick = { onSelect(d) })
                    }

                    Box(
                        modifier = Modifier
                            .size(ITEM_SIZE)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button, onClick = onOpenSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ICON_SIZE),
                        )
                    }
                }
            }
            PlusButton(visible = showAdd, label = addLabel, isVertical = false, onClick = onAdd)
        }
    }
}

@Composable
private fun DockItem(tab: Tab, selected: Boolean, dark: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        !selected -> Color.Transparent
        dark -> scheme.primary
        else -> scheme.surfaceContainer
    }
    val content = when {
        !selected -> LocalContentColor.current
        dark -> scheme.onPrimary
        else -> scheme.onSurface
    }
    Box(
        modifier = Modifier
            .size(ITEM_SIZE)
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = content,
            modifier = Modifier.size(ICON_SIZE),
        )
    }
}

@Composable
private fun PlusButton(visible: Boolean, label: String, isVertical: Boolean, onClick: () -> Unit) {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val grow by animateFloatAsState(if (visible) 1f else 0f, spec, label = "plusGrow")
    val glyph by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 110),
        label = "plusGlyph",
    )
    if (grow <= 0.001f) return

    Layout(
        content = {
            FloatingActionButton(
                onClick = onClick,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(lerp(FAB_SIZE / 2, FAB_CORNER, grow)),
                modifier = Modifier
                    .size(FAB_SIZE)
                    .graphicsLayer { scaleX = grow; scaleY = grow },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = label,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { scaleX = glyph; scaleY = glyph; alpha = glyph },
                )
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val gap = FAB_GAP.toPx()
        if (isVertical) {
            val height = ((placeable.height + gap) * grow).roundToInt()
            layout(placeable.width, height) {
                placeable.place(0, height - placeable.height)
            }
        } else {
            val width = ((placeable.width + gap) * grow).roundToInt()
            layout(width, placeable.height) {
                placeable.place(width - placeable.width, 0)
            }
        }
    }
}

val DOCK_HEIGHT = 72.dp
private val ITEM_SIZE = 52.dp
private val ICON_SIZE = 24.dp

private val FAB_SIZE = DOCK_HEIGHT
private val FAB_CORNER = 24.dp
private val FAB_GAP = 10.dp
