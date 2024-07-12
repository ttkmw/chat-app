package event

import java.util.UUID

interface Event {
    val uuid: UUID
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class OnEvent()

class ParticipantJoined(override val uuid: UUID) : Event

class ParticipantDisconnectTriggered(override val uuid: UUID) : Event

class MessageSentToParticipants(override val uuid: UUID) : Event