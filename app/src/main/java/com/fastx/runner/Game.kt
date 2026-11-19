package com.fastx.runner

import android.content.SharedPreferences
import kotlin.math.*
import kotlin.random.Random

enum class State { MENU, GARAGE, COUNTDOWN, RACING, RESULT }

class CarSpec(
    val name: String, val price: Int, val top: Float, val accel: Float, val handling: Float,
    val len: Float, val wid: Float, val hgt: Float,
    val roofT: Float, val cabS: Float, val cabE: Float,
    val tailH: Float, val deckH: Float, val hoodH: Float, val noseH: Float,
    val color: FloatArray
)

class Theme(
    val name: String, val place: String,
    val skyTop: FloatArray, val skyHor: FloatArray,
    val sunDir: FloatArray, val sunCol: FloatArray,
    val gnd: FloatArray, val fog: Float,
    val night: Float, val wet: Float, val rain: Boolean, val cloud: Float,
    val terrain: Float, val kind: Int, val grade: FloatArray
)

class Traffic(var z: Float, val x: Float, var v: Float, val type: Int, val color: FloatArray) {
    var hit = false
    var near = false
    var passed = false
}

class Coin(val z: Float, val x: Float, var taken: Boolean, val big: Boolean)

fun sstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

class Game(private val prefs: SharedPreferences) {
    companion object {
        const val MAX_LEVEL = 30
        val LANES = floatArrayOf(-6f, -2f, 2f, 6f)
        private fun f3(a: Float, b: Float, c: Float) = floatArrayOf(a, b, c)

        val CARS = arrayOf(
            CarSpec("Vortex GT", 0, 62f, 15f, 1f, 4.4f, 1.85f, 1.25f, 0.50f, 0.30f, 0.66f, 0.45f, 0.55f, 0.42f, 0.28f, f3(0.75f, 0.02f, 0.02f)),
            CarSpec("Falcon RS", 4000, 70f, 17f, 1.2f, 4.5f, 1.88f, 1.22f, 0.48f, 0.30f, 0.64f, 0.46f, 0.56f, 0.42f, 0.27f, f3(0.03f, 0.2f, 0.75f)),
            CarSpec("Raptor S", 10000, 78f, 19f, 1.4f, 4.6f, 1.92f, 1.18f, 0.46f, 0.32f, 0.62f, 0.44f, 0.54f, 0.40f, 0.26f, f3(0.95f, 0.45f, 0.02f)),
            CarSpec("Phantom R", 20000, 86f, 21f, 1.6f, 4.7f, 1.95f, 1.15f, 0.45f, 0.33f, 0.62f, 0.42f, 0.52f, 0.38f, 0.24f, f3(0.02f, 0.02f, 0.025f)),
            CarSpec("Titan X", 38000, 93f, 23f, 1.8f, 4.8f, 2.0f, 1.14f, 0.44f, 0.34f, 0.60f, 0.42f, 0.52f, 0.36f, 0.23f, f3(0.85f, 0.85f, 0.9f)),
            CarSpec("Apex One", 60000, 100f, 26f, 2.0f, 4.9f, 2.02f, 1.10f, 0.43f, 0.32f, 0.60f, 0.40f, 0.50f, 0.34f, 0.22f, f3(0.1f, 0.8f, 0.35f))
        )
        val TSEDAN = CarSpec("sedan", 0, 0f, 0f, 0f, 4.6f, 1.85f, 1.45f, 0.46f, 0.22f, 0.74f, 0.7f, 0.72f, 0.55f, 0.45f, f3(1f, 1f, 1f))
        val TSUV = CarSpec("suv", 0, 0f, 0f, 0f, 4.8f, 2.0f, 1.85f, 0.5f, 0.18f, 0.78f, 0.85f, 0.85f, 0.6f, 0.5f, f3(1f, 1f, 1f))
        val TCOL = arrayOf(
            f3(0.5f, 0.5f, 0.52f), f3(0.05f, 0.05f, 0.06f), f3(0.6f, 0.08f, 0.08f),
            f3(0.08f, 0.2f, 0.5f), f3(0.75f, 0.75f, 0.78f), f3(0.1f, 0.35f, 0.15f), f3(0.8f, 0.6f, 0.1f)
        )
        val THEMES = arrayOf(
            Theme("İskoçya", "Highlands", f3(0.18f, 0.27f, 0.42f), f3(0.55f, 0.6f, 0.66f), f3(0.45f, 0.38f, 0.8f), f3(2.4f, 2.1f, 1.7f), f3(0.07f, 0.15f, 0.05f), 0.0032f, 0f, 0.55f, false, 0.8f, 16f, 0, f3(1f, 1.02f, 1.05f)),
            Theme("Kahire", "Çöl Güneşi", f3(0.2f, 0.42f, 0.85f), f3(0.95f, 0.8f, 0.6f), f3(-0.35f, 0.55f, 0.75f), f3(4.8f, 3.8f, 2.6f), f3(0.7f, 0.5f, 0.28f), 0.0018f, 0f, 0f, false, 0.25f, 8f, 1, f3(1.06f, 1f, 0.9f)),
            Theme("Roma", "Gün Batımı", f3(0.12f, 0.17f, 0.4f), f3(1f, 0.48f, 0.22f), f3(0.25f, 0.14f, 0.96f), f3(7f, 3f, 1.1f), f3(0.22f, 0.18f, 0.14f), 0.0026f, 0.15f, 0.25f, false, 0.5f, 4f, 2, f3(1.08f, 0.98f, 0.92f)),
            Theme("Himalayalar", "Zirve Yolu", f3(0.08f, 0.28f, 0.75f), f3(0.72f, 0.85f, 1f), f3(-0.4f, 0.5f, 0.75f), f3(4.6f, 4.4f, 4.1f), f3(0.8f, 0.84f, 0.92f), 0.0022f, 0f, 0.1f, false, 0.35f, 42f, 3, f3(0.95f, 1f, 1.08f)),
            Theme("San Francisco", "Gece Neon", f3(0.004f, 0.01f, 0.04f), f3(0.07f, 0.05f, 0.15f), f3(0.3f, 0.4f, 0.85f), f3(0.22f, 0.28f, 0.5f), f3(0.02f, 0.022f, 0.03f), 0.0035f, 1f, 0.85f, false, 0.3f, 5f, 4, f3(0.95f, 1f, 1.12f)),
            Theme("Şanghay", "Fırtına Gecesi", f3(0.015f, 0.02f, 0.04f), f3(0.1f, 0.11f, 0.17f), f3(-0.3f, 0.35f, 0.88f), f3(0.2f, 0.24f, 0.4f), f3(0.02f, 0.02f, 0.025f), 0.0055f, 0.85f, 1f, true, 1f, 3f, 5, f3(0.9f, 1f, 1.1f))
        )
    }

    private fun rf(a: Float, b: Float) = a + Random.nextFloat() * (b - a)

    @Volatile var state = State.MENU
    var coins = prefs.getInt("coins", 1000)
    var maxLevel = prefs.getInt("maxLevel", 1)
    var level = 1
    var carSel = prefs.getInt("car", 0)
    var viewCar = carSel
    var owned = prefs.getInt("owned", 1)
    val upg = IntArray(3) { prefs.getInt("upg$it", 0) }
    val stars = IntArray(MAX_LEVEL + 2) { prefs.getInt("st$it", 0) }

    val theme: Theme get() = THEMES[(level - 1) % THEMES.size]
    val shownCar: Int get() = if (state == State.GARAGE) viewCar else carSel

    // pist
    var seed = 0f
    var cf = 1f
    var hf = 1f
    fun cx(z: Float): Float {
        val s = z + seed
        return cf * (sin(s * 0.0021f) * 60f + sin(s * 0.0053f + 1.3f) * 25f + sin(s * 0.011f + 2.1f) * 8f)
    }
    fun cy(z: Float): Float {
        val s = z + seed
        return hf * (sin(s * 0.0027f + 0.5f) * 9f + sin(s * 0.0071f) * 3f)
    }
    fun curv(z: Float) = (cx(z + 6f) - 2f * cx(z) + cx(z - 6f)) / 36f
    fun ter(x: Float, z: Float): Float =
        (sin(x * 0.011f + z * 0.006f) * 0.5f + 0.5f) * (sin(z * 0.009f - x * 0.007f + 1f) * 0.5f + 0.5f)
    fun groundY(z: Float, lat: Float): Float {
        val a = abs(lat)
        val b = cy(z)
        return when {
            a <= 8f -> b
            a <= 13f -> b - 0.25f * (a - 8f) / 5f
            else -> b - 0.25f + theme.terrain * ter(cx(z) + lat, z) * sstep(13f, 60f, a)
        }
    }
    fun applyLevel() {
        seed = level * 913f
        cf = (0.6f + 0.03f * level).coerceAtMost(1.4f)
        hf = if (theme.kind == 3) 1.6f else 1f
    }

    // yarış durumu
    var z = 0f
    var z0 = 0f
    var ox = 0f
    var ovx = 0f
    var speed = 0f
    var steer = 0f
    var nitro = 60f
    var health = 100f
    var timeLeft = 50f
    var raceCoins = 0
    var overtakes = 0
    var nears = 0
    var cd = 3f
    var nitroOn = false
    var skid = false
    var scrape = false
    @Volatile var left = false
    @Volatile var right = false
    @Volatile var nitBtn = false
    @Volatile var braking = false
    val traffic = ArrayList<Traffic>()
    val coinList = ArrayList<Coin>()
    var shake = 0f
    var flash = 0f
    private var flashT = 5f
    var crashCount = 0
    var coinCount = 0
    var beepCount = 0
    var goCount = 0
    var nearCount = 0
    var thunderCount = 0
    var rpm = 900f
    var load = 0.2f
    var coinX = 0f
    var coinZ = 0f
    var msg = ""
    var msgT = 0f
    var garageAngle = 0.6f
    var time = 0f
    private var nextSpawnZ = 90f
    private var nextCoinZ = 80f
    private var nextCP = 600f

    // sonuç
    var resultOk = false
    var resultReward = 0
    var resultStars = 0
    var resultM2 = false
    var resultM3 = false

    val target: Float get() = min(8000f, 2400f + 450f * level)
    fun m2Target() = 250 + 30 * level
    fun m3Text(): String = if (level % 2 == 1) "${5 + level / 2} araç geç" else "${3 + level / 3} ramak kala geç"
    fun m3Value(): Int = if (level % 2 == 1) overtakes else nears
    fun m3Target(): Int = if (level % 2 == 1) 5 + level / 2 else 3 + level / 3
    fun upgradeCost(k: Int) = (350 * (upg[k] + 1) * (1f + carSel * 0.6f)).toInt()

    init { applyLevel() }

    private fun say(s: String) { msg = s; msgT = 1.8f }

    private fun save() {
        val e = prefs.edit()
        e.putInt("coins", coins).putInt("maxLevel", maxLevel).putInt("car", carSel).putInt("owned", owned)
        for (i in 0..2) e.putInt("upg$i", upg[i])
        for (i in stars.indices) e.putInt("st$i", stars[i])
        e.apply()
    }

    // UI komutları
    @Synchronized fun toMenu() {
        state = State.MENU; speed = 0f; z = 0f; ox = 0f; ovx = 0f
        traffic.clear(); coinList.clear(); nitroOn = false; left = false; right = false
        nitBtn = false; braking = false; applyLevel()
    }
    @Synchronized fun openGarage() { viewCar = carSel; state = State.GARAGE }
    @Synchronized fun changeLevel(d: Int) { level = (level + d).coerceIn(1, maxLevel); applyLevel() }
    @Synchronized fun viewNext(d: Int) { viewCar = (viewCar + d + CARS.size) % CARS.size }
    @Synchronized fun buyOrSelect() {
        val bit = 1 shl viewCar
        if (owned and bit != 0) carSel = viewCar
        else if (coins >= CARS[viewCar].price) {
            coins -= CARS[viewCar].price; owned = owned or bit; carSel = viewCar
        }
        save()
    }
    @Synchronized fun upgrade(k: Int) {
        if (upg[k] >= 5) return
        val c = upgradeCost(k)
        if (coins >= c) { coins -= c; upg[k]++; save() }
    }
    @Synchronized fun nextLevel() { if (level < maxLevel) level++; applyLevel(); startRace() }
    @Synchronized fun startRace() {
        applyLevel()
        z = 0f; z0 = 0f; ox = 0f; ovx = 0f; speed = 0f; steer = 0f
        nitro = 60f; health = 100f; timeLeft = 50f
        raceCoins = 0; overtakes = 0; nears = 0
        traffic.clear(); coinList.clear()
        nextSpawnZ = 90f; nextCoinZ = 80f; nextCP = 600f
        cd = 3f; state = State.COUNTDOWN; beepCount++
        msg = ""; left = false; right = false; nitBtn = false; braking = false
    }

    // oyun döngüsü
    @Synchronized fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0.001f, 0.05f)
        time += dt
        msgT = max(0f, msgT - dt)
        shake = max(0f, shake - dt * 2.2f)
        if (theme.rain) {
            flashT -= dt
            if (flashT < 0f) { flash = 1f; flashT = rf(3f, 10f); thunderCount++ }
        }
        flash = max(0f, flash - dt * 3.5f)
        when (state) {
            State.MENU, State.GARAGE -> {
                garageAngle += dt * 0.5f
                speed = 0f; rpm = 1000f; load = 0.2f
                nitroOn = false; skid = false; scrape = false
            }
            State.COUNTDOWN -> {
                val before = ceil(cd).toInt()
                cd -= dt
                val after = ceil(cd).toInt()
                if (after != before && after >= 1) beepCount++
                rpm = 2500f + 3500f * (1f - cd / 3f); load = 1f
                spawnTraffic(); spawnCoins()
                if (cd <= 0f) { state = State.RACING; goCount++; say("BAŞLA!") }
            }
            State.RACING -> step(dt)
            State.RESULT -> {
                speed = max(0f, speed - 25f * dt)
                z += speed * dt
                rpm = 1500f + speed * 20f; load = 0.1f
                nitroOn = false; skid = false; scrape = false
            }
        }
    }

    private fun step(dt: Float) {
        val spec = CARS[carSel]
        val eng = upg[0]; val nit = upg[1]; val han = upg[2]
        nitroOn = nitBtn && nitro > 1f && speed > 12f
        val vmax = spec.top * (1f + 0.035f * eng) * (if (nitroOn) 1.3f else 1f)
        val a = spec.accel * (1f + 0.06f * eng) * (if (nitroOn) 1.9f else 1f)
        load = 1f
        if (braking) { speed -= 42f * dt; load = 0.1f }
        else {
            val r = (speed / vmax).coerceIn(0f, 1.5f)
            speed += a * (1f - r * r * r) * dt
        }
        speed -= 0.0009f * speed * speed * dt
        if (speed < 0f) speed = 0f

        if (nitroOn) nitro -= 24f * dt / (1f + 0.12f * nit)
        else nitro = min(100f, nitro + (2f + 0.8f * nit) * dt)
        nitro = nitro.coerceIn(0f, 100f)

        // yanal hareket
        val st = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        steer += (st - steer) * min(1f, dt * 7f)
        val lim = (6.2f + 0.8f * han) * (1f - 0.30f * (speed / (spec.top * 1.2f)).coerceIn(0f, 1f))
        ovx += (steer * lim - ovx) * min(1f, dt * (5f + 0.5f * han))
        ovx -= curv(z) * speed * speed * 0.22f * dt
        ox += ovx * dt
        val limit = 8f - spec.wid / 2f - 0.1f
        scrape = false
        if (abs(ox) > limit) {
            ox = sign(ox) * limit
            ovx = -ovx * 0.25f
            speed = max(0f, speed - 14f * dt)
            health -= 2f * dt
            scrape = speed > 8f
            shake = max(shake, 0.25f)
        }
        skid = (abs(steer) > 0.6f && speed > 35f) || (braking && speed > 25f)

        z += speed * dt
        timeLeft -= dt

        // RPM / vites
        val ratio = (speed / (spec.top * 1.1f)).coerceIn(0f, 1f)
        val gf = ratio * 6f
        val gear = min(5, gf.toInt())
        val frac = gf - gear
        rpm = 2200f + 5800f * frac + (if (nitroOn) 500f else 0f)

        spawnTraffic(); spawnCoins()

        val hitDz0 = spec.len / 2f
        val it = traffic.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t.z += t.v * dt
            val tl = if (t.type == 2) 8f else 4.7f
            val tw = if (t.type == 2) 2.5f else 1.9f
            val dx = t.x - ox
            val dz = t.z - z
            val hitDx = (spec.wid + tw) / 2f * 0.88f
            val hitDz = (hitDz0 + tl / 2f) * 0.9f
            if (!t.hit && abs(dx) < hitDx && abs(dz) < hitDz) {
                t.hit = true
                health -= 18f + speed * 0.12f
                speed *= 0.55f
                shake = 1f
                crashCount++
                ovx = -sign(dx) * 4f
                t.v = max(10f, t.v * 0.7f)
                say("ÇARPMA!")
            }
            if (!t.near && !t.hit && abs(dz) < 4f && abs(dx) >= hitDx && abs(dx) < hitDx + 0.9f && speed > t.v + 5f) {
                t.near = true; nears++; nearCount++; nitro = min(100f, nitro + 10f); say("RAMAK KALA! +NİTRO")
            }
            if (!t.passed && t.z < z - 6f) { t.passed = true; overtakes++ }
            if (t.z < z - 60f) it.remove()
        }
        val ci = coinList.iterator()
        while (ci.hasNext()) {
            val c = ci.next()
            if (!c.taken && abs(c.x - ox) < 1.7f && abs(c.z - z) < 3f) {
                c.taken = true
                raceCoins += if (c.big) 50 else 10
                coinCount++; coinX = cx(c.z) + c.x; coinZ = c.z
                nitro = min(100f, nitro + 1.5f)
            }
            if (c.z < z - 30f) ci.remove()
        }

        val dist = z - z0
        if (dist >= nextCP) {
            nextCP += 600f
            val bonus = max(7f, 12f - level / 4f)
            timeLeft += bonus
            say("KONTROL NOKTASI +${bonus.toInt()}sn")
        }
        if (dist >= target) finish(true)
        else if (timeLeft <= 0f || health <= 0f) { health = max(health, 0f); timeLeft = max(timeLeft, 0f); finish(false) }
    }

    private fun spawnTraffic() {
        while (nextSpawnZ < z + 450f) {
            nextSpawnZ += rf(40f, 75f) / (1f + 0.02f * level)
            val x = LANES[Random.nextInt(4)]
            var clash = false
            for (t in traffic) if (abs(t.x - x) < 1f && abs(t.z - nextSpawnZ) < 30f) clash = true
            if (clash) continue
            val type = if (Random.nextFloat() < 0.18f) 2 else Random.nextInt(2)
            traffic.add(Traffic(nextSpawnZ, x, rf(22f, 40f), type, TCOL[Random.nextInt(TCOL.size)]))
        }
    }

    private fun spawnCoins() {
        while (nextCoinZ < z + 450f) {
            val x = LANES[Random.nextInt(4)]
            val big = Random.nextInt(8) == 0
            for (i in 0 until 6) coinList.add(Coin(nextCoinZ + i * 9f, x, false, big && i == 3))
            nextCoinZ += rf(130f, 220f)
        }
    }

    private fun finish(ok: Boolean) {
        state = State.RESULT
        resultOk = ok
        resultM2 = raceCoins >= m2Target()
        resultM3 = m3Value() >= m3Target()
        var s = 0
        if (ok) { s = 1; if (resultM2) s++; if (resultM3) s++ }
        resultStars = s
        val dist = (z - z0).toInt().coerceAtLeast(0)
        var reward = raceCoins + dist / 5
        if (ok) {
            val newStars = max(0, s - stars[level])
            reward += newStars * level * 60 + 100 * level
            stars[level] = max(stars[level], s)
            if (level == maxLevel && level < MAX_LEVEL) maxLevel++
        }
        resultReward = reward
        coins += reward
        save()
    }
}
