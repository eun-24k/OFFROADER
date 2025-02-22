package com.ing.offroader.ui.activity.radio

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RadioViewModel: ViewModel() {

    private var _radioUiState = MutableLiveData<RadioUiState>()

    val radioUiState : LiveData<RadioUiState> = _radioUiState
}