package org.kbroman.android.polarpvc2

class FFT(n: Int) {
    private val n: Int
    private val m: Int

    private val cos: DoubleArray
    private val sin: DoubleArray

    init {
        this.n = n
        this.m = (Math.log(n.toDouble()) / Math.log(2.0)).toInt()

        // Make sure n is a power of 2
        if (n != 1 shl m)
            throw RuntimeException("FFT length must be power of 2")

        // Precompute tables
        cos = DoubleArray(n / 2)
        sin = DoubleArray(n / 2)

        for (i in 0 until n / 2) {
            cos[i] = Math.cos(-2.0 * Math.PI * i / n)
            sin[i] = Math.sin(-2.0 * Math.PI * i / n)
        }
    }

    fun fft(x: DoubleArray, y: DoubleArray) {
        var i: Int
        var j = 0
        var n1: Int
        var n2 = 1
        var a: Int
        var c: Double
        var s: Double
        var t1: Double
        var t2: Double

        // Bit-reverse
        i = 0
        while (i < n - 1) {
            if (i < j) {
                t1 = x[i]
                x[i] = x[j]
                x[j] = t1
                t1 = y[i]
                y[i] = y[j]
                y[j] = t1
            }
            n1 = n / 2
            while (n1 <= j) {
                j -= n1
                n1 /= 2
            }
            j += n1
            i++
        }

        // FFT
        for (i in 0 until m) {
            n1 = n2
            n2 = n2 shl 1
            a = 0
            while (a < n) {
                for (k in 0 until n1) {
                    c = cos[k * n / n2]
                    s = sin[k * n / n2]
                    t1 = c * x[a + k + n1] - s * y[a + k + n1]
                    t2 = s * x[a + k + n1] + c * y[a + k + n1]
                    x[a + k + n1] = x[a + k] - t1
                    y[a + k + n1] = y[a + k] - t2
                    x[a + k] += t1
                    y[a + k] += t2
                }
                a += n2
            }
        }
    }
}