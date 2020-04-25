package com.example.soundwave

import android.Manifest
import android.media.*
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.math.*

/***************************************************************
 * SoundWave
 * Leiyi Zhang
 * University of Washington at Urbana-Champaign
 * April 18, 2020
 *
 * gesture recognition of push and pull using doppler
 *
 * Permission to copy and use this program is granted
 * as long as this header is included.
 */

class MainActivity : AppCompatActivity() {

    private val duration = 10
    private val sampleRate = 44100
    private val numSamples = duration * sampleRate

    //    private val sample = DoubleArray(numSamples)
    private val frequency = 18500 //Hz
//    private val sound = ByteArray(2 * numSamples)

    private val shortSample = ShortArray(numSamples)

    private val readSize = 2048
    private val buffer = ShortArray(readSize)
    private val x = DoubleArray(readSize)
    private val y = DoubleArray(readSize)
    private val p = DoubleArray(readSize / 2)
    private val transformer = FFT(readSize)

    private val center_bin = floor((frequency / 22050.0) * 1024).toInt()
    private var bandwidthInit = false
    private var bandLeftBase = 0
    private var bandRightBase = 0

    private val LEFT = -1
    private val RIGHT = 1
    private val NONE = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        Log.d("center_bin", center_bin.toString())

        val threadPlay = Thread(Runnable {
            genTone()
            playSound()
        })
        threadPlay.start()

        Thread.sleep(300)
        val thread = Thread(Runnable {
            recordAndAnalysis()
        })
        thread.start()

        changeText("None")
    }

    fun recordAndAnalysis() {
        val audioRecord = getRecord()
        audioRecord.startRecording()
        while (true) {
            for (i in p.indices) p[i] = 0.0

            val readResult = audioRecord.read(buffer, 0, readSize)
            if (readResult != readSize)
                Log.e("read_result", readResult.toString())

            for (i in 0 until readSize) {
                x[i] = buffer[i].toDouble() / Short.MAX_VALUE // signed 16 bit
                y[i] = 0.0
            }

            transformer.fft(x, y)

            //TODO: original does not have sqrt
            for (i in p.indices) p[i] = sqrt(x[i /*+ p.size*/].pow(2) + y[i /*+ p.size*/].pow(2))

            val signalCondition = p.slice((center_bin - 33)..(center_bin + 33))

            val motion = analyseBins(signalCondition)
            val freq = motion[1]
            changeText(
                when (motion[0]) {
                    LEFT -> "Pull\n$freq Hz"
                    RIGHT -> "Push\n$freq Hz"
                    else -> "no"
                }
            )

        }
    }

    /**
     * @return (shiftDirection, result frequency)
     */
    fun analyseBins(signalCondition: List<Double>): IntArray {
        val peakStrength = signalCondition[33]
        val threshold = peakStrength * .1
        val pThreshold = peakStrength * .3

        var lPointer = 32
        var lband = 0
        while (lPointer >= 0 && signalCondition[lPointer] >= threshold) {
            lband++
            lPointer--
        }
        var lPeak = false
        var lStart = lPointer - 1
        var lEnd = lStart
        for (i in lPointer - 1 downTo 0)
            if (signalCondition[i] >= pThreshold) {
                if (!lPeak) {
                    lPeak = true
                    lStart = i
                    lEnd = lStart
                } else {
                    lEnd = i
                }
            }

        var rPointer = 34
        var rband = 0
        while (rPointer < 67 && signalCondition[rPointer] >= threshold) {
            rband++
            rPointer++
        }
        var rPeak = false
        var rStart = rPointer + 1
        var rEnd = rStart
        for (i in rPointer + 1 until 67)
            if (signalCondition[i] >= pThreshold) {
                if (!rPeak) {
                    rPeak = true
                    rStart = i
                    rEnd = rStart
                } else {
                    rEnd = i
                }
            }

        if (!bandwidthInit) {
//            bandLeftBase = lband
//            bandRightBase = rband
            bandLeftBase = 4
            bandRightBase = 5
            bandwidthInit = true
            Log.d("bandLeftBase", bandLeftBase.toString())
            Log.d("bandRightBase", bandRightBase.toString())
        }
        var res = NONE
//        if (lband != 0)
//            Log.d("lband_info", lband.toString())
//        if (rband != 0)
//            Log.d("rband_info", rband.toString())
        if (lband > bandLeftBase || lPeak) {
            res = LEFT
//        } else {
//            bandLeftBase = lband
//            Log.d("bandLeftBase", bandLeftBase.toString())
        }
        if (rband > bandRightBase || rPeak) {
            res = RIGHT
//        } else {
//            bandRightBase = rband
//            Log.d("bandRightBase", bandRightBase.toString())
        }

        val freq = when {
            lband > bandLeftBase -> binToBand(33 - lband)
            rband > bandRightBase -> binToBand(33 + rband)
            lPeak -> binToBand(lStart - (lStart - lEnd) / 2)
            rPeak -> binToBand(rStart + (rEnd - rStart) / 2)
            else -> 18500.0
        }
        return intArrayOf(res, freq.toInt())
    }

    fun binToBand(bin: Int): Double {
        val stride = 22050.0 / 1024
        return (bin - 33) * stride + 18500
    }

    fun getRecord(): AudioRecord {
        val minSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(2 * minSize)
            .build()
    }

//    fun genToneIncorrect() { // fill out the array
//        for (i in 0 until numSamples) {
//            sample[i] = sin(2 * PI * i / (sampleRate / frequency))
//        }
//        // convert to 16 bit pcm sound array
//        // assumes the sample buffer is normalised.
//        var idx = 0
//        for (dVal in sample) { // scale to maximum amplitude
//            val `val` = (dVal * Short.MAX_VALUE).toShort()
//            // in 16 bit wav PCM, first byte is the low order byte
//            sound[idx++] = (`val`.toInt() and 0x00ff).toByte()
//            sound[idx++] = (`val`.toInt() and 0xff00 ushr 8).toByte()
//        }
//    }

    fun genTone() {
        var angle = 0.0
        val increment: Double = 2 * PI * frequency / sampleRate // angular increment
        for (i in shortSample.indices) {
            shortSample[i] = (sin(angle) * Short.MAX_VALUE).toShort()
            angle += increment
        }
    }

    fun playSound() {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(2 * numSamples)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audioTrack.setVolume(1.0f)

        audioTrack.write(shortSample, 0, shortSample.size) // write data to audio hardware

        audioTrack.setLoopPoints(0, audioTrack.bufferSizeInFrames, -1)
        audioTrack.play()
    }

    fun changeText(text: String) {
        if (text != "no") {
            displayBox.text = text
        }
    }
}


class FFT(var n: Int) {
    var m: Int

    // Lookup tables.  Only need to recompute when size of FFT changes.
    var cos: DoubleArray
    var sin: DoubleArray
    var window: DoubleArray

//    protected fun makeWindow() {
//        // Make a blackman window:
//        // w(n)=0.42-0.5cos{(2*PI*n)/(N-1)}+0.08cos{(4*PI*n)/(N-1)};
//        window = DoubleArray(n)
//        for (i in window.indices) window[i] =
//            (0.42 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1))
//                    + 0.08 * Math.cos(4 * Math.PI * i / (n - 1)))
//    }

    /***************************************************************
     * fft.c
     * Douglas L. Jones
     * University of Illinois at Urbana-Champaign
     * January 19, 1992
     * http://cnx.rice.edu/content/m12016/latest/
     *
     * fft: in-place radix-2 DIT DFT of a complex input
     *
     * input:
     * n: length of FFT: must be a power of two
     * m: n = 2**m
     * input/output
     * x: double array of length n with real part of data
     * y: double array of length n with imag part of data
     *
     * Permission to copy and use this program is granted
     * as long as this header is included.
     */
    fun fft(x: DoubleArray, y: DoubleArray) {
        var i: Int
        var j: Int
        var k: Int
        var n1: Int
        var n2: Int
        var a: Int
        var c: Double
        var s: Double
        var e: Double
        var t1: Double
        var t2: Double
        // Bit-reverse
        j = 0
        n2 = n / 2
        i = 1
        while (i < n - 1) {
            n1 = n2
            while (j >= n1) {
                j -= n1
                n1 /= 2
            }
            j += n1
            if (i < j) {
                t1 = x[i]
                x[i] = x[j]
                x[j] = t1
                t1 = y[i]
                y[i] = y[j]
                y[j] = t1
            }
            i++
        }
        // FFT
        n1 = 0
        n2 = 1
        i = 0
        while (i < m) {
            n1 = n2
            n2 += n2
            a = 0
            j = 0
            while (j < n1) {
                c = cos[a]
                s = sin[a]
                a += 1 shl m - i - 1
                k = j
                while (k < n) {
                    t1 = c * x[k + n1] - s * y[k + n1]
                    t2 = s * x[k + n1] + c * y[k + n1]
                    x[k + n1] = x[k] - t1
                    y[k + n1] = y[k] - t2
                    x[k] = x[k] + t1
                    y[k] = y[k] + t2
                    k += n2
                }
                j++
            }
            i++
        }
    }

    init {
        m = (ln(n.toDouble()) / ln(2.0)).toInt()
        // Make sure n is a power of 2
        if (n != 1 shl m) throw RuntimeException("FFT length must be power of 2")
        // precompute tables
        cos = DoubleArray(n / 2)
        sin = DoubleArray(n / 2)
        for (i in 0 until n / 2) {
            cos[i] = cos(-2 * Math.PI * i / n)
            sin[i] = sin(-2 * Math.PI * i / n)
        }
        window = DoubleArray(n)
        for (i in window.indices) window[i] =
            (0.42 - 0.5 * cos(2 * Math.PI * i / (n - 1))
                    + 0.08 * cos(4 * Math.PI * i / (n - 1)))
    }
}