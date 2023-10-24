package base.player


interface IPlayerListener {
    fun onStatusChanged(status: PlayerStatus)
    fun onProgress(totalDuring: Int, currentPosition: Int, percentage: Float)
}