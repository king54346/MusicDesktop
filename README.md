#todo
https://github.com/JetBrains/compose-multiplatform/tree/master/experimental/components/VideoPlayer


讲了[Precompose](https://github.com/Tlaster/PreCompose)这个跨平台Navigation框架的使用， 它基本复刻了Jetpack Navigation、Lifecycle、ViewModel这些组件，
使用方式也基本保持一致，美滋滋！当然LiveData已经被废弃了，推荐使用Flow代替～至于网络请求，Retrofit照用不误，又一次美滋滋～

- 老规矩，先定义一波BaseResult、BaseViewModel、ViewStateComponent(页面状态切换组件)
```
代码： 略
```
- Model层
```
class LyricResult(
    val transUser: LyricContributorBean?,
    val lyricUser: LyricContributorBean?,
    val lrc: LrcBean?,
    val tlyric: LrcBean?
) : BaseResult()
```
- ViewModel层
```
class CpnLyricViewModel : BaseViewModel() {
     fun getLyric(id: Long) = launchFlow {
        NCRetrofitClient.getNCApi().getLyric(id)
    }
}

interface NCApi {
    @GET("/lyric")
    suspend fun getLyric(@Query("id") id: Long): LyricResult
}
```
- View层
```
@Composable
fun CpnLyric() {
    ViewStateComponent(
        key = "CpnLyric-${id}",
        loadDataBlock = {viewModel.getLyric(id)}
    ) {
        LyricList(it)
    }
}
```
### 怎么播放音乐
至于在Compose Desktop上怎么播放音乐呢，毕竟没有Android的MediaPlayer，在github上找了找，发现[succlz123](https://github.com/succlz123)大佬开源的Compose Multiplatform项目
[AcFun-Client-Multiplatform](https://github.com/succlz123/AcFun-Client-Multiplatform)，里面有视频播放的功能，是基于[vlcj](https://github.com/caprica/vlcj)来实现的，看了下vlcj的api，使用AudioPlayerComponent播放音乐不是问题

### 关于嵌套滑动
开发过程中，有些交互感觉需要涉及到嵌套滑动，在Jetpack Compose中，使用NestedScrollConnection来处理嵌套滑动到场景，于是乎，写了一堆✨✨代码后，
发现NestedScrollConnection在Compose Desktop中完全不起作用，后面找了下github的issue，发现有哥们也遇到了哈哈哈，然而官方21年的回复是暂时没有计划，
到现在还是没有解决，凉飕飕～    
![img](https://github.com/sskEvan/NCMusicDesktop/blob/master/readme/nested_issue1.webp)    
![img](https://github.com/sskEvan/NCMusicDesktop/blob/master/readme/nested_issue2.webp)   

### 第三方框架
- [PreCompose](https://github.com/Tlaster/PreCompose)
- [zxing](https://github.com/zxing/zxing)
- [compose-imageloader-desktop](https://github.com/succlz123/compose-desktop-imageloader)
- [vlcj](https://github.com/caprica/vlcj)

### 运行效果图
![img](https://github.com/sskEvan/NCMusicDesktop/blob/master/readme/登录.gif)    
![img](https://github.com/sskEvan/NCMusicDesktop/blob/master/readme/首页.gif)    
![img](https://github.com/sskEvan/NCMusicDesktop/blob/master/readme/歌单列表.gif)    
![img](https://github.com/sskEvan/NCMusicDesktop/blob/master/readme/歌单详情.gif)    
![img](https://github.com/sskEvan/NCMusicDesktop/blob/master/readme/音乐播放.gif)    
![img](https://github.com/sskEvan/NCMusicDesktop/blob/master/readme/主题切换.gif)    
