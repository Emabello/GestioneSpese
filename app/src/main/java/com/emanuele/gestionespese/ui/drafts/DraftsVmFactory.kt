package com.emanuele.gestionespese.ui.drafts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.emanuele.gestionespese.data.repo.SpesaDraftRepository

class DraftsVmFactory(
    private val repo: SpesaDraftRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DraftsViewModel(repo) as T
    }
}