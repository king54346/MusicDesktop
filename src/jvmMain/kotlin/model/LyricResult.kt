package model

import androidx.annotation.Keep
import model.BaseResult


@Keep
class LyricResult(
    val transUser: LyricContributorBean?,
    val lyricUser: LyricContributorBean?,
    val lrc: LrcBean?,
    val tlyric: LrcBean?
) : BaseResult() {
    override fun isEmpty() = transUser == null && lyricUser == null && lrc == null && tlyric == null
}

@Keep
data class LyricContributorBean(
    val id: Long,
    val status: Int,
    val demand: Int,
    val userid: Long,
    val nickname: String,
    val uptime: Long
)


@Keep
data class LrcBean(
    val version: Int,
    val lyric: String
)