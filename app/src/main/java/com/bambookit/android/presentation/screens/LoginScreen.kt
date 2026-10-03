package com.bambookit.android.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bambookit.android.data.AuthRepository
import com.bambookit.android.data.Config
import com.bambookit.android.presentation.theme.BambooObsidian
import com.bambookit.android.presentation.theme.StatusFailed
import com.bambookit.android.presentation.theme.StatusFailedTint
import com.bambookit.android.presentation.theme.StatusSuccess
import com.bambookit.android.presentation.theme.StatusSuccessTint
import com.bambookit.android.presentation.theme.TextPrimary
import com.bambookit.android.presentation.theme.TextSecondary
import kotlinx.coroutines.launch

private enum class Mode { SignIn, SignUp, Reset }

@Composable
fun LoginScreen(auth: AuthRepository, onGoogle: () -> Unit, googleAvailable: Boolean) {
    var mode by remember { mutableStateOf(Mode.SignIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val canSubmit = !busy && email.isNotBlank() && (mode == Mode.Reset || password.isNotBlank())

    fun submit() {
        if (!canSubmit) return
        busy = true
        error = null
        notice = null
        scope.launch {
            runCatching {
                when (mode) {
                    Mode.SignIn -> auth.signIn(email, password)
                    Mode.SignUp -> if (auth.signUp(email, password) == null) {
                        mode = Mode.SignIn
                        notice = "Check $email to confirm your account, then sign in."
                    }
                    Mode.Reset -> {
                        auth.resetPassword(email)
                        notice = "If an account exists for $email, a reset link is on its way."
                    }
                }
            }.onFailure { error = it.message }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize().background(BambooObsidian).systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(com.bambookit.android.R.drawable.bambookit_mark), contentDescription = "BambooKit",
                modifier = Modifier.size(76.dp).clip(RoundedCornerShape(20.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Text("BambooKit", color = TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("Your PC's AI coding agent, in your pocket.", color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))

            if (!Config.authConfigured) {
                Notice("Sign-in is not configured in this build (missing Supabase URL or anon key).", ok = false)
                return@Column
            }

            BkCard(padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
                Text(
                    when (mode) { Mode.SignIn -> "Sign in"; Mode.SignUp -> "Create your account"; Mode.Reset -> "Reset your password" },
                    color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when (mode) {
                        Mode.SignIn -> "Use the same account as BambooKit Desktop."
                        Mode.SignUp -> "One account for Desktop, phone and web."
                        Mode.Reset -> "We'll email you a link to choose a new password."
                    },
                    color = TextSecondary, fontSize = 13.sp,
                )
                Spacer(Modifier.height(16.dp))

                if (mode != Mode.Reset && googleAvailable) {
                    OutlinedButton(onClick = onGoogle, enabled = !busy, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Continue with Google") }
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (mode == Mode.Reset) ImeAction.Done else ImeAction.Next),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                )
                if (mode != Mode.Reset) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showPassword) "Hide password" else "Show password", tint = TextSecondary)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
                    )
                }
                error?.let { Spacer(Modifier.height(12.dp)); Notice(it, ok = false) }
                notice?.let { Spacer(Modifier.height(12.dp)); Notice(it, ok = true) }
                Spacer(Modifier.height(16.dp))
                Button(onClick = ::submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Please wait…")
                    } else Text(when (mode) { Mode.SignIn -> "Sign in"; Mode.SignUp -> "Create account"; Mode.Reset -> "Send reset link" })
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (mode == Mode.SignIn) {
                    TextButton(onClick = { mode = Mode.SignUp; error = null; notice = null }) { Text("Create account") }
                    TextButton(onClick = { mode = Mode.Reset; error = null; notice = null }) { Text("Forgot password?", color = TextSecondary) }
                } else {
                    TextButton(onClick = { mode = Mode.SignIn; error = null }) { Text("Back to sign in") }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, ok: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (ok) StatusSuccessTint else StatusFailedTint).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null, tint = if (ok) StatusSuccess else StatusFailed, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text(text, color = if (ok) StatusSuccess else StatusFailed, fontSize = 13.sp)
    }
}
