/*
 * State ownership adapted from KernelSU Manager's SettingsViewModel, commit
 * b6e50f9a4f5fa7a14b68e7945d172ddbeae36415.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.superisland.appearance

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import io.github.superisland.model.AppAppearanceSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Root-theme state owner, matching KernelSU's MainActivityViewModel responsibility. UI controls
 * write through [AppAppearanceSettingsViewModel]; this owner only observes persisted changes.
 */
class AppAppearanceViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = AppAppearanceStore(application)
    private val _appearance = MutableStateFlow(store.load())
    val appearance: StateFlow<AppAppearanceSettings> = _appearance.asStateFlow()

    private val unregister =
        store.observe { updated ->
            _appearance.value = updated
        }

    override fun onCleared() {
        unregister()
        super.onCleared()
    }
}
