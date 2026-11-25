package com.fastx.runner

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class GpuMesh(val vbo: Int, val ibo: Int, val count: Int)

object MeshGen {
    class Data(val v: FloatArray, val i: ShortArray)

    private fun sgnPow(x: Float, e: Float): Float = if (x >= 0f) x.pow(e) else -((-x).pow(e))

    private fun interp(p: FloatArray, t: Float): Float {
        val n = p.size / 2
        if (t <= p[0]) return p[1]
        if (t >= p[(n - 1) * 2]) return p[(n - 1) * 2 + 1]
        var i = 0
        while (i < n - 2 && t > p[(i + 1) * 2]) i++
        val t0 = p[i * 2]; val t1 = p[(i + 1) * 2]
        val u = (t - t0) / (t1 - t0)
        val s = u * u * (3f - 2f * u)
        return p[i * 2 + 1] * (1f - s) + p[(i + 1) * 2 + 1] * s
    }

    private fun bump(t: Float, a: Float, b: Float): Float {
        val u = ((t - a) / (b - a)).coerceIn(0f, 1f)
        return sin(u * PI.toFloat()).pow(0.6f)
    }

    /** Parametrik otomobil gövdesi. N*M*2 üçgen. Vertex: pos3 nrm3 part1 */
    fun car(s: CarSpec, N: Int, M: Int): Data {
        val L = s.len; val W = s.wid; val H = s.hgt
        val top = floatArrayOf(
            0f, s.tailH, 0.08f, s.tailH + 0.04f, s.cabS, s.deckH,
            s.roofT - 0.09f, 0.93f, s.roofT, 1f, s.roofT + 0.09f, 0.93f,
            s.cabE, s.hoodH + 0.12f, s.cabE + 0.08f, s.hoodH,
            0.9f, s.noseH + 0.04f, 1f, s.noseH
        )
        val wid = floatArrayOf(0f, 0.80f, 0.12f, 0.96f, 0.3f, 1f, 0.62f, 0.98f, 0.82f, 0.93f, 1f, 0.84f)
        val vc = (N + 1) * M
        val pos = FloatArray(vc * 3)
        val part = FloatArray(vc)
        val e = 2f / 4.2f   // daha köşeli (gerçek araç gibi) kesit
        val twoPi = (2.0 * PI).toFloat()
        for (i in 0..N) {
            val t = i.toFloat() / N
            var cap = 1f
            val ce = 0.035f
            if (t < ce) { val u = t / ce; cap = sqrt(1f - (1f - u) * (1f - u)) }
            else if (t > 1f - ce) { val u = (1f - t) / ce; cap = sqrt(1f - (1f - u) * (1f - u)) }
            val ht = interp(top, t) * H
            val hb = 0.17f * H
            val hh = (ht - hb) / 2f * cap
            val ym = (ht + hb) / 2f
            val hw = interp(wid, t) * W / 2f * cap
            val cabM = bump(t, s.cabS, s.cabE)
            for (j in 0 until M) {
                val a = twoPi * j / M
                val c = cos(a); val sn = sin(a)
                var x = hw * sgnPow(c, e)
                val y = ym + hh * sgnPow(sn, e)
                val belt = 0.50f * H
                if (sn > 0f) {
                    val k = ((y - belt) / (0.3f * H)).coerceIn(0f, 1f)
                    x *= 1f - 0.24f * cabM * k
                }
                val idx = i * M + j
                pos[idx * 3] = x; pos[idx * 3 + 1] = y; pos[idx * 3 + 2] = (t - 0.5f) * L
                var p = 0f
                if (sn > 0f && y > belt + 0.04f * H && t > s.cabS - 0.04f && t < s.cabE + 0.05f &&
                    !(sn > 0.9f && abs(t - s.roofT) < 0.08f)) p = 1f
                else if (sn < -0.2f) p = 2f
                if (t > 0.965f && abs(x) > 0.42f * W / 2f && y > 0.2f * H && y < 0.45f * H) p = 3f
                if (t < 0.035f && abs(x) > 0.2f * W / 2f && y > 0.3f * H && y < 0.55f * H) p = 4f
                part[idx] = p
            }
        }
        val idxA = ShortArray(N * M * 6)
        var k = 0
        for (i in 0 until N) for (j in 0 until M) {
            val a = i * M + j
            val b = i * M + (j + 1) % M
            val c = (i + 1) * M + j
            val d = (i + 1) * M + (j + 1) % M
            idxA[k++] = a.toShort(); idxA[k++] = c.toShort(); idxA[k++] = b.toShort()
            idxA[k++] = b.toShort(); idxA[k++] = c.toShort(); idxA[k++] = d.toShort()
        }
        val nrm = FloatArray(vc * 3)
        var f = 0
        while (f < idxA.size) {
            val ia = (idxA[f].toInt() and 0xFFFF) * 3
            val ib = (idxA[f + 1].toInt() and 0xFFFF) * 3
            val ic = (idxA[f + 2].toInt() and 0xFFFF) * 3
            val e1x = pos[ib] - pos[ia]; val e1y = pos[ib + 1] - pos[ia + 1]; val e1z = pos[ib + 2] - pos[ia + 2]
            val e2x = pos[ic] - pos[ia]; val e2y = pos[ic + 1] - pos[ia + 1]; val e2z = pos[ic + 2] - pos[ia + 2]
            val nx = e1y * e2z - e1z * e2y
            val ny = e1z * e2x - e1x * e2z
            val nz = e1x * e2y - e1y * e2x
            for (q in intArrayOf(ia, ib, ic)) { nrm[q] += nx; nrm[q + 1] += ny; nrm[q + 2] += nz }
            f += 3
        }
        val out = FloatArray(vc * 7)
        for (q in 0 until vc) {
            var nx = nrm[q * 3]; var ny = nrm[q * 3 + 1]; var nz = nrm[q * 3 + 2]
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-8f)
            nx /= l; ny /= l; nz /= l
            out[q * 7] = pos[q * 3]; out[q * 7 + 1] = pos[q * 3 + 1]; out[q * 7 + 2] = pos[q * 3 + 2]
            out[q * 7 + 3] = nx; out[q * 7 + 4] = ny; out[q * 7 + 5] = nz
            out[q * 7 + 6] = part[q]
        }
        return Data(out, idxA)
    }

    /** OBJ metninden araç ağı üretir. Uzunluk X ekseni (ön +X), yukarı Y. Tekerlek malzemeleri atlanır (oyun kendi tekerleğini çizer). */
    fun fromObj(text: String, s: CarSpec): Data {
        val px = ArrayList<Float>(); val py = ArrayList<Float>(); val pz = ArrayList<Float>()
        val faces = ArrayList<IntArray>()
        val fpart = ArrayList<Float>()
        var cur = 0f
        var skip = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '#') continue
            val t = line.split(Regex("\\s+"))
            when (t[0]) {
                "v" -> { px.add(t[1].toFloat()); py.add(t[2].toFloat()); pz.add(t[3].toFloat()) }
                "usemtl" -> {
                    val n = if (t.size > 1) t[1].lowercase() else ""
                    skip = false
                    cur = when {
                        n.contains("tire") || n.contains("tyre") || n.contains("wheel") || n.contains("rim") -> { skip = true; 0f }
                        n.contains("glass") || n.contains("window") -> 1f
                        n.contains("tail") || n.contains("brake") -> 4f
                        n.contains("light") || n.contains("lamp") -> 3f
                        else -> 0f
                    }
                }
                "f" -> if (!skip) {
                    val ids = IntArray(t.size - 1) { k ->
                        val a = t[k + 1].split('/')[0].toInt()
                        if (a < 0) px.size + a else a - 1
                    }
                    faces.add(ids); fpart.add(cur)
                }
            }
        }
        var minX = 1e9f; var maxX = -1e9f; var maxY = 0.001f; var minZ = 1e9f; var maxZ = -1e9f
        var tris = 0
        for (f in faces) {
            if (f.size >= 3) tris += f.size - 2
            for (i in f) {
                minX = min(minX, px[i]); maxX = max(maxX, px[i])
                maxY = max(maxY, py[i])
                minZ = min(minZ, pz[i]); maxZ = max(maxZ, pz[i])
            }
        }
        val xc = (minX + maxX) / 2f
        val zc = (minZ + maxZ) / 2f
        val sx = s.len / max(maxX - minX, 1e-4f)
        val sz = s.wid / max(maxZ - minZ, 1e-4f)
        val sy = s.hgt / maxY
        val out = FloatArray(tris * 3 * 7)
        var o = 0
        val tp = FloatArray(9)
        for ((fi, f) in faces.withIndex()) {
            for (k in 1 until f.size - 1) {
                val ids = intArrayOf(f[0], f[k], f[k + 1])
                for (q in 0..2) {
                    val i = ids[q]
                    tp[q * 3] = -(pz[i] - zc) * sz
                    tp[q * 3 + 1] = py[i] * sy
                    tp[q * 3 + 2] = (px[i] - xc) * sx
                }
                val e1x = tp[3] - tp[0]; val e1y = tp[4] - tp[1]; val e1z = tp[5] - tp[2]
                val e2x = tp[6] - tp[0]; val e2y = tp[7] - tp[1]; val e2z = tp[8] - tp[2]
                var nx = e1y * e2z - e1z * e2y
                var ny = e1z * e2x - e1x * e2z
                var nz = e1x * e2y - e1y * e2x
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                if (l < 1e-8f) { nx = 0f; ny = 1f; nz = 0f } else { nx /= l; ny /= l; nz /= l }
                for (q in 0..2) {
                    out[o++] = tp[q * 3]; out[o++] = tp[q * 3 + 1]; out[o++] = tp[q * 3 + 2]
                    out[o++] = nx; out[o++] = ny; out[o++] = nz
                    out[o++] = fpart[fi]
                }
            }
        }
        val idx = ShortArray(tris * 3) { it.toShort() }
        return Data(out, idx)
    }

    fun lathe(pr: FloatArray, parts: FloatArray, segs: Int): Data {
        val vl = ArrayList<Float>()
        val il = ArrayList<Short>()
        var base = 0
        val twoPi = (2.0 * PI).toFloat()
        for (s in parts.indices) {
            val x0 = pr[s * 2]; val r0 = pr[s * 2 + 1]; val x1 = pr[s * 2 + 2]; val r1 = pr[s * 2 + 3]
            var nx = r1 - r0
            var nr = -(x1 - x0)
            val nl = sqrt(nx * nx + nr * nr).coerceAtLeast(1e-6f)
            nx /= nl; nr /= nl
            for (j in 0..segs) {
                val a = twoPi * j / segs
                val c = cos(a); val sn = sin(a)
                var pid = parts[s]
                if (pid == 5f && ((j * 10) / segs) % 2 == 1) pid = 2f
                vl.add(x0); vl.add(r0 * c); vl.add(r0 * sn); vl.add(nx); vl.add(nr * c); vl.add(nr * sn); vl.add(pid)
                vl.add(x1); vl.add(r1 * c); vl.add(r1 * sn); vl.add(nx); vl.add(nr * c); vl.add(nr * sn); vl.add(pid)
            }
            for (j in 0 until segs) {
                val a = base + j * 2
                il.add(a.toShort()); il.add((a + 1).toShort()); il.add((a + 2).toShort())
                il.add((a + 1).toShort()); il.add((a + 3).toShort()); il.add((a + 2).toShort())
            }
            base += (segs + 1) * 2
        }
        return Data(vl.toFloatArray(), il.toShortArray())
    }

    fun wheel(): Data {
        val pr = floatArrayOf(
            -0.13f, 0f, -0.13f, 0.20f, -0.13f, 0.30f, -0.10f, 0.335f, -0.05f, 0.345f,
            0.05f, 0.345f, 0.10f, 0.335f, 0.13f, 0.30f, 0.13f, 0.20f, 0.11f, 0.20f, 0.11f, 0f
        )
        val parts = floatArrayOf(2f, 2f, 2f, 2f, 2f, 2f, 2f, 2f, 5f, 5f)
        return lathe(pr, parts, 48)
    }

    fun cube(): Data {
        val v = ArrayList<Float>()
        val il = ArrayList<Short>()
        val ns = arrayOf(
            floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f)
        )
        var base = 0
        for (n in ns) {
            val u = if (abs(n[1]) > 0.5f) floatArrayOf(1f, 0f, 0f) else floatArrayOf(0f, 1f, 0f)
            val w = floatArrayOf(n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0])
            val sg = arrayOf(floatArrayOf(-1f, -1f), floatArrayOf(1f, -1f), floatArrayOf(1f, 1f), floatArrayOf(-1f, 1f))
            for (s in sg) {
                for (k in 0..2) v.add(n[k] * 0.5f + u[k] * 0.5f * s[0] + w[k] * 0.5f * s[1])
                v.add(n[0]); v.add(n[1]); v.add(n[2]); v.add(0f)
            }
            il.add(base.toShort()); il.add((base + 1).toShort()); il.add((base + 2).toShort())
            il.add(base.toShort()); il.add((base + 2).toShort()); il.add((base + 3).toShort())
            base += 4
        }
        return Data(v.toFloatArray(), il.toShortArray())
    }

    /** Taban y=0, r=0.5; tepe y=1. smooth=false: düz yüzeyli (piramit) */
    fun cone(n: Int, smooth: Boolean): Data {
        val v = ArrayList<Float>()
        val il = ArrayList<Short>()
        val twoPi = (2.0 * PI).toFloat()
        var base = 0
        for (i in 0 until n) {
            val a0 = twoPi * i / n
            val a1 = twoPi * (i + 1) / n
            val am = (a0 + a1) / 2f
            val b0 = floatArrayOf(0.5f * cos(a0), 0f, 0.5f * sin(a0))
            val b1 = floatArrayOf(0.5f * cos(a1), 0f, 0.5f * sin(a1))
            val pts = arrayOf(b0, b1, floatArrayOf(0f, 1f, 0f))
            val nrms: Array<FloatArray>
            if (smooth) {
                fun nn(a: Float): FloatArray {
                    val x = cos(a); val y = 0.5f; val z = sin(a)
                    val l = sqrt(x * x + y * y + z * z)
                    return floatArrayOf(x / l, y / l, z / l)
                }
                nrms = arrayOf(nn(a0), nn(a1), nn(am))
            } else {
                val e1 = floatArrayOf(b1[0] - b0[0], b1[1] - b0[1], b1[2] - b0[2])
                val e2 = floatArrayOf(-b0[0], 1f, -b0[2])
                var nx = e1[1] * e2[2] - e1[2] * e2[1]
                var ny = e1[2] * e2[0] - e1[0] * e2[2]
                var nz = e1[0] * e2[1] - e1[1] * e2[0]
                val l = sqrt(nx * nx + ny * ny + nz * nz)
                nx /= l; ny /= l; nz /= l
                nrms = arrayOf(floatArrayOf(nx, ny, nz), floatArrayOf(nx, ny, nz), floatArrayOf(nx, ny, nz))
            }
            for (q in 0..2) {
                v.add(pts[q][0]); v.add(pts[q][1]); v.add(pts[q][2])
                v.add(nrms[q][0]); v.add(nrms[q][1]); v.add(nrms[q][2]); v.add(0f)
            }
            il.add(base.toShort()); il.add((base + 1).toShort()); il.add((base + 2).toShort())
            base += 3
        }
        return Data(v.toFloatArray(), il.toShortArray())
    }

    fun upload(d: Data): GpuMesh {
        val ids = IntArray(2)
        GLES30.glGenBuffers(2, ids, 0)
        val vb = ByteBuffer.allocateDirect(d.v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(d.v); vb.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, ids[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, d.v.size * 4, vb, GLES30.GL_STATIC_DRAW)
        val ib = ByteBuffer.allocateDirect(d.i.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(d.i); ib.position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ids[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, d.i.size * 2, ib, GLES30.GL_STATIC_DRAW)
        return GpuMesh(ids[0], ids[1], d.i.size)
    }
}
