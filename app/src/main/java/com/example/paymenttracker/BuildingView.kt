package com.example.paymenttracker

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * Фон-сцена: чем больше оплачено, тем выше построен дом.
 * 0% — участок с забором и краном, 100% — готовый комплекс с деревьями.
 */
class BuildingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val argb = ArgbEvaluator()
    private val path = Path()
    private var shown = 0f
    private var animator: ValueAnimator? = null

    /** Сколько места снизу оставить под кнопки (px). */
    var bottomReserve: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    fun setProgress(target: Float, animate: Boolean = true) {
        val t = target.coerceIn(0f, 1f)
        animator?.cancel()
        if (!animate) {
            shown = t
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(shown, t).apply {
            duration = 1600
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    // ---------- helpers ----------
    private fun dp(v: Float): Float = v * resources.displayMetrics.density
    private fun clamp(v: Float): Float = v.coerceIn(0f, 1f)
    private fun c(hex: String): Int = Color.parseColor(hex)
    private fun mix(a: Int, b: Int, t: Float): Int = argb.evaluate(clamp(t), a, b) as Int
    private fun withAlpha(color: Int, a: Float): Int = Color.argb(
        (Color.alpha(color) * clamp(a)).toInt(),
        Color.red(color), Color.green(color), Color.blue(color)
    )

    private fun fill(color: Int) {
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = color
    }

    private fun stroke(color: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.shader = null
        paint.color = color
        paint.strokeWidth = width
    }

    private fun localProgress(p: Float, start: Float, end: Float): Float =
        clamp((p - start) / (end - start))

    // ---------- drawing ----------
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val p = shown
        val groundY = h - max(bottomReserve, dp(104f))
        val floorH = min(h * 0.044f, dp(40f))

        // небо
        val skyTop = mix(c("#56677A"), c("#3D8BFD"), p)
        val skyBottom = mix(c("#B7C3CE"), c("#CDEBFF"), p)
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(0f, 0f, 0f, groundY, skyTop, skyBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, groundY, paint)
        paint.shader = null

        // далёкий город
        val heights = floatArrayOf(.10f, .17f, .12f, .21f, .14f, .19f, .11f, .16f, .13f)
        val cw = w / heights.size
        fill(Color.argb(46, 255, 255, 255))
        for (i in heights.indices) {
            canvas.drawRect(i * cw + dp(2f), groundY - heights[i] * h, (i + 1) * cw - dp(2f), groundY, paint)
        }

        // земля
        fill(c("#37444D"))
        canvas.drawRect(0f, groundY, w, h, paint)
        fill(mix(c("#8D6E4F"), c("#5FAE5B"), p))
        canvas.drawRect(0f, groundY, w, groundY + dp(10f), paint)

        // здания
        val lpLeft = localProgress(p, 0.08f, 0.92f)
        val lpRight = localProgress(p, 0.16f, 1.0f)
        val lpMain = localProgress(p, 0.0f, 1.0f)
        drawBuilding(canvas, w * 0.04f, w * 0.22f, 5, floorH, groundY, lpLeft, c("#DCE6EF"), 2, false)
        drawBuilding(canvas, w * 0.70f, w * 0.25f, 7, floorH, groundY, lpRight, c("#E9D5C7"), 3, false)
        drawBuilding(canvas, w * 0.30f, w * 0.34f, 9, floorH, groundY, lpMain, c("#F4EAD5"), 1, true)

        // деревья
        val ta = clamp((p - 0.85f) / 0.15f)
        if (ta > 0f) {
            drawTree(canvas, w * 0.275f, groundY, floorH, ta)
            drawTree(canvas, w * 0.975f, groundY, floorH, ta)
            drawTree(canvas, w * 0.03f, groundY, floorH, ta)
        }

        // кран
        drawCrane(canvas, w, floorH, groundY, clamp(lpMain / 0.7f), p)
    }

    private fun drawBuilding(
        cv: Canvas, left: Float, bw: Float, floors: Int, floorH: Float, groundY: Float,
        lp: Float, facade: Int, seed: Int, main: Boolean
    ) {
        val s = clamp(lp / 0.7f)          // каркас
        val f = clamp((lp - 0.3f) / 0.6f) // отделка
        val r = clamp((lp - 0.9f) / 0.1f) // крыша и финал
        val foundH = floorH * 0.22f
        val baseY = groundY - foundH

        // фундамент
        fill(withAlpha(c("#6B7078"), clamp(lp * 25f)))
        cv.drawRect(left - dp(4f), baseY, left + bw + dp(4f), groundY, paint)

        val cols = max(2, (bw / dp(30f)).toInt())
        val cellW = bw / cols

        for (i in 0 until floors) {
            val fh = clamp(s * floors - i)
            if (fh <= 0f) break
            val bottom = baseY - i * floorH
            val top = bottom - fh * floorH

            // бетонный каркас
            fill(c("#C9CDD1"))
            cv.drawRect(left, top, left + bw, bottom, paint)
            fill(c("#9AA0A6"))
            for (k in 0..cols) {
                val x = left + k * cellW
                cv.drawRect(x - dp(1.5f), top, x + dp(1.5f), bottom, paint)
            }
            fill(c("#8A9096"))
            cv.drawRect(left - dp(2f), top, left + bw + dp(2f), top + dp(3f), paint)

            // отделка и окна
            val ff = clamp(f * floors - i)
            if (ff > 0f && fh >= 1f) {
                fill(withAlpha(facade, ff))
                cv.drawRect(left, top, left + bw, bottom, paint)
                val ww = cellW * 0.52f
                val wh = floorH * 0.5f
                for (k in 0 until cols) {
                    val wx = left + k * cellW + (cellW - ww) / 2f
                    val wy = top + (floorH - wh) / 2f
                    val lit = r > 0f && ((i * 7 + k * 13 + seed) % 5 == 0)
                    fill(withAlpha(if (lit) c("#FFD54F") else c("#4F9BDB"), ff))
                    cv.drawRect(wx, wy, wx + ww, wy + wh, paint)
                    fill(withAlpha(Color.argb(70, 255, 255, 255), ff))
                    cv.drawRect(wx, wy, wx + ww * 0.45f, wy + wh, paint)
                }
                fill(withAlpha(c("#B8AD97"), ff))
                cv.drawRect(left - dp(2f), top, left + bw + dp(2f), top + dp(3f), paint)
            }
        }

        // дверь
        val doorA = clamp(f * floors * 2f - 0.5f)
        if (doorA > 0f && s >= 1f / floors) {
            val cx = left + bw / 2f
            fill(withAlpha(c("#7A4B2E"), doorA))
            cv.drawRect(cx - dp(8f), baseY - floorH * 0.62f, cx + dp(8f), baseY, paint)
        }

        // крыша
        if (r > 0f) {
            val topY = baseY - floors * floorH
            val capH = dp(5f) * r
            fill(withAlpha(c("#5D6670"), r))
            cv.drawRect(left - dp(3f), topY - capH, left + bw + dp(3f), topY, paint)
            fill(withAlpha(c("#7B8590"), r))
            cv.drawRect(
                left + bw * 0.15f, topY - capH - floorH * 0.45f * r,
                left + bw * 0.40f, topY - capH, paint
            )
            if (main) {
                val ax = left + bw * 0.7f
                val ay = topY - capH
                val ah = floorH * 1.1f * r
                stroke(withAlpha(c("#444B52"), r), dp(1.5f))
                cv.drawLine(ax, ay, ax, ay - ah, paint)
                if (r >= 0.98f) {
                    fill(c("#E53935"))
                    path.reset()
                    path.moveTo(ax, ay - ah)
                    path.lineTo(ax + dp(16f), ay - ah + dp(5f))
                    path.lineTo(ax, ay - ah + dp(10f))
                    path.close()
                    cv.drawPath(path, paint)
                }
            }
        }

        // строительный забор
        val fenceA = 1f - clamp(lp * 2.5f)
        if (fenceA > 0f) {
            val x1 = left - dp(10f)
            val x2 = left + bw + dp(10f)
            val fh = dp(18f)
            stroke(withAlpha(c("#FF9800"), fenceA), dp(2f))
            cv.drawLine(x1, groundY - fh, x2, groundY - fh, paint)
            cv.drawLine(x1, groundY - fh / 2f, x2, groundY - fh / 2f, paint)
            var x = x1
            while (x <= x2) {
                cv.drawLine(x, groundY, x, groundY - fh, paint)
                x += dp(14f)
            }
        }
    }

    private fun drawCrane(cv: Canvas, w: Float, floorH: Float, groundY: Float, mainS: Float, p: Float) {
        val a = 1f - clamp((p - 0.9f) / 0.08f)
        if (a <= 0f) return
        val towerX = w * 0.67f
        val topY = groundY - (mainS * 9f + 2.6f) * floorH
        val halfW = dp(5f)
        val yellow = withAlpha(c("#F5B301"), a)

        // башня
        stroke(yellow, dp(2f))
        cv.drawLine(towerX - halfW, groundY, towerX - halfW, topY, paint)
        cv.drawLine(towerX + halfW, groundY, towerX + halfW, topY, paint)
        val seg = dp(16f)
        path.reset()
        var y = groundY
        var toRight = true
        path.moveTo(towerX - halfW, y)
        while (y - seg > topY) {
            y -= seg
            path.lineTo(if (toRight) towerX + halfW else towerX - halfW, y)
            toRight = !toRight
        }
        cv.drawPath(path, paint)

        // стрела и растяжки
        stroke(yellow, dp(4f))
        cv.drawLine(towerX - w * 0.40f, topY, towerX + w * 0.17f, topY, paint)
        stroke(yellow, dp(1.5f))
        cv.drawLine(towerX, topY - dp(22f), towerX, topY, paint)
        cv.drawLine(towerX, topY - dp(22f), towerX - w * 0.40f, topY, paint)
        cv.drawLine(towerX, topY - dp(22f), towerX + w * 0.17f, topY, paint)

        // противовес и кабина
        fill(withAlpha(c("#6B7078"), a))
        cv.drawRect(towerX + w * 0.12f, topY, towerX + w * 0.17f, topY + dp(14f), paint)
        fill(withAlpha(c("#37444D"), a))
        cv.drawRect(towerX - dp(14f), topY + dp(3f), towerX - dp(2f), topY + dp(15f), paint)

        // трос и груз
        val cx = towerX - w * 0.22f
        val hookY = topY + (groundY - topY) * 0.35f
        stroke(withAlpha(c("#37444D"), a), dp(1.2f))
        cv.drawLine(cx, topY, cx, hookY, paint)
        fill(withAlpha(c("#B5651D"), a))
        cv.drawRect(cx - dp(12f), hookY, cx + dp(12f), hookY + dp(10f), paint)
    }

    private fun drawTree(cv: Canvas, cx: Float, groundY: Float, floorH: Float, t: Float) {
        fill(c("#6D4C41"))
        cv.drawRect(cx - dp(2f), groundY - t * floorH * 0.9f, cx + dp(2f), groundY, paint)
        fill(c("#3E8E41"))
        cv.drawCircle(cx, groundY - t * floorH * 1.1f, t * floorH * 0.55f, paint)
        fill(c("#4CAF50"))
        cv.drawCircle(cx - t * dp(5f), groundY - t * floorH * 1.25f, t * floorH * 0.38f, paint)
    }
}
