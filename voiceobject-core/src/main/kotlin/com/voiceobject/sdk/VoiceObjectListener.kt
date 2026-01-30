package com.voiceobject.sdk.core

interface VoiceObjectListener {
    fun onReady(isReady: Boolean)
    fun onResult(jsonResult: String)
    fun onError(error: String)
}