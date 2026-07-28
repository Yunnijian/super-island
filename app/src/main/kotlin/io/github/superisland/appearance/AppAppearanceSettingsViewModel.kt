/*
 * State ownership follows KernelSU Manager SettingsViewModel at
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

/** Owns immediate control state while the root theme independently observes persisted settings. */
class AppAppearanceSettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val store = AppAppearanceStore(application)
    private val _appearance = MutableStateFlow(store.load())
    val appearance: StateFlow<AppAppearanceSettings> = _appearance.asStateFlow()

    private var writingFromUi = false
    private val unregister =
        store.observe { updated ->
            if (!writingFromUi) _appearance.value = updated
        }

    fun update(transform: (AppAppearanceSettings) -> AppAppearanceSettings) {
        val previous = _appearance.value
        val updated = transform(previous).normalized()
        if (updated == previous) return

        // Keep the pressed control and generated KernelSU page state in the same frame. The root
        // theme observes the preference write synchronously, so publishing this state afterward
        // would briefly recompose the old selection and make switches feel stuck or delayed.
        _appearance.value = updated
        writingFromUi = true
        try {
            store.save(previous, updated)
        } catch (error: Throwable) {
            _appearance.value = previous
            throw error
        } finally {
            writingFromUi = false
        }
    }

    override fun onCleared() {
        unregister()
        super.onCleared()
    }
}
