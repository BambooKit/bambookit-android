package com.bambookit.android.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.ModelRef
import com.bambookit.android.data.ProvidersInfo
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooGreen
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusWarning
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary

/** "Provider / model" for a picked model, with names from the PC's providers list when known. */
internal fun modelLabel(ref: ModelRef, info: ProvidersInfo?): String {
    val p = info?.providers?.firstOrNull { it.id == ref.providerID }
    val m = p?.models?.firstOrNull { it.id == ref.modelID }
    return brandModel("${p?.name ?: ref.providerID} / ${m?.name ?: ref.modelID}") ?: ref.modelID
}

/** The model chip of the composer and the new-session sheet: the current model, tap to change. */
@Composable
internal fun ModelChip(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 40.dp).clickable(enabled = enabled, onClickLabel = "Change model", onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Memory, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(Icons.Filled.KeyboardArrowDown, "Change model", tint = TextMuted, modifier = Modifier.size(18.dp))
    }
}

/**
 * Pick a provider, then a model, from the PC's real providers (GET /v1/devices/:id/providers). "PC default"
 * sends no model. Providers without a key are listed but can't be picked until a key is added in Profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelPickerSheet(
    store: BambooStore,
    deviceId: String,
    pcName: String,
    selected: ModelRef?,
    currentLabel: String?,
    onPick: (ModelRef?) -> Unit,
    onDismiss: () -> Unit,
) {
    val all by store.providers.collectAsState()
    val view = all[deviceId]
    LaunchedEffect(deviceId) { store.loadProviders(deviceId) }
    var openProvider by remember { mutableStateOf(selected?.providerID) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BambooSurfaceElevated) {
        Column(Modifier.padding(horizontal = Space.screen)) {
            Text("Model", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                currentLabel?.let { "This session uses $it." } ?: "Choose the AI model for the next message.",
                color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = Space.s),
            )
        }
        HorizontalDivider(color = BambooBorder)
        val info = view?.info
        val err = view?.error
        when {
            info == null && (view == null || view.loading) -> LoadingState("Reading the models on $pcName…")
            info == null && err != null -> ErrorState(
                errorTitle(err, pcName, "Couldn't read the models"), errorMessage(err, pcName),
                if (err.desktopOutdated) Icons.Filled.SystemUpdate else if (err.desktopUnavailable) Icons.Filled.CloudOff else Icons.Filled.ErrorOutline,
                onRetry = { store.loadProviders(deviceId, force = true) }, retrying = view.loading,
                color = if (err.desktopUnavailable || err.desktopOutdated) StatusWarning else StatusFailed,
            )
            info != null -> LazyColumn(contentPadding = PaddingValues(bottom = Space.xl)) {
                item {
                    PickRow(
                        "PC default", info.default?.let { "Currently ${modelLabel(it, info)}" } ?: "The model set on $pcName",
                        selected = selected == null, enabled = true,
                    ) { onPick(null) }
                    HorizontalDivider(color = BambooBorder)
                }
                val providers = info.providers.sortedWith(compareByDescending<com.bambookit.android.data.AiProvider> { it.configured }.thenBy { (it.name ?: it.id).lowercase() })
                if (providers.isEmpty()) item {
                    EmptyState("No AI providers on $pcName", "Add a provider key in Profile → AI providers, or in BambooKit Desktop.", Icons.Filled.Memory)
                }
                providers.forEach { p ->
                    val expanded = openProvider == p.id
                    item(key = "p:" + p.id) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { openProvider = if (expanded) null else p.id }.padding(horizontal = Space.screen),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name ?: p.id, color = if (p.configured) TextPrimary else TextMuted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    if (p.configured) "${plural(p.models.size, "model")} · Configured ✓" else "No key on $pcName · add one in Profile → AI providers",
                                    color = if (p.configured) StatusSuccess else TextMuted, fontSize = 11.sp,
                                )
                            }
                            Icon(
                                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                if (expanded) "Hide models" else "Show models", tint = TextMuted,
                            )
                        }
                    }
                    if (expanded) items(p.models, key = { "m:" + p.id + "/" + it.id }) { m ->
                        val ref = ModelRef(p.id, m.id)
                        PickRow(brandModel(m.name ?: m.id) ?: m.id, m.id.takeIf { m.name != null && it != m.name }, selected = selected == ref, enabled = p.configured, indent = true) { onPick(ref) }
                    }
                    item(key = "d:" + p.id) { HorizontalDivider(color = BambooBorder) }
                }
            }
        }
    }
}

@Composable
private fun PickRow(title: String, subtitle: String?, selected: Boolean, enabled: Boolean, indent: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, onClick = onClick).padding(start = if (indent) Space.screen + 12.dp else Space.screen - 12.dp, end = Space.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = BambooGreen, unselectedColor = TextSecondary))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) TextPrimary else TextMuted, fontSize = 14.sp)
            subtitle?.let { Text(it, color = TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}
