import event.Event
import event.EventConsumer
import event.OnEvent
import event.ParticipantDisconnectionStarted
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import kotlin.reflect.KClass

class Participant(
    private val socketChannel: SocketChannel,
) : EventConsumer() {
    private val byteBuffer = ByteBuffer.allocate(1024)
    var disconnected = false

    @OnEvent
    fun onDisconnectionStarted(event: ParticipantDisconnectionStarted) {
    }

    fun read(from: InetSocketAddress) {
        val message = StringBuilder()
        while (socketChannel.read(byteBuffer).also { disconnected = it == -1 } > 0) {
            message.append(UTF8Codec.DECODER.decode(byteBuffer).toString())
            byteBuffer.clear()
        }

        if (message.isNotEmpty()) {
            ChatMessage.of(
                from = from,
                to = socketChannel.remoteAddress as InetSocketAddress,
                message = message.toString(),
            )
        }
    }

    companion object {
        fun join(socketChannel: SocketChannel): Participant {
            return Participant(
                socketChannel = socketChannel,
            )
        }
    }

    override fun consume(event: Event) {
        (onEvents[event::class] ?: throw IllegalStateException("no onEventMethod")).call(this, event)
    }

    override fun getConsumingEvents(): Set<KClass<out Event>> {
        return this.onEvents.keys
    }
}
