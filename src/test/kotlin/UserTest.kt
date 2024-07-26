import event.EventBroker
import event.MessageBroadcast
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.channels.SocketChannel
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.locks.ReentrantLock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserTest {
    @Test
    fun readWithLock() {
        // given
        val latch = CountDownLatch(1)

        val mockSocketChannel = mock(SocketChannel::class.java)
        `when`(mockSocketChannel.read(any<ByteBuffer>())).then {
            latch.await()
        }

        val mockEventBroker = mock(EventBroker::class.java)
        `when`(mockEventBroker.add(any<MessageBroadcast>())).then {
            latch.countDown()
        }

        val readLock = ReentrantLock()
        val user =
            User(
                uuid = UUID.randomUUID(),
                socketChannel = mockSocketChannel,
                readLock = readLock,
                writeLock = mock(ReentrantLock::class.java),
                eventBroker = mockEventBroker,
            )

        // when
        val threads =
            (0..1).map {
                Thread { user.read() }
            }.onEach { it.start() }

        // then
        @Suppress("ControlFlowWithEmptyBody")
        while (!threads.all { it.state == Thread.State.WAITING }) {}
        assertTrue { threads.all { it.state == Thread.State.WAITING } }
    }

    @Test
    fun readWithoutLock() {
        // given
        val latch = CountDownLatch(1)

        val firstMessage = "first"
        val secondMessage = "second"
        val mockSocketChannel = mock(SocketChannel::class.java)
        `when`(mockSocketChannel.read(any<ByteBuffer>())).thenAnswer { invocation ->
            latch.await()
            val byteBuffer = invocation.getArgument<ByteBuffer>(0)
            UTF8Codec.ENCODER.encode(CharBuffer.wrap(firstMessage), byteBuffer, false)
            1
        }.thenAnswer { invocation ->
            val byteBuffer = invocation.getArgument<ByteBuffer>(0)
            UTF8Codec.ENCODER.encode(CharBuffer.wrap(secondMessage), byteBuffer, false)
            1
        }.then {
            0
        }

        val mockEventBroker = mock(EventBroker::class.java)
        `when`(mockEventBroker.add(any<MessageBroadcast>())).then {
            latch.countDown()
        }.then {}

        val user =
            User(
                uuid = UUID.randomUUID(),
                socketChannel = mockSocketChannel,
                readLock = mock(ReentrantLock::class.java),
                writeLock = mock(ReentrantLock::class.java),
                eventBroker = mockEventBroker,
            )

        // when
        val threads =
            (0..1).map {
                Thread { user.read() }
            }.onEach { it.start() }

        threads.forEach { it.join() }

        // then
        val argumentCaptor = argumentCaptor<MessageBroadcast>()
        verify(mockEventBroker, times(2))
            .add(argumentCaptor.capture())
        assertEquals(secondMessage, argumentCaptor.firstValue.message)
        assertEquals(firstMessage, argumentCaptor.secondValue.message)
    }
}
