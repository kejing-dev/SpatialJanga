package com.kejing.towerwood.scene

import android.util.Log
import kotlinx.coroutines.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.PhysicsWorldComponent
import com.pico.spatial.core.ecs.RigidBodyComponent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.PhysicsVelocityComponent
import com.pico.spatial.core.ecs.PhysicsForceComponent
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.simulation.SimulationClock
import com.pico.spatial.core.ecs.simulation.SolverIterations
import com.pico.spatial.core.ecs.simulation.MassProperties
import com.pico.spatial.core.ecs.simulation.RigidBodyMode
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.EulerAngles
import com.kejing.towerwood.ui.game.*
import com.kejing.towerwood.domain.usecase.StackRulesUseCase
import com.kejing.towerwood.ui.game.GameViewModel
import kotlin.math.*

/** Owns physics and transforms; high-frequency input never round-trips through Compose. */
class TowerScene(private val vm: GameViewModel) {
    companion object {
        /** v1.1: block geometry, spacing, thresholds and the base plate all derive from this one factor (design change A). */
        const val BLOCK_SCALE = 1.5f
        /** Base plate height inside the 0.9 m tall volumetric container. */
        const val BASE_Y = -.40f
        const val LAYER_HEIGHT = .01502f * BLOCK_SCALE
        const val SLOT_WIDTH = .0251f * BLOCK_SCALE
        /** Highest point a held block may be lifted to; the container top is at +0.45 m. */
        const val TOP_CLAMP = .42f
        /** Saved rounds carry this geometry tag; a round recorded at another scale is ignored rather than restored. */
        const val SNAPSHOT_TAG = "S150"
        /** Held-block follow: ~50 ms exponential lag feels attached without visible inertia; the cap only guards pose jumps. */
        private const val FOLLOW_TAU = .05f
        private const val MAX_FOLLOW_SPEED = 1.2f
        /** Resting centre height of a block that starts in the given layer. */
        fun restY(layer: Int) = BASE_Y + (.012f + .0075f) * BLOCK_SCALE + layer * LAYER_HEIGHT
    }
    private val S = BLOCK_SCALE
    private val updateScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    val root = Entity()
    private val rules = StackRulesUseCase(S)
    private val themeModels=mutableMapOf<String,Entity>()
    private val blocks = mutableListOf<Entity>()
    private val previous = mutableMapOf<Int, Vector3>()
    private val undoHistory = mutableListOf<String>()
    private val layers = IntArray(54) { it / 3 }
    private val world = PhysicsWorldComponent(simulationClock = SimulationClock(1f/120, .05f, 1f), solverIterations = SolverIterations(32, 8))
    private var held: Int? = null
    private var selected: Int? = null
    private var target = Vector3.ZERO
    private var handle = Vector3.ZERO
    private var origin = Vector3.ZERO
    private var extracted = false
    private var stable = 0f
    private var releasedTime = 0f
    private var elapsed = 0f
    private var tickSeconds = 0f
    private var lastClock = android.os.SystemClock.elapsedRealtime()
    private var highest = 17
    private var topCount = 0
    private var ready = false
    private var live = false
    private var sleeping=true
    private var activeFloor=0
    private var wristStart: Quat? = null
    private var blockStart = Quat()
    private var rotationTarget = Quat()
    private val guides=mutableListOf<Entity>()
    private var captionAnchor: Entity? = null
    private var captionOrigin = Vector3.ZERO
    private val material = PhysicsMaterialResource(.8f,.65f,0f)
    private val shape = ShapeResource.createBox(Vector3(.074f*S,.0145f*S,.024f*S))
    init { root.components.set(world) }
    suspend fun load() {
        val model = Entity.loadSuspend("asset://wood_block.glb")
        themeModels["wood_block"]=model
        WoodTheme.entries.flatMap { it.assets }.distinct().filter { it!="wood_block" }.forEach {
            themeModels[it]=Entity.loadSuspend("asset://themes/$it.glb")
        }
        val base = Entity.loadSuspend("asset://base.glb")
        base.components.get<TransformComponent>()?.setPosition(Vector3(0f,BASE_Y+.006f*S,0f))?.scaleBy(S)
        val collider = Entity().apply {
            components.get<TransformComponent>()!!.setPosition(Vector3(0f,BASE_Y+.006f*S,0f))
            components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(.24f*S,.012f*S,.24f*S))), material))
        }
        root.addChild(base); root.addChild(collider)
        repeat(54) { id ->
            val e = Entity().apply {
                setName("Wood_$id")
                components.set(CollisionComponent(listOf(shape),material))
                components.set(InteractableComponent())
                components.set(HoverEffectComponent())
                addChild(visual(model))
            }
            root.addChild(e); blocks.add(e)
        }
        repeat(3) { slot ->
            val guide=visual(model)
            guide.components.set(OpacityControllerComponent(.18f))
            root.addChild(guide);guides.add(guide)
        }
        reset(); applyTheme(vm.uiState.value.woodTheme); ready = true; vm.ready()
        updateScope.launch {
            var last=android.os.SystemClock.elapsedRealtimeNanos()
            while(isActive) {
                delay(16)
                val now=android.os.SystemClock.elapsedRealtimeNanos()
                update((now-last)/1_000_000_000f);last=now
            }
        }
        Log.i("TowerWood", "SCENE_BOUNDS ${root.getVisualBounds(relativeTo=root,recursive=true,enabledOnly=true)}")
        Log.i("TowerWood", "SCENE_READY blocks=54 scale=$S dimensions=${.075f*S}x${.015f*S}x${.025f*S}m container=volumetric")
    }
    /** Clones a wood model and applies the block scale to the visual only; colliders are sized explicitly. */
    private fun visual(model: Entity) = requireNotNull(model.clone()).also { it.components.get<TransformComponent>()?.scaleBy(S) }
    fun applyTheme(theme:WoodTheme) {
        if(themeModels.isEmpty())return
        blocks.forEachIndexed { id,entity ->
            entity.getChildren().toList().forEach { it.destroy() }
            entity.addChild(visual(themeModels.getValue(theme.assetForBlock(id))))
        }
        vm.setTheme(theme)
    }
    /** Parents the caption attachment in container space and keeps it riding above the current top layer. */
    fun attachCaption(entity: Entity, origin: Vector3) {
        captionAnchor=entity; captionOrigin=origin
        entity.components.get<TransformComponent>()?.setPosition(captionPosition())
    }
    private fun captionPosition() = Vector3(captionOrigin.x, captionOrigin.y+topSurfaceY()+.07f*S, captionOrigin.z+.04f)
    internal fun themeInspection()=vm.uiState.value.woodTheme to blocks.map { it.getChildren().size }
    private fun transform(id: Int) = blocks[id].components.get<TransformComponent>()!!
    private fun body() = RigidBodyComponent(MassProperties(.014f*S*S*S, Vector3(.000000992f,.000007292f,.000006825f)*(S*S*S*S*S)),RigidBodyMode.DYNAMIC).apply {
        linearDamping=.3f; angularDamping=.8f; isAffectedByGravity=true
    }
    fun reset() {
        live=false; sleeping=true; activeFloor=0; held=null; selected=null; highest=17; topCount=0; extracted=false; elapsed=0f; stable=0f; tickSeconds=0f
        blocks.forEachIndexed { id,e ->
            e.components.remove(RigidBodyComponent::class.java); e.components.remove(PhysicsForceComponent::class.java); e.components.remove(PhysicsVelocityComponent::class.java)
            layers[id]=id/3
            val layer=id/3; val slot=id%3-1
            val t=transform(id)
            t.setPosition(Vector3(if(layer%2==0) 0f else slot*SLOT_WIDTH, restY(layer), if(layer%2==0)slot*SLOT_WIDTH else 0f))
            t.setEulerAngles(EulerAngles(yaw=if(layer%2==0)0f else 90f))
            previous[id]=t.position
        }
        freeze(true);updateGuides()
    }
    fun start() { if(!ready)return; undoHistory.clear(); reset(); live=true; freeze(false); vm.event(GameEvent.Start);vm.saveInitial(snapshot());Log.i("TowerWood","ROUND_STARTED") }
    private var frozen = true
    private var frozenPoses=emptyList<Pair<Vector3,Quat>>()
    private var freezeFrames=0
    fun freeze(value: Boolean) {
        frozen=value
        lastClock=android.os.SystemClock.elapsedRealtime()
        if(value) { frozenPoses=blocks.indices.map { (if(it==held)handle else transform(it).position) to Quat(transform(it).quaternion) };freezeFrames=4 }
        blocks.forEachIndexed { id,e ->
            val fixed=value || sleeping || (layers[id]<activeFloor && id!=selected) || id==held
            if(fixed) {
                e.components.remove(RigidBodyComponent::class.java)
                e.components.remove(PhysicsVelocityComponent::class.java)
            } else if(e.components.get<RigidBodyComponent>()==null) e.components.set(body())
        }
    }

    fun pause(lost: Boolean = false) {
        if(!live)return
        held?.let { blocks[it].components.remove(PhysicsForceComponent::class.java) }
        freeze(true); held=null
        vm.event(if(lost)GameEvent.TrackingLost else GameEvent.Pause)
    }
    fun openSettings() { pause();vm.settings() }
    fun requestRestart() { pause();vm.event(GameEvent.RestartPrompt) }
    fun undo() {
        val checkpoint=undoHistory.removeLastOrNull() ?: return
        restore(checkpoint)
        vm.undoRestored(undoHistory.isNotEmpty())
        resume()
        // Resume supplies generic wording; restore the explicit undo feedback.
        vm.undoRestored(undoHistory.isNotEmpty())
        Log.i("TowerWood","UNDO_RESTORED score=${vm.uiState.value.score}")
    }
    fun resume() { held=null; previous.clear(); if(selected==null)freeze(false); vm.event(GameEvent.Resume) }
    private fun idOf(e: Entity?): Int? { var p=e; while(p!=null) { val i=blocks.indexOf(p); if(i>=0)return i; p=p.getParent() }; return null }
    fun grab(entity: Entity?, wrist: Quat? = null) {
        if(!live || vm.uiState.value.phase !in listOf(Phase.PLAYING,Phase.SETTLING))return
        val id=idOf(entity) ?: return
        if(!rules.canGrab(layers[id],highest,selected,id))return
        if(selected==null) {
            val state=vm.uiState.value
            undoHistory.add("${state.score}|${state.seconds}|${snapshot()}")
            vm.undoAvailable(true)
            selected=id; origin=transform(id).position; extracted=false }
        held=id; target=transform(id).position;handle=target; previous[id]=target; stable=0f
        wristStart=wrist;blockStart=transform(id).quaternion;rotationTarget=blockStart
        sleeping=false;activeFloor=layers[id]+1;freeze(false);vm.selected(id)
    }
    fun drag(delta: Vector3) {
        if(held==null)return
        val axis=blockStart.rotateVector(Vector3(1f,0f,0f))
        val constrained=if(extracted)delta else if(abs(axis.x)>.7f)Vector3(delta.x,0f,0f) else Vector3(0f,0f,delta.z)
        target=Vector3((target.x+constrained.x).coerceIn(-.15f*S,.15f*S),(target.y+constrained.y).coerceIn(BASE_Y+.018f*S,TOP_CLAMP),(target.z+constrained.z).coerceIn(-.15f*S,.15f*S))
    }
    fun release() {
        val id=held ?:return
        val e=blocks[id]
        e.components.remove(RigidBodyComponent::class.java)
        e.components.remove(PhysicsVelocityComponent::class.java)
        transform(id).setPosition(handle).setQuaternion(rotationTarget)
        e.components.set(body())
        held=null;freeze(false);releasedTime=0f;stable=0f;vm.released()
    }
    fun rotate(wrist: Quat) { val first=wristStart ?: return; if(held!=null)rotationTarget=wrist*first.conjugate()*blockStart }
    private fun topSurfaceY() = blocks.indices.filter { layers[it]==highest }.maxOfOrNull { transform(it).position.y+.015f*S } ?: restY(highest+1)
    private fun updateGuides() {
        guides.forEachIndexed { slot,e ->
            e.components.get<TransformComponent>()?.apply {
                setPosition(Vector3(if((highest+1)%2==0)0f else (slot-1)*SLOT_WIDTH,topSurfaceY(),if((highest+1)%2==0)(slot-1)*SLOT_WIDTH else 0f))
                setEulerAngles(EulerAngles(yaw=if((highest+1)%2==0)0f else 90f))
            }
            e.enabled=selected!=null && vm.uiState.value.guidesEnabled
        }
    }
    fun update(delta: Float) {
        if(frozen) {
            if(freezeFrames>0) { frozenPoses.forEachIndexed { i,p -> transform(i).setPosition(p.first).setQuaternion(p.second) };freezeFrames-- }
            return
        }
        if(!ready || !live || vm.uiState.value.phase !in listOf(Phase.PLAYING,Phase.HOLDING,Phase.SETTLING))return
        val dt=delta.coerceIn(.001f,.05f)
        val now=android.os.SystemClock.elapsedRealtime()
        val activeSeconds=(now-lastClock)/1000f;lastClock=now
        elapsed+=activeSeconds;tickSeconds+=activeSeconds
        if(tickSeconds>=1f) { val count=tickSeconds.toInt();tickSeconds-=count;vm.tick(count) }
        captionAnchor?.components?.get<TransformComponent>()?.setPosition(captionPosition())
        if(sleeping)return
        blocks.forEachIndexed { id,e ->
            if(layers[id]<activeFloor && id!=selected)return@forEachIndexed
            val t=transform(id); val p=t.position;val last=previous[id] ?:p
            val velocity=(p-last)*(1f/dt)
            if(id==held) {
                // v1.1: critically damped follow instead of the 0.08 m/s crawl, so the block tracks the hand without inertia.
                val step=target-handle
                var move=step*(1f-exp(-dt/FOLLOW_TAU))
                val length=sqrt(move.x*move.x+move.y*move.y+move.z*move.z)
                val cap=MAX_FOLLOW_SPEED*dt
                if(length>cap)move=move*(cap/length)
                handle=handle+move
                // Direct grab follows a bounded handle with a collider; release restores the rigid body.
                // Force servos are unstable when the background emulator delivers sparse pose updates.
                t.setPosition(handle).setQuaternion(rotationTarget)
                if(abs(handle.x-origin.x)>.077f*S || abs(handle.z-origin.z)>.077f*S)extracted=true
            }
            val droppedSelection=id==selected && held==null && releasedTime>.35f && (abs(p.x)>.06f*S || abs(p.z)>.06f*S) && p.y<topSurfaceY()-.022f*S
            if(id!=held && (droppedSelection || (elapsed>1.5f && rules.hasFallen(p.y,BASE_Y,layers[id],(acos(t.quaternion.rotateVector(Vector3(0f,1f,0f)).y.coerceIn(-1f,1f))*180f/PI.toFloat()))))) {
                live=false;freeze(true);vm.finished("木塔倒下了。每一次尝试，都更接近平衡。");Log.i("TowerWood","ROUND_FINISHED block=$id position=$p rotation=${t.eulerAngles} score=${vm.uiState.value.score}");return
            }
            if(id==selected && held==null) {
                releasedTime+=dt
                val movement=sqrt((p-last).x.pow(2)+(p-last).y.pow(2)+(p-last).z.pow(2))
                val targetY=topSurfaceY()
                val axis=t.quaternion.rotateVector(Vector3(1f,0f,0f))
                val rightDirection=if((highest+1)%2==0)abs(axis.x)>.96f else abs(axis.z)>.96f
                val supported=blocks.indices.any { other -> other!=id && layers[other]==highest && transform(other).position.let { abs(it.y-(p.y-.015f*S))<.005f*S && abs(it.x-p.x)<.045f*S && abs(it.z-p.z)<.045f*S } }
                if(rightDirection && supported && rules.isSupported(p.x,p.y,p.z,targetY,(acos(t.quaternion.rotateVector(Vector3(0f,1f,0f)).y.coerceIn(-1f,1f))*180f/PI.toFloat())) && movement<.00025f*S)stable+=dt else stable=0f
                if(stable>=1f) {
                    layers[id]=highest+1; topCount++
                    if(topCount==3) { highest++;topCount=0 }
                    selected=null;stable=0f;vm.scored(snapshot());sleeping=true;freeze(false)
                    if(highest>=28) {live=false;freeze(true);vm.finished("达到本空间的安全高度，挑战完成！")}
                } else if(releasedTime>1.2f) {
                    if(!extracted && (p-origin).let{abs(it.x)<.003f*S&&abs(it.y)<.003f*S&&abs(it.z)<.003f*S}) { selected=null;if(undoHistory.isNotEmpty())undoHistory.removeAt(undoHistory.lastIndex);vm.undoAvailable(undoHistory.isNotEmpty());vm.cancelled();sleeping=true;freeze(false) }
                    else if(vm.uiState.value.phase==Phase.SETTLING)vm.invalid()
                }
            }
            previous[id]=p
        }
        updateGuides()
    }
    private fun snapshot() = "$SNAPSHOT_TAG|$highest,$topCount|" + blocks.mapIndexed { i,_ -> val t=transform(i); "$i,${layers[i]},${t.position.x},${t.position.y},${t.position.z},${t.quaternion.x},${t.quaternion.y},${t.quaternion.z},${t.quaternion.w}" }.joinToString(";")
    fun restore(value: String) {
        try {
            val sections=value.split("|");require(sections.size==5 && sections[2]==SNAPSHOT_TAG)
            val meta=sections[3].split(","); val poses=sections[4].split(";");require(poses.size==54)
            // Parse and validate before mutating the live scene.
            val parsed=poses.map { it.split(",").map(String::toFloat) }
            require(parsed.all { it.size==9 && it.all(Float::isFinite) })
            reset(); highest=meta[0].toInt();topCount=meta[1].toInt()
            parsed.forEachIndexed { id,p ->
                require(p[0].toInt()==id)
                layers[id]=p[1].toInt()
                transform(id).setPosition(Vector3(p[2],p[3],p[4])).setQuaternion(Quat(p[5],p[6],p[7],p[8]))
                blocks[id].components.set(body())
            }
            live=true;freeze(true);vm.restored(sections[0].toInt(),sections[1].toInt());updateGuides()
        } catch(e:Exception) { Log.e("TowerWood","Invalid saved round",e);reset();vm.ready() }
    }
    internal fun grabDiagnostics() = "held=$held selected=$selected target=$target handle=$handle pose=${selected?.let { transform(it).position }} phase=${vm.uiState.value.phase} top=${(51..53).map { transform(it).position }}"
    internal fun inspection() = Triple(ready,vm.uiState.value,blocks.mapIndexed { id,_ -> transform(id).position })
    fun close() {updateScope.cancel();live=false;captionAnchor=null;root.destroy();blocks.clear();themeModels.values.forEach { it.destroy() };themeModels.clear()}
}
