import java.util.UUID

sealed class ChatMessage(
    open val uuid: UUID,
    open val from: UUID,
    open val message: String,
) {
    data class BroadcastChatMessage(
        override val uuid: UUID,
        override val from: UUID,
        override val message: String,
    ) : ChatMessage(
            uuid = uuid,
            from = from,
            message = message,
        )

    companion object {
        fun broadcast(
            from: UUID,
            message: String,
        ): BroadcastChatMessage {
            return BroadcastChatMessage(
                uuid = UUID.randomUUID(),
                from = from,
                message = message,
            )
        }
    }
}
