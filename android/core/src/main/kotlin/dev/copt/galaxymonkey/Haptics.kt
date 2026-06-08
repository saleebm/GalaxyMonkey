package dev.copt.galaxymonkey

enum class ImpactStyle { LIGHT, MEDIUM, HEAVY }

enum class NotificationType { SUCCESS, WARNING, ERROR }

interface Haptics {
    fun impact(style: ImpactStyle)
    fun notification(type: NotificationType)
}

object NoOpHaptics : Haptics {
    override fun impact(style: ImpactStyle) {}
    override fun notification(type: NotificationType) {}
}
