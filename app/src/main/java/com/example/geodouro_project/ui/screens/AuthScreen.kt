package com.example.geodouro_project.ui.screens

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geodouro_project.R
import com.example.geodouro_project.data.repository.AuthRepository
import com.example.geodouro_project.di.AppContainer
import com.example.geodouro_project.ui.theme.GeodouroBrandGreen
import com.example.geodouro_project.ui.theme.GeodouroGreen
import com.example.geodouro_project.ui.theme.GeodouroLightBg
import com.example.geodouro_project.ui.theme.GeodouroTextPrimary
import com.example.geodouro_project.ui.theme.GeodouroTextSecondary
import com.example.geodouro_project.ui.theme.GeodouroWhite
import com.example.geodouro_project.ui.theme.geodouroLoadingIndicatorColor
import com.example.geodouro_project.ui.theme.geodouroOutlinedBorderColor
import com.example.geodouro_project.ui.theme.geodouroOutlinedButtonColors
import com.example.geodouro_project.ui.theme.geodouroOutlinedTextFieldColors
import com.example.geodouro_project.ui.theme.geodouroPrimaryButtonColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val identifier: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
) {
    val identifierError: String?
        get() = if (identifier.isBlank()) "Introduz o username ou email." else null

    val passwordError: String?
        get() = when {
            password.isBlank() -> "Introduz a password."
            password.length < 6 -> "A password deve ter pelo menos 6 caracteres."
            else -> null
        }

    val canSubmit: Boolean
        get() = !isSubmitting && identifierError == null && passwordError == null
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onIdentifierChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            identifier = value,
            errorMessage = null
        )
    }

    fun onPasswordChanged(value: String) {
        _uiState.value = _uiState.value.copy(
            password = value,
            errorMessage = null
        )
    }

    fun submitLogin() {
        val currentState = _uiState.value
        if (!currentState.canSubmit) {
            _uiState.value = currentState.copy(
                errorMessage = "Preenche os campos obrigatorios para continuar."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
            val result = authRepository.login(
                identifier = _uiState.value.identifier,
                password = _uiState.value.password
            )
            _uiState.value = _uiState.value.copy(
                isSubmitting = false,
                errorMessage = result.exceptionOrNull()?.message
            )
        }
    }

    fun continueAsGuest() {
        if (_uiState.value.isSubmitting) {
            return
        }

        viewModelScope.launch {
            authRepository.continueAsGuest()
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(
                        AppContainer.provideAuthRepository(context)
                    ) as T
                }
            }
    }
}

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val viewModel: AuthViewModel = viewModel(
        factory = AuthViewModel.factory(context.applicationContext)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GeodouroWhite)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 44.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GeodouroBrandGreen,
                            GeodouroGreen.copy(alpha = 0.85f)
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 24.dp, vertical = 26.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "Sessao GeoDouro",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_s_fundo),
                        contentDescription = "Geodouro",
                        modifier = Modifier.height(64.dp)
                    )
                    Text(
                        text = "Entra para continuar a explorar a flora do Douro.",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 30.sp
                    )
                    Text(
                        text = "Podes autenticar-te ou seguir como convidado. A sessao fica guardada neste dispositivo.",
                        color = Color.White.copy(alpha = 0.88f),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = GeodouroWhite),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Iniciar sessao",
                    style = MaterialTheme.typography.titleLarge,
                    color = GeodouroTextPrimary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Usa o username ou email registado na base de dados.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GeodouroTextSecondary
                )

                OutlinedTextField(
                    value = uiState.identifier,
                    onValueChange = viewModel::onIdentifierChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    isError = uiState.identifier.isNotBlank() && uiState.identifierError != null,
                    colors = geodouroOutlinedTextFieldColors(),
                    leadingIcon = {
                        Icon(Icons.Default.PersonOutline, contentDescription = null)
                    },
                    label = {
                        Text("Username ou email")
                    },
                    supportingText = {
                        val showError = uiState.identifier.isNotBlank() && uiState.identifierError != null
                        if (showError) {
                            Text(uiState.identifierError.orEmpty())
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                )

                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    isError = uiState.password.isNotBlank() && uiState.passwordError != null,
                    colors = geodouroOutlinedTextFieldColors(),
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (passwordVisible) {
                                    "Esconder password"
                                } else {
                                    "Mostrar password"
                                }
                            )
                        }
                    },
                    label = {
                        Text("Password")
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    supportingText = {
                        val showError = uiState.password.isNotBlank() && uiState.passwordError != null
                        if (showError) {
                            Text(uiState.passwordError.orEmpty())
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )

                uiState.errorMessage?.let { message ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Button(
                    onClick = viewModel::submitLogin,
                    enabled = uiState.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = geodouroPrimaryButtonColors()
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                            color = geodouroLoadingIndicatorColor()
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    Text(
                        text = if (uiState.isSubmitting) "A validar..." else "Fazer login",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                OutlinedButton(
                    onClick = viewModel::continueAsGuest,
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = geodouroOutlinedButtonColors(),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = geodouroOutlinedBorderColor(enabled = !uiState.isSubmitting)
                    )
                ) {
                    Text("Entrar como convidado")
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = GeodouroLightBg
        ) {
            Text(
                text = "O login autentica agora diretamente contra os utilizadores autenticados da tua base de dados.",
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = GeodouroTextSecondary,
                lineHeight = 18.sp
            )
        }
    }
}
