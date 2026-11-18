package space.nbtca.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import space.nbtca.app.core.auth.AuthManager
import space.nbtca.app.core.network.ApiClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTicketScreen(
    apiClient: ApiClient,
    authManager: AuthManager,
    onBack: () -> Unit,
    onCreated: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val auth by authManager.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var problem by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var qq by remember { mutableStateOf("") }
    val email = auth.profile?.email.orEmpty()

    var isSubmitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val subtext = MaterialTheme.colorScheme.onSurfaceVariant

    BackHandler { onBack() }

    fun submit() {
        if (problem.trim().isEmpty()) {
            errorText = "问题描述不能为空"
            return
        }
        if (phone.trim().length < 11) {
            errorText = "请输入 11 位有效手机号码"
            return
        }
        errorText = null
        isSubmitting = true
        scope.launch {
            apiClient.createTicket(problem.trim(), model.trim(), phone.trim(), qq.trim()).fold(
                onSuccess = {
                    isSubmitting = false
                    snackbar.showSnackbar("工单创建成功")
                    delay(300)
                    onCreated()
                },
                onFailure = {
                    isSubmitting = false
                    errorText = it.message
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("预约维修") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", modifier = Modifier.size(20.dp))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "提交后，技术员会联系你确认维修时间和地点。",
                style = MaterialTheme.typography.bodyMedium,
                color = subtext
            )

            RepairField(
                label = "问题描述",
                required = true,
                value = problem,
                onValueChange = { problem = it },
                placeholder = "告诉我们你遇到的问题",
                minLines = 4
            )

            RepairField(
                label = "型号",
                required = false,
                value = model,
                onValueChange = { model = it },
                placeholder = "你的设备型号"
            )

            Text(
                text = "联系方式",
                style = MaterialTheme.typography.titleMedium
            )

            RepairField(
                label = "邮箱",
                required = true,
                value = email,
                onValueChange = {},
                placeholder = "example@nbtca.space",
                supportingText = "接收维修进度通知",
                readOnly = true,
                keyboardType = KeyboardType.Email
            )
            RepairField(
                label = "电话号码",
                required = true,
                value = phone,
                onValueChange = { input -> if (input.length <= 11) phone = input.filter { it.isDigit() } },
                placeholder = "11 位手机号",
                keyboardType = KeyboardType.Phone
            )

            RepairField(
                label = "QQ",
                required = false,
                value = qq,
                onValueChange = { qq = it.filter { ch -> ch.isDigit() } },
                placeholder = "你的QQ号",
                keyboardType = KeyboardType.Number
            )

            errorText?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = { submit() },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("提交预约")
                }
            }

        }
    }
}

@Composable
private fun RepairField(
    label: String,
    required: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    supportingText: String? = null,
    readOnly: Boolean = false,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(if (required) "$label（必填）" else label) },
        placeholder = { Text(placeholder) },
        supportingText = supportingText?.let { { Text(it) } },
        readOnly = readOnly,
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (minLines == 1) ImeAction.Next else ImeAction.Default
        )
    )
}
