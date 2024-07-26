package socket

import java.net.SocketAddress
import java.nio.ByteBuffer

interface Channel {
    fun read(byteBuffer: ByteBuffer): ReadState

    fun write(byteBuffer: ByteBuffer)

    fun close()

    val remoteAddress: SocketAddress

    enum class ReadState {
        SUCCESS,
        FINISHED,
        DISCONNECTED,
    }
}
