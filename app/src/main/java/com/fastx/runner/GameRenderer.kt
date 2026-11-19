package com.fastx.runner

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Build
import android.os.PowerManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

private fun hsh(k: Int, s: Int): Float {
    var x = k * 374761393 + s * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

class Particles(val cap: Int) {
    val x = FloatArray(cap); val y = FloatArray(cap); val z = FloatArray(cap)
    val vx = FloatArray(cap); val vy = FloatArray(cap); val vz = FloatArray(cap)
    val life = FloatArray(cap); val maxL = FloatArray(cap); val size = FloatArray(cap); val grow = FloatArray(cap)
    val r = FloatArray(cap); val g = FloatArray(cap); val b = FloatArray(cap); val a = FloatArray(cap)
    val grav = FloatArray(cap); val stretch = FloatArray(cap); val kind = IntArray(cap)
    var n = 0

    fun add(k: Int, px: Float, py: Float, pz: Float, ax: Float, ay: Float, az: Float, lf: Float, sz: Float, gr: Float,
            cr: Float, cg: Float, cb: Float, ca: Float, gv: Float, st: Float) {
        if (n >= cap) return
        val i = n++
        kind[i] = k; x[i] = px; y[i] = py; z[i] = pz; vx[i] = ax; vy[i] = ay; vz[i] = az
        life[i] = lf; maxL[i] = lf; size[i] = sz; grow[i] = gr
        r[i] = cr; g[i] = cg; b[i] = cb; a[i] = ca; grav[i] = gv; stretch[i] = st
    }

    fun update(dt: Float) {
        var i = 0
        while (i < n) {
            life[i] -= dt
            if (life[i] <= 0f) { copy(n - 1, i); n--; continue }
            vy[i] += grav[i] * dt
            x[i] += vx[i] * dt; y[i] += vy[i] * dt; z[i] += vz[i] * dt
            i++
        }
    }

    private fun copy(s: Int, d: Int) {
        if (s == d) return
        x[d] = x[s]; y[d] = y[s]; z[d] = z[s]; vx[d] = vx[s]; vy[d] = vy[s]; vz[d] = vz[s]
        life[d] = life[s]; maxL[d] = maxL[s]; size[d] = size[s]; grow[d] = grow[s]
        r[d] = r[s]; g[d] = g[s]; b[d] = b[s]; a[d] = a[s]; grav[d] = grav[s]; stretch[d] = stretch[s]; kind[d] = kind[s]
    }
}

class GameRenderer(private val ctx: Context, private val g: Game) : GLSurfaceView.Renderer {
    private lateinit var pObj: Prog
    private lateinit var pGround: Prog
    private lateinit var pSky: Prog
    private lateinit var pPost: Prog
    private lateinit var pPart: Prog
    private lateinit var cube: GpuMesh
    private lateinit var pyr: GpuMesh
    private lateinit var cone: GpuMesh
    private lateinit var wheel: GpuMesh
    private var carHi: GpuMesh? = null
    private var carHiIdx = -1
    private val carLo = arrayOfNulls<GpuMesh>(2)
    private var bound: GpuMesh? = null

    private var roadVbo = 0
    private var roadIbo = 0
    private var roadIdxCount = 0
    private var partVbo = 0
    private val SL = 140
    private val LATS = floatArrayOf(-420f, -200f, -120f, -80f, -55f, -38f, -26f, -18f, -13f, -8f, 8f, 13f, 18f, 26f, 38f, 55f, 80f, 120f, 200f, 420f)
    private val LP = LATS.size
    private lateinit var roadFb: FloatBuffer
    private lateinit var partFb: FloatBuffer
    private val partArr = FloatArray(1400 * 6 * 9)

    private var fbo = 0
    private var colorTex = 0
    private var depthRb = 0
    private var rw = 0
    private var rh = 0
    private var fboScale = 1f
    private var floatOk = true
    private var sw = 0
    private var sh = 0
    private var scale = 0.9f
    private var cap = 1f
    private var ema = 0.0167f
    private var frames = 0
    private var calm = 0

    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val carM = FloatArray(16)
    private val wm = FloatArray(16)
    private var ex = 0f; private var ey = 0f; private var ez = 0f
    private var tx = 0f; private var ty = 0f; private var tz = 0f
    private var fovCur = 58f
    private var sunD = floatArrayOf(0f, 1f, 0f)
    private var lastT = 0L
    private var tAcc = 0f
    private val parts = Particles(900)
    private var lastCrash = 0
    private var lastCoin = 0
    private var wheelSpin = 0f
    private var flashV = 0f
    private var camR = floatArrayOf(1f, 0f, 0f)
    private var camU = floatArrayOf(0f, 1f, 0f)
    private var camF = floatArrayOf(0f, 0f, 1f)
    private var carX = 0f; private var carY = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        floatOk = ext.contains("GL_EXT_color_buffer_float") || ext.contains("GL_EXT_color_buffer_half_float")
        pObj = Prog(Shaders.OBJ_VS, Shaders.OBJ_FS)
        pGround = Prog(Shaders.GROUND_VS, Shaders.GROUND_FS)
        pSky = Prog(Shaders.FULL_VS, Shaders.SKY_FS)
        pPost = Prog(Shaders.FULL_VS, Shaders.POST_FS)
        pPart = Prog(Shaders.PART_VS, Shaders.PART_FS)
        cube = MeshGen.upload(MeshGen.cube())
        pyr = MeshGen.upload(MeshGen.cone(4, false))
        cone = MeshGen.upload(MeshGen.cone(10, true))
        wheel = MeshGen.upload(MeshGen.wheel())
        carLo[0] = MeshGen.upload(MeshGen.car(Game.TSEDAN, 48, 56))
        carLo[1] = MeshGen.upload(MeshGen.car(Game.TSUV, 48, 56))
        carHi = null; carHiIdx = -1; bound = null
        fbo = 0; colorTex = 0; depthRb = 0; fboScale = 0f

        val ids = IntArray(3)
        GLES30.glGenBuffers(3, ids, 0)
        roadVbo = ids[0]; roadIbo = ids[1]; partVbo = ids[2]
        val idx = ArrayList<Short>()
        for (s in 0 until SL - 1) for (p in 0 until LP - 1) {
            val i0 = s * LP + p; val i1 = i0 + 1; val i2 = i0 + LP; val i3 = i2 + 1
            idx.add(i0.toShort()); idx.add(i2.toShort()); idx.add(i1.toShort())
            idx.add(i1.toShort()); idx.add(i2.toShort()); idx.add(i3.toShort())
        }
        roadIdxCount = idx.size
        val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(idx.toShortArray()); ib.position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, roadIbo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, ib, GLES30.GL_STATIC_DRAW)
        roadFb = ByteBuffer.allocateDirect(SL * LP * 4 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        partFb = ByteBuffer.allocateDirect(partArr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        lastT = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        sw = width; sh = height
        fboScale = 0f
    }

    // ---------- kalite / FBO ----------
    private fun adaptQuality() {
        frames++
        if (frames % 120 == 0) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            cap = 1f
            if (pm.isPowerSaveMode) cap = 0.7f
            if (Build.VERSION.SDK_INT >= 29) {
                val ts = pm.currentThermalStatus
                if (ts >= PowerManager.THERMAL_STATUS_SEVERE) cap = min(cap, 0.55f)
                else if (ts >= PowerManager.THERMAL_STATUS_MODERATE) cap = min(cap, 0.75f)
            }
        }
        if (frames % 20 == 0) {
            if (ema > 1.25f / 60f) { scale -= 0.06f; calm = 0 }
            else if (ema < 1.06f / 60f) { calm++; if (calm > 6) { scale += 0.03f; calm = 0 } }
            else calm = 0
        }
        scale = scale.coerceIn(0.5f, 1f)
        if (scale > cap) scale = cap
    }

    private fun ensureFbo() {
        if (abs(scale - fboScale) < 0.04f && fbo != 0) return
        fboScale = scale
        val w = max(64, (sw * scale).toInt() and 1.inv())
        val h = max(64, (sh * scale).toInt() and 1.inv())
        makeFbo(w, h)
    }

    private fun makeFbo(w: Int, h: Int) {
        if (fbo != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(colorTex), 0)
            GLES30.glDeleteRenderbuffers(1, intArrayOf(depthRb), 0)
        }
        rw = w; rh = h
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0); colorTex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTex)
        val levels = (floor(log2(max(w, h).toFloat())) + 1).toInt()
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, levels, if (floatOk) GLES30.GL_RGBA16F else GLES30.GL_RGBA8, w, h)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glGenRenderbuffers(1, ids, 0); depthRb = ids[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depthRb)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, w, h)
        GLES30.glGenFramebuffers(1, ids, 0); fbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, colorTex, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depthRb)
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE && floatOk) {
            floatOk = false
            makeFbo(w, h)
        }
    }

    // ---------- yardımcılar ----------
    private fun en(n: Int) {
        for (i in 0..3) if (i < n) GLES30.glEnableVertexAttribArray(i) else GLES30.glDisableVertexAttribArray(i)
    }

    private fun bind(m: GpuMesh) {
        if (bound === m) return
        bound = m
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, m.vbo)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, m.ibo)
        en(3)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 28, 0)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 28, 12)
        GLES30.glVertexAttribPointer(2, 1, GLES30.GL_FLOAT, false, 28, 24)
    }

    private fun common(p: Prog) {
        val th = g.theme
        p.use()
        GLES30.glUniform3f(p.u("u_cam"), ex, ey, ez)
        GLES30.glUniform3f(p.u("u_sun"), sunD[0], sunD[1], sunD[2])
        GLES30.glUniform3fv(p.u("u_sunCol"), 1, th.sunCol, 0)
        GLES30.glUniform3fv(p.u("u_skyTop"), 1, th.skyTop, 0)
        GLES30.glUniform3fv(p.u("u_skyHor"), 1, th.skyHor, 0)
        GLES30.glUniform3fv(p.u("u_gnd"), 1, th.gnd, 0)
        GLES30.glUniform3f(p.u("u_fogCol"), th.skyHor[0] * 0.9f, th.skyHor[1] * 0.9f, th.skyHor[2] * 0.9f)
        GLES30.glUniform1f(p.u("u_fog"), th.fog)
        GLES30.glUniform1f(p.u("u_night"), th.night)
        GLES30.glUniform1f(p.u("u_flash"), flashV)
        GLES30.glUniform1f(p.u("u_time"), tAcc)
        GLES30.glUniform1f(p.u("u_wet"), if (th.rain) 1f else th.wet)
        val z = g.z
        val slope = (g.cx(z + 2f) - g.cx(z - 2f)) / 4f
        GLES30.glUniform3f(p.u("u_hlPos"), carX, carY + 0.7f, z + 1.5f)
        GLES30.glUniform3f(p.u("u_hlDir"), slope, -0.12f, 1f)
        GLES30.glUniform1f(p.u("u_hlOn"), th.night)
        GLES30.glUniformMatrix4fv(p.u("u_vp"), 1, false, vp, 0)
    }

    private fun objDraw(m: GpuMesh, r: Float, gg: Float, b: Float, er: Float, eg: Float, eb: Float, win: Float, mode: Float) {
        bind(m)
        GLES30.glUniformMatrix4fv(pObj.u("u_model"), 1, false, model, 0)
        GLES30.glUniform3f(pObj.u("u_color"), r, gg, b)
        GLES30.glUniform3f(pObj.u("u_emis"), er, eg, eb)
        GLES30.glUniform1f(pObj.u("u_win"), win)
        GLES30.glUniform1f(pObj.u("u_mode"), mode)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, m.count, GLES30.GL_UNSIGNED_SHORT, 0)
    }

    private fun box(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, yaw: Float,
                    r: Float, gg: Float, b: Float, er: Float = 0f, eg: Float = 0f, eb: Float = 0f, win: Float = 0f) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y + sy * 0.5f, z)
        if (yaw != 0f) Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        objDraw(cube, r, gg, b, er, eg, eb, win, 0f)
    }

    private fun coneAt(m: GpuMesh, x: Float, y: Float, z: Float, w: Float, h: Float, yaw: Float, r: Float, gg: Float, b: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        if (yaw != 0f) Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, w, h, w)
        objDraw(m, r, gg, b, 0f, 0f, 0f, 0f, 0f)
    }

    // ---------- kare ----------
    override fun onDrawFrame(gl: GL10?) {
        if (sw == 0) return
        val now = System.nanoTime()
        var dt = (now - lastT) / 1e9f
        lastT = now
        if (dt > 0.1f) dt = 0.1f
        tAcc += dt
        ema = ema * 0.95f + dt * 0.05f
        g.update(dt)
        adaptQuality()
        ensureMeshes()
        ensureFbo()
        wheelSpin += g.speed * dt / 0.33f * 57.2958f
        wheelSpin %= 360f
        flashV = g.flash * (0.55f + 0.45f * sin(tAcc * 70f))
        setupCamera(dt)
        emitParticles(dt)
        parts.update(dt)
        renderScene()
        post()
    }

    private fun ensureMeshes() {
        val ci = g.shownCar
        if (ci != carHiIdx) {
            val old = carHi
            if (old != null) GLES30.glDeleteBuffers(2, intArrayOf(old.vbo, old.ibo), 0)
            carHi = MeshGen.upload(MeshGen.car(Game.CARS[ci], 190, 240))
            carHiIdx = ci
            bound = null
        }
    }

    private fun setupCamera(dt: Float) {
        val th = g.theme
        val sdn = sqrt(th.sunDir[0] * th.sunDir[0] + th.sunDir[1] * th.sunDir[1] + th.sunDir[2] * th.sunDir[2])
        sunD = floatArrayOf(th.sunDir[0] / sdn, th.sunDir[1] / sdn, th.sunDir[2] / sdn)
        val z = g.z
        val dxdz = (g.cx(z + 2f) - g.cx(z - 2f)) / 4f
        carX = g.cx(z) + g.ox
        carY = g.cy(z)
        val sn = (g.speed / 90f).coerceIn(0f, 1.3f)
        var fovT: Float
        if (g.state == State.MENU || g.state == State.GARAGE) {
            val a = g.garageAngle
            val d = if (g.state == State.GARAGE) 6.4f else 7.8f
            ex = carX + sin(a) * d; ey = carY + 1.5f; ez = z + cos(a) * d
            tx = carX; ty = carY + 0.55f; tz = z
            fovT = 42f
        } else {
            val back = 6.8f + sn * 1.8f
            val cz = z - back
            val sh = g.shake * 0.5f + sn * 0.03f + (if (g.nitroOn) 0.06f else 0f)
            ex = g.cx(cz) + g.ox * 0.8f + (Random.nextFloat() - 0.5f) * sh
            ey = g.cy(cz) + 2.3f + sn * 0.4f + (Random.nextFloat() - 0.5f) * sh
            ez = cz
            tx = g.cx(z + 25f) + g.ox * 0.9f; ty = g.cy(z + 25f) + 1.0f; tz = z + 25f
            fovT = 56f + sn * 14f + (if (g.nitroOn) 8f else 0f)
        }
        fovCur += (fovT - fovCur) * min(1f, dt * 4f)
        var fx = tx - ex; var fy = ty - ey; var fz = tz - ez
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl; fy /= fl; fz /= fl
        var rx = fy * 0f - fz * 1f; var ry = fz * 0f - fx * 0f; var rz = fx * 1f - fy * 0f
        val rl = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(1e-6f)
        rx /= rl; ry /= rl; rz /= rl
        val ux = ry * fz - rz * fy; val uy = rz * fx - rx * fz; val uz = rx * fy - ry * fx
        if (g.state == State.MENU) {
            val s = 2.2f
            ex -= rx * s; ey -= ry * s; ez -= rz * s
            tx -= rx * s; ty -= ry * s; tz -= rz * s
        }
        camF = floatArrayOf(fx, fy, fz); camR = floatArrayOf(rx, ry, rz); camU = floatArrayOf(ux, uy, uz)
        val asp = sw.toFloat() / sh
        Matrix.perspectiveM(proj, 0, fovCur, asp, 0.4f, 1800f)
        Matrix.setLookAtM(view, 0, ex, ey, ez, tx, ty, tz, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        if (dxdz.isNaN()) return
    }

    private fun renderScene() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, rw, rh)
        GLES30.glDepthMask(true)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glDisable(GLES30.GL_BLEND)
        bound = null

        // gökyüzü
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        common(pSky)
        GLES30.glUniform3fv(pSky.u("u_right"), 1, camR, 0)
        GLES30.glUniform3fv(pSky.u("u_up"), 1, camU, 0)
        GLES30.glUniform3fv(pSky.u("u_fwd"), 1, camF, 0)
        GLES30.glUniform1f(pSky.u("u_tanY"), tan(Math.toRadians(fovCur / 2.0)).toFloat())
        GLES30.glUniform1f(pSky.u("u_asp"), sw.toFloat() / sh)
        GLES30.glUniform1f(pSky.u("u_cloud"), g.theme.cloud)
        en(0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDepthMask(true)

        drawGround()

        common(pObj)
        drawShadows()
        drawProps()
        drawTraffic()
        drawCoins()
        drawPlayer()
        drawParticles()
    }

    private fun drawGround() {
        val step = 3.2f
        val z0 = floor((ez - 30f) / step) * step
        roadFb.clear()
        for (s in 0 until SL) {
            val z = if (s < 60) z0 + s * step else z0 + 60 * step + (s - 60) * 8f
            val cxv = g.cx(z)
            for (p in 0 until LP) {
                val lat = LATS[p]
                roadFb.put(cxv + lat); roadFb.put(g.groundY(z, lat)); roadFb.put(z); roadFb.put(lat)
            }
        }
        roadFb.position(0)
        common(pGround)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, roadVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, SL * LP * 16, roadFb, GLES30.GL_STREAM_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, roadIbo)
        en(2)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, 16, 12)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, roadIdxCount, GLES30.GL_UNSIGNED_SHORT, 0)
        bound = null
    }

    private fun drawShadows() {
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-2f, -2f)
        val ly = max(0.3f, sunD[1])
        val offX = -sunD[0] / ly * 0.6f
        val offZ = -sunD[2] / ly * 0.6f
        fun sh(x: Float, z: Float, w: Float, l: Float, yaw: Float) {
            val y = g.cy(z) + 0.03f
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x + offX, y, z + offZ)
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, w * 1.4f, 0.02f, l * 1.25f)
            objDraw(cube, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 2f)
        }
        val spec = Game.CARS[g.shownCar]
        val z = g.z
        val yaw = Math.toDegrees(atan((g.cx(z + 2f) - g.cx(z - 2f)) / 4f + g.ovx / max(g.speed, 8f)).toDouble()).toFloat()
        sh
