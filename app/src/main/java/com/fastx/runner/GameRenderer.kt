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
        fun blob(x: Float, z: Float, w: Float, l: Float, yaw: Float) {
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, x + offX, g.cy(z) + 0.03f, z + offZ)
            Matrix.rotateM(model, 0, yaw, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, w * 1.4f, 0.02f, l * 1.25f)
            objDraw(cube, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 2f)
        }
        val spec = Game.CARS[g.shownCar]
        val z = g.z
        val racing = g.state != State.MENU && g.state != State.GARAGE
        val yaw = if (racing) Math.toDegrees(atan((g.cx(z + 2f) - g.cx(z - 2f)) / 4f + g.ovx / max(g.speed, 8f)).toDouble()).toFloat() else 0f
        blob(carX, z, spec.wid, spec.len, yaw)
        for (t in g.traffic) {
            if (t.z > z + 300f) continue
            val tw = if (t.type == 2) 2.5f else 1.9f
            val tl = if (t.type == 2) 8f else 4.7f
            val ty2 = Math.toDegrees(atan((g.cx(t.z + 2f) - g.cx(t.z - 2f)) / 4f).toDouble()).toFloat()
            blob(g.cx(t.z) + t.x, t.z, tw, tl, ty2)
        }
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
    }

    private fun drawProps() {
        val th = g.theme
        val night = th.night
        val k0 = floor((ez - 30f) / 40f).toInt()
        val deg = 57.29578f
        val lampE = 0.15f + night * 9f
        val win = if (th.kind == 2) 0.5f else if (night > 0.4f) 1f else 0.12f
        val sides = intArrayOf(-1, 1)
        for (k in k0..k0 + 12) {
            val zk = k * 40f
            for (q in 0..1) {
                val zz = zk + 10f + q * 20f
                val yaw = atan((g.cx(zz + 10f) - g.cx(zz - 10f)) / 20f) * deg
                for (sd in sides) box(g.cx(zz) + sd * 8.9f, g.cy(zz), zz, 0.14f, 0.9f, 20.2f, yaw, 0.55f, 0.57f, 0.6f)
            }
            for (sd in sides) {
                val x = g.cx(zk) + sd * 10.2f
                val y = g.cy(zk)
                box(x, y, zk, 0.18f, 7.5f, 0.18f, 0f, 0.3f, 0.3f, 0.32f)
                box(x - sd * 1.0f, y + 7.4f, zk, 2.2f, 0.18f, 0.5f, 0f, 0.4f, 0.4f, 0.4f, lampE, lampE * 0.78f, lampE * 0.45f)
            }
            when (th.kind) {
                0, 3 -> for (sd in sides) for (t in 0..1) {
                    val r1 = hsh(k, sd * 10 + t * 3 + 1)
                    val r2 = hsh(k, sd * 10 + t * 3 + 2)
                    val r3 = hsh(k, sd * 10 + t * 3 + 3)
                    val lat = sd * (15f + r1 * 50f)
                    val zz = zk + r2 * 40f
                    val x = g.cx(zz) + lat
                    val y = g.groundY(zz, lat) - 1.2f
                    val h = 9f + r3 * 8f
                    val dk = 0.7f + r3 * 0.6f
                    if (th.kind == 0) {
                        box(x, y, zz, 0.6f, 3f, 0.6f, 0f, 0.15f, 0.1f, 0.06f)
                        coneAt(cone, x, y + 2f, zz, 4f + r3 * 3f, h, 0f, 0.04f * dk, 0.16f * dk, 0.05f * dk)
                    } else coneAt(cone, x, y, zz, 3.5f + r3 * 2f, h * 0.9f, 0f, 0.8f * dk, 0.85f * dk, 0.9f * dk)
                }
                2, 4, 5 -> for (sd in sides) for (row in 0..1) {
                    val salt = sd * 20 + row * 7
                    val r1 = hsh(k, salt + 1); val r2 = hsh(k, salt + 2); val r3 = hsh(k, salt + 3)
                    val r4 = hsh(k, salt + 4); val r5 = hsh(k, salt + 5)
                    val lat = sd * (if (row == 0) 28f + r1 * 30f else 75f + r1 * 55f)
                    val zz = zk + r2 * 30f
                    val w = 16f + r3 * 18f
                    val h = if (th.kind == 2) 20f + r4 * 28f else (if (row == 0) 40f + r4 * r4 * 150f else 90f + r4 * 180f)
                    val x = g.cx(zz) + lat
                    val y = g.cy(zz) - 4f
                    val cr: Float; val cg: Float; val cb: Float
                    if (th.kind == 2) { cr = 0.55f * (0.8f + r5 * 0.4f); cg = 0.4f * (0.8f + r5 * 0.4f); cb = 0.28f }
                    else { cr = 0.07f + r5 * 0.04f; cg = 0.08f + r5 * 0.04f; cb = 0.11f + r5 * 0.05f }
                    box(x, y, zz, w, h + 4f, 22f, 0f, cr, cg, cb, 0f, 0f, 0f, win)
                    if (th.kind != 2 && r5 > 0.55f) {
                        val ci = (r5 * 10f).toInt() % 3
                        val er = if (ci == 0) 8f else if (ci == 1) 0.5f else 9f
                        val eg = if (ci == 0) 0.5f else if (ci == 1) 5f else 3f
                        val eb = if (ci == 0) 6f else if (ci == 1) 8f else 0.5f
                        box(x, y + h + 4f, zz, w * 0.9f, 1.2f, 20f, 0f, 0.1f, 0.1f, 0.1f, er, eg, eb)
                    }
                }
            }
        }
        // uzak dağlar, piramitler, kumullar
        for (k in k0..k0 + 22) {
            val zk = k * 40f
            if (th.kind == 0 || th.kind == 3) {
                if ((k and 1) != 0) continue
                for (sd in sides) {
                    val r1 = hsh(k, sd * 5 + 41); val r2 = hsh(k, sd * 5 + 42)
                    val lat = sd * (160f + r1 * 200f)
                    val x = g.cx(zk) + lat
                    val y = g.cy(zk) - 30f
                    if (th.kind == 3) {
                        val w = 250f + r2 * 250f; val h = 200f + r2 * 250f
                        coneAt(cone, x, y, zk, w, h, 0f, 0.45f, 0.47f, 0.52f)
                        coneAt(cone, x, y + h * 0.62f, zk, w * 0.4f, h * 0.38f, 0f, 0.95f, 0.96f, 1f)
                    } else coneAt(cone, x, y, zk, 200f + r2 * 200f, 70f + r2 * 90f, 0f, 0.1f, 0.14f, 0.09f)
                }
            } else if (th.kind == 1) {
                for (sd in sides) {
                    val r1 = hsh(k, sd * 5 + 51); val r2 = hsh(k, sd * 5 + 52)
                    if (k % 3 == 0) {
                        val w = 130f + r2 * 120f
                        coneAt(pyr, g.cx(zk) + sd * (120f + r1 * 200f), g.cy(zk) - 8f, zk, w, w * 0.62f, r1 * 90f, 0.78f, 0.62f, 0.38f)
                    } else if ((k and 1) == 0) {
                        coneAt(cone, g.cx(zk) + sd * (70f + r1 * 150f), g.cy(zk) - 8f, zk, 140f + r2 * 80f, 14f + r2 * 20f, 0f, 0.72f, 0.54f, 0.3f)
                    }
                }
            }
        }
    }

    private fun carMatrix(x: Float, y: Float, z: Float, yaw: Float, pitch: Float, roll: Float) {
        Matrix.setIdentityM(carM, 0)
        Matrix.translateM(carM, 0, x, y, z)
        Matrix.rotateM(carM, 0, yaw, 0f, 1f, 0f)
        Matrix.rotateM(carM, 0, pitch, 1f, 0f, 0f)
        Matrix.rotateM(carM, 0, roll, 0f, 0f, 1f)
    }

    private fun drawCarMesh(mesh: GpuMesh, spec: CarSpec, col: FloatArray, steerDeg: Float, brake: Float) {
        GLES30.glUniform1f(pObj.u("u_brake"), brake)
        System.arraycopy(carM, 0, model, 0, 16)
        objDraw(mesh, col[0], col[1], col[2], 0f, 0f, 0f, 0f, 1f)
        val wr = 0.31f + 0.04f * (spec.hgt / 1.4f)
        val s = wr / 0.345f
        val wx = spec.wid / 2f - 0.12f
        val wz = spec.len * 0.30f
        for (i in 0..3) {
            val sx = if (i % 2 == 0) -1f else 1f
            val fz = if (i < 2) 1f else -1f
            System.arraycopy(carM, 0, wm, 0, 16)
            Matrix.translateM(wm, 0, sx * wx, wr, fz * wz)
            if (fz > 0f) Matrix.rotateM(wm, 0, steerDeg, 0f, 1f, 0f)
            Matrix.rotateM(wm, 0, wheelSpin, 1f, 0f, 0f)
            Matrix.scaleM(wm, 0, s * sx, s, s)
            System.arraycopy(wm, 0, model, 0, 16)
            objDraw(wheel, 0.02f, 0.02f, 0.02f, 0f, 0f, 0f, 0f, 1f)
        }
        GLES30.glUniform1f(pObj.u("u_brake"), 0f)
    }

    private fun drawTraffic() {
        val deg = 57.29578f
        for (t in g.traffic) {
            if (t.z > ez + 430f || t.z < ez - 15f) continue
            val x = g.cx(t.z) + t.x
            val y = g.cy(t.z)
            val yaw = atan((g.cx(t.z + 2f) - g.cx(t.z - 2f)) / 4f) * deg
            val pitch = -atan((g.cy(t.z + 2f) - g.cy(t.z - 2f)) / 4f) * deg
            if (t.type == 2) {
                carMatrix(x, y, t.z, yaw, pitch, 0f)
                System.arraycopy(carM, 0, model, 0, 16)
                Matrix.translateM(model, 0, 0f, 0.5f + 1.6f, -0.9f)
                Matrix.scaleM(model, 0, 2.5f, 3.2f, 6.2f)
                objDraw(cube, 0.75f, 0.76f, 0.8f, 0f, 0f, 0f, 0f, 0f)
                System.arraycopy(carM, 0, model, 0, 16)
                Matrix.translateM(model, 0, 0f, 0.5f + 1.3f, 3.2f)
                Matrix.scaleM(model, 0, 2.4f, 2.6f, 2.0f)
                objDraw(cube, t.color[0], t.color[1], t.color[2], 0f, 0f, 0f, 0f, 0f)
                for (sd in intArrayOf(-1, 1)) {
                    System.arraycopy(carM, 0, model, 0, 16)
                    Matrix.translateM(model, 0, sd * 0.9f, 1.0f, -4.05f)
                    Matrix.scaleM(model, 0, 0.4f, 0.3f, 0.1f)
                    objDraw(cube, 0.3f, 0f, 0f, 3.5f, 0.15f, 0.1f, 0f, 0f)
                    System.arraycopy(carM, 0, model, 0, 16)
                    Matrix.translateM(model, 0, sd * 1.15f, 0.45f, -2.5f)
                    Matrix.scaleM(model, 0, 0.3f, 0.9f, 1.0f)
                    objDraw(cube, 0.02f, 0.02f, 0.02f, 0f, 0f, 0f, 0f, 0f)
                }
            } else {
                carMatrix(x, y, t.z, yaw, pitch, 0f)
                drawCarMesh(carLo[t.type]!!, if (t.type == 0) Game.TSEDAN else Game.TSUV, t.color, 0f, 0f)
            }
        }
    }

    private fun drawCoins() {
        val yaw = (tAcc * 180f) % 360f
        for (c in g.coinList) {
            if (c.taken || c.z > ez + 420f || c.z < ez - 10f) continue
            val x = g.cx(c.z) + c.x
            val y = g.cy(c.z) + 0.6f + sin(tAcc * 3f + c.z) * 0.15f
            if (c.big) box(x, y, c.z, 0.9f, 0.9f, 0.9f, yaw, 0.1f, 0.5f, 0.7f, 0.3f, 2.5f, 4f)
            else box(x, y, c.z, 1.1f, 1.1f, 0.12f, yaw, 1f, 0.75f, 0.1f, 3f, 2.2f, 0.3f)
        }
    }

    private fun drawPlayer() {
        val ci = g.shownCar
        val spec = Game.CARS[ci]
        val mesh = carHi ?: return
        val z = g.z
        val racing = g.state != State.MENU && g.state != State.GARAGE
        val deg = 57.29578f
        var yaw = 0f; var pitch = 0f; var roll = 0f
        var y = carY
        if (racing) {
            yaw = atan((g.cx(z + 2f) - g.cx(z - 2f)) / 4f + g.ovx / max(g.speed, 8f)) * deg
            pitch = -atan((g.cy(z + 2f) - g.cy(z - 2f)) / 4f) * deg
            roll = -g.steer * 3f
            y += sin(tAcc * 40f) * 0.004f * (g.speed / 50f)
        }
        carMatrix(carX, y, z, yaw, pitch, roll)
        drawCarMesh(mesh, spec, spec.color, if (racing) g.steer * 22f else 0f, if (g.braking && racing) 1f else 0f)
    }

    private fun rr() = Random.nextFloat()

    private fun emitParticles(dt: Float) {
        val st = g.state
        if (st == State.MENU || st == State.GARAGE) { lastCrash = g.crashCount; lastCoin = g.coinCount; return }
        val spec = Game.CARS[g.carSel]
        val z = g.z
        if (g.nitroOn) for (sd in intArrayOf(-1, 1)) for (i in 0..1) {
            val x = carX + sd * 0.5f
            val y = carY + 0.45f
            val zz = z - spec.len / 2f - 0.1f
            parts.add(0, x, y, zz, (rr() - 0.5f) * 0.8f, rr() * 0.4f, -(3f + rr() * 4f), 0.3f, 0.28f, 2f, 4f, 1.6f, 0.4f, 1f, 0f, 0f)
            parts.add(0, x, y, zz, (rr() - 0.5f) * 0.3f, rr() * 0.2f, -(5f + rr() * 5f), 0.18f, 0.16f, 1f, 0.5f, 1f, 4f, 1f, 0f, 0f)
        }
        if (g.crashCount != lastCrash) {
            lastCrash = g.crashCount
            repeat(45) {
                parts.add(0, carX + (rr() - 0.5f) * 1.5f, carY + 0.5f + rr() * 0.5f, z + spec.len / 2f,
                    (rr() - 0.5f) * 14f, rr() * 7f + 1f, rr() * 12f - 2f, 0.5f + rr() * 0.5f, 0.1f, 0f, 4f, 2f, 0.5f, 1f, -9f, 0.06f)
            }
            repeat(14) {
                parts.add(1, carX + (rr() - 0.5f) * 1.5f, carY + 0.6f, z + spec.len / 2f,
                    (rr() - 0.5f) * 3f, rr() * 2f, rr() * 3f, 1.2f, 0.8f, 3f, 0.45f, 0.45f, 0.45f, 0.5f, 0.3f, 0f)
            }
        }
        if (g.scrape) {
            val side = if (g.ox > 0f) 1f else -1f
            repeat(3) {
                parts.add(0, carX + side * spec.wid / 2f, carY + 0.3f, z + (rr() - 0.5f) * 3f,
                    side * rr() * 3f, rr() * 3f + 1f, rr() * 8f - 2f, 0.4f, 0.08f, 0f, 4f, 2f, 0.5f, 1f, -9f, 0.05f)
            }
        }
        if (g.skid) for (sd in intArrayOf(-1, 1)) {
            parts.add(1, carX + sd * 0.8f, carY + 0.2f, z - spec.len * 0.3f, 0f, 0.3f, 0f, 0.9f, 0.5f, 2f, 0.6f, 0.6f, 0.6f, 0.35f, 0.3f, 0f)
        }
        if (g.coinCount != lastCoin) {
            lastCoin = g.coinCount
            repeat(10) {
                parts.add(0, g.coinX, g.cy(g.coinZ) + 1f, g.coinZ, (rr() - 0.5f) * 4f, rr() * 4f, (rr() - 0.5f) * 4f,
                    0.5f, 0.12f, 0f, 3f, 2.2f, 0.4f, 1f, -4f, 0f)
            }
        }
    }

    private var qn = 0

    private fun quad(cx: Float, cy: Float, cz: Float, ax: Float, ay: Float, az: Float,
                     bx: Float, by: Float, bz: Float, r: Float, gg: Float, b: Float, a: Float) {
        if ((qn + 1) * 54 > partArr.size) return
        val sx = intArrayOf(-1, 1, 1, -1)
        val sy = intArrayOf(-1, -1, 1, 1)
        val order = intArrayOf(0, 1, 2, 0, 2, 3)
        var o = qn * 54
        for (k in order) {
            val u = sx[k].toFloat(); val v = sy[k].toFloat()
            partArr[o++] = cx + ax * u + bx * v
            partArr[o++] = cy + ay * u + by * v
            partArr[o++] = cz + az * u + bz * v
            partArr[o++] = (u + 1f) * 0.5f
            partArr[o++] = (v + 1f) * 0.5f
            partArr[o++] = r; partArr[o++] = gg; partArr[o++] = b; partArr[o++] = a
        }
        qn++
    }

    private fun streak(px: Float, py: Float, pz: Float, dx: Float, dy: Float, dz: Float, len: Float, width: Float,
                       r: Float, gg: Float, b: Float, a: Float) {
        val dl = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-5f)
        val nx = dx / dl; val ny = dy / dl; val nz = dz / dl
        val ex2 = ex - px; val ey2 = ey - py; val ez2 = ez - pz
        var cx = ny * ez2 - nz * ey2
        var cy = nz * ex2 - nx * ez2
        var cz = nx * ey2 - ny * ex2
        val cl = sqrt(cx * cx + cy * cy + cz * cz).coerceAtLeast(1e-5f)
        cx = cx / cl * width; cy = cy / cl * width; cz = cz / cl * width
        quad(px, py, pz, nx * len * 0.5f, ny * len * 0.5f, nz * len * 0.5f, cx, cy, cz, r, gg, b, a)
    }

    private fun addParticle(i: Int) {
        val fade = (parts.life[i] / parts.maxL[i]).coerceIn(0f, 1f)
        val sz = parts.size[i] * (1f + parts.grow[i] * (1f - fade))
        val al = parts.a[i] * fade
        if (parts.stretch[i] > 0f) {
            val sp = sqrt(parts.vx[i] * parts.vx[i] + parts.vy[i] * parts.vy[i] + parts.vz[i] * parts.vz[i])
            streak(parts.x[i], parts.y[i], parts.z[i], parts.vx[i], parts.vy[i], parts.vz[i],
                max(0.2f, parts.stretch[i] * sp), sz, parts.r[i], parts.g[i], parts.b[i], al)
        } else {
            quad(parts.x[i], parts.y[i], parts.z[i], camR[0] * sz, camR[1] * sz, camR[2] * sz,
                camU[0] * sz, camU[1] * sz, camU[2] * sz, parts.r[i], parts.g[i], parts.b[i], al)
        }
    }

    private fun drawParticles() {
        val th = g.theme
        qn = 0
        for (i in 0 until parts.n) if (parts.kind[i] == 0) addParticle(i)
        val racing = g.state == State.RACING || g.state == State.COUNTDOWN
        if (th.rain) {
            for (i in 0 until 260) {
                val u1 = hsh(i, 1); val u2 = hsh(i, 2); val u3 = hsh(i, 3)
                val x = ex + (u1 - 0.5f) * 50f
                val zz = ez + u2 * 70f - 10f
                val y = ey + 12f - ((u3 * 30f + tAcc * 28f) % 30f)
                streak(x, y, zz, 0f, -1f, -(g.speed / 90f) * 0.5f, 1.0f, 0.02f, 0.6f, 0.7f, 0.9f, 0.5f)
            }
        }
        if (racing && g.speed > 45f) {
            val k = ((g.speed - 45f) / 45f).coerceIn(0f, 1f)
            for (i in 0 until 40) {
                val ang = hsh(i, 11) * 6.2832f
                val rad = 3f + hsh(i, 12) * 6f
                val zz = ez + 3f + (((hsh(i, 13) * 40f - tAcc * g.speed * 0.8f) % 40f) + 40f) % 40f
                streak(ex + cos(ang) * rad, ey + sin(ang) * rad * 0.6f, zz, 0f, 0f, 1f, g.speed * 0.05f, 0.03f, 1f, 1f, 1f, 0.3f * k)
            }
        }
        val nAdd = qn
        for (i in 0 until parts.n) if (parts.kind[i] == 1) addParticle(i)
        val nSm = qn - nAdd
        if (qn == 0) return
        partFb.clear()
        partFb.put(partArr, 0, qn * 54)
        partFb.position(0)
        pPart.use()
        GLES30.glUniformMatrix4fv(pPart.u("u_vp"), 1, false, vp, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, partVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, qn * 54 * 4, partFb, GLES30.GL_STREAM_DRAW)
        en(3)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 36, 0)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 36, 12)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, 36, 20)
        GLES30.glDepthMask(false)
        GLES30.glEnable(GLES30.GL_BLEND)
        if (nAdd > 0) {
            GLES30.glBlendFuncSeparate(GLES30.GL_ONE, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
            GLES30.glUniform1f(pPart.u("u_add"), 1f)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, nAdd * 6)
        }
        if (nSm > 0) {
            GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
            GLES30.glUniform1f(pPart.u("u_add"), 0f)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, nAdd * 6, nSm * 6)
        }
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        bound = null
    }

    private fun post() {
        val th = g.theme
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTex)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, sw, sh)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(false)
        pPost.use()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, colorTex)
        GLES30.glUniform1i(pPost.u("u_tex"), 0)
        GLES30.glUniform2f(pPost.u("u_texel"), 1f / rw, 1f / rh)

        val sv = floatArrayOf(ex + sunD[0] * 1000f, ey + sunD[1] * 1000f, ez + sunD[2] * 1000f, 1f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, vp, 0, sv, 0)
        var rays = 0f
        var su = 0.5f; var sv2 = 0.5f
        if (out[3] > 0.01f && th.night < 0.5f && scale > 0.6f) {
            su = out[0] / out[3] * 0.5f + 0.5f
            sv2 = out[1] / out[3] * 0.5f + 0.5f
            val off = sqrt((su - 0.5f) * (su - 0.5f) + (sv2 - 0.5f) * (sv2 - 0.5f))
            rays = 0.9f * (1f - (off - 0.6f).coerceIn(0f, 1f))
        }
        val racing = g.state == State.RACING || g.state == State.COUNTDOWN || g.state == State.RESULT
        val sn = (g.speed / 90f).coerceIn(0f, 1.3f)
        val blur = if (racing) sn * sn * 0.05f + (if (g.nitroOn) 0.05f else 0f) else 0f
        val aberr = if (g.nitroOn) 0.012f else if (racing && sn > 0.8f) 0.004f else 0f
        val lowHp = if (racing && g.health < 35f) (35f - g.health) / 35f * 0.5f else 0f
        GLES30.glUniform2f(pPost.u("u_sunUV"), su, sv2)
        GLES30.glUniform1f(pPost.u("u_rays"), rays)
        GLES30.glUniform1f(pPost.u("u_blur"), blur)
        GLES30.glUniform1f(pPost.u("u_aberr"), aberr)
        GLES30.glUniform1f(pPost.u("u_vig"), 0.5f)
        GLES30.glUniform1f(pPost.u("u_exp"), 0.8f + 0.7f * th.night)
        GLES30.glUniform1f(pPost.u("u_bloom"), 0.5f + th.night * 0.4f)
        GLES30.glUniform1f(pPost.u("u_flash"), flashV)
        GLES30.glUniform1f(pPost.u("u_damage"), max(lowHp, g.shake * 0.35f))
        GLES30.glUniform1f(pPost.u("u_time"), tAcc)
        GLES30.glUniform1f(pPost.u("u_ssr"), if (scale > 0.6f) 1f else 0f)
        GLES30.glUniform3fv(pPost.u("u_tint"), 1, th.grade, 0)
        en(0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glDepthMask(true)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }
}
