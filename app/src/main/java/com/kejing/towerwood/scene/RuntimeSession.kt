package com.kejing.towerwood.scene
import java.lang.ref.WeakReference
/** Weak scene handle for in-process integration tests; exposes no external IPC. */
internal object RuntimeSession { var scene: WeakReference<TowerScene>? = null }
