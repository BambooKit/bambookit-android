package com.bambookit.android.presentation.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.Account
import com.bambookit.android.data.BambooStore
import com.bambookit.android.data.NICKNAME_MAX
import com.bambookit.android.data.NicknameCheck
import com.bambookit.android.data.validateNickname
import com.bambookit.android.presentation.theme.BambooBorder
import com.bambookit.android.presentation.theme.BambooBorderStrong
import com.bambookit.android.presentation.theme.BambooSurfaceElevated
import com.bambookit.android.presentation.theme.LockScrim
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.TextMuted
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Nickname text field with live validation (1-40 characters). */
@Composable
private fun NicknameInput(value: String, onChange: (String) -> Unit, error: String?, enabled: Boolean, onDone: () -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(NICKNAME_MAX + 10)) }, enabled = enabled, singleLine = true,
        label = { Text("Nickname") },
        placeholder = { Text("How BambooKit greets you") },
        isError = error != null,
        supportingText = { Text(error ?: "${value.trim().length}/$NICKNAME_MAX · shown on your phone, PCs and the website", color = if (error != null) StatusFailed else TextMuted) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BambooBorderStrong, unfocusedBorderColor = BambooBorder),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Profile screen: edit the BambooKit nickname (PATCH /v1/me). */
@Composable
fun NicknameEditor(store: BambooStore, account: Account) {
    val profile by store.profile.collectAsState()
    val context = LocalContext.current
    val saved = account.nickname ?: ""
    var value by rememberSaveable(saved) { mutableStateOf(saved) }
    var serverError by remember { mutableStateOf<String?>(null) }
    val check = validateNickname(value)
    val changed = value.trim() != saved
    val error = serverError ?: (check as? NicknameCheck.Invalid)?.reason?.takeIf { changed && value.isNotEmpty() }
    fun save() {
        val ok = check as? NicknameCheck.Ok ?: return
        if (!changed || profile.savingName) return
        serverError = null
        store.saveNickname(ok.value) { err ->
            serverError = err
            if (err == null) Toast.makeText(context, "Nickname saved", Toast.LENGTH_SHORT).show()
        }
    }
    Column {
        NicknameInput(value, { value = it; serverError = null }, error, enabled = !profile.savingName, onDone = ::save)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
            if (account.nickname == null && !account.name.isNullOrBlank()) Text(
                "Without a nickname BambooKit uses \"${account.name}\" from your sign-in.", color = TextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f),
            ) else Spacer(Modifier.weight(1f))
            Button(onClick = ::save, enabled = changed && check is NicknameCheck.Ok && !profile.savingName) {
                if (profile.savingName) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Text("Save")
            }
        }
    }
}

/**
 * One-time, skippable "Set up your profile" sheet after sign-in when the account has no name:
 * a nickname and an optional photo. [onClose] is called once, whatever the outcome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupSheet(store: BambooStore, account: Account, onClose: () -> Unit) {
    val profile by store.profile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by rememberSaveable { mutableStateOf("") }
    var serverError by remember { mutableStateOf<String?>(null) }
    var preparing by remember { mutableStateOf(false) }
    val check = validateNickname(value)
    val error = serverError ?: (check as? NicknameCheck.Invalid)?.reason?.takeIf { value.isNotEmpty() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        preparing = true
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { prepareAvatar(context, uri) }
            preparing = false
            if (bytes == null) Toast.makeText(context, "That image can't be used. Pick a JPEG, PNG or WebP photo.", Toast.LENGTH_LONG).show()
            else store.uploadAvatar(bytes)
        }
    }
    fun save() {
        val ok = check as? NicknameCheck.Ok ?: return
        serverError = null
        store.saveNickname(ok.value) { err -> if (err == null) onClose() else serverError = err }
    }
    if (LocalAppLocked.current) return
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet, containerColor = BambooSurfaceElevated) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.screen).navigationBarsPadding().imePadding().padding(bottom = Space.l)) {
            Text("Set up your profile", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Choose how BambooKit greets you. You can change this later in Profile.",
                color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = Space.l),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Avatar(store, profile.account?.avatarUrl ?: account.avatarUrl, value.ifBlank { account.email }, 64.dp)
                    if (profile.photoBusy || preparing) Box(Modifier.size(64.dp).clip(CircleShape).background(LockScrim.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("Photo (optional)", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (account.cloudStorage) OutlinedButton(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !profile.photoBusy && !preparing,
                    ) {
                        Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (profile.account?.avatarStored == true) "Change photo" else "Add photo")
                    } else Text("Photos can't be added yet on this server.", color = TextMuted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(Space.l))
            NicknameInput(value, { value = it; serverError = null }, error, enabled = !profile.savingName, onDone = ::save)
            Spacer(Modifier.height(Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s), modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Skip") }
                Button(onClick = ::save, enabled = check is NicknameCheck.Ok && !profile.savingName, modifier = Modifier.weight(1f)) {
                    if (profile.savingName) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Text("Save")
                }
            }
        }
    }
}
