package com.fastx.runner

import android.content.SharedPreferences
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

enum class State { MENU, GARAGE, COUNTDOWN, RACING, RESULT, PAUSED, SETTINGS }

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

class Racer(val isPlayer: Boolean, var spec: Int, val name: String, val color: FloatArray) {
    var z = 0f
    var x = 0f
    var vx = 0f
    var v = 0f
    var steer = 0f
    var lane = 0f
    var think = 0f
    var topMul = 1f
    var finished = false
    var finishTime = 0f
    var nitro = 25f
    var nitroOn = false
}

class Pickup(val z: Float, val x: Float) { var taken = false }

/** type 0: koni, 1: bariyer, 2: yağ lekesi */
class Obstacle(val z: Float, val x: Float, val type: Int) { var hit = false }

fun sstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

fun fmtTime(t: Float): String {
    val tt = max(0f, t)
    val m = (tt / 60f).toInt()
    val s = tt - m * 60f
    return String.format(Locale.US, "%02d:%05.2f", m, s)
}

class Game(private val prefs: SharedPreferences) {
    companion object {
        const val MAX_LEVEL = 50
        const val LAPS = 3
        const val RACERS = 8
        val LANES = floatArrayOf(-6f, -2f, 2f, 6f)
        val DIFF_NAMES = arrayOf("KOLAY", "NORMAL", "ZOR")
        val DIFF_MUL = floatArrayOf(0.80f, 0.90f, 0.99f)
        val RB_UP = floatArrayOf(0f, 0.03f, 0.06f)
        val RB_DN = floatArrayOf(0.06f, 0.04f, 0.02f)
        val AI_SEEK = floatArrayOf(2f, 4f, 7f)
        val AI_NITRO_START = floatArrayOf(50f, 35f, 22f)
        val NAMES = arrayOf("Kaan", "Deniz", "Mert", "Ayşe", "Emre", "Selin", "Burak", "Elif")
        val PLACE_REWARD = intArrayOf(1200, 800, 600, 400, 300, 200, 150, 100)
        private fun f3(a: Float, b: Float, c: Float) = floatArrayOf(a, b, c)

        val RCOL = arrayOf(
            f3(0.75f, 0.03f, 0.03f), f3(0.05f, 0.2f, 0.7f), f3(0.95f, 0.45f, 0.03f), f3(0.03f, 0.03f, 0.035f),
            f3(0.85f, 0.85f, 0.88f), f3(0.08f, 0.5f, 0.2f), f3(0.9f, 0.75f, 0.1f), f3(0.4f, 0.1f, 0.55f)
        )

        val CARS = arrayOf(
            CarSpec("Sedan GT", 0, 62f, 15f, 1f, 4.85f, 1.86f, 1.42f, 0.47f, 0.26f, 0.64f, 0.55f, 0.62f, 0.48f, 0.30f, f3(0.62f, 0.63f, 0.66f)),
            CarSpec("Falcon RS", 4000, 70f, 17f, 1.2f, 4.5f, 1.88f, 1.22f, 0.48f, 0.30f, 0.64f, 0.46f, 0.56f, 0.42f, 0.27f, f3(0.03f, 0.2f, 0.75f)),
            CarSpec("Raptor S", 10000, 78f, 19f, 1.4f, 4.6f, 1.92f, 1.18f, 0.46f, 0.32f, 0.62f, 0.44f, 0.54f, 0.40f, 0.26f, f3(0.95f, 0.45f, 0.02f)),
            CarSpec("Phantom R", 20000, 86f, 21f, 1.6f, 4.7f, 1.95f, 1.15f, 0.45f, 0.33f, 0.62f, 0.42f, 0.52f, 0.38f, 0.24f, f3(0.02f, 0.02f, 0.025f)),
            CarSpec("Titan X", 38000, 93f, 23f, 1.8f, 4.8f, 2.0f, 1.14f, 0.44f, 0.34f, 0.60f, 0.42f, 0.52f, 0.36f, 0.23f, f3(0.85f, 0.85f, 0.9f)),
            CarSpec("Apex One", 60000, 100f, 26f, 2.0f, 4.9f, 2.02f, 1.10f, 0.43f, 0.32f, 0.60f, 0.40f, 0.50f, 0.34f, 0.22f, f3(0.1f, 0.8f, 0.35f))
        )
        val THEMES = arrayOf(
            Theme("İskoçya", "Highlands", f3(0.18f, 0.27f, 0.42f), f3(0.55f, 0.6f, 0.66f), f3(0.45f, 0.38f, 0.8f), f3(2.4f, 2.1f, 1.7f), f3(0.07f, 0.15f, 0.05f), 0.0032f, 0f, 0.55f, false, 0.8f, 16f, 0, f3(1f, 1.02f, 1.05f)),
            Theme("Kahire", "Çöl Güneşi", f3(0.2f, 0.42f, 0.85f), f3(0.95f, 0.8f, 0.6f), f3(-0.35f, 0.55f, 0.75f), f3(4.8f, 3.8f, 2.6f), f3(0.7f, 0.5f, 0.28f), 0.0018f, 0f, 0f, false, 0.25f, 8f, 1, f3(1.06f, 1f, 0.9f)),
            Theme("Roma", "Gün Batımı", f3(0.12f, 0.17f, 0.4f), f3(1f, 0.48f, 0.22f), f3(0.25f, 0.14f, 0.96f), f3(7f, 3f, 1.1f), f3(0.22f, 0.18f, 0.14f), 0.0026f, 0.15f, 0.25f, false, 0.5f, 4f, 2, f3(1.08f, 0.98f, 0.92f)),
            Theme("Himalayalar", "Zirve Yolu", f3(0.08f, 0.28f, 0.75f), f3(0.72f, 0.85f, 1f), f3(-0.4f, 0.5f, 0.75f), f3(4.6f, 4.4f, 4.1f), f3(0.8f, 0.84f, 0.92f), 0.0022f, 0f, 0.1f, false, 0.35f, 36f, 3, f3(0.95f, 1f, 1.08f)),
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

    // ayarlar
    var weather = prefs.getInt("weather", 0)
    var ground = prefs.getInt("ground", 0)
    var reflect = prefs.getBoolean("reflect", true)
    var difficulty = prefs.getInt("diff", 1).coerceIn(0, 2)
    @Volatile var settingsFromMenu = true
    private var pausedFrom = State.RACING
    var onExit: (() -> Unit)? = null

    private var thCache: Theme? = null
    private var thKey = -1
    val theme: Theme
        get() {
            val key = ((level - 1) % THEMES.size) * 10 + weather
            var t = thCache
            if (t == null || key != thKey) {
                t = buildTheme()
                thCache = t
                thKey = key
            }
            return t
        }

    private fun buildTheme(): Theme {
        val b = THEMES[(level - 1) % THEMES.size]
        return when (weather) {
            1 -> Theme(b.name, b.place, f3(0.2f, 0.42f, 0.85f), f3(0.9f, 0.82f, 0.7f),
                f3(-0.35f, 0.55f, 0.75f), f3(4.8f, 3.8f, 2.6f),
                if (b.night > 0.3f) f3(0.22f, 0.27f, 0.18f) else b.gnd,
                0.0018f, 0f, 0f, false, 0.25f, b.terrain, b.kind, f3(1.05f, 1f, 0.93f))
            2 -> if (b.night > 0.5f)
                Theme(b.name, b.place, b.skyTop, b.skyHor, b.sunDir, b.sunCol, b.gnd,
                    max(b.fog, 0.0045f), b.night, 1f, true, 1f, b.terrain, b.kind, b.grade)
            else
                Theme(b.name, b.place, f3(0.12f, 0.14f, 0.19f), f3(0.38f, 0.41f, 0.46f),
                    f3(0.3f, 0.5f, 0.8f), f3(0.6f, 0.6f, 0.68f),
                    f3(b.gnd[0] * 0.6f, b.gnd[1] * 0.6f, b.gnd[2] * 0.6f),
                    0.0045f, 0.2f, 1f, true, 1f, b.terrain, b.kind, f3(0.95f, 1f, 1.06f))
            else -> b
        }
    }

    val wetValue: Float
        get() = when (ground) {
            1 -> 0f
            2 -> 1f
            else -> if (theme.rain) 1f else theme.wet
        }

    val menuLike: Boolean
        get() = state == State.MENU || state == State.GARAGE || (state == State.SETTINGS && settingsFromMenu)
    val menuView: Boolean
        get() = state == State.MENU || (state == State.SETTINGS && settingsFromMenu)

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
            else -> b - 0.25f + theme.terrain * ter(cx(z) + lat, z) * sstep(30f, 110f, a)
        }
    }
    fun applyLevel() {
        seed = level * 913f
        cf = (0.6f + 0.025f * level).coerceAtMost(1.6f)
        hf = if (theme.kind == 3) 1.6f else 1f
    }

    val lapLen: Float get() = min(3000f, 1500f + 60f * level)
    val total: Float get() = lapLen * LAPS

    // yarış durumu
    val me = Racer(true, 0, "SEN", floatArrayOf(1f, 1f, 1f))
    @Volatile var ai: List<Racer> = emptyList()
    @Volatile var obstacles: List<Obstacle> = emptyList()
    @Volatile var pickups: List<Pickup> = emptyList()
    private var all: Array<Racer> = arrayOf(me)

    var z: Float
        get() = me.z
        set(value) { me.z = value }
    var ox: Float
        get() = me.x
        set(value) { me.x = value }
    var ovx: Float
        get() = me.vx
        set(value) { me.vx = value }
    var speed: Float
        get() = me.v
        set(value) { me.v = value }
    var steer: Float
        get() = me.steer
        set(value) { me.steer = value }

    var nitro = 25f
    var health = 100f
    var raceTime = 0f
    var nitroGot = 0
    var cd = 3f
    var nitroOn = false
    var skid = false
    var scrape = false
    var oilT = 0f
    private var hitCd = 0f
    @Volatile var left = false
    @Volatile var right = false
    @Volatile var nitBtn = false
    @Volatile var braking = false
    var shake = 0f
    var flash = 0f
    private var flashT = 5f
    var crashCount = 0
    var coinCount = 0
    var beepCount = 0
    var goCount = 0
    var thunderCount = 0
    var rpm = 900f
    var load = 0.2f
    var coinX = 0f
    var coinZ = 0f
    var msg = ""
    var msgT = 0f
    var garageAngle = 0.6f
    var time = 0f
    var winnerName = ""
    var winnerTime = 0f

    // sonuç
    var resultPlace = 1
    var resultTime = 0f
    var resultReward = 0
    var resultStars = 0
    var resultM1 = false
    var resultM2 = false
    var resultM3 = false

    val lap: Int get() = ((max(0f, me.z) / lapLen).toInt() + 1).coerceIn(1, LAPS)
    fun m3Target() = 3 + level / 3
    fun upgradeCost(k: Int) = (350 * (upg[k] + 1) * (1f + carSel * 0.6f)).toInt()

    private fun key(r: Racer) = if (r.finished) 100000f - r.finishTime else r.z
    fun myPlace(): Int {
        if (state == State.RESULT) return resultPlace
        val k = key(me)
        var p = 1
        for (a in ai) if (key(a) > k) p++
        return p
    }

    init { applyLevel() }

    private fun say(s: String) { msg = s; msgT = 1.8f }

    private fun save() {
        val e = prefs.edit()
        e.putInt("coins", coins).putInt("maxLevel", maxLevel).putInt("car", carSel).putInt("owned", owned)
        for (i in 0..2) e.putInt("upg$i", upg[i])
        for (i in stars.indices) e.putInt("st$i", stars[i])
        e.putInt("weather", weather).putInt("ground", ground).putBoolean("reflect", reflect).putInt("diff", difficulty)
        e.apply()
    }

    // UI komutları
    @Synchronized fun toMenu() {
        state = State.MENU; me.v = 0f; me.z = 0f; me.x = 0f; me.vx = 0f
        ai = emptyList(); obstacles = emptyList(); pickups = emptyList()
        all = arrayOf(me)
        nitroOn = false; left = false; right = false
        nitBtn = false; braking = false; skid = false; scrape = false; applyLevel()
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
        me.spec = carSel
        val mySlot = 4 + Random.nextInt(4)
        val list = ArrayList<Racer>()
        var n = 0
        for (s in 0 until RACERS) {
            val row = s / 4
            val col = s % 4
            val gx = LANES[col]
            val gz = -6f - row * 11f
            if (s == mySlot) {
                me.x = gx; me.z = gz
            } else {
                val r = Racer(false, Random.nextInt(CARS.size), NAMES[n % NAMES.size], RCOL[n % RCOL.size])
                r.x = gx; r.z = gz; r.lane = gx
                r.topMul = rf(0.95f, 1.05f)
                r.think = rf(0f, 0.3f)
                r.nitro = 25f
                r.nitroOn = false
                list.add(r)
                n++
            }
        }
        ai = list
        val arr = ArrayList<Racer>()
        arr.add(me); arr.addAll(list)
        all = arr.toTypedArray()
        me.vx = 0f; me.v = 0f; me.steer = 0f; me.finished = false; me.finishTime = 0f
        nitro = 25f; health = 100f; raceTime = 0f; nitroGot = 0; oilT = 0f; hitCd = 0f
        winnerName = ""; winnerTime = 0f
        buildTrack()
        cd = 3f; state = State.COUNTDOWN; beepCount++
        msg = ""; left = false; right = false; nitBtn = false; braking = false
        nitroOn = false; skid = false; scrape = false
    }

    private fun buildTrack() {
        val obs = ArrayList<Obstacle>()
        val pk = ArrayList<Pickup>()
        val tot = total
        val density = min(1.6f, 1f + 0.02f * level + 0.12f * difficulty)
        var zz = 260f
        while (zz < tot - 120f) {
            val ln = Random.nextInt(4)
            val adj = if (ln < 3) ln + 1 else ln - 1
            val r = Random.nextFloat()
            when {
                r < 0.40f -> for (i in 0..2) obs.add(Obstacle(zz + i * 3.5f, LANES[ln], 0))
                r < 0.70f -> {
                    obs.add(Obstacle(zz, LANES[ln], 1))
                    if (level > 4 && Random.nextFloat() < 0.5f) obs.add(Obstacle(zz, LANES[adj], 1))
                }
                else -> {
                    obs.add(Obstacle(zz, LANES[ln], 2))
                    if (level > 2 && Random.nextFloat() < 0.5f) obs.add(Obstacle(zz + 2f, LANES[adj], 2))
                }
            }
            zz += rf(65f, 140f) / density
        }
        var zp = 200f
        while (zp < tot - 60f) {
            var lane = Random.nextInt(4)
            for (t in 0..3) {
                var clash = false
                for (o in obs) if (abs(o.z - zp) < 14f && abs(o.x - LANES[lane]) < 2f) { clash = true; break }
                if (!clash) break
                lane = (lane + 1) % 4
            }
            pk.add(Pickup(zp, LANES[lane]))
            zp += rf(220f, 380f)
        }
        obstacles = obs
        pickups = pk
    }

    @Synchronized fun pause() {
        if (state == State.RACING || state == State.COUNTDOWN) {
            pausedFrom = state
            state = State.PAUSED
            left = false; right = false; nitBtn = false; braking = false
            nitroOn = false; skid = false; scrape = false
        }
    }
    @Synchronized fun resume() { if (state == State.PAUSED) state = pausedFrom }
    @Synchronized fun openSettings() {
        settingsFromMenu = (state == State.MENU)
        state = State.SETTINGS
    }
    @Synchronized fun closeSettings() { state = if (settingsFromMenu) State.MENU else State.PAUSED }
    @Synchronized @JvmName("applyWeatherSetting") fun setWeather(v: Int) { weather = v; applyLevel(); save() }
    @Synchronized @JvmName("applyGroundSetting") fun setGround(v: Int) { ground = v; save() }
    @Synchronized @JvmName("applyReflectSetting") fun setReflect(v: Boolean) { reflect = v; save() }
    @Synchronized @JvmName("applyDifficultySetting") fun setDifficulty(v: Int) { difficulty = v.coerceIn(0, 2); save() }
    @Synchronized fun cycleDifficulty() { setDifficulty((difficulty + 1) % 3) }
    fun quitApp() { onExit?.invoke() }

    // oyun döngüsü
    @Synchronized fun update(dtIn: Float) {
        val dt = dtIn.coerceIn(0.001f, 0.05f)
        time += dt
        msgT = max(0f, msgT - dt)
        shake = max(0f, shake - dt * 2.2f)
        hitCd = max(0f, hitCd - dt)
        if (theme.rain) {
            flashT -= dt
            if (flashT < 0f) { flash = 1f; flashT = rf(3f, 10f); thunderCount++ }
        }
        flash = max(0f, flash - dt * 3.5f)
        when (state) {
            State.MENU, State.GARAGE -> {
                garageAngle += dt * 0.5f
                me.v = 0f; rpm = 1000f; load = 0.2f
                nitroOn = false; skid = false; scrape = false
            }
            State.SETTINGS -> {
                if (settingsFromMenu) {
                    garageAngle += dt * 0.5f
                    me.v = 0f; rpm = 1000f; load = 0.2f
                    nitroOn = false; skid = false; scrape = false
                }
            }
            State.PAUSED -> { }
            State.COUNTDOWN -> {
                val before = ceil(cd).toInt()
                cd -= dt
                val after = ceil(cd).toInt()
                if (after != before && after >= 1) beepCount++
                rpm = 2500f + 3500f * (1f - cd / 3f); load = 1f
                if (cd <= 0f) { state = State.RACING; goCount++; say("BAŞLA!") }
            }
            State.RACING -> step(dt)
            State.RESULT -> {
                me.v = max(0f, me.v - 25f * dt)
                me.z += me.v * dt
                rpm = 1500f + me.v * 20f; load = 0.1f
                nitroOn = false; skid = false; scrape = false
                for (a in ai) aiStep(a, dt)
            }
        }
    }

    private fun step(dt: Float) {
        raceTime += dt
        playerStep(dt)
        for (a in ai) aiStep(a, dt)
        for (i in all.indices) for (j in i + 1 until all.size) collide(all[i], all[j])
        for (r in all) {
            val lim = 8f - CARS[r.spec].wid / 2f - 0.1f
            r.x = r.x.coerceIn(-lim, lim)
        }
        if (me.z >= total && !me.finished) {
            me.finished = true
            me.finishTime = raceTime
            if (winnerName.isEmpty()) { winnerName = "SEN"; winnerTime = raceTime }
            finishRace()
        }
    }

    private fun playerStep(dt: Float) {
        val spec = CARS[carSel]
        val eng = upg[0]; val nit = upg[1]; val han = upg[2]
        nitroOn = nitBtn && nitro > 1f && me.v > 12f
        val hpf = 0.75f + 0.25f * (health / 100f).coerceIn(0f, 1f)
        val vmax = spec.top * (1f + 0.035f * eng) * hpf * (if (nitroOn) 1.3f else 1f)
        val a = spec.accel * (1f + 0.06f * eng) * (if (nitroOn) 1.9f else 1f)
        load = 1f
        if (braking) { me.v -= 42f * dt; load = 0.1f }
        else {
            val r = (me.v / vmax).coerceIn(0f, 1.5f)
            me.v += a * (1f - r * r * r) * dt
        }
        me.v -= 0.0009f * me.v * me.v * dt
        if (me.v < 0f) me.v = 0f

        if (nitroOn) nitro -= 10f * dt / (1f + 0.12f * nit)
        nitro = nitro.coerceIn(0f, 100f)

        // yanal hareket
        oilT = max(0f, oilT - dt)
        val st0 = (if (left) 1f else 0f) - (if (right) 1f else 0f)
        val st = if (oilT > 0f) st0 * 0.35f + sin(time * 11f) * 0.5f else st0
        me.steer += (st - me.steer) * min(1f, dt * 7f)
        val lim = (6.2f + 0.8f * han) * (1f - 0.30f * (me.v / (spec.top * 1.2f)).coerceIn(0f, 1f))
        me.vx += (me.steer * lim - me.vx) * min(1f, dt * (5f + 0.5f * han))
        me.vx -= curv(me.z) * me.v * me.v * 0.22f * dt
        me.x += me.vx * dt
        val limit = 8f - spec.wid / 2f - 0.1f
        scrape = false
        if (abs(me.x) > limit) {
            me.x = sign(me.x) * limit
            me.vx = -me.vx * 0.25f
            me.v = max(0f, me.v - 14f * dt)
            health -= 2f * dt
            scrape = me.v > 8f
            shake = max(shake, 0.25f)
        }
        skid = (abs(me.steer) > 0.6f && me.v > 35f) || (braking && me.v > 25f) || oilT > 0f

        me.z += me.v * dt

        // vites / rpm
        val ratio = (me.v / (spec.top * 1.1f)).coerceIn(0f, 1f)
        val gf = ratio * 6f
        val gear = min(5, gf.toInt())
        val frac = gf - gear
        rpm = 2200f + 5800f * frac + (if (nitroOn) 500f else 0f)

        // engeller
        for (o in obstacles) {
            val dz = o.z - me.z
            if (dz < -8f || dz > 8f) continue
            val dx = o.x - me.x
            when (o.type) {
                0 -> if (!o.hit && abs(dx) < spec.wid / 2f + 0.35f && abs(dz) < spec.len / 2f + 0.3f) {
                    o.hit = true
                    me.v *= 0.88f; health -= 2f; shake = max(shake, 0.4f); crashCount++
                }
                1 -> if (!o.hit && abs(dx) < spec.wid / 2f + 1.6f && abs(dz) < spec.len / 2f + 0.5f) {
                    o.hit = true
                    me.v *= 0.35f; health -= 8f + me.v * 0.05f; shake = 1f; crashCount++; say("ENGEL!")
                }
                2 -> if (!o.hit && abs(dx) < 1.9f && abs(dz) < 2.2f) {
                    o.hit = true
                    oilT = 1.4f; say("YAĞ LEKESİ!")
                }
            }
        }
        // nitro tüpleri
        for (p in pickups) {
            if (p.taken) continue
            val dz = p.z - me.z
            if (dz > 6f || dz < -6f) continue
            if (abs(p.x - me.x) < 2f && abs(dz) < 3.5f) {
                p.taken = true
                nitro = min(100f, nitro + 25f)
                nitroGot++
                coinCount++; coinX = cx(p.z) + p.x; coinZ = p.z
                say("+%25 NİTRO")
            }
        }
        if (health < 0f) health = 0f
    }

    private fun aiTop(a: Racer): Float {
        val base = CARS[carSel].top * (1f + 0.035f * upg[0])
        return base * DIFF_MUL[difficulty] * (1f + 0.004f * level) * a.topMul
    }

    private fun aiStep(a: Racer, dt: Float) {
        val spec = CARS[a.spec]
        if (state == State.COUNTDOWN) { a.v = 0f; a.vx = 0f; a.nitroOn = false; return }
        // nitro kullanımı
        if (a.finished || a.nitro <= 1f) a.nitroOn = false
        else if (!a.nitroOn) {
            val endgame = total - a.z < 450f
            if (a.nitro >= AI_NITRO_START[difficulty] || (endgame && a.nitro > 5f)) a.nitroOn = true
        }
        if (a.nitroOn) {
            var blocked = false
            for (q in all) {
                if (q === a) continue
                val d = q.z - a.z
                if (d > 0f && d < 14f && abs(q.x - a.x) < 2.2f) { blocked = true; break }
            }
            if (blocked && total - a.z > 450f) a.nitroOn = false else a.nitro -= 10f * dt
        }
        if (a.nitro < 0f) a.nitro = 0f
        var vt = aiTop(a) * (if (a.nitroOn) 1.3f else 1f)
        val gap = a.z - me.z
        if (gap < -60f) vt *= 1f + RB_UP[difficulty] * min(1f, (-gap - 60f) / 200f)
        if (gap > 80f) vt *= 1f - RB_DN[difficulty] * min(1f, (gap - 80f) / 200f)
        if (a.finished) vt = 30f
        val r = a.v / max(vt, 1f)
        a.v += spec.accel * 0.95f * (if (a.nitroOn) 1.9f else 1f) * (1f - r * r * r).coerceIn(-0.8f, 1f) * dt
        if (a.v < 0f) a.v = 0f

        // şerit seçimi
        a.think -= dt
        if (a.think <= 0f) {
            a.think = 0.25f + rf(0f, 0.15f)
            var best = a.lane
            var bestP = 1e9f
            for (ln in LANES) {
                var p = abs(ln - a.x) * 0.4f
                if (ln != a.lane) p += 0.5f
                val look = 25f + a.v * 0.8f
                for (o in obstacles) {
                    if (o.hit && o.type != 2) continue
                    val d = o.z - a.z
                    if (d < 0f || d > look) continue
                    if (abs(o.x - ln) < 2.3f) p += if (o.type == 2) 3f else 12f
                }
                for (q in all) {
                    if (q === a) continue
                    val d = q.z - a.z
                    if (d < 0f || d > 30f) continue
                    if (abs(q.x - ln) < 2.3f && q.v < a.v + 5f) p += 8f
                }
                for (pk in pickups) {
                    if (pk.taken) continue
                    val d = pk.z - a.z
                    if (d < 8f || d > 70f + a.v * 0.5f) continue
                    if (abs(pk.x - ln) < 2.3f && a.nitro < 95f) p -= AI_SEEK[difficulty]
                }
                if (p < bestP) { bestP = p; best = ln }
            }
            a.lane = best
        }
        val dxl = a.lane - a.x
        a.vx += (dxl.coerceIn(-1f, 1f) * 5f - a.vx) * min(1f, dt * 4f)
        a.x += a.vx * dt
        a.z += a.v * dt

        // nitro tüplerini kap
        for (pk in pickups) {
            if (pk.taken) continue
            val dzp = pk.z - a.z
            if (dzp > 6f || dzp < -6f) continue
            if (abs(pk.x - a.x) < 2f && abs(dzp) < 3.5f) {
                pk.taken = true
                a.nitro = min(100f, a.nitro + 25f)
            }
        }

        // engel çarpması
        for (o in obstacles) {
            if (o.type == 2 || o.hit) continue
            val dz = o.z - a.z
            if (dz < -4f || dz > 4f) continue
            val reach = if (o.type == 0) spec.wid / 2f + 0.35f else spec.wid / 2f + 1.6f
            if (abs(o.x - a.x) < reach) {
                o.hit = true
                a.v *= if (o.type == 0) 0.9f else 0.45f
            }
        }

        if (!a.finished && a.z >= total) {
            a.finished = true
            a.finishTime = raceTime
            if (winnerName.isEmpty()) { winnerName = a.name; winnerTime = raceTime }
            if (!me.finished) say("${a.name} BİTİRDİ!")
        }
    }

    private fun collide(a: Racer, b: Racer) {
        val sa = CARS[a.spec]
        val sb = CARS[b.spec]
        val dx = b.x - a.x
        val dz = b.z - a.z
        val hx = (sa.wid + sb.wid) / 2f * 0.95f
        val hz = (sa.len + sb.len) / 2f * 0.97f
        if (abs(dx) >= hx || abs(dz) >= hz) return
        val penX = hx - abs(dx)
        val penZ = hz - abs(dz)
        if (penZ < penX * 3f) {
            val rear = if (dz >= 0f) a else b
            val front = if (dz >= 0f) b else a
            rear.z = front.z - hz
            if (rear.isPlayer) {
                if (hitCd <= 0f) {
                    hitCd = 0.9f
                    health -= 6f + me.v * 0.06f
                    shake = 1f
                    crashCount++
                    say("ÇARPMA!")
                    me.v *= 0.3f
                } else me.v = min(me.v, front.v)
            } else rear.v = min(rear.v, front.v)
        } else {
            val sg = if (dx >= 0f) 1f else -1f
            a.x -= sg * penX * 0.5f
            b.x += sg * penX * 0.5f
            a.vx = -sg * 2f
            b.vx = sg * 2f
            if (a.isPlayer || b.isPlayer) {
                me.v *= 0.985f
                if (hitCd <= 0f) {
                    hitCd = 0.5f
                    health -= 2f
                    shake = max(shake, 0.5f)
                    crashCount++
                }
            }
        }
    }

    private fun finishRace() {
        state = State.RESULT
        var place = 1
        for (a in ai) if (a.finished) place++
        resultPlace = place
        resultTime = me.finishTime
        resultM1 = place <= 3
        resultM2 = place == 1
        resultM3 = nitroGot >= m3Target()
        var s = 0
        if (resultM1) { s++; if (resultM2) s++; if (resultM3) s++ }
        resultStars = s
        var reward = PLACE_REWARD[(place - 1).coerceIn(0, 7)] + level * 40 + nitroGot * 15
        if (resultM1) {
            val newStars = max(0, s - stars[level])
            reward += newStars * level * 60
            stars[level] = max(stars[level], s)
            if (level == maxLevel && level < MAX_LEVEL) maxLevel++
        }
        resultReward = reward
        coins += reward
        save()
    }
}
