import kotlinx.coroutines.*
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import java.util.concurrent.LinkedBlockingQueue
import javax.sound.sampled.*


const val maxReadAheadBufferMicros = 1000 * 1000L

//跟踪音频播放的时间
private class PlaybackTimer {
    private var startTime = -1L
    private val soundLine: DataLine?

    constructor(soundLine: DataLine?) {
        this.soundLine = soundLine
    }

    constructor() {
        soundLine = null
    }

    // 开始计时
    fun start() {
        if (soundLine == null) {
            startTime = System.nanoTime()
        }
    }

    // 返回从 start() 调用后经过的微秒数
    fun elapsedMicros(): Long {
        return if (soundLine == null) {
            check(startTime >= 0) { "PlaybackTimer not initialized." }
            (System.nanoTime() - startTime) / 1000
        } else {
            soundLine.microsecondPosition
        }
    }
}

object AudioProcessingExample {
    @JvmStatic
    fun main(args: Array<String>) {
        val filepath = "C:\\Users\\admin\\Desktop\\NCMusicDesktop-master\\src\\jvmMain\\resources\\1.mp3"
        val grabber: FrameGrabber = FFmpegFrameGrabber(filepath)
        grabber.start()

        val soundLine: SourceDataLine?
        if (grabber.audioChannels > 0) {
            val audioFormat = AudioFormat(grabber.sampleRate.toFloat(), 16, grabber.audioChannels, true, true)
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            soundLine = AudioSystem.getLine(info) as SourceDataLine
            soundLine.open(audioFormat)
            soundLine.start()
        } else {
            soundLine = null
        }

        while (true) {
            val frame = grabber.grab() ?: break
            if (frame.type === Frame.Type.AUDIO) {
                val channelSamplesShortBuffer = frame.samples[0] as ShortBuffer
                channelSamplesShortBuffer.rewind()

                val outBuffer = ByteBuffer.allocate(channelSamplesShortBuffer.capacity() * 2)

                for (i in 0 until channelSamplesShortBuffer.capacity()) {
                    val value = channelSamplesShortBuffer[i]
                    outBuffer.putShort(value)
                }

                soundLine?.write(outBuffer.array(), 0, outBuffer.capacity())
                outBuffer.clear()
            }
        }

        soundLine?.drain()
        soundLine?.close()
        grabber.stop()
    }
}


class AudioPlayer(private val filepath: String) {
    private val END_OF_QUEUE_MARKER = ByteBuffer.allocate(0)
    private val frameQueue = LinkedBlockingQueue<ByteBuffer>()

    private var grabber: FFmpegFrameGrabber = FFmpegFrameGrabber(filepath)
    private lateinit var soundLine: SourceDataLine

    private val job = SupervisorJob()
    private var playbackScope: CoroutineScope? = null

    init {
        setup()
    }

    private fun setup() {
        grabber.start()
        val audioFormat = AudioFormat(grabber.sampleRate.toFloat(), 16, grabber.audioChannels, true, true)
        val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
        soundLine = AudioSystem.getLine(info) as SourceDataLine
        soundLine.open(audioFormat)
        soundLine.start()
    }

    fun start() {
        playbackScope = CoroutineScope(Dispatchers.IO + job)

        playbackScope?.launch {
            try {
                while (isActive) {
                    val frame: Frame = grabber.grab() ?: break
                    if (frame.type === Frame.Type.AUDIO) {
                        val channelSamplesShortBuffer = frame.samples[0] as ShortBuffer
                        channelSamplesShortBuffer.rewind()

                        val outBuffer = ByteBuffer.allocate(channelSamplesShortBuffer.capacity() * 2)
                        for (i in 0 until channelSamplesShortBuffer.capacity()) {
                            val value = channelSamplesShortBuffer[i]
                            outBuffer.putShort(value)
                        }
                        frameQueue.put(outBuffer)
                    }
                }
            } finally {
                frameQueue.put(END_OF_QUEUE_MARKER)
            }
        }

        playbackScope?.launch {
            try {
                while (isActive) {
                    val outBuffer = frameQueue.take()
                    if (outBuffer === END_OF_QUEUE_MARKER) break
                    soundLine.write(outBuffer.array(), 0, outBuffer.capacity())
                }
            } finally {
                soundLine.drain()
            }
        }
    }

    fun pause() {
        soundLine.stop()
    }

    fun resume() {
        soundLine.start()
    }

    fun seekTo(position: Float) {
        val skipFrames = (position * grabber.lengthInFrames).toLong()
        grabber.setFrameNumber(skipFrames.toInt())
        frameQueue.clear()
    }

    fun close() {
        playbackScope?.let {
            it.cancel()
            soundLine.close()
            grabber.stop()
            grabber.release()
        }
    }
}

class AudioPlayer2(private val filepath: String) {
    private var grabber: FFmpegFrameGrabber = FFmpegFrameGrabber(filepath)
    private lateinit var clip: Clip
    private var audioFrames = mutableListOf<ByteBuffer>() // 使用列表存储帧数据
    private var currentFrameIndex = 0
    init {
        setup()
    }

    private fun setup() {
        grabber.start()

        val audioFormat = AudioFormat(
            grabber.sampleRate.toFloat(), 16,
            grabber.audioChannels, true, true
        )

        val info = DataLine.Info(Clip::class.java, audioFormat)
        clip = AudioSystem.getLine(info) as Clip

        // 获取整个音频数据
        val outputStream = ByteArrayOutputStream()
        while (true) {
            val frame: Frame = grabber.grab() ?: break
            if (frame.type === Frame.Type.AUDIO) {
                val channelSamplesShortBuffer = frame.samples[0] as ShortBuffer
                val outBuffer = ByteBuffer.allocate(channelSamplesShortBuffer.capacity() * 2)
                while (channelSamplesShortBuffer.hasRemaining()) {
                    outBuffer.putShort(channelSamplesShortBuffer.get())
                }
                outputStream.write(outBuffer.array())
            }
        }

        val audioBytes = outputStream.toByteArray()
        val audioInputStream = AudioInputStream(ByteArrayInputStream(audioBytes), audioFormat, audioBytes.size.toLong())
        clip.open(audioInputStream)
    }

    fun start() {
        clip.start()
    }

    fun pause() {
        clip.stop()
    }

    fun resume() {
        clip.start()
    }

    fun seekTo(position: Float) {
        val framePosition = (position * clip.frameLength).toInt()
        clip.framePosition = framePosition
    }

    fun close() {
        clip.close()
        grabber.stop()
        grabber.release()
    }
}

class MediaPlayer(private val filepath: String) {
    private var grabber: FFmpegFrameGrabber = FFmpegFrameGrabber(filepath)
    private lateinit var soundLine: SourceDataLine

    init {
        setup()
    }

    private fun setup() {
        grabber.start()
        val audioFormat = AudioFormat(grabber.sampleRate.toFloat(), 16, grabber.audioChannels, true, true)
        val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
        soundLine = AudioSystem.getLine(info) as SourceDataLine
        soundLine.open(audioFormat)
    }

    fun play() {
        val audioBytes = mutableListOf<Byte>()
        while (true) {
            val frame = grabber.grab() ?: break
            if (frame.type === Frame.Type.AUDIO) {
                val channelSamplesShortBuffer = frame.samples[0] as ShortBuffer
                channelSamplesShortBuffer.rewind()
                val outBuffer = ByteBuffer.allocate(channelSamplesShortBuffer.capacity() * 2)
                for (i in 0 until channelSamplesShortBuffer.capacity()) {
                    val value = channelSamplesShortBuffer[i]
                    outBuffer.putShort(value)
                }
                audioBytes.addAll(outBuffer.array().toList())
            }
        }
        soundLine.start()
        soundLine.write(audioBytes.toByteArray(), 0, audioBytes.size)
        soundLine.drain()
    }

    fun pause() {
        soundLine.stop()
    }

    fun resume() {
        soundLine.start()
    }

    fun stop() {
        soundLine.stop()
        soundLine.close()
        grabber.stop()
    }
}

fun main() = runBlocking {
    val filepath = "C:\\Users\\admin\\Desktop\\NCMusicDesktop-master\\src\\jvmMain\\resources\\1.mp3"
    val player = AudioPlayer2(filepath)
    player.start()
    delay(5000) // Play for 10 seconds

    player.pause()
    delay(5000) // Pause for 5 seconds

    player.resume()
    delay(10000) // Resume play for 5 seconds

//    player.seekTo(0.5f) // Jump to the middle of the audio track

//    delay(30000)

//    player.stop() // Close the player and free resources
}

object AudioProcessingExample2 {

    private val END_OF_QUEUE_MARKER = ByteBuffer.allocate(0)
    private val frameQueue = LinkedBlockingQueue<ByteBuffer>()
    private val job = SupervisorJob() // Supervisor Job to control children coroutines

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        val filepath = "C:\\Users\\admin\\Desktop\\NCMusicDesktop-master\\src\\jvmMain\\resources\\1.mp3"
        val grabber: FrameGrabber = FFmpegFrameGrabber(filepath)
        grabber.start()

        val audioFormat = AudioFormat(grabber.sampleRate.toFloat(), 16, grabber.audioChannels, true, true)
        val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
        val soundLine: SourceDataLine = AudioSystem.getLine(info) as SourceDataLine
        soundLine.open(audioFormat)
        soundLine.start()

        // 抓取音频帧的协程
        launch(Dispatchers.IO + job) {
            try {
                while (isActive) {
                    val frame = grabber.grab()
                    if (frame == null) break

                    if (frame.type === Frame.Type.AUDIO) {
                        val channelSamplesShortBuffer = frame.samples[0] as ShortBuffer
                        channelSamplesShortBuffer.rewind()

                        val outBuffer = ByteBuffer.allocate(channelSamplesShortBuffer.capacity() * 2)
                        for (i in 0 until channelSamplesShortBuffer.capacity()) {
                            val value = channelSamplesShortBuffer[i]
                            outBuffer.putShort(value)
                        }
                        frameQueue.put(outBuffer)
                    }
                }
            } finally {
                grabber.stop()
                frameQueue.put(END_OF_QUEUE_MARKER) // 添加结束标志
            }
        }

        // 播放音频的协程
        launch(Dispatchers.IO + job) {
            try {
                while (isActive) {
                    val outBuffer = frameQueue.take()
                    if (outBuffer === END_OF_QUEUE_MARKER) {
                        break
                    } // 检测到结束标志
                    soundLine.write(outBuffer.array(), 0, outBuffer.capacity())
                }
            } finally {
                soundLine.drain()
                soundLine.close()
            }
        }

        // 示例：运行10秒后尝试停止所有协程
        delay(1000_000)

        job.cancel() // 取消所有子协程
    }
}