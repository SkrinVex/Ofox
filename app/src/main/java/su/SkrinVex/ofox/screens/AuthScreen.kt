package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.utils.ValidationConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(repository: Repository, onAuthSuccess: () -> Unit) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showVerificationDialog by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(showError) {
        if (showError) {
            delay(4000)
            showError = false
        }
    }

    LaunchedEffect(isLogin) {
        showError = false
        errorMessage = ""
    }

    if (showVerificationDialog) {
        AlertDialog(
            onDismissRequest = { },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { 
                Text(
                    "Подтверждение email",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Код отправлен на:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                email,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "• Убедитесь, что email введён верно",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "• Проверьте папку «Спам»",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = verificationCode,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) verificationCode = it },
                        label = { Text("Код из письма") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    if (showError) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (verificationCode.length == 6) {
                            scope.launch {
                                isLoading = true
                                showError = false
                                val result = repository.verifyCode(email.trim(), verificationCode)
                                result.fold(
                                    onSuccess = {
                                        showVerificationDialog = false
                                        isLoading = false
                                        onAuthSuccess()
                                    },
                                    onFailure = {
                                        errorMessage = it.message ?: "Ошибка проверки кода"
                                        showError = true
                                        isLoading = false
                                    }
                                )
                            }
                        }
                    },
                    enabled = verificationCode.length == 6 && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Подтвердить")
                    }
                }
            },
            dismissButton = null
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(24.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "OFOX",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isLogin) "Добро пожаловать!" else "Создать аккаунт",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.medium
                )
                .padding(4.dp)
        ) {
            Button(
                onClick = { isLogin = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLogin) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isLogin) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Вход")
            }
            
            Button(
                onClick = { isLogin = false },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!isLogin) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (!isLogin) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Регистрация")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (!isLogin) {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= ValidationConstants.MAX_NAME_LENGTH) name = it },
                label = { Text("Имя (макс. ${ValidationConstants.MAX_NAME_LENGTH})") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                supportingText = { Text("${name.length}/${ValidationConstants.MAX_NAME_LENGTH}") }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        OutlinedTextField(
            value = email,
            onValueChange = { if (it.length <= ValidationConstants.MAX_EMAIL_LENGTH) email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            supportingText = { Text("${email.length}/${ValidationConstants.MAX_EMAIL_LENGTH}") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { if (it.length <= ValidationConstants.MAX_PASSWORD_LENGTH) password = it },
            label = { Text("Пароль (мин. ${ValidationConstants.MIN_PASSWORD_LENGTH})") },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            supportingText = { Text("${password.length}/${ValidationConstants.MAX_PASSWORD_LENGTH}") }
        )
        
        if (!isLogin) {
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { if (it.length <= ValidationConstants.MAX_PASSWORD_LENGTH) confirmPassword = it },
                label = { Text("Подтвердить пароль") },
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
        }
        
        if (showError) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                if (isLoading) return@Button
                scope.launch {
                    isLoading = true
                    showError = false
                    
                    when {
                        email.trim().isEmpty() -> {
                            errorMessage = "Введите email"
                            showError = true
                        }
                        password.isEmpty() -> {
                            errorMessage = "Введите пароль"
                            showError = true
                        }
                        !isLogin && name.trim().isEmpty() -> {
                            errorMessage = "Введите имя"
                            showError = true
                        }
                        !isLogin && password.length < 6 -> {
                            errorMessage = "Пароль должен быть минимум 6 символов"
                            showError = true
                        }
                        !isLogin && password != confirmPassword -> {
                            errorMessage = "Пароли не совпадают"
                            showError = true
                        }
                        else -> {
                            if (isLogin) {
                                val result = repository.login(email.trim(), password)
                                result.fold(
                                    onSuccess = { 
                                        isLoading = false
                                        onAuthSuccess() 
                                    },
                                    onFailure = { 
                                        errorMessage = it.message ?: "Ошибка подключения"
                                        showError = true
                                    }
                                )
                            } else {
                                val result = repository.register(email.trim(), password, name.trim())
                                result.fold(
                                    onSuccess = {
                                        isLoading = false
                                        verificationCode = ""
                                        showVerificationDialog = true
                                    },
                                    onFailure = { 
                                        errorMessage = it.message ?: "Ошибка подключения"
                                        showError = true
                                    }
                                )
                            }
                        }
                    }
                    isLoading = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = MaterialTheme.shapes.medium,
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = if (isLogin) "Войти" else "Зарегистрироваться",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
