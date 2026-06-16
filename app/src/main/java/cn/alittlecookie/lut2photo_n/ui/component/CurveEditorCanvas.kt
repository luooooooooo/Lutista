package cn.alittlecookie.lut2photo_n.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import cn.alittlecookie.lut2photo_n.data.model.FloatPoint
import cn.alittlecookie.lut2photo_n.util.CurveInterpolator

/** Curve interpolation delegate — uses shared natural cubic spline. */
fun catmullRomInterpolate(points: List<FloatPoint>, x: Float): Float =
    CurveInterpolator.interpolate(points, x)

/**
 * Photoshop-style curve editor.
 *
 * Architecture: Box with pointerInput (stable) + drawBehind (snapshot redraw).
 * Endpoints (0,0) and (1,1) are always present and draggable along canvas edges.
 */
@Composable
fun CurveEditorCanvas(
    points: List<FloatPoint>,
    onPointsChanged: (List<FloatPoint>) -> Unit,
    onPointsPreview: ((List<FloatPoint>) -> Unit)? = null,
    channelColor: Color,
    modifier: Modifier = Modifier
) {
    val localRef = remember { mutableStateOf(listOf(FloatPoint(0f, 0f), FloatPoint(1f, 1f))) }
    var isDragging = remember { false }

    LaunchedEffect(points) {
        if (!isDragging) {
            val newPts = if (points.isEmpty()) {
                listOf(FloatPoint(0f, 0f), FloatPoint(1f, 1f))
            } else {
                points
            }
            if (localRef.value != newPts) {
                localRef.value = newPts
            }
        }
    }

    fun allPts(): List<FloatPoint> = localRef.value

    fun userPts(): List<FloatPoint> = localRef.value.toList()

    Box(
        modifier = modifier
            .aspectRatio(1f)
            // ── Main gesture ─────────────────────────────────────────
            .pointerInput(Unit) {
                val dragThresholdPx = 8.dp.toPx()
                val nearThreshold = 0.05f

                while (true) {
                    val down = awaitPointerEventScope {
                        awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull()
                    } ?: continue

                    val downX = down.position.x / size.width
                    val downY = 1f - (down.position.y / size.height)

                    var dragIdx = -1
                    var minDist = Float.MAX_VALUE
                    val snapshot = allPts()
                    for (i in snapshot.indices) {
                        val dx = snapshot[i].x - downX
                        val dy = snapshot[i].y - downY
                        val d = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (d < nearThreshold && d < minDist) {
                            minDist = d; dragIdx = i
                        }
                    }

                    var moved = false
                    var dragging = dragIdx >= 0
                    var longPressed = false
                    if (dragging) isDragging = true

                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break

                            if (!change.pressed) {
                                if (dragging) {
                                    isDragging = false; onPointsChanged(userPts())
                                } else if (moved) {
                                    isDragging = false; onPointsChanged(userPts())
                                } else if (!longPressed) {
                                    if (dragIdx < 0) {
                                        val nx = downX.coerceIn(0.01f, 0.99f)
                                        val ny = downY.coerceIn(0.01f, 0.99f)
                                        val list = allPts().toMutableList()
                                        val ins = list.indexOfFirst { it.x > nx }
                                        if (ins >= 0) list.add(ins, FloatPoint(nx, ny))
                                        else list.add(FloatPoint(nx, ny))
                                        localRef.value = list
                                        onPointsChanged(userPts())
                                    }
                                }
                                change.consume(); break
                            }

                            // Long press detection: held >500ms without dragging
                            if (!dragging && !moved && !longPressed && dragIdx < 0) {
                                val elapsed = change.uptimeMillis - down.uptimeMillis
                                if (elapsed > 500) {
                                    longPressed = true
                                    val p = allPts()
                                    val nearIdx = p.indices.minByOrNull { i ->
                                        val dx = p[i].x - downX; val dy = p[i].y - downY; dx * dx + dy * dy
                                    } ?: -1
                                    if (nearIdx >= 0) {
                                        val pt = p[nearIdx]
                                        val dist = kotlin.math.sqrt((pt.x - downX) * (pt.x - downX) + (pt.y - downY) * (pt.y - downY))
                                        if (dist < 0.05f) {
                                            val isEndpoint = nearIdx == 0 || nearIdx == p.size - 1
                                            if (!isEndpoint) {
                                                val list = p.toMutableList(); list.removeAt(nearIdx)
                                                localRef.value = list; onPointsChanged(userPts())
                                            }
                                        }
                                    }
                                }
                            }

                            if (dragging) {
                                var newX = change.position.x / size.width
                                var newY = 1f - (change.position.y / size.height)
                                newX = newX.coerceIn(0f, 1f)
                                newY = newY.coerceIn(0f, 1f)
                                val p = allPts()
                                val isFirst = dragIdx == 0
                                val isLast = dragIdx == p.size - 1
                                if (isFirst) newX = 0f
                                else if (isLast) newX = 1f
                                else {
                                    val prevX = if (dragIdx > 0) p[dragIdx - 1].x + 0.01f else 0f
                                    val nextX = if (dragIdx < p.size - 1) p[dragIdx + 1].x - 0.01f else 1f
                                    newX = newX.coerceIn(prevX, nextX)
                                }
                                val list = p.toMutableList()
                                list[dragIdx] = FloatPoint(newX, newY)
                                localRef.value = list
                                (onPointsPreview ?: onPointsChanged)(userPts())
                                change.consume()
                            } else {
                                val distPx = kotlin.math.sqrt(
                                    (change.position.x - down.position.x).let { it * it } +
                                            (change.position.y - down.position.y).let { it * it }
                                )
                                if (distPx > dragThresholdPx && !moved) {
                                    val nx = (change.position.x / size.width).coerceIn(0.01f, 0.99f)
                                    val ny = (1f - change.position.y / size.height).coerceIn(0.01f, 0.99f)
                                    val list = allPts().toMutableList()
                                    val ins = list.indexOfFirst { it.x > nx }
                                    if (ins >= 0) list.add(ins, FloatPoint(nx, ny))
                                    else list.add(FloatPoint(nx, ny))
                                    localRef.value = list
                                    dragIdx = if (ins >= 0) ins else list.size - 1
                                    dragging = true; isDragging = true; moved = true
                                    onPointsPreview?.invoke(userPts())
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
            // ── Draw ─────────────────────────────────────────────────
            .drawBehind {
                val canvasW = size.width
                val canvasH = size.height
                val currentPts = localRef.value

                // Grid
                val gridColor = Color.Gray.copy(alpha = 0.2f)
                for (f in listOf(0.25f, 0.5f, 0.75f)) {
                    drawLine(gridColor, Offset(0f, f * canvasH), Offset(canvasW, f * canvasH), strokeWidth = 1.dp.toPx())
                    drawLine(gridColor, Offset(f * canvasW, 0f), Offset(f * canvasW, canvasH), strokeWidth = 1.dp.toPx())
                }

                // Identity diagonal
                drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, canvasH), Offset(canvasW, 0f), strokeWidth = 1.dp.toPx())

                // Curve
                val sortedPts = currentPts.sortedBy { it.x }
                val path = Path()
                var started = false
                for (i in 0..200) {
                    val x = i.toFloat() / 200
                    val y = catmullRomInterpolate(sortedPts, x)
                    val cx = x * canvasW; val cy = (1f - y) * canvasH
                    if (!started) { path.moveTo(cx, cy); started = true } else path.lineTo(cx, cy)
                }
                drawPath(path, channelColor, style = Stroke(2.dp.toPx()))

                // Control points
                for (pt in currentPts) {
                    val cx = pt.x * canvasW; val cy = (1f - pt.y) * canvasH
                    drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(channelColor, radius = 5.dp.toPx(), center = Offset(cx, cy))
                }
            }
    )
}
