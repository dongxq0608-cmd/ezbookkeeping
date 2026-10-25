package com.ezbook.mobile.ui.auth

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.ezbook.mobile.Graph
import com.ezbook.mobile.data.model.AuthResponse
import com.ezbook.mobile.data.remote.ApiException
import com.ezbook.mobile.util.describeError
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onEditServer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loginName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var tempToken by remember { mutableStateOf("") }
    var show2FA by remember { mutableStateOf(false) }
    var passcode by remember { mutableStateOf("") }

    fun complete(auth: AuthResponse) {
        if (auth.need2FA) {
            tempToken = auth.token
            show2FA = true
            return
        }
        Graph.store.saveSession(auth.token, auth.user)
        Graph.repo.invalidate()
        onLoggedIn()
    }

    fun doLogin() {
        if (loginName.isBlank() || password.isBlank()) {
            error = "请输入用户名和密码"
            return
        }
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                complete(Graph.api.login(loginName.trim(), password))
            } catch (e: ApiException) {
                error = describeError(e)
            } catch (e: Exception) {
                error = describeError(e)
            } finally {
                loading = false
            }
        }
    }

    fun doTwoFactor() {
        if (passcode.length != 6) {
            error = "请输入 6 位动态验证码"
            return
        }
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                val auth = Graph.api.authorizeTwoFactor(tempToken, passcode.trim())
                show2FA = false
                passcode = ""
                complete(auth)
            } catch (e: Exception) {
                error = describeError(e)
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(80.dp))

        Box(
            modifier = Modifier
                .size(60.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "EzBook",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(6.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.clickable { onEditServer() }
        ) {
            Text(
                text = Graph.store.serverUrl ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "更换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = loginName,
            onValueChange = {
                loginName = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("用户名或邮箱") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            )
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { doLogin() })
        )

        if (error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = { doLogin() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = !loading
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("登录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(20.dp))

        TextButton(onClick = {
            val url = Graph.store.serverUrl
            if (!url.isNullOrBlank()) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            }
        }) {
            Text("在浏览器中打开（注册 / 找回密码）")
        }

        Spacer(Modifier.height(40.dp))
    }

    if (show2FA) {
        AlertDialog(
            onDismissRequest = {
                show2FA = false
                passcode = ""
            },
            title = { Text("两步验证") },
            text = {
                Column {
                    Text(
                        text = "请输入身份验证器中的 6 位动态验证码",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = { input ->
                            passcode = input.filter { it.isDigit() }.take(6)
                            error = null
                        },
                        singleLine = true,
                        label = { Text("动态验证码") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { doTwoFactor() })
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { doTwoFactor() }, enabled = !loading) {
                    Text(if (loading) "验证中…" else "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    show2FA = false
                    passcode = ""
                }) { Text("取消") }
            }
        )
    }
}
