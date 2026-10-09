package com.reality.android.ui.navigation

import androidx.lifecycle.ViewModel
import com.reality.android.core.network.MutationGate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor(gate: MutationGate) : ViewModel() {
    val writing = gate.busy
}
