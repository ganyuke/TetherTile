package org.nextcolor.tethertile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import org.nextcolor.tethertile.Shell

data class SetupState(
    val status: Shell.Status,
    val tileAdded: Boolean,
    /** 1 on, 0 off, -1 unknown. */
    val tethering: Int,
    val switching: Boolean,
    val error: String?,
)

@Composable
fun SetupScreen(
    state: SetupState,
    onGetShizuku: () -> Unit,
    onOpenShizuku: () -> Unit,
    onAllow: () -> Unit,
    onAddTile: () -> Unit,
    onTethering: (Boolean) -> Unit,
) {
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Tether Tile") },
                scrollBehavior = behavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        val ready = state.status == Shell.Status.Ready
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            item { SectionHeader("Setup") }
            item {
                Row(
                    Icons.Rounded.Terminal, "Shizuku", 0, 3,
                    supporting = when (state.status) {
                        Shell.Status.NotInstalled -> "Not installed"
                        Shell.Status.NotRunning -> "Not running"
                        else -> "Running"
                    },
                ) {
                    when (state.status) {
                        Shell.Status.NotInstalled -> Button(onGetShizuku) { Text("Get Shizuku") }
                        Shell.Status.NotRunning -> Button(onOpenShizuku) { Text("Open") }
                        else -> Done()
                    }
                }
            }
            item {
                Row(
                    Icons.Rounded.Key, "Shizuku access", 1, 3,
                    supporting = if (ready) "Allowed" else "Not allowed",
                ) {
                    if (ready) Done() else Button(onAllow, enabled = state.status == Shell.Status.NoPermission) { Text("Allow") }
                }
            }
            item {
                Row(
                    Icons.Rounded.DashboardCustomize, "Quick Settings tile", 2, 3,
                    supporting = if (state.tileAdded) "Added" else "Not added",
                ) {
                    if (state.tileAdded) Done() else Button(onAddTile) { Text("Add") }
                }
            }

            item { SectionHeader("Tethering") }
            item {
                val on = state.tethering == 1
                SegmentedListItem(
                    onClick = { onTethering(!on) },
                    enabled = ready && !state.switching,
                    shapes = ListItemDefaults.segmentedShapes(0, 1),
                    colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
                    leadingContent = { Icon(Icons.Rounded.BluetoothConnected, contentDescription = null) },
                    supportingContent = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    trailingContent = {
                        Switch(checked = on, onCheckedChange = onTethering, enabled = ready && !state.switching)
                    },
                ) { Text("Bluetooth tethering") }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Row(
    icon: ImageVector,
    headline: String,
    index: Int,
    count: Int,
    supporting: String,
    trailing: @Composable () -> Unit,
) {
    SegmentedListItem(
        onClick = {},
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = { Text(supporting) },
        trailingContent = trailing,
    ) { Text(headline) }
}

@Composable
private fun Done() {
    Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
}
