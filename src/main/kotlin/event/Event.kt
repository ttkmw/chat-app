package event

import java.util.UUID

interface Event {
    val uuid: UUID
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnEvent()

class UserJoined(override val uuid: UUID) : Event

class UserDisconnectionStarted(override val uuid: UUID) : Event

class MessageSentToUsers(override val uuid: UUID) : Event
