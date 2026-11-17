package com.example.bluehive.net

import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * HTTPS on Android 5-7.0 (2026-11-17, for the 2016 Fire TV Stick on Fire OS 5).
 *
 * THE PROBLEM. Our servers, TMDB's images and GitHub's downloads use Let's
 * Encrypt certificates, which chain to ISRG Root X1. Android only ships that
 * root from 7.1.1 (API 25); older devices that never got it refuse the
 * connection ("Trust anchor for certification path not found").
 *
 * THE FIX. Below API 25 every OkHttp client and every HttpURLConnection trusts
 * the TV's own roots PLUS the two ISRG roots shipped in the APK
 * (resources/android5/, fingerprints checked in Android5TlsTest), and video skips
 * Cronet (ExoPlayerConfig), whose trust store can't be extended, for OkHttp.
 * From API 25 on nothing changes at all.
 */
object Android5Tls {

    private const val TAG = "Android5Tls"

    /** Android 7.1.1. The first version whose own trust store has ISRG Root X1. */
    const val FIRST_SDK_WITH_ISRG_ROOT = 25

    /** The roots shipped in the APK, as Java resources (no Context needed). */
    val BUNDLED_ROOTS = listOf("/android5/isrg_root_x1.pem", "/android5/isrg_root_x2.pem")

    fun needsBundledRoots(sdk: Int = Build.VERSION.SDK_INT): Boolean = sdk < FIRST_SDK_WITH_ISRG_ROOT

    /** Reads PEM certificates. Pure, so the shipped files are checked in a unit test. */
    fun readCertificates(input: InputStream): List<X509Certificate> =
        input.use { CertificateFactory.getInstance("X.509").generateCertificates(it).map { c -> c as X509Certificate } }

    fun bundledRoots(): List<X509Certificate> = BUNDLED_ROOTS.flatMap { path ->
        val stream = Android5Tls::class.java.getResourceAsStream(path)
            ?: error("missing $path in the APK")
        readCertificates(stream)
    }

    /** The TV's own roots plus the bundled ones, built once, only when needed. */
    private val trust: Pair<SSLSocketFactory, X509TrustManager>? by lazy {
        if (!needsBundledRoots()) return@lazy null
        runCatching {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            // The device's roots first, so nothing that worked before stops working.
            val system = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(null as KeyStore?) }
                .trustManagers.filterIsInstance<X509TrustManager>().first()
            system.acceptedIssuers.forEachIndexed { i, cert -> store.setCertificateEntry("system-$i", cert) }
            bundledRoots().forEachIndexed { i, cert -> store.setCertificateEntry("bundled-$i", cert) }
            val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(store) }
                .trustManagers.filterIsInstance<X509TrustManager>().first()
            val ctx = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
            Log.i(TAG, "Android ${Build.VERSION.SDK_INT}: trusting ${system.acceptedIssuers.size} device roots + " +
                "${BUNDLED_ROOTS.size} bundled Let's Encrypt roots")
            ctx.socketFactory to tm
        }.onFailure { Log.e(TAG, "could not add the bundled roots: ${it.message}") }.getOrNull()
    }

    /** Call once from Application.onCreate: covers every HttpURLConnection. No-op from API 25. */
    fun install() {
        trust?.let { (factory, _) -> HttpsURLConnection.setDefaultSSLSocketFactory(factory) }
    }

    fun apply(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        trust?.let { (factory, tm) -> builder.sslSocketFactory(factory, tm) }
        return builder
    }
}

/** Every OkHttp client in the app goes through this (Android5TlsTest checks the source). */
fun OkHttpClient.Builder.android5Tls(): OkHttpClient.Builder = Android5Tls.apply(this)
