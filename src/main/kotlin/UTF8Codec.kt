import java.nio.charset.CharsetDecoder
import java.nio.charset.CharsetEncoder
import java.nio.charset.StandardCharsets

object UTF8Codec {
    val ENCODER: CharsetEncoder = StandardCharsets.UTF_8.newEncoder()
    val DECODER: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
}
