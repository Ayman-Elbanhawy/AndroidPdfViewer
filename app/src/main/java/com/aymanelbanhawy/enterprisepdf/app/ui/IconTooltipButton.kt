package com.aymanelbanhawy.enterprisepdf.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconTooltipButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val iconTint = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }

    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(tooltip)
            }
        },
        state = rememberTooltipState(),
    ) {
        Surface(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .sizeIn(minWidth = 52.dp, minHeight = 52.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = tooltip
                },
            shape = CircleShape,
            color = containerColor,
            contentColor = iconTint,
            tonalElevation = if (selected) 6.dp else 1.dp,
            shadowElevation = if (selected) 6.dp else 0.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.36f) else MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = LocalContentColor.current,
                )
            }
        }
    }
}
