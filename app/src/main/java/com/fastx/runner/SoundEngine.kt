package com.fastx.runner

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.*

/** Tüm sesler kodla sentezlenir: motor, turbo, rüzgâr, nitro, fren, çarpma, para, bip, gök gürültüsü. */
class SoundEngine(private val g: Game) {
    private val SR = 32000
    @Volatile private var running = false
    private var thread: Thread? = null
    private var seedN = 22222

    private fun noise(): Float {
        seedN = seedN xor (seedN shl 13)
        seedN = seedN xor (seedN ushr 17)
        seedN = seedN xor (seedN shl 5)
        return (seedN and 0xFFFFFF) / 8388608f - 1f
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread { loop() }.apply { priority = Thread.MAX_PRIORITY; start() }
    }

    fun stop() {
        running = false
        try { thread?.join(800) } catch (_: Exception) {}
        thread = null
    }

    private fun loop() {
        val chunk = 512
        val minBuf = AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(SR)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(max(minBuf, chunk * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        val out = ShortArray(chunk)
        val twoPi = (2.0 * PI).toFloat()

        var ph = 0f; var rpmS = 900f; var lp = 0f; var wl = 0f; var lp2 = 0f; var nv = 0f; var tp = 0f
        var crashE = 0f; var thumpP = 0f
        var coinE = 0f; var coinP = 0f
        var beepE = 0f; var beepP = 0f; var beepF = 880f
        var thE = 0f; var th = 0f
        var sk1 = 0f; var sk2 = 0f; var skE = 0f; var scE = 0f
        var lastCrash = g.crashCount; var lastCoin = g.coinCount; var lastBeep = g.beepCount
        var lastGo = g.goCount; var lastTh = g.thunderCount

        while (running) {
            if (g.crashCount != lastCrash) { lastCrash = g.crashCount; crashE = 1f }
            if (g.coinCount != lastCoin) { lastCoin = g.coinCount; coinE = 1f; coinP = 0f }
            if (g.beepCount != lastBeep) { lastBeep = g.beepCount; beepE = 1f; beepF = 880f }
            if (g.goCount != lastGo) { lastGo = g.goCount; beepE = 1.4f; beepF = 1320f }
            if (g.thunderCount != lastTh) { lastTh = g.thunderCount; thE = 1f }

            val menu = g.state == State.MENU || g.state == State.GARAGE
            val vol = if (menu) 0.35f else 1f
            val rpmT = g.rpm
            val load = g.load
            val sn = (g.speed / 90f).coerceIn(0f, 1.3f)
            val nit = if (g.nitroOn) 1f else 0f
            val skidT = if (g.skid) 1f else 0f
            val scT = if (g.scrape) 1f else 0f

            for (i in 0 until chunk) {
                val n = noise()
                rpmS += (rpmT - rpmS) * 0.0008f
                val f = rpmS / 60f * 4f
                ph += twoPi * f / SR
                if (ph > twoPi) ph -= twoPi
                var e = sin(ph) + 0.55f * sin(2f * ph + 0.4f) + 0.4f * sin(3f * ph + 1.1f) +
                        0.3f * sin(4f * ph) + 0.2f * sin(6f * ph + 0.7f) + 0.4f * sin(0.5f * ph)
                e = e / (1f + abs(e) * (0.5f + load))
                lp += (n - lp) * 0.06f
                val eng = (e * 0.55f + lp * 0.7f * (0.3f + 0.7f * load)) * (0.5f + 0.5f * load)

                tp += twoPi * (1500f + rpmS * 0.4f) / SR
                if (tp > twoPi) tp -= twoPi
                val turbo = sin(tp) * 0.025f * (load + nit)

                wl += (n - wl) * 0.12f
                val wind = wl * 0.6f * sn * sn

                nv += (nit - nv) * 0.0004f
                lp2 += (n - lp2) * 0.25f
                val hp = n - lp2
                val whoosh = hp * 0.5f * nv

                skE += (skidT - skE) * 0.0006f
                val sk = 1.8755f * sk1 - 0.9216f * sk2 + n * 0.3f
                sk2 = sk1; sk1 = sk
                val skid = sk * 0.10f * skE

                scE += (scT - scE) * 0.001f
                val scrape = hp * 0.35f * scE

                crashE *= 0.99991f
                thumpP += twoPi * 55f / SR
                if (thumpP > twoPi) thumpP -= twoPi
                val crash = (sin(thumpP) * 0.8f + n * 0.6f) * crashE * crashE * 0.9f

                var coin = 0f
                if (coinE > 0.001f) {
                    coinP += twoPi * (if (coinE > 0.6f) 1318f else 1760f) / SR
                    coinE *= 0.9996f
                    coin = sin(coinP) * coinE * coinE * 0.25f
                }

                var beep = 0f
                if (beepE > 0.001f) {
                    beepP += twoPi * beepF / SR
                    beepE *= 0.99985f
                    beep = sin(beepP) * min(1f, beepE) * 0.3f
                }

                th += (n - th) * 0.004f
                thE *= 0.99998f
                val thunder = th * thE * 6f

                var s = (eng * 0.9f + turbo + wind) * vol + whoosh + skid + scrape + crash + coin + beep + thunder
                s = s.coerceIn(-1f, 1f)
                out[i] = (s * 28000f).toInt().toShort()
            }
            track.write(out, 0, chunk)
        }
        try { track.stop() } catch (_: Exception) {}
        track.release()
    }
}
