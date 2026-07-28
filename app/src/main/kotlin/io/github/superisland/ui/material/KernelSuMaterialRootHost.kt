/* Adapted from KernelSU Manager MainActivity at b6e50f9a4f5fa7a14b68e7945d172ddbeae36415. */
package io.github.superisland.ui.material

import android.annotation.SuppressLint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable

/** Material root surface matching KernelSU MainActivity's NavDisplay host. */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun KernelSuMaterialRootHost(content: @Composable () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainer) { content() }
}
