// Overlay plumbing, like WidgetController: every WindowManager/binder call is fail-soft (catch-all),
// and the object holds the whole bubble lifecycle in one place.
@file:Suppress("TooManyFunctions", "TooGenericExceptionCaught", "LongMethod", "CyclomaticComplexMethod")

package com.bydmate.app.acbubble

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.bydmate.app.R
import com.bydmate.app.data.local.LocalePreferences
import com.bydmate.app.data.remote.DiParsData
import com.bydmate.app.data.autoservice.SentinelDecoder
import com.bydmate.app.data.nativestack.FidAddresses
import com.bydmate.app.data.push.FidPushChannel
import com.bydmate.app.data.vehicle.BatchReadItem
import com.bydmate.app.data.vehicle.HelperClient
import com.bydmate.app.data.vehicle.VehicleApi
import com.bydmate.app.data.vehicle.VehicleApiImpl
import com.bydmate.app.service.TrackingService
import com.bydmate.app.ui.overlay.OverlayLifecycleOwner
import com.bydmate.app.ui.widget.DragGestureLogic
import com.bydmate.app.ui.widget.WidgetController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Hilt bridge for [AcBubbleController], a plain object outside the graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AcBubbleEntryPoint {
    fun vehicleApi(): VehicleApi
    fun helperClient(): HelperClient
    fun fidPushChannel(): FidPushChannel
}

/**
 * Owns the floating AC bubble overlay: one window holding the bubble (or the edge tab, or the
 * pinned strip's grip) and, when opened, the control strip next to it. The window follows the
 * screen: a rotation re-clamps it, and a strip too wide for the screen is scaled down to fit.
 * Lifecycle mirrors [WidgetController] (attached from
 * TrackingService / app background, detached while BYDMate itself is on screen) and it obeys
 * the same visibility rules (camera, car settings, own overlays, optional YouTube/home-only).
 */
object AcBubbleController {

    private const val TAG = "AcBubble"
    private const val DRAG_THRESHOLD_DP = 8
    private const val STEP_DEBOUNCE_MS = 400L
    private const val TICK_MS = 1_000L
    /** After a tap's write, when to read the car back. */
    private const val READBACK_AFTER_WRITE_MS = 1_000L
    /** After a custom button's writes, when to check which of them the car took. */
    private const val PRESET_CHECK_AFTER_MS = 1_200L
    /** Lets Compose redraw at a new size before the window is measured again. */
    private const val RELAYOUT_DELAY_MS = 80L
    /** Strip scale changes smaller than this are not worth a relayout. */
    private const val FIT_EPSILON = 0.01f
    /** Own read of the climate state while the strip is open: same cadence as the service poll. */
    private const val OWN_READ_EVERY_MS = 3_000L
    /** A snapshot older than this (service stopped, car asleep) shows as unknown, never as fact. */
    private const val STALE_MS = 20_000L
    private const val TX_READ = 5
    /** The polled climate fields plus the three the bubble reads on its own (FidMap names). */
    private val POLL_FIELDS = listOf("acStatus", "acCtrlMode", "acCirc", "acDefrostFront", "fanLevel", "acTemp", "acWindMode")
    private val OWN_FIELDS = listOf("acCompressor", "acDefrostRear", "acVentilation")
    private val CLIMATE_FIELDS = POLL_FIELDS + OWN_FIELDS
    private val FIELD_TO_OVERLAY = mapOf(
        "acCompressor" to AcStateOverlay.Field.COMPRESSOR,
        "acDefrostRear" to AcStateOverlay.Field.DEFROST_REAR,
        "acVentilation" to AcStateOverlay.Field.FAN_ONLY,
    )

    @Volatile private var appForegrounded = false
    @Volatile private var previewMode = false

    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var bubbleView: ComposeView? = null
    private var stripView: ComposeView? = null
    private var lifecycle: OverlayLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var scope: CoroutineScope? = null
    private var appCtx: Context? = null

    // Bubble top-left in screen px; the window origin moves away from it while the strip opens
    // to the left, so the bubble position is kept separately.
    private var bubbleX = 0
    private var bubbleY = 0

    private val overlay = AcStateOverlay()
    // Two sources of the car's state: the service poll (plus push) and the bubble's own batch
    // read; the fresher one wins, and either is ignored once it is older than STALE_MS.
    private var pollState = AcState()
    private var pollAtMs = 0L
    private var ownValues: Map<String, Int?> = emptyMap()
    private var ownAtMs = 0L
    /** Fields whose read fid this firmware answers "no such feature" for. */
    private val unsupported = mutableSetOf<AcStateOverlay.Field>()
    private val shown = mutableStateOf(AcState())
    // expanded = what the user asked for (drives the animation); stripLaidOut = the strip view is
    // in the window. They differ only while the strip is fading out.
    private val expanded = mutableStateOf(false)
    private var stripLaidOut = false
    private val opensLeft = mutableStateOf(false)
    private val pressed = mutableStateOf(false)
    /** What stands in for the bubble while the strip is closed. */
    private enum class Mode { BUBBLE, PINNED, DOCKED }
    private val mode = mutableStateOf(Mode.BUBBLE)
    /** "Always show the panel": the strip never folds and the bubble turns into a drag grip. */
    private val isPinned get() = mode.value == Mode.PINNED
    /** The edge tab: hangs from a side of the screen and opens the strip inward. */
    private val isDocked get() = mode.value == Mode.DOCKED
    /** Which screen edge the tab hangs from. */
    private val dockRight = mutableStateOf(true)
    private val alphaState = mutableStateOf(1f)
    private val scaleState = mutableStateOf(1f)
    private val theme = mutableStateOf(AcTheme.DEFAULT)
    /** The strip's own scale: the user's, or smaller when the screen is too narrow for it. */
    private val stripScale = mutableStateOf(1f)
    /** The custom buttons shown on the strip, by slot; empty hides their row. */
    private val presets = mutableStateOf<List<IndexedValue<AcPreset>>>(emptyList())
    /** What each lit custom button changed, so a second tap can put it back. In memory only. */
    private val presetSnapshots = mutableMapOf<Int, AcState>()
    private var callbacks: ComponentCallbacks? = null
    private var lastTouchMs = 0L
    private val stepJobs = mutableMapOf<String, Job>()
    private var collapseJob: Job? = null

    /**
     * The bubble is an add-on to the app: whatever goes wrong inside it is logged and takes the
     * bubble down, never the app. Every entry point from outside and every UI callback runs here.
     */
    private inline fun guard(where: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$where failed, bubble removed: ${e.javaClass.simpleName}: ${e.message}", e)
            try { detachUnsafe("error:$where") } catch (e2: Exception) { Log.e(TAG, "cleanup failed: ${e2.message}") }
        }
    }

    @Synchronized
    fun attach(context: Context, reason: String = "unspecified") = guard("attach") { attachUnsafe(context, reason) }

    @Synchronized
    fun detach(reason: String = "unspecified") = guard("detach") { detachUnsafe(reason) }

    private fun attachUnsafe(context: Context, reason: String) {
        if (appForegrounded && !previewMode) return
        if (root != null) return
        val ctx = context.applicationContext
        val prefs = AcBubblePreferences(ctx)
        if (!prefs.isEnabled()) return
        if (!android.provider.Settings.canDrawOverlays(ctx)) return
        appCtx = ctx
        val viewCtx = localizedContext(ctx)
        val windowManager = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm = windowManager
        val metrics = screen()
        scaleState.value = prefs.getScale()
        stripScale.value = scaleState.value
        alphaState.value = prefs.getAlpha()
        theme.value = AcTheme.of(prefs.getThemeKey())
        mode.value = modeOf(prefs)
        dockRight.value = prefs.isDockRight()
        presets.value = shownPresets(prefs.getPresets())
        loadPosition(prefs, metrics)

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }
        params = p

        val owner = OverlayLifecycleOwner().also { it.onCreate() }
        lifecycle = owner

        val bubble = ComposeView(viewCtx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                when (mode.value) {
                    Mode.PINNED -> AcGrip(theme.value, alphaState.value, stripScale.value, stripHeightDp())
                    Mode.DOCKED -> AcTab(shown.value, theme.value, alphaState.value, scaleState.value, stripHeightDp(),
                        dockRight.value, pressed.value)
                    Mode.BUBBLE -> AcBubble(shown.value, theme.value, alphaState.value, scaleState.value, pressed.value)
                }
            }
            setOnTouchListener(BubbleTouchListener(prefs))
        }
        val strip = ComposeView(viewCtx).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                AcStrip(shown.value, theme.value, alphaState.value, stripScale.value, expanded.value, opensLeft.value,
                    presets.value, ::onAction, ::onPreset)
            }
            visibility = View.GONE
        }
        val container = LinearLayout(viewCtx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            addView(bubble)
            // A tap anywhere else on screen folds the strip back.
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_OUTSIDE && !isPinned) guard("outside") { collapse() }
                false
            }
        }
        bubbleView = bubble
        stripView = strip
        root = container
        expanded.value = false
        stripLaidOut = false

        try {
            windowManager.addView(container, p)
        } catch (e: Exception) {
            Log.e(TAG, "addView failed: ${e.message}")
            detachUnsafe("addview_failed")
            return
        }
        Log.i(TAG, "attached ($reason)")
        // A rotation (or any screen-size change) re-fits the window to the new screen.
        val cb = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = guard("rotate") { onScreenChanged() }
            override fun onLowMemory() = Unit
        }
        ctx.registerComponentCallbacks(cb)
        callbacks = cb
        startSubscriptions(ctx, prefs)
        // The window is on screen only after its first layout pass: open the pinned strip then.
        if (isPinned) container.post { guard("pinned_open") { expand() } }
    }

    private fun shownPresets(all: List<AcPreset>): List<IndexedValue<AcPreset>> =
        all.withIndex().filter { it.value.enabled && !it.value.isEmpty }

    /** The strip's height in dp at scale 1: taller while custom buttons are shown. */
    private fun stripHeightDp(): Int = acStripHeightDp(presets.value.isNotEmpty())

    private fun modeOf(prefs: AcBubblePreferences): Mode = modeOf(prefs.isPinned(), prefs.isDocked())
    private fun modeOf(pinned: Boolean, docked: Boolean): Mode = when {
        docked -> Mode.DOCKED
        pinned -> Mode.PINNED
        else -> Mode.BUBBLE
    }

    /** The saved spot of the current mode, or its default; a docked tab always ends up on its edge. */
    private fun loadPosition(prefs: AcBubblePreferences, metrics: DisplayMetrics) {
        if (isDocked) {
            bubbleY = prefs.getDockY().takeIf { it != AcBubblePreferences.NO_POSITION } ?: metrics.heightPixels / 3
        } else if (prefs.hasPosition()) {
            bubbleX = prefs.getX()
            bubbleY = prefs.getY()
        } else {
            // Default: right edge, a third of the way down — clear of the BYD status bar.
            bubbleX = metrics.widthPixels - bubbleW(metrics) - dp(metrics, 16)
            bubbleY = metrics.heightPixels / 3
        }
        clampBubble(metrics)
    }

    /** The screen turned or changed size: keep the bubble on it and reopen the strip to fit. */
    private fun onScreenChanged() {
        val r = root ?: return
        clampBubble(screen())
        if (stripLaidOut) {
            val open = expanded.value
            removeStrip()
            if (open || isPinned) r.postDelayed({ guard("rotate_open") { expand() } }, RELAYOUT_DELAY_MS)
        } else {
            placeWindow()
        }
    }

    private fun detachUnsafe(reason: String) {
        val was = root != null
        scope?.cancel()
        scope = null
        stepJobs.clear()
        collapseJob = null
        root?.let { r -> try { wm?.removeView(r) } catch (e: Exception) { Log.w(TAG, "removeView: ${e.message}") } }
        callbacks?.let { cb -> try { appCtx?.unregisterComponentCallbacks(cb) } catch (_: Exception) {} }
        callbacks = null
        bubbleView?.setOnTouchListener(null)
        lifecycle?.onDestroy()
        root = null
        bubbleView = null
        stripView = null
        lifecycle = null
        params = null
        wm = null
        expanded.value = false
        stripLaidOut = false
        if (was) Log.i(TAG, "detached ($reason)")
    }

    @Synchronized
    fun setAppForegrounded(foreground: Boolean) = guard("foreground") {
        appForegrounded = foreground
        if (foreground && !previewMode) detachUnsafe("app_foreground")
    }

    /** The app language changed: the bubble comes back in the new one (at once, or when the app leaves). */
    @Synchronized
    fun relocale(context: Context) = guard("relocale") {
        if (root == null) return@guard
        detachUnsafe("relocale")
        attachUnsafe(context, "relocale")
    }

    /** Settings sliders show the bubble over BYDMate while dragged, like the stats widget. */
    @Synchronized
    fun setPreviewMode(context: Context, active: Boolean) = guard("preview") {
        previewMode = active
        if (active) attachUnsafe(context, "preview") else if (appForegrounded) detachUnsafe("preview_end")
    }

    // --- Data + visibility ---

    private fun startSubscriptions(ctx: Context, prefs: AcBubblePreferences) {
        // An exception escaping one of the bubble's coroutines must not reach the app's main thread.
        val handler = CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "bubble coroutine failed, bubble removed: ${e.message}", e)
            Handler(Looper.getMainLooper()).post { guard("coroutine") { detachUnsafe("error:coroutine") } }
        }
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main + handler)
        scope = s

        s.launch {
            TrackingService.lastData.collect { data: DiParsData? ->
                pollState = AcState.from(data)
                pollAtMs = if (data == null) 0L else TrackingService.lastDataAtMs
                refresh()
            }
        }
        // Push: the three own fields are subscribed like every FidMap entry, so a change made on
        // the stock screen lands here the moment the car reports it.
        s.launch {
            try {
                val channel = entryPoint(ctx).fidPushChannel()
                channel.events.collect { e ->
                    val field = OWN_FIELDS.firstOrNull { FidAddresses.fid(it) == e.fid } ?: return@collect
                    ownValues = ownValues + (field to SentinelDecoder.decodeInt(e.intValue))
                    ownAtMs = System.currentTimeMillis()
                    refresh()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "push subscription failed: ${e.message}")
            }
        }
        s.launch { prefs.alphaFlow().collect { alphaState.value = it } }
        s.launch { prefs.themeKeyFlow().collect { theme.value = AcTheme.of(it) } }
        s.launch {
            combine(prefs.pinnedFlow(), prefs.dockedFlow()) { p, d -> modeOf(p, d) }.collect { m ->
                if (mode.value == m) return@collect
                // The bubble, the grip and the tab differ in size: rebuild the layout around the new one.
                removeStrip()
                mode.value = m
                dockRight.value = prefs.isDockRight()
                loadPosition(prefs, screen())
                placeWindow()
                if (isPinned) expand()
            }
        }
        s.launch {
            prefs.scaleFlow().collect {
                if (scaleState.value != it) {
                    scaleState.value = it
                    stripScale.value = it
                    clampBubble(screen())
                    placeWindow()
                    if (stripLaidOut) {
                        val open = expanded.value
                        removeStrip()
                        if (open || isPinned) root?.postDelayed({ guard("rescale") { expand() } }, RELAYOUT_DELAY_MS)
                    }
                }
            }
        }
        s.launch {
            prefs.presetsFlow().collect { all ->
                val next = shownPresets(all)
                if (next == presets.value) return@collect
                // An edited button starts fresh: its old "before" no longer belongs to it.
                val old = presets.value
                presetSnapshots.keys.retainAll { slot -> next.firstOrNull { it.index == slot } == old.firstOrNull { it.index == slot } }
                presets.value = next
                // The row of custom buttons changes the strip's height: lay it out again.
                if (next.isEmpty() != old.isEmpty()) {
                    clampBubble(screen())
                    placeWindow()
                    if (stripLaidOut) {
                        val open = expanded.value
                        removeStrip()
                        if (open || isPinned) root?.postDelayed({ guard("presets") { expand() } }, RELAYOUT_DELAY_MS)
                    }
                }
            }
        }
        // Ticks expire held taps and fold the strip after the idle timeout.
        s.launch {
            while (isActive) {
                delay(TICK_MS)
                refresh()
                val idleMs = SystemClock.uptimeMillis() - lastTouchMs
                if (expanded.value && !isPinned && idleMs >= prefs.getAutoCloseSec() * 1_000L) collapse()
            }
        }
        // While the strip is open, read the whole climate state ourselves every few seconds: the
        // three fields the poll lacks, and everything else when the service is not polling.
        s.launch {
            while (isActive) {
                if (stripLaidOut) readClimate(ctx)
                delay(OWN_READ_EVERY_MS)
            }
        }
        s.launch {
            val hide = combine(
                combine(
                    TrackingService.cameraActive,
                    TrackingService.youtubeForeground,
                    prefs.hideOnYoutubeFlow(),
                    TrackingService.foregroundPackage,
                    prefs.homeOnlyFlow(),
                ) { cam, yt, hideYt, fg, homeOnly ->
                    val home = if (homeOnly) WidgetController.homePackagesForWidget(ctx) else emptySet()
                    WidgetController.hideReason(cam, yt, hideYt, fg, emptySet(), homeOnly, home)
                },
                WidgetController.suppressReasons,
            ) { reason, suppressed -> reason ?: suppressed.firstOrNull() }
            hide.collect { reason ->
                if (reason != null) removeStrip()
                root?.visibility = if (reason != null) View.GONE else View.VISIBLE
                if (reason == null && isPinned) expand()
            }
        }
    }

    /**
     * One batch read of every climate fid through the daemon (one binder round trip), at the
     * addresses resolved for this firmware. A fid the car answers "no feature" for is remembered
     * as unsupported; any other sentinel or a failed read leaves the value unknown.
     */
    private suspend fun readClimate(ctx: Context) {
        val addresses = CLIMATE_FIELDS.map { FidAddresses.of(it) }
        val raw: List<Int?> = try {
            withContext(Dispatchers.IO) {
                val helper = entryPoint(ctx).helperClient()
                helper.readBatch(addresses.map { BatchReadItem(TX_READ, it.device, it.fid) })
                    ?.map { (status, value) -> value.takeIf { status == 0 } }
                    // Older daemons have no batch: fall back to one read per fid.
                    ?: addresses.map { helper.read(it.device, it.fid)?.toInt() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "climate read failed: ${e.message}")
            return
        }
        val values = mutableMapOf<String, Int?>()
        CLIMATE_FIELDS.forEachIndexed { i, field ->
            val v = raw[i]
            FIELD_TO_OVERLAY[field]?.let { f ->
                if (v == SentinelDecoder.FEATURE_LINK_ERROR) unsupported += f else if (v != null) unsupported -= f
            }
            values[field] = v?.let(SentinelDecoder::decodeInt)
        }
        ownValues = values
        ownAtMs = System.currentTimeMillis()
        refresh()
    }

    /** The car as best known right now: the fresher of the two sources, unknown when both are stale. */
    private fun carNow(): AcState {
        val now = System.currentTimeMillis()
        val poll = pollState.takeIf { now - pollAtMs < STALE_MS }
        val own = AcState.fromFields(ownValues).takeIf { now - ownAtMs < STALE_MS }
        val base = when {
            own != null && (poll == null || ownAtMs >= pollAtMs) -> own
            poll != null -> poll
            else -> AcState()
        }
        return base.copy(compressor = own?.compressor, defrostRear = own?.defrostRear, fanOnly = own?.fanOnly)
    }

    private fun refresh() {
        shown.value = overlay.merge(carNow(), SystemClock.uptimeMillis(), unsupported)
    }

    // --- Taps ---

    private fun onAction(action: AcAction) = guard("tap") { onActionUnsafe(action) }

    private fun onActionUnsafe(action: AcAction) {
        lastTouchMs = SystemClock.uptimeMillis()
        val target = AcCommands.target(action, shown.value)
        overlay.request(action, target, lastTouchMs)
        refresh()
        val write = AcCommands.write(action, target)
        val s = scope ?: return
        when (action) {
            // Quick repeated taps on a stepper send only the final value.
            is AcAction.FanStep, is AcAction.TempStep -> {
                val key = action::class.simpleName.orEmpty()
                stepJobs[key]?.cancel()
                stepJobs[key] = s.launch {
                    delay(STEP_DEBOUNCE_MS)
                    // Sent on its own job: the next tap cancels the wait, never a write under way.
                    s.launch { send(write) }
                }
            }
            else -> s.launch { send(write) }
        }
    }

    /** A custom button: the first tap sends its values, a tap while it is lit puts back the old ones. */
    private fun onPreset(slot: Int) = guard("preset") {
        lastTouchMs = SystemClock.uptimeMillis()
        val preset = presets.value.firstOrNull { it.index == slot }?.value ?: return@guard
        val current = shown.value
        val plan = if (AcPresets.matches(preset, current)) {
            AcPresets.revert(preset, presetSnapshots.remove(slot), current)
        } else {
            presetSnapshots[slot] = current
            AcPresets.apply(preset, current)
        }
        if (plan == null) {
            toast(R.string.ac_preset_nothing_to_undo)
            return@guard
        }
        overlay.request(plan.fields, plan.target, lastTouchMs)
        refresh()
        scope?.launch { sendAll(plan.steps.map { it.second }, plan) }
    }

    private suspend fun send(write: AcWrite) = sendAll(listOf(write))

    /**
     * Writes back to back, with no pause; one message if any of them fails, one read-back at the
     * end. For a custom button's [plan], the car is read once its writes are out and whatever it
     * did not take (a value dropped while the climate was still waking up) is sent once more.
     */
    private suspend fun sendAll(writes: List<AcWrite>, plan: AcPlan? = null) {
        val ctx = appCtx ?: return
        var failed = writes.count { !writeOne(ctx, it) } > 0
        if (plan != null) {
            delay(PRESET_CHECK_AFTER_MS)
            readClimate(ctx)
            val missed = AcPresets.missed(plan, carNow())
            if (missed.isNotEmpty()) {
                Log.i(TAG, "preset: sending again $missed")
                if (missed.count { !writeOne(ctx, it) } > 0) failed = true
            }
        }
        if (failed) toast(R.string.ac_bubble_write_failed)
        // Read the car back shortly after, so the strip shows what it really did (a refused
        // value, or a mode the car changed along with ours) without waiting for the next poll.
        scope?.launch {
            delay(READBACK_AFTER_WRITE_MS)
            readClimate(ctx)
        }
    }

    private suspend fun writeOne(ctx: Context, write: AcWrite): Boolean {
        // A write that has left for the car finishes its audit row and verdict even if the bubble
        // is torn down meanwhile.
        val result = try {
            withContext(Dispatchers.IO + NonCancellable) {
                val api = entryPoint(ctx).vehicleApi()
                when (write) {
                    is AcWrite.Command -> api.dispatch(write.text)
                    // The compressor has no translator phrase; VehicleApiImpl.doWrite is the same
                    // allowlist + audit path dispatch() ends in.
                    is AcWrite.Direct -> (api as? VehicleApiImpl)?.doWrite(write.action, write.value)
                        ?: Result.failure(IllegalStateException("vehicle api unavailable"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.onFailure { Log.w(TAG, "write $write failed: ${it.message}") }.onSuccess { Log.i(TAG, "write $write ok") }
        return result.isSuccess
    }

    /** Shown in the app's language, not the head unit's: the plain app context follows the system locale. */
    private fun toast(resId: Int) {
        val ctx = root?.context ?: appCtx?.let(::localizedContext) ?: return
        val text = ctx.getString(resId)
        Handler(Looper.getMainLooper()).post {
            try { Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show() } catch (_: Exception) {}
        }
    }

    // --- Expand / collapse ---

    @Synchronized
    private fun toggle() = if (expanded.value) collapse() else expand()

    @Synchronized
    private fun expand() {
        if (expanded.value) return
        val r = root ?: return
        val strip = stripView ?: return
        val bubble = bubbleView ?: return
        val metrics = screen()
        lastTouchMs = SystemClock.uptimeMillis()
        if (!stripLaidOut) {
            // Open inward from the tab's edge, or toward the side of the bubble with more room.
            val left = if (isDocked) dockRight.value else AcBubbleLayout.opensLeft(bubbleX, bubbleW(metrics), metrics.widthPixels)
            opensLeft.value = left
            // Only the strip moves: removing the bubble mid-gesture would cancel the touch on it.
            if (strip.parent != null) r.removeView(strip)
            if (bubble.parent == null) r.addView(bubble)
            // A small gap between the bubble and the strip, on the side they meet.
            val gap = dp(metrics, (AC_STRIP_GAP_DP * stripScale.value).toInt())
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { if (left) rightMargin = gap else leftMargin = gap }
            r.addView(strip, if (left) 0 else 1, lp)
            strip.visibility = View.VISIBLE
            stripLaidOut = true
            placeWindow()
        }
        // Set after the window has its full size, so the strip animates in from the first frame.
        collapseJob?.cancel()
        expanded.value = true
        appCtx?.let { ctx -> scope?.launch { readClimate(ctx) } }
    }

    /** Animates the strip away; the window shrinks once the animation is over. */
    @Synchronized
    private fun collapse() {
        if (!expanded.value) return
        expanded.value = false
        val s = scope ?: return removeStrip()
        collapseJob?.cancel()
        collapseJob = s.launch {
            delay(AC_STRIP_ANIM_MS.toLong())
            if (!expanded.value) removeStrip()
        }
    }

    /** Takes the strip out of the window right away (drag start, hide, end of the fade). */
    @Synchronized
    private fun removeStrip() {
        expanded.value = false
        if (!stripLaidOut) return
        stripLaidOut = false
        stripView?.visibility = View.GONE
        placeWindow()
    }

    /**
     * Anchors the window so the bubble stays put: from the left edge normally, from the right
     * edge while the strip opens to the left (the window then grows leftward on its own).
     */
    @Synchronized
    private fun placeWindow() {
        val p = params ?: return
        val r = root ?: return
        val metrics = screen()
        if (stripLaidOut && !r.isAttachedToWindow) {
            // Measuring a ComposeView before its window exists throws: try again once it does.
            r.post { guard("place") { placeWindow() } }
            return
        }
        if (stripLaidOut) {
            // A WRAP_CONTENT overlay is first measured at the dialog width (~580dp) and Compose
            // never reports "too small", so the strip would be cut off: size the window exactly.
            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            r.measure(unspecified, unspecified)
            p.width = r.measuredWidth
            p.height = r.measuredHeight
            // A strip wider than the room beside the bubble (a rotated screen, a big size setting)
            // is drawn smaller; Compose needs a frame to relayout, then the window is sized again.
            // Measured at the current strip scale; the fit is judged from the user's own scale, so
            // a strip shrunk for a narrow screen grows back once there is room again.
            val naturalPx = ((stripView?.measuredWidth ?: 0) * scaleState.value / stripScale.value).toInt()
            val room = metrics.widthPixels - bubbleW(metrics) - dp(metrics, (AC_STRIP_GAP_DP * stripScale.value).toInt())
            val fit = AcBubbleLayout.fitScale(scaleState.value, naturalPx, room)
            if (fit < stripScale.value - FIT_EPSILON || fit > stripScale.value + FIT_EPSILON) {
                stripScale.value = fit
                r.postDelayed({ guard("refit") { if (stripLaidOut) placeWindow() } }, RELAYOUT_DELAY_MS)
            }
        } else {
            p.width = WindowManager.LayoutParams.WRAP_CONTENT
            p.height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        if (stripLaidOut && opensLeft.value) {
            p.gravity = Gravity.TOP or Gravity.END
            p.x = metrics.widthPixels - (bubbleX + bubbleW(metrics))
        } else {
            p.gravity = Gravity.TOP or Gravity.START
            p.x = bubbleX
        }
        // Whatever the side, the whole window stays on the screen.
        if (stripLaidOut) p.x = AcBubbleLayout.clampWindowX(p.x, p.width, metrics.widthPixels)
        // The strip is taller than the bubble; centre it on the bubble's row.
        val stripPx = dp(metrics, (stripHeightDp() * stripScale.value).toInt())
        p.y = if (stripLaidOut) {
            (bubbleY - (stripPx - bubbleH(metrics)) / 2).coerceIn(0, (metrics.heightPixels - stripPx).coerceAtLeast(0))
        } else {
            bubbleY
        }
        try { wm?.updateViewLayout(r, p) } catch (e: Exception) { Log.w(TAG, "updateViewLayout: ${e.message}") }
    }

    // --- Touch on the bubble: tap opens, drag moves ---

    private class BubbleTouchListener(
        private val prefs: AcBubblePreferences,
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            // Read per event: the display can change while the bubble lives.
            guard("touch") { handle(e, screen()) }
            return true
        }

        private fun handle(e: MotionEvent, metrics: DisplayMetrics) {
            val threshold = dp(metrics, DRAG_THRESHOLD_DP)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = bubbleX; startY = bubbleY
                    dragging = false
                    pressed.value = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (!dragging && !DragGestureLogic.isTap(0, 0, dx, dy, threshold)) {
                        dragging = true
                        pressed.value = false
                        // The pinned strip travels with its grip; the bubble's folds away.
                        if (!isPinned) removeStrip()
                    }
                    if (dragging) {
                        bubbleX = startX + dx
                        bubbleY = startY + dy
                        clampBubble(metrics, dragging = true)
                        placeWindow()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pressed.value = false
                    val wasDrag = dragging
                    dragging = false
                    if (wasDrag && isDocked) {
                        // The tab snaps back to the nearer edge wherever it is let go.
                        dockRight.value = AcBubbleLayout.docksRight(bubbleX, bubbleW(metrics), metrics.widthPixels)
                        clampBubble(metrics)
                        placeWindow()
                        prefs.saveDock(dockRight.value, bubbleY)
                    } else if (wasDrag) {
                        prefs.savePosition(bubbleX, bubbleY)
                        // Re-pick the side the pinned strip opens to at its new place, once this
                        // touch event is fully delivered.
                        if (isPinned) root?.post { guard("repin") { removeStrip(); expand() } }
                    } else if (e.action == MotionEvent.ACTION_UP && !isPinned) {
                        toggle()
                    }
                }
            }
        }
    }

    // --- Helpers ---

    /** Keeps the bubble on screen; a docked tab is also pulled flush to its edge (not while dragged). */
    private fun clampBubble(metrics: DisplayMetrics, dragging: Boolean = false) {
        val (x, y) = DragGestureLogic.clampToScreen(
            x = bubbleX, y = bubbleY, widgetWidth = bubbleW(metrics), widgetHeight = bubbleH(metrics),
            screenWidth = metrics.widthPixels, screenHeight = metrics.heightPixels,
        )
        bubbleX = if (isDocked && !dragging) AcBubbleLayout.dockX(dockRight.value, bubbleW(metrics), metrics.widthPixels) else x
        bubbleY = y
    }

    private fun bubbleW(metrics: DisplayMetrics): Int = when (mode.value) {
        Mode.PINNED -> dp(metrics, (GRIP_W_DP * stripScale.value).toInt())
        Mode.DOCKED -> dp(metrics, (AC_TAB_W_DP * scaleState.value).toInt())
        Mode.BUBBLE -> dp(metrics, (AC_BUBBLE_W_DP * scaleState.value).toInt())
    }
    private fun bubbleH(metrics: DisplayMetrics): Int = when (mode.value) {
        Mode.PINNED -> dp(metrics, (stripHeightDp() * stripScale.value).toInt())
        Mode.DOCKED -> dp(metrics, (stripHeightDp() * scaleState.value).toInt())
        Mode.BUBBLE -> dp(metrics, (AC_BUBBLE_H_DP * scaleState.value).toInt())
    }

    private fun dp(metrics: DisplayMetrics, dp: Int): Int = (dp * metrics.density).toInt()

    /**
     * The screen as it is right now. The app's own displayMetrics can lag behind a rotation and
     * shrink in split screen, so the overlay measures the display itself.
     */
    private fun screen(): DisplayMetrics {
        val ctx = appCtx ?: return android.content.res.Resources.getSystem().displayMetrics
        val display = (ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return ctx.resources.displayMetrics
        @Suppress("DEPRECATION")
        return DisplayMetrics().also { display.getRealMetrics(it) }
    }

    private fun entryPoint(ctx: Context): AcBubbleEntryPoint =
        EntryPointAccessors.fromApplication(ctx, AcBubbleEntryPoint::class.java)

    private fun localizedContext(appCtx: Context): Context {
        val lang = LocalePreferences(appCtx).getLanguage() ?: return appCtx
        val config = Configuration(appCtx.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
        return appCtx.createConfigurationContext(config)
    }
}
