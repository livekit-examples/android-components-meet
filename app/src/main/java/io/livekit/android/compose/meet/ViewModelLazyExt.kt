package io.livekit.android.compose.meet

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

typealias CreateViewModel<VM> = () -> VM

inline fun <reified VM : ViewModel> ComponentActivity.viewModelByFactory(
    noinline create: CreateViewModel<VM>
): Lazy<VM> {
    return viewModels {
        createViewModelFactoryFactory(create)
    }
}

fun <VM> createViewModelFactoryFactory(
    create: CreateViewModel<VM>
): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return create() as? T
                ?: throw IllegalArgumentException("Unknown viewmodel class!")
        }
    }
}
