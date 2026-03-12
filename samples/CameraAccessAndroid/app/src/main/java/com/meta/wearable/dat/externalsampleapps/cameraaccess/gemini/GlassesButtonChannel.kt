package com.meta.wearable.dat.externalsampleapps.cameraaccess.gemini

import kotlinx.coroutines.flow.MutableSharedFlow

object GlassesButtonChannel {
    enum class Event { SHORT_PRESS, LONG_PRESS }
    val events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
}
