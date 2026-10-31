package com.example.bluehive.webview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  The extraction loader's globe (v575, design 5 from the owner's mock-ups).
//
//  A browser is working behind the wait, so the loader says "the web": a
//  wireframe globe turning left to right, the BlueHive bee riding on it, and
//  beams flying from the bee's spot out to servers around the world, a dot
//  travelling each one. Coloured as the old spinning hive was: deep blue
//  #0044FF / #2244FF / #4466FF into purple #9922FF / #6622CC, highlights
//  #88AAFF, #55AAFF, #33DDFF, with its soft blue halo rings.
//
//  TWO HIVES, half a turn apart, so the globe can turn forever and one always
//  faces the viewer: each fades in at the left edge, crosses, and fades out at
//  the right as the other comes round. Each has its own four servers.
//
//  EVERYTHING ANIMATED IS READ IN THE DRAW PHASE (onDrawBehind), so the
//  overlay never recomposes while it turns; the grid's lat/long points are
//  computed once (drawWithCache) and only rotated per frame.
// ─────────────────────────────────────────────────────────────────────────────

/** One full turn of the globe. */
internal const val GLOBE_TURN_MS = 6000

/** A dot's trip along its beam, from the hive to its server. */
internal const val GLOBE_BEAM_MS = 1000

/** The globe's radius and the bee's size. */
internal val GLOBE_RADIUS = 100.dp
internal val GLOBE_BEE_SIZE = 64.dp

private val DEEP = Color(0xFF080420)
private val BLUE2 = Color(0xFF2244FF)
private val BLUE3 = Color(0xFF4466FF)
private val PURPLE = Color(0xFF9922FF)
private val PURPLE2 = Color(0xFF6622CC)
private val HIGHLIGHT = Color(0xFF88AAFF)
private val BEAM_START = Color(0xFF55AAFF)
private val BEAM_DOT = Color(0xFF33DDFF)

private const val TILT_DEG = 20f

/** The hives (lat, lon), half a turn apart; each with its servers. */
private val HIVES = listOf(
    floatArrayOf(20f, -30f) to listOf(floatArrayOf(45f, 40f), floatArrayOf(-15f, 80f), floatArrayOf(35f, 140f), floatArrayOf(-35f, 10f)),
    floatArrayOf(-20f, 150f) to listOf(floatArrayOf(-40f, 220f), floatArrayOf(15f, 260f), floatArrayOf(-30f, 320f), floatArrayOf(40f, 190f)),
)

/** A beam's points, from hive to server. */
private const val BEAM_STEPS = 20

@Composable
internal fun GlobeLoader(bee: ImageBitmap?) {
    val inf = rememberInfiniteTransition(label = "globe")
    val spin = inf.animateFloat(0f, 360f, infiniteRepeatable(tween(GLOBE_TURN_MS, easing = LinearEasing)), label = "globeSpin")
    val beam = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(GLOBE_BEAM_MS, easing = LinearEasing)), label = "globeBeam")

    Spacer(
        modifier = Modifier
            // Larger than the 200 dp slot it sits in, so the halo rings are not cut.
            .requiredSize(GLOBE_RADIUS * 2 + 80.dp)
            .drawWithCache {
                val c = Offset(size.width / 2f, size.height / 2f)
                val r = GLOBE_RADIUS.toPx()
                val grid = gridLines()
                val front = Path()
                val back = Path()
                val beamPath = Path()
                val frontBrush = Brush.horizontalGradient(listOf(BLUE3, PURPLE), c.x - r, c.x + r)
                val backBrush = Brush.horizontalGradient(listOf(BLUE2.copy(alpha = 0.22f), PURPLE2.copy(alpha = 0.22f)), c.x - r, c.x + r)
                val fine = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round)
                val soft = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round)
                val beamStroke = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
                val blueGlow = Brush.radialGradient(listOf(BLUE3.copy(alpha = 0.40f), Color.Transparent), Offset(c.x - r * 0.35f, c.y - r * 0.3f), r * 0.75f)
                val purpleGlow = Brush.radialGradient(listOf(PURPLE2.copy(alpha = 0.38f), Color.Transparent), Offset(c.x + r * 0.4f, c.y + r * 0.38f), r * 0.75f)
                val beeSize = GLOBE_BEE_SIZE.toPx()
                val tilt = TILT_DEG * PI.toFloat() / 180f
                val ct = cos(tilt)
                val st = sin(tilt)
                val out = FloatArray(3)

                fun project(latDeg: Float, lonDeg: Float, rot: Float) {
                    val la = latDeg * PI.toFloat() / 180f
                    val lo = (lonDeg + rot) * PI.toFloat() / 180f
                    val x = cos(la) * sin(lo)
                    val y0 = sin(la)
                    val z0 = cos(la) * cos(lo)
                    out[0] = c.x + r * x
                    out[1] = c.y - r * (y0 * ct - z0 * st)
                    out[2] = y0 * st + z0 * ct
                }

                onDrawBehind {
                    val rot = spin.value
                    val phase0 = beam.value

                    // Halo, sphere and its blue-purple glow.
                    for (i in 0 until 4) {
                        drawCircle(BLUE2.copy(alpha = 0.055f * (4 - i)), r + (i + 1) * 9.dp.toPx(), c)
                    }
                    drawCircle(DEEP.copy(alpha = 0.94f), r, c)
                    drawCircle(blueGlow, r, c)
                    drawCircle(purpleGlow, r, c)

                    // The grid, split into the half facing us and the half behind.
                    front.reset(); back.reset()
                    for (line in grid) {
                        var px = 0f; var py = 0f; var pz = 0f
                        var i = 0
                        while (i < line.size) {
                            project(line[i], line[i + 1], rot)
                            if (i > 0) {
                                val p = if (pz + out[2] > 0f) front else back
                                p.moveTo(px, py); p.lineTo(out[0], out[1])
                            }
                            px = out[0]; py = out[1]; pz = out[2]
                            i += 2
                        }
                    }
                    drawPath(back, backBrush, style = fine)
                    drawPath(front, frontBrush, alpha = 0.22f, style = soft)
                    drawPath(front, frontBrush, alpha = 0.82f, style = fine)
                    drawCircle(HIGHLIGHT.copy(alpha = 0.20f), r, c, style = soft)
                    drawCircle(HIGHLIGHT.copy(alpha = 0.92f), r, c, style = Stroke(1.4.dp.toPx()))

                    // Beams, dots, servers, and the bee, for whichever hive faces us.
                    for ((hive, servers) in HIVES) {
                        project(hive[0], hive[1], rot)
                        val hx = out[0]; val hy = out[1]
                        val seen = smoothstep(0f, 0.3f, out[2])
                        if (seen <= 0f) continue
                        servers.forEachIndexed { k, s ->
                            beamPath.reset()
                            var started = false
                            var sx = 0f; var sy = 0f; var sz = 0f
                            var dx = 0f; var dy = 0f; var dz = -1f
                            val head = ((phase0 + k / servers.size.toFloat()) % 1f * BEAM_STEPS).toInt()
                            for (j in 0..BEAM_STEPS) {
                                val f = j / BEAM_STEPS.toFloat()
                                project(hive[0] + (s[0] - hive[0]) * f, hive[1] + (s[1] - hive[1]) * f, rot)
                                val lift = 1f + 0.25f * sin(PI.toFloat() * f)
                                val x = c.x + (out[0] - c.x) * lift
                                val y = c.y + (out[1] - c.y) * lift
                                if (out[2] > -0.05f) {
                                    if (started) beamPath.lineTo(x, y) else { beamPath.moveTo(x, y); started = true }
                                } else started = false
                                if (j == head) { dx = x; dy = y; dz = out[2] }
                                sx = x; sy = y; sz = out[2]
                            }
                            drawPath(
                                beamPath,
                                Brush.linearGradient(listOf(BEAM_START, PURPLE), Offset(hx, hy), Offset(sx, sy)),
                                alpha = 0.67f * seen,
                                style = beamStroke
                            )
                            if (dz > -0.05f) {
                                drawCircle(BEAM_DOT.copy(alpha = 0.24f * seen), 7.dp.toPx(), Offset(dx, dy))
                                drawCircle(BEAM_DOT.copy(alpha = seen), 3.2.dp.toPx(), Offset(dx, dy))
                            }
                            if (sz > 0f) {
                                drawCircle(PURPLE.copy(alpha = 0.27f * seen), 7.dp.toPx(), Offset(sx, sy))
                                drawCircle(PURPLE.copy(alpha = seen), 3.6.dp.toPx(), Offset(sx, sy))
                            }
                        }
                        drawCircle(BEAM_START.copy(alpha = 0.31f * seen), 9.dp.toPx(), Offset(hx, hy))
                        drawCircle(Color.White.copy(alpha = seen), 4.5.dp.toPx(), Offset(hx, hy))
                        if (bee != null) drawBee(bee, hx, hy - beeSize * 0.62f, beeSize, seen)
                    }
                }
            }
    )
}

private fun DrawScope.drawBee(bee: ImageBitmap, cx: Float, cy: Float, size: Float, alpha: Float) {
    val s = size.roundToInt()
    drawImage(
        image = bee,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bee.width, bee.height),
        dstOffset = IntOffset((cx - size / 2f).roundToInt(), (cy - size / 2f).roundToInt()),
        dstSize = IntSize(s, s),
        alpha = alpha
    )
}

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The grid as polylines of (lat, lon) pairs: meridians every 30° and
 * parallels every 30° up to 60°, sampled every 4°.
 */
internal fun gridLines(): List<FloatArray> {
    val lines = ArrayList<FloatArray>()
    for (lon in 0 until 360 step 30) {
        val pts = ArrayList<Float>()
        for (lat in -90..90 step 4) { pts += lat.toFloat(); pts += lon.toFloat() }
        lines += pts.toFloatArray()
    }
    for (lat in -60..60 step 30) {
        val pts = ArrayList<Float>()
        for (lon in 0..360 step 4) { pts += lat.toFloat(); pts += lon.toFloat() }
        lines += pts.toFloatArray()
    }
    return lines
}
