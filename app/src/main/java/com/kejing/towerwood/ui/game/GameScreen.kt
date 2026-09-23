package com.kejing.towerwood.ui.game

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import androidx.compose.ui.unit.dp
import com.kejing.towerwood.data.repository.LocalGameRepository
import com.kejing.towerwood.ui.game.*
import com.kejing.towerwood.scene.TowerScene
import com.kejing.towerwood.ui.game.GameViewModel
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter

@Composable
fun GameScreen() {
    val context=LocalContext.current
    val vm: GameViewModel = viewModel(factory = remember {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return GameViewModel(LocalGameRepository(context)) as T
            }
        }
    })
    val scene=remember { TowerScene(vm).also { com.kejing.towerwood.scene.RuntimeSession.scene=java.lang.ref.WeakReference(it) } }
    val state by vm.uiState.collectAsState()
    val audio=remember { android.media.SoundPool.Builder().setMaxStreams(2).build() }
    val woodSound=remember { audio.load(context,com.kejing.towerwood.R.raw.wood_tap,1) }
    LaunchedEffect(state.score) { if(state.score>0&&state.soundEnabled)audio.play(woodSound,.5f,.5f,1,0,1f) }
    val density=LocalDensity.current
    val converter=LocalPhysicalLengthConverter.current
    var spatialContent by remember { mutableStateOf<SpatialViewContent?>(null) }
    val focused=LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if(!focused)scene.pause() }
    DisposableEffect(Unit) { onDispose { scene.close();audio.release() } }
    SpatialView(
        modifier=Modifier.fillMaxSize().pointerInput(Unit) {
            detectSpatialDragGesture(context, targetedToEntity=TargetEntity.any(),
                onDragStart={ _, value ->
                    val rotation=spatialContent?.let { it.convertRotation(value.inputDevicePose.rawRotation.toQuaternion(),ViewCoordinateSpace.Global,it.localSpatialCoordinateSpace) }
                    scene.grab(value.targetEntity,rotation)
                },
                onDragEnd={ scene.release() }, onDragCancel={ scene.pause(true) }
            ) { value ->
                fun meter(px:Float)=with(density) { converter.dpToLength(px.toDp(),LengthUnit.Meters) }
                scene.drag(Vector3(meter(value.dragAmount.x),-meter(value.dragAmount.y),meter(value.dragAmount.z)))
                spatialContent?.let { scene.rotate(it.convertRotation(value.inputDevicePose.rawRotation.toQuaternion(),ViewCoordinateSpace.Global,it.localSpatialCoordinateSpace)) }
            }
        },
        initial={ content,attachments ->
            spatialContent=content
            scene.root.components.get<TransformComponent>()?.setPosition(Vector3(-.13f,0f,0f))
            content.addEntity(scene.root)
            attachments.entity("controls")?.let {
                it.components.get<TransformComponent>()?.setPosition(Vector3(.17f,.02f,.02f))
                content.addEntity(it)
            }
            // Physics control owns its main-thread clock independently of a throttled render surface.
            try { scene.load() } catch(e:Exception) { android.util.Log.e("TowerWood","Load failed",e);vm.error() }
        },
        attachments={
            AttachmentPanel(id="controls") {
                // design-style: opaque-root Figma dark green panel; container glass is disabled in manifest.
                Column(Modifier.width(480.dp).clip(RoundedCornerShape(32.dp)).background(PicoTheme.colorScheme.fillPrimary).padding(32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Text("TOWER WOOD",style=PicoTheme.typography.labelMedium,color=PicoTheme.colorScheme.labelSecondary)
                    Text(when(state.phase) {Phase.SETTINGS->"找到你的节奏";Phase.PAUSED->"歇一会儿";Phase.TRACKING_LOST->"找回你的手";Phase.RESULT->"这一次的平衡";Phase.HELP->"一块一块，慢慢来";Phase.RESTART->"重新开始？";else->"叠木之间"},style=PicoTheme.typography.headlineLarge,color=PicoTheme.colorScheme.labelPrimary)
                    if(state.phase !in listOf(Phase.HOME,Phase.LOADING,Phase.HELP,Phase.SETTINGS)) {
                        Text("${state.score}  次稳稳叠放",style=PicoTheme.typography.displaySmall,color=PicoTheme.colorScheme.labelPrimary)
                        Text("${state.seconds/60}:${(state.seconds%60).toString().padStart(2,'0')}    最佳 ${state.best}",style=PicoTheme.typography.bodyMedium,color=PicoTheme.colorScheme.labelSecondary)
                    }
                    if(state.phase!=Phase.SETTINGS) Text(if(state.phase==Phase.HELP) "① 捏住最高完整层下方的木块\n② 缓慢抽出，另一只手不扶塔\n③ 移至塔顶，交替方向摆放\n④ 松手后稳定 1 秒，即可得分\n\n抽出后请完成同一块。移动窗口前先暂停。" else state.message,style=PicoTheme.typography.bodyMediumMultiline,color=PicoTheme.colorScheme.labelPrimary)
                    when(state.phase) {
                        Phase.HOME -> { Action("开始挑战") {scene.start()}; if(vm.hasSavedRound()) Action("恢复上次局面") {scene.restore(vm.savedRound())}; Action("玩法说明") {vm.event(GameEvent.Help)}; Action("设置") {scene.openSettings()} }
                        Phase.SETTINGS -> {
                            Text("木块配色",style=PicoTheme.typography.titleMedium,color=PicoTheme.colorScheme.labelPrimary)
                            WoodTheme.entries.chunked(2).forEach { pair ->
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { theme ->
                                        Button(onClick={scene.applyTheme(theme)},modifier=Modifier.weight(1f).height(124.dp),colors=ButtonDefaults.buttonColors(containerColor=if(state.woodTheme==theme)PicoTheme.colorScheme.interaction else PicoTheme.colorScheme.fillSecondary,contentColor=if(state.woodTheme==theme)PicoTheme.colorScheme.labelPrimaryLight else PicoTheme.colorScheme.labelPrimary)) {
                                            Column(Modifier.fillMaxWidth().padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                                Image(painterResource(theme.preview),contentDescription=theme.label,modifier=Modifier.fillMaxWidth().height(76.dp))
                                                // design-style: inherited-content-color Button
                                                Text(theme.label+if(state.woodTheme==theme)" ✓" else "",style=PicoTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("木块音效",style=PicoTheme.typography.bodyMedium,color=PicoTheme.colorScheme.labelPrimary)
                                Switch(checked=state.soundEnabled,onCheckedChange=vm::setSound)
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("塔顶放置提示",style=PicoTheme.typography.bodyMedium,color=PicoTheme.colorScheme.labelPrimary)
                                Switch(checked=state.guidesEnabled,onCheckedChange=vm::setGuides)
                            }
                            Action("完成") {vm.closeHelp()}
                        }
                        Phase.LOADING -> Text("正在加载 54 块原木",style=PicoTheme.typography.bodySmall,color=PicoTheme.colorScheme.labelSecondary)
                        Phase.PAUSED,Phase.TRACKING_LOST -> {Action("继续挑战") {scene.resume()};Action("配色与设置") {scene.openSettings()};Action("重新开始") {scene.requestRestart()}}
                        Phase.RESTART -> {Action("确认重新开始") {scene.start()};Action("保留当前局面") {vm.cancelRestart()}}
                        Phase.HELP -> Action("明白了") { vm.closeHelp() }
                        Phase.RESULT -> {Action("重新开始") {scene.start()};Action("玩法说明") {vm.event(GameEvent.Help)}}
                        Phase.ERROR -> Text("请关闭并重新打开此空间窗口",style=PicoTheme.typography.bodySmall,color=PicoTheme.colorScheme.labelSecondary)
                        else -> {Action("暂停") {scene.pause()};Action("重新开始") {scene.requestRestart()}}
                    }
                    if(state.canUndo && state.phase in listOf(Phase.PLAYING,Phase.HOLDING,Phase.SETTLING,Phase.PAUSED,Phase.TRACKING_LOST,Phase.RESULT)) {
                        Action("撤销上一步") {scene.undo()}
                    }
                    Text("54 块原木 / 18 层起始",style=PicoTheme.typography.bodyTiny,color=PicoTheme.colorScheme.labelSecondary)
                }
            }
        }
    )
}
@Composable
private fun Action(label:String,onClick:()->Unit) {
    Button(onClick=onClick,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=PicoTheme.colorScheme.interaction,contentColor=PicoTheme.colorScheme.labelPrimaryLight)) {
        // design-style: inherited-content-color Button
        Text(label)
    }
}
