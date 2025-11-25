package com.d4viddf.hyperbridge.models

enum class NotificationType(val label: String) {
    STANDARD("Messages & General"),
    PROGRESS("Downloads & Progress"),
    MEDIA("Music & Media"),
    NAVIGATION("Maps & GPS"),
    CALL("Calls"),
    TIMER("Timers & Alarms")
}