package com.kejing.towerwood.ui.game

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModel
import com.kejing.towerwood.R
import com.kejing.towerwood.data.repository.LocalGameRepository
import com.kejing.towerwood.scene.TowerScene
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import kotlinx.coroutines.delay

/** Phases where the compact HUD replaces the menu panel. */
private val GAMEPLAY = setOf(Phase.PLAYING, Phase.HOLDING, Phase.SETTLING, Phase.PAUSED, Phase.TRACKING_LOST, Phase.RESTART)
/** Phases that need the icon bar visible without a tap. */
private val AUTO_EXPAND = setOf(Phase.PAUSED, Phase.TRACKING_LOST, Phase.RESTART)
private val ROOT_OFFSET = Vector3(-.10f, 0f, 0f)
/** Menu panel sits beside the tower at eye level; the compact HUD drops to the right-front corner of the base plate. */
private val MENU_POSITION = Vector3(.24f, 0f, .02f)
private val HUD_POSITION = Vector3(.26f, -.30f, .08f)
private const val HELP_TEXT = "① 捏住最高完整层下方的木块\n② 缓慢抽出，另一只手不扶塔\n③ 移至塔顶，交替方向摆放\n④ 松手后稳定 1 秒，即可得分\n\n抽出后请完成同一块。游戏中点底座旁的图标展开操作。"

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
    var controls by remember { mutableStateOf<com.pico.spatial.core.ecs.Entity?>(null) }
    val focused=LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if(!focused)scene.pause() }
    DisposableEffect(Unit) { onDispose { scene.close();audio.release() } }
    // v1.1 HUD: a single icon during play; it expands into the icon bar and folds back after 10 s without a tap.
    var hudExpanded by remember { mutableStateOf(false) }
    var hudTouched by remember { mutableStateOf(0L) }
    val touch={ hudTouched=SystemClock.elapsedRealtime() }
    LaunchedEffect(state.phase) { if(state.phase in AUTO_EXPAND) { hudExpanded=true; touch() } }
    val inGame=state.phase in GAMEPLAY
    LaunchedEffect(inGame,controls) { controls?.components?.get<TransformComponent>()?.setPosition(if(inGame)HUD_POSITION else MENU_POSITION) }
    LaunchedEffect(hudExpanded, hudTouched, state.phase) {
        if(hudExpanded && state.phase !in AUTO_EXPAND) { delay(10_000); hudExpanded=false }
    }
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
            scene.root.components.get<TransformComponent>()?.setPosition(ROOT_OFFSET)
            content.addEntity(scene.root)
            attachments.entity("controls")?.let {
                it.components.get<TransformComponent>()?.setPosition(MENU_POSITION)
                content.addEntity(it)
                controls=it
            }
            attachments.entity("caption")?.let {
                content.addEntity(it)
                scene.attachCaption(it, ROOT_OFFSET)
            }
            // Physics control owns its main-thread clock independently of a throttled render surface.
            try { scene.load() } catch(e:Exception) { android.util.Log.e("TowerWood","Load failed",e);vm.error() }
        },
        attachments={
            AttachmentPanel(id="controls") {
                if(state.phase in GAMEPLAY) Hud(state,hudExpanded,scene,vm,onToggle={ hudExpanded=it;touch() },onTouch=touch)
                else MenuPanel(state,scene,vm)
            }
            AttachmentPanel(id="caption") { Caption(state) }
        }
    )
}

private fun clock(seconds:Int)="${seconds/60}:${(seconds%60).toString().padStart(2,'0')}"
private fun statusOf(phase:Phase)=when(phase) { Phase.HOLDING->"持块中";Phase.SETTLING->"等待稳定";Phase.PAUSED->"已暂停";Phase.TRACKING_LOST->"追踪丢失";Phase.RESTART->"重新开始？";else->"进行中" }

@Composable
private fun Hud(state:GameUiState,expanded:Boolean,scene:TowerScene,vm:GameViewModel,onToggle:(Boolean)->Unit,onTouch:()->Unit) {
    val c=PicoTheme.colorScheme
    if(!expanded) {
        // design-style: collapsed HUD is one 56 dp icon; the step count lives in the expanded bar and the caption.
        IconButton(onClick={ onToggle(true) },size=IconButtonDefaults.iconButtonSize(56.dp),shape=CircleShape,
            colors=IconButtonDefaults.iconButtonColors(containerColor=c.fillPrimary,contentColor=c.interaction)) {
            Icon(painterResource(R.drawable.ic_hud),contentDescription="展开操作",modifier=Modifier.size(26.dp),tint=c.interaction)
        }
        return
    }
    val paused=state.phase==Phase.PAUSED || state.phase==Phase.TRACKING_LOST
    // design-style: opaque-root Figma dark green panel; container glass is disabled in manifest.
    Column(Modifier.width(340.dp).clip(RoundedCornerShape(24.dp)).background(c.fillPrimary).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Row(verticalAlignment=Alignment.Bottom) {
                Text("${state.score}",style=PicoTheme.typography.displaySmall,color=c.labelPrimary)
                Spacer(Modifier.width(6.dp))
                Text("步",style=PicoTheme.typography.bodyMedium,color=c.labelSecondary,modifier=Modifier.padding(bottom=6.dp))
            }
            Text(statusOf(state.phase),style=PicoTheme.typography.bodyMedium,color=c.interaction)
            IconButton(onClick={ onToggle(false) },size=IconButtonDefaults.iconButtonSize(36.dp),shape=CircleShape,
                colors=IconButtonDefaults.iconButtonColors(containerColor=Color.Transparent,contentColor=c.labelSecondary)) {
                Icon(painterResource(R.drawable.ic_close),contentDescription="收起",modifier=Modifier.size(20.dp),tint=c.labelSecondary)
            }
        }
        Text("${clock(state.seconds)}   ·   最佳 ${state.best}",style=PicoTheme.typography.bodyMedium,color=c.labelSecondary)
        if(state.phase==Phase.RESTART) {
            Text("本局进度将被替换，个人最佳会保留。",style=PicoTheme.typography.bodyMedium,color=c.labelPrimary)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) { Action("保留本局") { vm.cancelRestart();onTouch() } }
                Box(Modifier.weight(1f)) { Action("重新开始",R.drawable.ic_restart,primary=false) { scene.start();onToggle(false) } }
            }
        } else {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                HudIcon(if(paused)R.drawable.ic_play else R.drawable.ic_pause,if(paused)"继续" else "暂停",primary=paused) { if(paused)scene.resume() else scene.pause();onTouch() }
                HudIcon(R.drawable.ic_undo,"撤销上一步",enabled=state.canUndo) { scene.undo();onTouch() }
                HudIcon(R.drawable.ic_restart,"重新开始") { scene.requestRestart();onTouch() }
                HudIcon(R.drawable.ic_tune,"配色与设置") { scene.openSettings() }
                HudIcon(R.drawable.ic_help,"玩法说明") { vm.event(GameEvent.Help) }
            }
        }
    }
}

@Composable
private fun HudIcon(icon:Int,label:String,primary:Boolean=false,enabled:Boolean=true,onClick:()->Unit) {
    val c=PicoTheme.colorScheme
    val tint=if(primary)c.labelPrimaryLight else c.labelPrimary
    IconButton(onClick=onClick,enabled=enabled,size=IconButtonDefaults.iconButtonSize(48.dp),shape=RoundedCornerShape(14.dp),modifier=Modifier.alpha(if(enabled)1f else .4f),
        colors=IconButtonDefaults.iconButtonColors(containerColor=if(primary)c.interaction else c.fillSecondary,contentColor=tint)) {
        Icon(painterResource(icon),contentDescription=label,modifier=Modifier.size(24.dp),tint=tint)
    }
}

@Composable
private fun Caption(state:GameUiState) {
    if(state.phase !in GAMEPLAY || state.message.isBlank())return
    val c=PicoTheme.colorScheme
    // design-style: opaque-root Figma dark green chip; carries the v1.0 panel body text above the tower during play.
    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(c.fillPrimary.copy(alpha=.88f)).padding(horizontal=16.dp,vertical=9.dp)) {
        Text(state.message,style=PicoTheme.typography.bodyMedium,color=c.labelPrimary)
    }
}

@Composable
private fun MenuPanel(state:GameUiState,scene:TowerScene,vm:GameViewModel) {
    val c=PicoTheme.colorScheme
    // design-style: opaque-root Figma dark green panel; container glass is disabled in manifest.
    Column(Modifier.width(420.dp).clip(RoundedCornerShape(32.dp)).background(c.fillPrimary).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("TOWER WOOD",style=PicoTheme.typography.labelMedium,color=c.labelSecondary)
        Text(when(state.phase) {Phase.SETTINGS->"找到你的节奏";Phase.RESULT->"这一次的平衡";Phase.HELP->"一块一块，慢慢来";else->"叠木之间"},style=PicoTheme.typography.headlineLarge,color=c.labelPrimary)
        if(state.phase==Phase.RESULT) {
            Text("${state.score}  次稳稳叠放",style=PicoTheme.typography.displaySmall,color=c.labelPrimary)
            Text("${clock(state.seconds)}    最佳 ${state.best}",style=PicoTheme.typography.bodyMedium,color=c.labelSecondary)
        }
        if(state.phase!=Phase.SETTINGS) Text(if(state.phase==Phase.HELP)HELP_TEXT else state.message,style=PicoTheme.typography.bodyMediumMultiline,color=c.labelPrimary)
        when(state.phase) {
            Phase.HOME -> {
                Action("开始挑战",R.drawable.ic_play) {scene.start()}
                if(vm.hasSavedRound()) Action("恢复上次局面",R.drawable.ic_undo,primary=false) {scene.restore(vm.savedRound())}
                Action("玩法说明",R.drawable.ic_help,primary=false) {vm.event(GameEvent.Help)}
                Action("配色与设置",R.drawable.ic_tune,primary=false) {scene.openSettings()}
            }
            Phase.SETTINGS -> {
                Text("木块配色",style=PicoTheme.typography.titleMedium,color=c.labelPrimary)
                WoodTheme.entries.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        pair.forEach { theme ->
                            Button(onClick={scene.applyTheme(theme)},modifier=Modifier.weight(1f).height(124.dp),colors=ButtonDefaults.buttonColors(containerColor=if(state.woodTheme==theme)c.interaction else c.fillSecondary,contentColor=if(state.woodTheme==theme)c.labelPrimaryLight else c.labelPrimary)) {
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
                    Text("木块音效",style=PicoTheme.typography.bodyMedium,color=c.labelPrimary)
                    Switch(checked=state.soundEnabled,onCheckedChange=vm::setSound)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("塔顶放置提示",style=PicoTheme.typography.bodyMedium,color=c.labelPrimary)
                    Switch(checked=state.guidesEnabled,onCheckedChange=vm::setGuides)
                }
                Action("完成") {vm.closeHelp()}
            }
            Phase.LOADING -> Text("正在加载 54 块原木",style=PicoTheme.typography.bodySmall,color=c.labelSecondary)
            Phase.HELP -> Action("明白了") { vm.closeHelp() }
            Phase.RESULT -> {
                Action("再来一局",R.drawable.ic_restart) {scene.start()}
                if(state.canUndo) Action("撤销上一步",R.drawable.ic_undo,primary=false) {scene.undo()}
                Action("玩法说明",R.drawable.ic_help,primary=false) {vm.event(GameEvent.Help)}
            }
            Phase.ERROR -> Text("请关闭并重新打开此空间窗口",style=PicoTheme.typography.bodySmall,color=c.labelSecondary)
            else -> {}
        }
        Text("54 块原木 / 18 层起始 · 木块 × ${TowerScene.BLOCK_SCALE}",style=PicoTheme.typography.bodyTiny,color=c.labelSecondary)
    }
}

@Composable
private fun Action(label:String,icon:Int?=null,primary:Boolean=true,onClick:()->Unit) {
    val c=PicoTheme.colorScheme
    val content=if(primary)c.labelPrimaryLight else c.labelPrimary
    Button(onClick=onClick,modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=if(primary)c.interaction else c.fillSecondary,contentColor=content)) {
        // design-style: inherited-content-color Button
        if(icon!=null) { Icon(painterResource(icon),contentDescription=null,modifier=Modifier.size(20.dp),tint=content);Spacer(Modifier.width(8.dp)) }
        Text(label)
    }
}
