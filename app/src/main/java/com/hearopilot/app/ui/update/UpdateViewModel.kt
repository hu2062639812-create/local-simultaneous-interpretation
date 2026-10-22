package com.hearopilot.app.ui.update

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    enum class CheckStatus { UPDATE_AVAILABLE, UP_TO_DATE, NO_RELEASE, FAILED }

    data class UiState(
        val currentVersion: String = "",
        val isChecking: Boolean = false,
        val release: AvailableRelease? = null,
        val dialogVisible: Boolean = false,
        val status: CheckStatus? = null
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(UiState(currentVersion = readCurrentVersion()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var checkJob: Job? = null

    fun checkForUpdates(manual: Boolean) {
        if (checkJob?.isActive == true) return
        if (!manual) {
            val lastCheck = prefs.getLong(KEY_LAST_AUTO_CHECK, 0L)
            if (System.currentTimeMillis() - lastCheck < AUTO_CHECK_INTERVAL_MS) return
        }

        checkJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isChecking = true, status = if (manual) null else it.status)
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { GitHubReleaseChecker.fetchLatest() }
            }

            // Only a successful check consumes the auto-check budget: on network
            // failure (e.g. GitHub unreachable) the next app start retries
            // instead of waiting another 24 hours.
            if (!manual && result.isSuccess) {
                prefs.edit().putLong(KEY_LAST_AUTO_CHECK, System.currentTimeMillis()).apply()
            }

            _uiState.update { state ->
                result.fold(
                    onSuccess = { release ->
                        when {
                            release == null -> state.copy(
                                isChecking = false,
                                status = if (manual) CheckStatus.NO_RELEASE else state.status
                            )

                            AppVersion.isNewer(release.version, state.currentVersion) -> {
                                val skipped = prefs.getString(KEY_SKIPPED_VERSION, null)
                                state.copy(
                                    isChecking = false,
                                    release = release,
                                    dialogVisible = manual || skipped != release.version,
                                    status = CheckStatus.UPDATE_AVAILABLE
                                )
                            }

                            else -> state.copy(
                                isChecking = false,
                                status = if (manual) CheckStatus.UP_TO_DATE else state.status
                            )
                        }
                    },
                    onFailure = {
                        state.copy(
                            isChecking = false,
                            status = if (manual) CheckStatus.FAILED else state.status
                        )
                    }
                )
            }
        }
    }

    fun dismissDialog() {
        _uiState.value.release?.let { release ->
            prefs.edit().putString(KEY_SKIPPED_VERSION, release.version).apply()
        }
        _uiState.update { it.copy(dialogVisible = false) }
    }

    private fun readCurrentVersion(): String = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }

    private companion object {
        const val PREFS_NAME = "update_check"
        const val KEY_LAST_AUTO_CHECK = "last_auto_check_at"
        const val KEY_SKIPPED_VERSION = "skipped_version"
        const val AUTO_CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
