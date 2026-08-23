package com.illusion.app.ui.smbsource

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.illusion.app.data.local.entity.SmbSourceEntity
import com.illusion.app.data.repository.SmbSourceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TestConnectionState {
    data object Idle : TestConnectionState
    data object Testing : TestConnectionState
    data object Success : TestConnectionState
    data class Failure(val message: String) : TestConnectionState
}

data class SmbSourceFormState(
    val displayName: String = "",
    val host: String = "",
    val share: String = "",
    val rootPath: String = "",
    val domain: String = "",
    val username: String = "",
    val password: String = "",
    val testState: TestConnectionState = TestConnectionState.Idle,
    val isSaving: Boolean = false
) {
    // Username is optional - a blank username means guest/anonymous SMB access.
    val canSave: Boolean
        get() = host.isNotBlank() && share.isNotBlank() && !isSaving
}

/**
 * Shared by both "add a source" and "edit a source": when [existingSource] is non-null the form
 * starts pre-filled from it (password left blank - a blank password on save means "keep the
 * existing one", handled by [SmbSourceRepository.updateSource]) and saving updates that row
 * in place instead of inserting a new one.
 */
class SmbSourceFormViewModel(
    private val smbSourceRepository: SmbSourceRepository,
    private val existingSource: SmbSourceEntity?
) : ViewModel() {
    private val _state = MutableStateFlow(
        existingSource?.let {
            SmbSourceFormState(
                displayName = it.displayName,
                host = it.host,
                share = it.share,
                rootPath = it.rootPath,
                domain = it.domain,
                username = it.username
            )
        } ?: SmbSourceFormState()
    )
    val state: StateFlow<SmbSourceFormState> = _state.asStateFlow()

    // Autofill convenience for repeat testing against the same NAS - not meant as validated
    // history, just "what did I already type into a source before". Seeded with the dev's own
    // NAS address/share so the suggestion is there from the very first source, not only after
    // one's already been saved.
    val hostSuggestions: StateFlow<List<String>> = smbSourceRepository.observeSources()
        .map { sources -> (listOf(SEED_HOST) + sources.map { it.host }).distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(SEED_HOST))
    val shareSuggestions: StateFlow<List<String>> = smbSourceRepository.observeSources()
        .map { sources -> (listOf(SEED_SHARE) + sources.map { it.share }).distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(SEED_SHARE))

    fun updateDisplayName(value: String) = _state.update { it.copy(displayName = value) }
    fun updateHost(value: String) = _state.update { it.copy(host = value) }
    fun updateShare(value: String) = _state.update { it.copy(share = value) }
    fun updateRootPath(value: String) = _state.update { it.copy(rootPath = value) }
    fun updateDomain(value: String) = _state.update { it.copy(domain = value) }
    fun updateUsername(value: String) = _state.update { it.copy(username = value) }
    fun updatePassword(value: String) = _state.update { it.copy(password = value) }

    fun testConnection() {
        val current = _state.value
        Log.d(TAG, "testConnection: start host=${current.host} share=${current.share}")
        _state.update { it.copy(testState = TestConnectionState.Testing) }
        viewModelScope.launch {
            try {
                val result = smbSourceRepository.testConnection(current.toTransientEntity(), current.password)
                Log.d(TAG, "testConnection: result=$result")
                _state.update {
                    it.copy(
                        testState = result.fold(
                            onSuccess = { TestConnectionState.Success },
                            onFailure = { e -> TestConnectionState.Failure(e.message ?: "Не удалось подключиться") }
                        )
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "testConnection: uncaught", e)
                _state.update { it.copy(testState = TestConnectionState.Failure(e.message ?: e::class.java.simpleName)) }
            }
        }
    }

    fun reportLocalNetworkPermissionDenied() {
        _state.update {
            it.copy(testState = TestConnectionState.Failure("Нет разрешения на доступ к локальной сети - откройте настройки приложения и разрешите его вручную"))
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val current = _state.value
        if (!current.canSave) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val id = if (existingSource != null) {
                smbSourceRepository.updateSource(
                    current.toTransientEntity().copy(id = existingSource.id, enabled = existingSource.enabled),
                    current.password.ifBlank { null }
                )
                existingSource.id
            } else {
                smbSourceRepository.addSource(current.toTransientEntity(), current.password)
            }
            _state.update { it.copy(isSaving = false) }
            onSaved(id)
        }
    }

    private fun SmbSourceFormState.toTransientEntity() = SmbSourceEntity(
        displayName = displayName.ifBlank { host },
        host = host,
        share = share,
        rootPath = rootPath,
        domain = domain,
        username = username
    )

    companion object {
        private const val TAG = "SmbSourceForm"
        private const val SEED_HOST = "192.168.2.1"
        private const val SEED_SHARE = "Movies"

        fun factory(smbSourceRepository: SmbSourceRepository, existingSource: SmbSourceEntity? = null) = viewModelFactory {
            initializer { SmbSourceFormViewModel(smbSourceRepository, existingSource) }
        }
    }
}
