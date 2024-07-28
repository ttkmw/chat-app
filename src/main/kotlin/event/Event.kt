package event

import java.util.UUID

interface Event

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnEvent()

data class UserJoined(
    val uuid: UUID,
    val otherUsers: List<UUID>,
) : Event

data class UserDisconnected(val uuid: UUID) : Event

data class MessageBroadcast(
    val uuid: UUID,
    val from: UUID,
    val message: String,
) : Event

class EventBrokerShutdown : Event
