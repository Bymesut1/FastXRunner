package com.fastx.runner

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class UiView(ctx: Context, private val g: Game) : View(ctx) {
    private class Btn(val r: RectF, val enabled: Boolean, val act: () -> Unit)

    private val btns = ArrayList<Btn>()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tf = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val leftR = RectF()
    private val rightR = RectF()
    private val nitR = RectF()
    private val brR = RectF()
    private val pauseR = RectF()
    private var W = 0f
    private var H = 0f

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        W = w.toFloat(); H = h.toFloat()
        val s = H * 0.27f; val m = H * 0.05f
        leftR.set(m, H - m - s, m + s, H - m)
        rightR.set(m * 2 + s, H - m - s, m * 2 + s * 2, H - m)
        val ns = H * 0.30f
        nitR.set(W - m - ns, H - m - ns, W - m, H - m)
        val bs = H * 0.2f
        brR.set(W - m * 2 - ns - bs, H - m - bs, W - m * 2 - ns, H - m)
        pauseR.set(W * 0.895f, H * 0.12f, W * 0.97f, H * 0.23f)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        p.style = Paint.Style.FILL; p.typeface = tf; p.textSize = size; p.color = color; p.textAlign = align
        p.setShadowLayer(size * 0.08f, 0f, size * 0.04f, Color.argb(160, 0, 0, 0))
        c.drawText(s, x, y, p)
        p.clearShadowLayer()
    }

    private fun panel(c: Canvas, r: RectF, a: Int = 150) {
        p.style = Paint.Style.FILL; p.color = Color.argb(a, 8, 10, 18)
        c.drawRoundRect(r, H * 0.02f, H * 0.02f, p)
    }

    private fun bar(c: Canvas, x: Float, y: Float, w: Float, h: Float, f: Float, color: Int) {
        p.style = Paint.Style.FILL; p.color = Color.argb(140, 0, 0, 0)
        c.drawRoundRect(RectF(x, y, x + w, y + h), h / 2, h / 2, p)
        p.color = color
        c.drawRoundRect(RectF(x, y, x + w * f.coerceIn(0f, 1f), y + h), h / 2, h / 2, p)
    }

    private fun button(c: Canvas, r: RectF, label: String, enabled: Boolean = true, hot: Boolean = false,
                       size: Float = H * 0.052f, act: () -> Unit) {
        p.style = Paint.Style.FILL
        p.color = if (!enabled) Color.argb(120, 70, 70, 80) else if (hot) Color.argb(235, 220, 30, 40) else Color.argb(200, 30, 34, 52)
        c.drawRoundRect(r, H * 0.025f, H * 0.025f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = H * 0.004f; p.color = Color.argb(160, 255, 255, 255)
        c.drawRoundRect(r, H * 0.025f, H * 0.025f, p)
        text(c, label, r.centerX(), r.centerY() + size * 0.35f, size, if (enabled) Color.WHITE else Color.GRAY, Paint.Align.CENTER)
        btns.add(Btn(RectF(r), enabled, act))
    }

    private fun r(x: Float, y: Float, w: Float, h: Float) = RectF(x * W, y * H, (x + w) * W, (y + h) * H)

    override fun onDraw(c: Canvas) {
        btns.clear()
        when (g.state) {
            State.MENU -> drawMenu(c)
            State.GARAGE -> drawGarage(c)
            State.COUNTDOWN -> { drawHud(c); drawCountdown(c) }
            State.RACING -> drawHud(c)
            State.RESULT -> drawResult(c)
            State.PAUSED -> { drawHud(c); drawPause(c) }
            State.SETTINGS -> drawSettings(c)
        }
        postInvalidateOnAnimation()
    }

    private fun starsStr(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

    private fun drawMenu(c: Canvas) {
        text(c, "FAST X", W * 0.04f, H * 0.14f, H * 0.12f, Color.rgb(255, 60, 50))
        text(c, "RUNNER", W * 0.04f, H * 0.25f, H * 0.09f, Color.WHITE)
        text(c, "PUAN: ${g.coins}", W * 0.97f, H * 0.08f, H * 0.055f, Color.rgb(255, 205, 60), Paint.Align.RIGHT)
        val pr = r(0.04f, 0.30f, 0.36f, 0.46f)
        panel(c, pr)
        val x = pr.left + W * 0.02f
        text(c, "SEVİYE ${g.level}", x, pr.top + H * 0.075f, H * 0.065f, Color.WHITE)
        text(c, "${g.theme.name} • ${g.theme.place}", x, pr.top + H * 0.125f, H * 0.04f, Color.rgb(180, 200, 255))
        text(c, starsStr(g.stars[g.level]), pr.right - W * 0.02f, pr.top + H * 0.075f, H * 0.06f, Color.rgb(255, 205, 60), Paint.Align.RIGHT)
        text(c, "GÖREVLER", x, pr.top + H * 0.19f, H * 0.04f, Color.rgb(255, 120, 100))
        text(c, "• ${g.target.toInt()} m yarışı bitir", x, pr.top + H * 0.25f, H * 0.04f, Color.WHITE)
        text(c, "• ${g.m2Target()} puan topla", x, pr.top + H * 0.31f, H * 0.04f, Color.WHITE)
        text(c, "• ${g.m3Text()}", x, pr.top + H * 0.37f, H * 0.04f, Color.WHITE)
        button(c, r(0.04f, 0.80f, 0.07f, 0.13f), "<", g.level > 1) { g.changeLevel(-1) }
        button(c, r(0.12f, 0.80f, 0.07f, 0.13f), ">", g.level < g.maxLevel) { g.changeLevel(1) }
        button(c, r(0.21f, 0.80f, 0.20f, 0.13f), "OYNA", true, true, H * 0.06f) { g.startRace() }
        button(c, r(0.43f, 0.80f, 0.14f, 0.13f), "GARAJ") { g.openGarage() }
        button(c, r(0.59f, 0.80f, 0.17f, 0.13f), "AYARLAR", true, false, H * 0.045f) { g.openSettings() }
    }

    private fun drawGarage(c: Canvas) {
        val sp = Game.CARS[g.viewCar]
        val owned = (g.owned and (1 shl g.viewCar)) != 0
        val pr = r(0.03f, 0.05f, 0.42f, 0.90f)
        panel(c, pr)
        val x = pr.left + W * 0.02f
        text(c, sp.name, x, pr.top + H * 0.10f, H * 0.08f, Color.WHITE)
        text(c, if (owned) (if (g.viewCar == g.carSel) "SEÇİLİ ARAÇ" else "SAHİPSİN") else "FİYAT: ${sp.price}",
            x, pr.top + H * 0.16f, H * 0.045f, Color.rgb(255, 205, 60))
        val bw = pr.width() - W * 0.04f
        val names = arrayOf("HIZ", "İVME", "YOL TUTUŞ")
        val vals = floatArrayOf((sp.top - 50f) / 55f, (sp.accel - 10f) / 18f, (sp.handling - 0.8f) / 1.4f)
        for (i in 0..2) {
            val y = pr.top + H * (0.22f + i * 0.07f)
            text(c, names[i], x, y + H * 0.03f, H * 0.035f, Color.LTGRAY)
            bar(c, x + W * 0.1f, y, bw - W * 0.1f, H * 0.03f, vals[i], Color.rgb(255, 70, 60))
        }
        text(c, "YÜKSELTMELER", x, pr.top + H * 0.47f, H * 0.04f, Color.rgb(255, 120, 100))
        val un = arrayOf("MOTOR", "NİTRO", "DİREKSİYON")
        for (i in 0..2) {
            val y = pr.top + H * (0.50f + i * 0.13f)
            text(c, "${un[i]}  ${g.upg[i]}/5", x, y + H * 0.07f, H * 0.042f, Color.WHITE)
            val maxed = g.upg[i] >= 5
            button(c, RectF(pr.right - W * 0.18f, y, pr.right - W * 0.02f, y + H * 0.1f),
                if (maxed) "MAKS" else "${g.upgradeCost(i)}", !maxed && g.coins >= g.upgradeCost(i), false, H * 0.04f) { g.upgrade(i) }
        }
        text(c, "PUAN: ${g.coins}", W * 0.97f, H * 0.18f, H * 0.055f, Color.rgb(255, 205, 60), Paint.Align.RIGHT)
        button(c, r(0.86f, 0.04f, 0.11f, 0.1f), "GERİ", true, false, H * 0.045f) { g.toMenu() }
        button(c, r(0.52f, 0.82f, 0.07f, 0.13f), "<") { g.viewNext(-1) }
        button(c, r(0.60f, 0.82f, 0.07f, 0.13f), ">") { g.viewNext(1) }
        val label = if (owned) "SEÇ" else "SATIN AL"
        val can = if (owned) g.viewCar != g.carSel else g.coins >= sp.price
        button(c, r(0.69f, 0.82f, 0.28f, 0.13f), label, can, can, H * 0.055f) { g.buyOrSelect() }
    }

    private fun drawCountdown(c: Canvas) {
        val n = ceil(g.cd).toInt()
        text(c, if (n >= 1) "$n" else "BAŞLA!", W / 2f, H * 0.42f, H * 0.25f, Color.rgb(255, 80, 60), Paint.Align.CENTER)
    }

    private fun drawHud(c: Canvas) {
        val dist = (g.z - g.z0).coerceAtLeast(0f)
        val tl = max(0, g.timeLeft.toInt())
        val tcol = if (tl < 10) Color.rgb(255, 70, 60) else Color.WHITE
        text(c, "%02d:%02d".format(tl / 60, tl % 60), W / 2f, H * 0.09f, H * 0.085f, tcol, Paint.Align.CENTER)
        bar(c, W * 0.3f, H * 0.115f, W * 0.4f, H * 0.018f, dist / g.target, Color.rgb(80, 200, 255))
        text(c, "${dist.toInt()} / ${g.target.toInt()} m", W / 2f, H * 0.17f, H * 0.035f, Color.LTGRAY, Paint.Align.CENTER)
        text(c, "HASAR", W * 0.03f, H * 0.065f, H * 0.035f, Color.LTGRAY)
        bar(c, W * 0.03f, H * 0.08f, W * 0.2f, H * 0.025f, g.health / 100f, if (g.health < 35f) Color.RED else Color.rgb(90, 220, 90))
        text(c, "PUAN: ${g.raceCoins}", W * 0.97f, H * 0.08f, H * 0.055f, Color.rgb(255, 205, 60), Paint.Align.RIGHT)
        // hız: küçük, ekranın üstünde
        text(c, "${(g.speed * 3.6f).toInt()} KM/H", W * 0.36f, H * 0.085f, H * 0.05f, Color.WHITE, Paint.Align.CENTER)
        if (g.msgT > 0f) {
            val a = (g.msgT.coerceAtMost(0.5f) / 0.5f * 255).toInt()
            text(c, g.msg, W / 2f, H * 0.34f, H * 0.07f, Color.argb(a, 255, 230, 80), Paint.Align.CENTER)
        }
        fun ctl(rc: RectF, label: String, on: Boolean, size: Float) {
            p.style = Paint.Style.FILL
            p.color = if (on) Color.argb(130, 255, 60, 50) else Color.argb(70, 255, 255, 255)
            c.drawRoundRect(rc, H * 0.04f, H * 0.04f, p)
            text(c, label, rc.centerX(), rc.centerY() + size * 0.35f, size, Color.argb(220, 255, 255, 255), Paint.Align.CENTER)
        }
        ctl(leftR, "<", g.left, H * 0.13f)
        ctl(rightR, ">", g.right, H * 0.13f)
        ctl(brR, "FREN", g.braking, H * 0.05f)
        ctl(nitR, "NİTRO", g.nitBtn, H * 0.065f)
        // duraklat butonu (puanın altında)
        p.style = Paint.Style.FILL; p.color = Color.argb(120, 20, 22, 34)
        c.drawRoundRect(pauseR, H * 0.025f, H * 0.025f, p)
        p.color = Color.WHITE
        val cxp = pauseR.centerX(); val cyp = pauseR.centerY()
        val bw = pauseR.width() * 0.12f; val bh = pauseR.height() * 0.5f
        c.drawRect(RectF(cxp - bw * 1.8f, cyp - bh / 2f, cxp - bw * 0.6f, cyp + bh / 2f), p)
        c.drawRect(RectF(cxp + bw * 0.6f, cyp - bh / 2f, cxp + bw * 1.8f, cyp + bh / 2f), p)
    }

    private fun dim(c: Canvas) {
        p.style = Paint.Style.FILL; p.color = Color.argb(150, 0, 0, 0)
        c.drawRect(0f, 0f, W, H, p)
    }

    private fun drawPause(c: Canvas) {
        dim(c)
        val pr = r(0.33f, 0.06f, 0.34f, 0.88f)
        panel(c, pr, 220)
        text(c, "DURAKLATILDI", pr.centerX(), pr.top + H * 0.11f, H * 0.06f, Color.WHITE, Paint.Align.CENTER)
        val bx = pr.left + W * 0.03f
        val bw = pr.width() - W * 0.06f
        fun b(i: Int, label: String, hot: Boolean, act: () -> Unit) {
            val y = pr.top + H * (0.16f + i * 0.18f)
            button(c, RectF(bx, y, bx + bw, y + H * 0.14f), label, true, hot, H * 0.05f, act)
        }
        b(0, "DEVAM ET", true) { g.resume() }
        b(1, "AYARLAR", false) { g.openSettings() }
        b(2, "ANA MENÜ", false) { g.toMenu() }
        b(3, "OYUNDAN ÇIK", false) { g.quitApp() }
    }

    private fun segRow(c: Canvas, label: String, yf: Float, opts: Array<String>, sel: Int, set: (Int) -> Unit) {
        val x0 = W * 0.14f
        text(c, label, x0, yf * H + H * 0.082f, H * 0.045f, Color.WHITE)
        val l = W * 0.34f
        val rt = W * 0.86f
        val gap = W * 0.01f
        val bw = (rt - l - gap * (opts.size - 1)) / opts.size
        for (i in opts.indices) {
            val x = l + i * (bw + gap)
            button(c, RectF(x, yf * H, x + bw, yf * H + H * 0.12f), opts[i], true, i == sel, H * 0.038f) { set(i) }
        }
    }

    private fun drawSettings(c: Canvas) {
        if (!g.settingsFromMenu) dim(c)
        val pr = r(0.10f, 0.05f, 0.80f, 0.90f)
        panel(c, pr, 225)
        text(c, "AYARLAR", W / 2f, H * 0.15f, H * 0.07f, Color.WHITE, Paint.Align.CENTER)
        segRow(c, "HAVA", 0.22f, arrayOf("OTOMATİK", "GÜNEŞLİ", "YAĞMURLU"), g.weather) { g.setWeather(it) }
        segRow(c, "ZEMİN", 0.42f, arrayOf("OTOMATİK", "NORMAL", "ISLAK"), g.ground) { g.setGround(it) }
        segRow(c, "YANSIMA", 0.62f, arrayOf("AÇIK", "KAPALI"), if (g.reflect) 0 else 1) { g.setReflect(it == 0) }
        button(c, r(0.40f, 0.80f, 0.20f, 0.11f), "GERİ", true, false, H * 0.05f) { g.closeSettings() }
    }

    private fun drawResult(c: Canvas) {
        val pr = r(0.25f, 0.08f, 0.50f, 0.84f)
        panel(c, pr, 200)
        val cx = pr.centerX()
        text(c, if (g.resultOk) "YARIŞ TAMAMLANDI!" else "YARIŞ BAŞARISIZ", cx, pr.top + H * 0.10f, H * 0.07f,
            if (g.resultOk) Color.rgb(90, 230, 110) else Color.rgb(255, 80, 70), Paint.Align.CENTER)
        text(c, starsStr(g.resultStars), cx, pr.top + H * 0.20f, H * 0.09f, Color.rgb(255, 205, 60), Paint.Align.CENTER)
        val x = pr.left + W * 0.03f
        val d = (g.z - g.z0).toInt()
        text(c, "Mesafe: $d m", x, pr.top + H * 0.29f, H * 0.045f, Color.WHITE)
        text(c, "Toplanan puan: ${g.raceCoins}", x, pr.top + H * 0.35f, H * 0.045f, Color.WHITE)
        text(c, "${if (g.resultOk) "✓" else "✗"} ${g.target.toInt()} m yarışı bitir", x, pr.top + H * 0.43f, H * 0.04f, Color.LTGRAY)
        text(c, "${if (g.resultM2) "✓" else "✗"} ${g.m2Target()} puan topla", x, pr.top + H * 0.49f, H * 0.04f, Color.LTGRAY)
        text(c, "${if (g.resultM3) "✓" else "✗"} ${g.m3Text()} (${g.m3Value()})", x, pr.top + H * 0.55f, H * 0.04f, Color.LTGRAY)
        text(c, "KAZANÇ: +${g.resultReward}", cx, pr.top + H * 0.65f, H * 0.06f, Color.rgb(255, 205, 60), Paint.Align.CENTER)
        val by = pr.top + H * 0.70f
        val bw = (pr.width() - W * 0.08f) / 3f
        val bx = pr.left + W * 0.02f
        button(c, RectF(bx, by, bx + bw, by + H * 0.11f), "MENÜ", true, false, H * 0.04f) { g.toMenu() }
        button(c, RectF(bx + bw + W * 0.02f, by, bx + bw * 2 + W * 0.02f, by + H * 0.11f), "TEKRAR", true, false, H * 0.04f) { g.startRace() }
        val canNext = g.resultOk && g.level < Game.MAX_LEVEL
        button(c, RectF(bx + bw * 2 + W * 0.04f, by, bx + bw * 3 + W * 0.04f, by + H * 0.11f), "SONRAKİ", canNext, canNext, H * 0.04f) { g.nextLevel() }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (g.state == State.RACING || g.state == State.COUNTDOWN) {
            val am = e.actionMasked
            if (am == MotionEvent.ACTION_DOWN || am == MotionEvent.ACTION_POINTER_DOWN) {
                val ai = e.actionIndex
                if (pauseR.contains(e.getX(ai), e.getY(ai))) { g.pause(); return true }
            }
            var l = false; var rt = false; var n = false; var b = false
            val up = am == MotionEvent.ACTION_UP || am == MotionEvent.ACTION_CANCEL
            if (!up) for (i in 0 until e.pointerCount) {
                if (am == MotionEvent.ACTION_POINTER_UP && i == e.actionIndex) continue
                val x = e.getX(i); val y = e.getY(i)
                if (leftR.contains(x, y)) l = true
                if (rightR.contains(x, y)) rt = true
                if (nitR.contains(x, y)) n = true
                if (brR.contains(x, y)) b = true
            }
            g.left = l; g.right = rt; g.nitBtn = n; g.braking = b
            return true
        }
        if (e.actionMasked == MotionEvent.ACTION_DOWN) {
            val bt = btns.lastOrNull { it.r.contains(e.x, e.y) && it.enabled }
            bt?.act?.invoke()
        }
        return true
    }
}
