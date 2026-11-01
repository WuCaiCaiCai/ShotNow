package com.shotnow.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppState {
    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected = _accessibilityConnected.asStateFlow()

    fun setRunning(value: Boolean) {
        _running.value = value
    }

    fun setAccessibilityConnected(value: Boolean) {
        _accessibilityConnected.value = value
    }
}
