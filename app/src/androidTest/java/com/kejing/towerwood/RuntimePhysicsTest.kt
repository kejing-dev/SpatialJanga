package com.kejing.towerwood

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kejing.towerwood.scene.RuntimeSession
import com.kejing.towerwood.scene.TowerScene
import com.kejing.towerwood.ui.game.Phase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class RuntimePhysicsTest {
    private val S=TowerScene.BLOCK_SCALE
    private val scene get() = RuntimeSession.scene!!.get()!!
    @Test fun towerLoadsSettlesAndPausesWithoutFalling() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var ready=false
        var attempts=0
        while(!ready && attempts++<80) {
            instrumentation.runOnMainSync { ready=RuntimeSession.scene?.get()?.inspection()?.first==true }
            if(!ready)Thread.sleep(250)
        }
        assertTrue("Scene must finish loading in emulator",ready)
        // The instrumentation launch path can rebuild the SpatialView content once the container gains focus,
        // which recreates GameScreen and its TowerScene; always resolve the live instance instead of caching it.
        Thread.sleep(3500)
        instrumentation.runOnMainSync { scene.start() }
        Thread.sleep(5000)
        // The emulator may deliver a delayed initial focus-loss notification after launch.
        instrumentation.runOnMainSync { if(scene.inspection().second.phase==Phase.PAUSED)scene.resume() }
        Thread.sleep(5000)
        captureRoots("playing")
        instrumentation.runOnMainSync {
            val (_,state,poses)=scene.inspection()
            assertEquals("A new tower must remain upright without input",Phase.PLAYING,state.phase)
            assertEquals(54,poses.size)
            assertTrue("Physics bodies must remain above base",poses.all { it.y>TowerScene.BASE_Y })
            assertEquals(0,state.score)
            assertTrue("Tower remains tall",poses.maxOf { it.y }>TowerScene.restY(17)-.01f)
            android.util.Log.i("TowerWoodValidation","IDLE_STABLE_5_SECONDS blocks=${poses.size} minY=${poses.minOf { it.y }} maxY=${poses.maxOf { it.y }}")
            scene.pause()
        }
        var before=listOf<com.pico.spatial.core.math.Vector3>()
        var elapsed=0
        instrumentation.runOnMainSync { before=scene.inspection().third;elapsed=scene.inspection().second.seconds }
        Thread.sleep(750)
        instrumentation.runOnMainSync {
            val (_,state,poses)=scene.inspection()
            assertEquals(Phase.PAUSED,state.phase)
            assertEquals(elapsed,state.seconds)
            assertTrue("Pause freezes each physics transform",poses.zip(before).all { (a,b) -> abs(a.y-b.y)<.0001f && abs(a.x-b.x)<.0001f })
            scene.resume()
            val candidate=scene.root.getChildren().first { it.getName()=="Wood_49" }
            scene.grab(candidate)
            scene.drag(com.pico.spatial.core.math.Vector3(.10f*S,0f,0f))
        }
        waitForPose { it.x > .08f*S }
        instrumentation.runOnMainSync {
            val (_,state,poses)=scene.inspection()
            android.util.Log.i("TowerWoodValidation","EXTRACTION phase=${state.phase} block49=${poses[49]}")
            assertEquals(Phase.HOLDING,state.phase)
            assertTrue("Force based grab moves upper block",poses[49].x>.06f*S)
            scene.pause()
        }
        captureRoots("paused")
        var extractionX=0f
        instrumentation.runOnMainSync {
            scene.resume()
            extractionX=scene.inspection().third[49].x
            scene.grab(scene.root.getChildren().first { it.getName()=="Wood_49" })
            scene.drag(com.pico.spatial.core.math.Vector3(0f,.075f*S,0f))
        }
        waitForPose { it.y > TowerScene.restY(16)+.06f*S }
        instrumentation.runOnMainSync { scene.drag(com.pico.spatial.core.math.Vector3(-extractionX,0f,0f)) }
        waitForPose { abs(it.x)<.004f*S }
        instrumentation.runOnMainSync { scene.drag(com.pico.spatial.core.math.Vector3(0f,-.045f*S,0f)) }
        waitForPose { it.y<TowerScene.restY(17)+.02f*S }
        instrumentation.runOnMainSync { scene.release() }
        Thread.sleep(6000)
        instrumentation.runOnMainSync {
            val (_,state,poses)=scene.inspection()
            android.util.Log.i("TowerWoodValidation","PLACEMENT phase=${state.phase} score=${state.score} block49=${poses[49]} ${scene.grabDiagnostics()}")
            assertEquals("Legal physical placement scores once",1,state.score)
            assertEquals(Phase.PLAYING,state.phase)
        }
        captureRoots("scored")
        instrumentation.runOnMainSync {
            scene.undo()
            assertEquals(0,scene.inspection().second.score)
            assertNull(scene.inspection().second.selected)
            assertEquals(Phase.PLAYING,scene.inspection().second.phase)
            scene.start()
            scene.grab(scene.root.getChildren().first { it.getName()=="Wood_49" })
            scene.drag(com.pico.spatial.core.math.Vector3(.14f*S,0f,0f))
        }
        waitForPose { it.x>.13f*S }
        instrumentation.runOnMainSync { scene.release() }
        Thread.sleep(4500)
        instrumentation.runOnMainSync {
            assertEquals("Dropped block must expose recovery actions",Phase.RESULT,scene.inspection().second.phase)
            assertTrue(scene.inspection().second.canUndo)
        }
        captureRoots("dropped")
        instrumentation.runOnMainSync { scene.undo() }
        Thread.sleep(500)
        instrumentation.runOnMainSync {
            val (_,state,poses)=scene.inspection()
            assertEquals(Phase.PLAYING,state.phase)
            assertNull(state.selected)
            assertEquals(0,state.score)
            assertTrue(abs(poses[49].x)<.001f)
            assertTrue(abs(poses[49].y-TowerScene.restY(16))<.001f)
            scene.grab(scene.root.getChildren().first { it.getName()=="Wood_49" })
            scene.requestRestart()
            assertEquals(Phase.RESTART,scene.inspection().second.phase)
            scene.start()
            assertEquals(Phase.PLAYING,scene.inspection().second.phase)
            assertFalse(scene.inspection().second.canUndo)
            assertNull(scene.inspection().second.selected)
            assertEquals(0,scene.inspection().second.score)
            assertEquals(54,scene.inspection().third.size)
            android.util.Log.i("TowerWoodValidation","DROP_UNDO_RESTART_PASSED")
        }
        captureRoots("reset")
        instrumentation.runOnMainSync {
            scene.openSettings()
            val beforeThemes=scene.inspection().third
            val score=scene.inspection().second.score
            for(theme in com.kejing.towerwood.ui.game.WoodTheme.entries) {
                scene.applyTheme(theme)
                assertEquals(theme,scene.themeInspection().first)
                assertTrue(scene.themeInspection().second.all { it==1 })
                assertEquals(beforeThemes,scene.inspection().third)
                assertEquals(score,scene.inspection().second.score)
            }
            assertEquals(Phase.SETTINGS,scene.inspection().second.phase)
            android.util.Log.i("TowerWoodValidation","FOUR_THEMES_SWITCHED_POSES_PRESERVED")
        }
        Thread.sleep(800)
        captureRoots("themes")
    }
    @Test fun themePickerPreviews() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var ready=false
        var attempts=0
        while(!ready && attempts++<100) {
            instrumentation.runOnMainSync { ready=RuntimeSession.scene?.get()?.inspection()?.first==true }
            if(!ready)Thread.sleep(250)
        }
        assertTrue(ready)
        instrumentation.runOnMainSync {
            val scene=RuntimeSession.scene!!.get()!!
            scene.openSettings()
            scene.applyTheme(com.kejing.towerwood.ui.game.WoodTheme.PASTEL)
            assertEquals(com.kejing.towerwood.ui.game.WoodTheme.PASTEL,scene.themeInspection().first)
        }
        Thread.sleep(1000)
        captureRoots("themes_final")
    }
    private fun waitForPose(predicate:(com.pico.spatial.core.math.Vector3)->Boolean) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        var reached=false
        repeat(150) {
            if(!reached) {
                instrumentation.runOnMainSync { reached=predicate(RuntimeSession.scene!!.get()!!.inspection().third[49]) }
                if(!reached)Thread.sleep(100)
            }
        }
        var diagnostic=""
        instrumentation.runOnMainSync { diagnostic=RuntimeSession.scene!!.get()!!.grabDiagnostics();android.util.Log.i("TowerWoodValidation",diagnostic) }
        assertTrue("Physics target must be reached within 15 seconds: $diagnostic",reached)
    }
    private fun captureRoots(label:String) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEachIndexed { index,view ->
                if(view.width>0 && view.height>0) {
                    val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
                    view.draw(android.graphics.Canvas(bitmap))
                    val values=android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"${label}_panel_$index.png")
                        put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
                        put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/TowerWood")
                    }
                    val resolver=instrumentation.targetContext.contentResolver
                    val uri=resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
                    resolver.openOutputStream(uri)!!.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle()
                }
            }
        }
    }
}
