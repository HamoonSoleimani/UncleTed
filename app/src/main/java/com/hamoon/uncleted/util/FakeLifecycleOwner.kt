package com.hamoon.uncleted.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * Manually controlled LifecycleOwner for background services.
 * Crucial for CameraX: transitions to RESUMED to ensure camera HAL pipes
 * and output image buffers bind without hanging.
 */
class FakeLifecycleOwner : LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    init {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    /**
     * Moves the lifecycle to RESUMED, allowing CameraX to bind and capture frames.
     */
    fun start() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun pause() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun stop() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    /**
     * Moves the lifecycle to DESTROYED, ensuring CameraX resources are cleanly released.
     */
    fun destroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}