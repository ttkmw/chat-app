import java.net.InetSocketAddress

data class ChatMessage(
    val from: InetSocketAddress,
    val to: InetSocketAddress,
    val message: String,
) {
    companion object {
        fun of(
            from: InetSocketAddress,
            to: InetSocketAddress,
            message: String,
        ): ChatMessage {
            return ChatMessage(
                from = from,
                to = to,
                message = message,
            )
        }
    }
}
