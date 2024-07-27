package socket

import UTF8Codec
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class SocketChannelStudyTest {
    private val channels = ConcurrentHashMap<SocketAddress, NioChannel>()
    private val threadPool = Executors.newFixedThreadPool(10)

    @Test
    fun read() {
        val serverSocketChannel = ServerSocketChannel.open()
        serverSocketChannel.bind(InetSocketAddress(8082))
        serverSocketChannel.configureBlocking(false)
        val selector = Selector.open()
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT)

        Thread {
            while (true) {
                selector.select()
                val selectedKeys = selector.selectedKeys()
                val selectedKeyIterator = selectedKeys.iterator()
                while (selectedKeyIterator.hasNext()) {
                    val selectedKey = selectedKeyIterator.next()
                    selectedKeyIterator.remove()
                    if (selectedKey.isAcceptable) {
                        val socketChannel = (selectedKey.channel() as ServerSocketChannel).accept()
                        with(socketChannel) {
                            this.configureBlocking(false)
                            this.register(selector, SelectionKey.OP_READ)
                            channels[socketChannel.remoteAddress] = NioChannel(socketChannel)
                        }
                    } else if (selectedKey.isReadable) {
                        val channel = channels[(selectedKey.channel() as SocketChannel).remoteAddress]!!
                        val byteBuffer = ByteBuffer.allocate(1024)
                        channel.read(byteBuffer)
                        byteBuffer.flip()
                        val receivedString = UTF8Codec.DECODER.decode(byteBuffer).toString()

                        val writeBuffer1 = ByteBuffer.allocate(2)
                        val writeBuffer2 = ByteBuffer.allocate(3)
                        val charBuffer = CharBuffer.wrap(receivedString)

                        UTF8Codec.ENCODER.encode(charBuffer, writeBuffer1, false)
                        UTF8Codec.ENCODER.encode(charBuffer, writeBuffer2, false)
                        writeBuffer1.flip()
                        writeBuffer2.flip()
                        threadPool.execute {
                            channel.write(writeBuffer1)
                        }
                        threadPool.execute {
                            channel.write(writeBuffer2)
                        }
                    }
                }
            }
        }.start()

        val message1 = "abcdefghijklmnop"
        val charBuffer1 = CharBuffer.wrap(message1)
        val writeBuffer1 = ByteBuffer.allocate(1024)

        UTF8Codec.ENCODER.encode(charBuffer1, writeBuffer1, false)
        writeBuffer1.flip()
        val clientSocket1 = SocketChannel.open(InetSocketAddress(8082))
        clientSocket1.write(writeBuffer1)

        val message2 = "12345678"
        val charBuffer2 = CharBuffer.wrap(message2)
        val writeBuffer2 = ByteBuffer.allocate(1024)

        UTF8Codec.ENCODER.encode(charBuffer2, writeBuffer2, false)
        writeBuffer2.flip()

        clientSocket1.write(writeBuffer2)

        val readByteBuffer = ByteBuffer.allocate(1024)
        clientSocket1.read(readByteBuffer)
        readByteBuffer.flip()
        val read = UTF8Codec.DECODER.decode(readByteBuffer).toString()
        println(read)

        readByteBuffer.clear()
        clientSocket1.read(readByteBuffer)
        readByteBuffer.flip()
        val read2 = UTF8Codec.DECODER.decode(readByteBuffer).toString()
        println(read2)

//        val serverSocket = serverSocketChannel.accept()
//        val readBuffer1 = ByteBuffer.allocate(1024)
//        val readBuffer2 = ByteBuffer.allocate(1024)
//        val channel = NioChannel(socketChannel = serverSocket)
//        Thread { channel.read(readBuffer1) }.start()
//        Thread { channel.read(readBuffer2) }.start()
//        readBuffer1.flip()
//        readBuffer2.flip()
//        val receivedString = UTF8Codec.DECODER.decode(readBuffer1).toString()
//        val receivedString2 = UTF8Codec.DECODER.decode(readBuffer2).toString()
//        println(receivedString)
//        println(receivedString2)
    }

    @Test
    fun write() {
        val writeBuffer = ByteBuffer.allocate(1024)

        val message1 = "12345678"
        val charBuffer1 = CharBuffer.wrap(message1)

        UTF8Codec.ENCODER.encode(charBuffer1, writeBuffer, false)

        val message2 = "abcdefg"
        val charBuffer2 = CharBuffer.wrap(message2)
        UTF8Codec.ENCODER.encode(charBuffer2, writeBuffer, false)
        writeBuffer.flip()
        println(UTF8Codec.DECODER.decode(writeBuffer).toString())

        val thread =
            Thread {
                println()
            }
    }
}
