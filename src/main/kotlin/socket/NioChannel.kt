package socket

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class NioChannel(private val socketChannel: SocketChannel) : Channel {
    override val remoteAddress: SocketAddress = socketChannel.remoteAddress

    override fun read(byteBuffer: ByteBuffer): Channel.ReadState {
        return when (socketChannel.read(byteBuffer)) {
            0 -> Channel.ReadState.FINISHED
            -1 -> Channel.ReadState.DISCONNECTED
            else -> Channel.ReadState.SUCCESS
        }
    }

    override fun write(byteBuffer: ByteBuffer) {
        socketChannel.write(byteBuffer)
    }

    override fun close() {
        socketChannel.close()
    }
}
