package com.appcapsule.vault.ui

import android.os.Bundle
import android.os.UserHandle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.appcapsule.vault.cross.CapsuleManager
import com.appcapsule.vault.cross.VaultBridgeClient
import com.appcapsule.vault.cross.VaultBridgeResult
import com.appcapsule.vault.cross.VaultState
import com.appcapsule.vault.data.PersonalAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var capsuleManager: CapsuleManager

    private val provisioningLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Provisioning also completes via onProfileProvisioningComplete /
        // AdminPolicyComplianceActivity; recreate() just refreshes our UI state.
        recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        capsuleManager = CapsuleManager(applicationContext)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CapsuleScreen(
                        capsuleManager = capsuleManager,
                        onCreateVault = { provisioningLauncher.launch(capsuleManager.buildCreateVaultIntent()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CapsuleScreen(capsuleManager: CapsuleManager, onCreateVault: () -> Unit) {
    val state = remember { capsuleManager.currentState() }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (state) {
                is VaultState.NotCreated -> NotCreatedContent(onCreateVault)
                is VaultState.ReadyFromPersonalSide -> PersonalSideContent(vaultUser = state.vaultUser)
                is VaultState.RunningInsideVault -> InsideVaultContent()
            }
        }
    }
}

@Composable
private fun NotCreatedContent(onCreateVault: () -> Unit) {
    Text(
        text = "Изолированное пространство ещё не создано",
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Будет создан отдельный рабочий профиль Android (тот же механизм, " +
            "что использует Samsung Knox и корпоративный BYOD). Приложения внутри " +
            "получат собственный User ID, собственное хранилище и не будут видеть " +
            "данные личного профиля.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = onCreateVault) {
        Text("Создать изолированное пространство")
    }
}

@Composable
private fun PersonalSideContent(vaultUser: UserHandle) {
    val context = LocalContext.current
    val repository = remember { PersonalAppsRepository(context) }
    val bridgeClient = remember { VaultBridgeClient(context) }
    val scope = rememberCoroutineScope()

    val apps by remember { mutableStateOf(repository.listLaunchableApps()) }
    var busyPackage by remember { mutableStateOf<String?>(null) }
    var lastMessage by remember { mutableStateOf<String?>(null) }

    Text(text = "Изолированное пространство активно", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Выберите приложение, чтобы добавить его копию в изолированное " +
            "пространство. После добавления откройте его через значок с бейджем " +
            "в списке приложений телефона (системный лаунчер сам покажет отдельный ярлык) — " +
            "оттуда запуск идёт уже полностью изолированно от личного профиля.",
        style = MaterialTheme.typography.bodySmall,
    )
    lastMessage?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = it, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(modifier = Modifier.height(12.dp))

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(apps, key = { it.packageName }) { app ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = app.icon.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = app.label)
                }
                if (busyPackage == app.packageName) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Button(onClick = {
                        busyPackage = app.packageName
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                bridgeClient.installIntoVault(vaultUser, app.packageName)
                            }
                            lastMessage = when (result) {
                                is VaultBridgeResult.Success -> "${app.label}: добавлено в изолированное пространство"
                                is VaultBridgeResult.Failure -> "${app.label}: не удалось (${result.reason})"
                            }
                            busyPackage = null
                        }
                    }) {
                        Text("Изолировать")
                    }
                }
            }
        }
    }
}

@Composable
private fun InsideVaultContent() {
    Text(
        text = "Это изолированное пространство",
        style = MaterialTheme.typography.titleLarge,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Вы открыли AppCapsule изнутри рабочего профиля. Управление " +
            "пространством (добавление и удаление приложений) выполняется из " +
            "личного профиля.",
        style = MaterialTheme.typography.bodyMedium,
    )
}
