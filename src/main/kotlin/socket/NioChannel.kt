package socket

import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.locks.ReentrantLock

class NioChannel(private val socketChannel: SocketChannel) : Channel {
    override val remoteAddress: SocketAddress = socketChannel.remoteAddress
    private val readLock = ReentrantLock()
    private val writeLock = ReentrantLock()

    override fun read(byteBuffer: ByteBuffer): Channel.ReadState {
        readLock.lock()
        try {
            return when (socketChannel.read(byteBuffer)) {
                0 -> Channel.ReadState.FINISHED
                -1 -> Channel.ReadState.DISCONNECTED
                else -> Channel.ReadState.REMAINING
            }
        } finally {
            readLock.unlock()
        }
    }

    override fun write(byteBuffer: ByteBuffer) {
        writeLock.lock()
        try {
            socketChannel.write(byteBuffer)
        } finally {
            writeLock.unlock()
        }
    }

    override fun close() {
        socketChannel.close()
    }
}
