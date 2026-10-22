package com.example.hackchaton_2026_crm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hackchaton_2026_crm.AppContainer
import com.example.hackchaton_2026_crm.QuickLeadApp

/** Builds a ViewModel factory that has access to the app's [AppContainer]. */
inline fun <reified VM : ViewModel> containerFactory(
    crossinline create: CreationExtras.(AppContainer) -> VM,
) = viewModelFactory {
    initializer {
        val app = this[APPLICATION_KEY] as QuickLeadApp
        create(app.container)
    }
}
