package base.player

import http.NCRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import model.SongBean
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ShortBuffer
import javax.sound.sampled.*
import kotlin.coroutines.cancellation.CancellationException

class NewPlayer : IPlayer {
    private var mStatus: PlayerStatus = PlayerStatus.IDLE
    private var mCurSongBean: SongBean? = null
    private lateinit var clip: Clip
    private var mJob: Job? = null

    //    进度条
    private var mDuring: Int = 0
    private var mCurTime: Int = 0
    private val mListeners = mutableListOf<IPlayerListener>()
    private lateinit var grabber: FFmpegFrameGrabber

    override fun setDataSource(songBean: SongBean) {
        mCurSongBean = songBean
        println("----${mCurSongBean?.name}----setDataSource()")
    }

    override fun start() {
        println("----${mCurSongBean?.name}----start()")
        if (mStatus == PlayerStatus.STARTED
        ) {
            pause()
        }
        mCurSongBean?.let {
            mDuring = it.dt
            mCurTime = 0
            updateProgress()
            println("播放")
            getSongUrlAndPlay(it.id)
        }
    }

    fun updateProgress() {
        if (mDuring != 0) {
            mListeners.forEach {
                it.onProgress(mDuring, mCurTime, mCurTime.toFloat() * 100 / mDuring)
            }
        }
    }
    private fun getSongUrlAndPlay(songId: Long) {
        mJob?.cancel()
        mJob = GlobalScope.launch(context = Dispatchers.IO) {
            try {
                val url = NCRetrofitClient.getNCApi().getSongUrl(songId).data.firstOrNull()?.url
                    ?: "https://music.163.com/song/media/outer/url?id=$songId.mp3"
                grabber = FFmpegFrameGrabber(url)
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
                clip.start()
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    println("getSongUrlAndPlay e = $e")
                    e.printStackTrace()
                    mListeners.forEach {
                        it.onStatusChanged(PlayerStatus.ERROR(PlayerErrorCode.ERROR_GET_URL, "获取歌曲播放链接失败"))
                    }
                }
            }
        }
    }

    override fun pause() {
        if (mStatus == PlayerStatus.STARTED) {
            println("----${mCurSongBean?.name}----pause()")
            clip.stop()
            setStatus(PlayerStatus.PAUSED)
        }
    }

    private fun setStatus(status: PlayerStatus) {
        mStatus = status
        mListeners.forEach {
            it.onStatusChanged(mStatus)
        }
    }

    override fun resume() {
        clip.start()
    }

    override fun stop() {
        if (::clip.isInitialized) {
            println("----${mCurSongBean?.name}----stop()")
            clip.close()
            grabber.stop()
            grabber.release()
            mDuring = 0
        } else {
            // clip未初始化
        }
        setStatus(PlayerStatus.STOPPED)
        setStatus(PlayerStatus.IDLE)
    }

    override fun seekTo(position: Float) {
        println("----${mCurSongBean?.name}----seekTo->${position}")
        val framePosition = (position * clip.frameLength).toInt()
        clip.framePosition = framePosition
    }

    override fun addListener(listener: IPlayerListener) {
        println(listener)
        mListeners.add(listener)
    }

    override fun removeListener(listener: IPlayerListener) {
        mListeners.remove(listener)
    }
}