package base.player

import model.SongBean


interface IPlayer {
    fun setDataSource(songBean: SongBean)
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(position: Float)

    fun envAvailable() = false

    fun addListener(listener: IPlayerListener)
    fun removeListener(listener: IPlayerListener)
}