import event.EventBroker
import event.MessageBroadcast
import event.UserDisconnected
import event.UserJoined
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import java.net.InetSocketAddress
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
    fun readWhenUserIsDisconnected() {
        val user =
            User(
                uuid = UUID.randomUUID(),
                socketChannel = mock(SocketChannel::class.java),
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mock(EventBroker::class.java),
            )
        user.disconnect()

        assertThrows<IllegalStateException>(USER_IS_DISCONNECTED_MESSAGE(user.uuid)) {
            user.read()
        }
    }

    @Test
    fun readAndBroadcastMessage() {
        // given
        val mockSocketChannel = mock(SocketChannel::class.java)
        val mockEventBroker = mock(EventBroker::class.java)
        val user =
            User(
                uuid = UUID.randomUUID(),
                socketChannel = mockSocketChannel,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mockEventBroker,
            )
        val message = "this is message"
        `when`(mockSocketChannel.read(any<ByteBuffer>())).thenAnswer { invocation ->
            val byteBuffer = invocation.getArgument<ByteBuffer>(0)
            UTF8Codec.ENCODER.encode(CharBuffer.wrap(message), byteBuffer, false)
            1
        }.then {
            0
        }

        // when
        user.read()

        val argumentCaptor = argumentCaptor<MessageBroadcast>()
        verify(mockEventBroker, times(1))
            .add(argumentCaptor.capture())
        val actual = argumentCaptor.firstValue
        assertEquals(user.uuid, actual.from)
        assertEquals(message, actual.message)
    }

    @Test
    fun onMessageBroadcastWhenPublisherEqualsSubscriber() {
        val mockUserSocket = mock(SocketChannel::class.java)
        val user =
            User(
                uuid = UUID.randomUUID(),
                socketChannel = mockUserSocket,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mock(EventBroker::class.java),
            )

        val event =
            MessageBroadcast(
                uuid = UUID.randomUUID(),
                from = user.uuid,
                message = "this is message",
            )

        assertTrue { user.uuid == event.from }

        // when
        user.onMessageBroadcast(event)

        // then
        verify(mockUserSocket, times(0)).write(any<ByteBuffer>())
    }

    @Test
    fun onMessageBroadcastFromOtherUser() {
        // given
        val mockUserSocket = mock(SocketChannel::class.java)
        val user =
            User(
                uuid = UUID.randomUUID(),
                socketChannel = mockUserSocket,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mock(EventBroker::class.java),
            )

        val event =
            MessageBroadcast(
                uuid = UUID.randomUUID(),
                from = UUID.randomUUID(),
                message = "this is message",
            )

        assertTrue { user.uuid != event.from }

        val captured = captureWrittenBuffer(mockUserSocket, ByteBuffer.allocate(1024))

        // when
        user.onMessageBroadcast(event)

        // then
        verify(mockUserSocket).write(any<ByteBuffer>())

        assertEquals(convertToBuffer(BROADCAST_MESSAGE(event.from, event.message)), captured)
        assertEquals(
            BROADCAST_MESSAGE(event.from, event.message),
            UTF8Codec.DECODER.decode(captured).toString(),
        )
    }

    @Test
    fun onDisconnectedWhenPublisherAndSubscriberIsSame() {
        // given
        val userUuid = UUID.randomUUID()
        val mockUserSocket = mock(SocketChannel::class.java)
        val user =
            User(
                uuid = userUuid,
                socketChannel = mockUserSocket,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mock(EventBroker::class.java),
            )
        user.disconnect()

        val event =
            UserDisconnected(
                uuid = userUuid,
            )
        assertTrue { user.uuid == event.uuid }

        // when
        user.onDisconnected(event)

        // then
        verify(mockUserSocket, times(0)).write(any<ByteBuffer>())
    }

    @Test
    fun onDisconnectWhenMessageSubscriberIsDisconnected() {
        // given
        val userUuid = UUID.randomUUID()
        val mockUserSocket = mock(SocketChannel::class.java)
        val user =
            User(
                uuid = userUuid,
                socketChannel = mockUserSocket,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mock(EventBroker::class.java),
            )
        user.disconnect()

        val disconnectedUserUuid = UUID.randomUUID()
        val event =
            UserDisconnected(
                uuid = disconnectedUserUuid,
            )
        assertTrue { user.uuid != event.uuid }

        // when
        user.onDisconnected(event)

        // then
        verify(mockUserSocket, times(0)).write(any<ByteBuffer>())
    }

    @Test
    fun onDisconnectedWhenOtherUserIsDisconnected() {
        // given
        val userUuid = UUID.randomUUID()
        val mockUserSocket = mock(SocketChannel::class.java)
        val user =
            User(
                uuid = userUuid,
                socketChannel = mockUserSocket,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mock(EventBroker::class.java),
            )

        val disconnectedUserUuid = UUID.randomUUID()
        val event =
            UserDisconnected(
                uuid = disconnectedUserUuid,
            )

        val captured = captureWrittenBuffer(mockUserSocket, ByteBuffer.allocate(1024))

        // when
        user.onDisconnected(event)

        // then
        verify(mockUserSocket).write(any<ByteBuffer>())

        assertEquals(convertToBuffer(USER_IS_DISCONNECTED_MESSAGE(disconnectedUserUuid)), captured)
        assertEquals(
            USER_IS_DISCONNECTED_MESSAGE(disconnectedUserUuid),
            UTF8Codec.DECODER.decode(captured).toString(),
        )
    }

    @Test
    fun onJoinedNewUserHasJoinedMessage() {
        val existingUserUuid = UUID.randomUUID()
        val mockExistingUserSocket = mock(SocketChannel::class.java)
        `when`(mockExistingUserSocket.remoteAddress).then { InetSocketAddress("localhost", 8081) }
        val existingUser =
            User(
                uuid = existingUserUuid,
                socketChannel = mockExistingUserSocket,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mock(EventBroker::class.java),
            )

        val newUserUuid = UUID.randomUUID()
        val event =
            UserJoined(
                uuid = newUserUuid,
                otherUsers = listOf(existingUserUuid),
            )

        val captured = captureWrittenBuffer(mockExistingUserSocket, ByteBuffer.allocate(1024))

        // when
        existingUser.onJoined(event)

        // then
        verify(mockExistingUserSocket).write(any<ByteBuffer>())

        assertEquals(convertToBuffer(NEW_USER_HAS_JOINED_MESSAGE(newUserUuid)), captured)
        assertEquals(
            NEW_USER_HAS_JOINED_MESSAGE(newUserUuid),
            UTF8Codec.DECODER.decode(captured).toString(),
        )
    }

    @Test
    fun onJoinedWelcomeMessageWhenThereAreNotExistingUsers() {
        // given
        val newUserUuid = UUID.randomUUID()
        val mockNewUserSocket = mock(SocketChannel::class.java)
        `when`(mockNewUserSocket.remoteAddress).then { InetSocketAddress("localhost", 8081) }
        val newUser =
            User(
                uuid = newUserUuid,
                socketChannel = mockNewUserSocket,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mock(EventBroker::class.java),
            )

        val event =
            UserJoined(
                uuid = newUserUuid,
                otherUsers = emptyList(),
            )

        val captured = captureWrittenBuffer(mockNewUserSocket, ByteBuffer.allocate(1024))

        // when
        newUser.onJoined(event)

        // then
        verify(mockNewUserSocket).write(any<ByteBuffer>())

        assertEquals(convertToBuffer(WELCOME_MESSAGE_WHEN_THERE_ARE_NOT_EXISTING_USERS_FORMAT(mockNewUserSocket.remoteAddress)), captured)
        assertEquals(
            WELCOME_MESSAGE_WHEN_THERE_ARE_NOT_EXISTING_USERS_FORMAT(mockNewUserSocket.remoteAddress),
            UTF8Codec.DECODER.decode(captured).toString(),
        )
    }

    @Test
    fun onJoinedWelcomeMessageWhenThereAreExistingUsers() {
        // given
        val newUserUuid = UUID.randomUUID()
        val mockNewUserSocket = mock(SocketChannel::class.java)
        `when`(mockNewUserSocket.remoteAddress).then { InetSocketAddress("localhost", 8081) }
        val newUser =
            User(
                uuid = newUserUuid,
                socketChannel = mockNewUserSocket,
                readLock = ReentrantLock(),
                writeLock = ReentrantLock(),
                eventBroker = mock(EventBroker::class.java),
            )

        val existingUserUuid = UUID.randomUUID()
        val event =
            UserJoined(
                uuid = newUserUuid,
                otherUsers = listOf(existingUserUuid),
            )

        val captured = captureWrittenBuffer(mockNewUserSocket, ByteBuffer.allocate(1024))

        // when
        newUser.onJoined(event)

        // then
        verify(mockNewUserSocket).write(any<ByteBuffer>())

        assertEquals(
            convertToBuffer(
                WELCOME_MESSAGE_WHEN_THERE_ARE_EXISTING_USERS_FORMAT(mockNewUserSocket.remoteAddress, listOf(existingUserUuid)),
            ),
            captured,
        )
        assertEquals(
            WELCOME_MESSAGE_WHEN_THERE_ARE_EXISTING_USERS_FORMAT(mockNewUserSocket.remoteAddress, listOf(existingUserUuid)),
            UTF8Codec.DECODER.decode(captured).toString(),
        )
    }

    @Test
    fun readWithLockThreadSafe() {
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
        while (!threads.all { it.state == Thread.State.WAITING }) {
        }
        assertTrue { threads.all { it.state == Thread.State.WAITING } }
    }

    @Test
    fun readWithoutLockThreadSafe() {
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

    private fun convertToBuffer(string: String): ByteBuffer {
        val expectedByteBuffer = ByteBuffer.allocate(1024)
        UTF8Codec.ENCODER.encode(
            CharBuffer.wrap(
                string,
            ),
            expectedByteBuffer,
            false,
        )
        expectedByteBuffer.flip()
        return expectedByteBuffer
    }

    private fun captureWrittenBuffer(
        mockUserSocket: SocketChannel,
        captured: ByteBuffer,
    ): ByteBuffer {
        doAnswer { invocation ->
            val original = invocation.getArgument<ByteBuffer>(0)
            original.mark()
            captured.put(original)
            original.reset()
            captured.flip()
            null
        }.`when`(mockUserSocket).write(any<ByteBuffer>())
        return captured
    }
}
