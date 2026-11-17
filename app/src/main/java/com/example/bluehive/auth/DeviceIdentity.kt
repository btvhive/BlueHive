package com.example.bluehive.auth

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore

/**
 * Who this device is, and how it proves it.
 *
 * TWO DIFFERENT THINGS, and conflating them is the mistake this file exists to
 * avoid:
 *
 *   FINGERPRINT (ANDROID_ID) is a LABEL. The device owner can read it, so
 *   anyone can claim it. It is useful only to RECOGNISE a returning TV — never
 *   to authorise one. The backend treats it the same way: bluehive.devices has
 *   it as an ordinary column, not a credential.
 *
 *   KEY (Android Keystore) is a PROOF. The private half is generated inside the
 *   secure element and cannot be extracted, even by the device's owner. Only the
 *   public half is ever sent. A server challenge signed by that key is something
 *   a copied fingerprint cannot produce.
 *
 * WHAT SURVIVES WHAT
 *   reinstall       fingerprint survives, KEY IS DESTROYED (Android deletes
 *                   Keystore entries with the app) — so a reinstalled app
 *                   registers a new key and re-links by logging in.
 *   factory reset   both change. The device is genuinely new.
 *
 * That asymmetry is why the account link comes from the user's credentials and
 * not from the fingerprint: a stolen ANDROID_ID gets an attacker a device row
 * with no account attached, which is worth nothing.
 */
object DeviceIdentity {

    private const val TAG = "DeviceIdentity"
    private const val KEY_ALIAS = "bluehive_device_key"

    // Where the fallback fingerprint is kept once minted. Its own file rather
    // than the session store, because it has to outlive a sign-out: the whole
    // point is that the same hardware is recognised as the same device the next
    // time somebody signs in on it.
    private const val FALLBACK_PREFS = "bluehive_device_identity"
    private const val FALLBACK_KEY   = "fallback_fingerprint"

    /**
     * Stable per device + per signing key, survives reinstall, changes on factory
     * reset. Scoped to our signing key since Android 8, so no other developer's
     * app sees the same value.
     *
     * Falls back to a random value only if ANDROID_ID is unreadable. That value
     * is PERSISTED, and the difference is not cosmetic: the fingerprint is what
     * the backend keys bluehive.devices on, one row per (user, fingerprint), and
     * a device row is what owns a share of the per-device profile quota. Minting
     * a fresh UUID per call — which this did until 2026-09-20 — meant a TV with
     * an unusable ANDROID_ID got a NEW device row on every single sign-in, and
     * with it a fresh allowance of profiles, without limit. It also scattered
     * that one television across the account's device list as a pile of
     * strangers.
     *
     * Persisted, the fallback survives sign-out and app restarts and dies with
     * the app's data — so a reinstall still looks like a new device, which is
     * the documented and acceptable degradation.
     */
    @SuppressLint("HardwareIds")
    fun fingerprint(context: Context): String {
        val id = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        if (id.isNullOrBlank() || id == "9774d56d682e549c") {
            // That literal is a well-known buggy constant shipped by a batch of
            // early devices, where every unit reported the same ANDROID_ID.
            // Treating it as unique would merge unrelated TVs into one device row.
            return persistentFallback(context)
        }
        return id
    }

    /**
     * The stored fallback id, minted once on first need.
     *
     * Not synchronized: two threads racing here would each write a UUID and the
     * last one wins, which costs one spurious device row exactly once in the
     * life of an install — far cheaper than holding a lock on a path that runs
     * during sign-in. Any later read returns whichever value landed.
     */
    private fun persistentFallback(context: Context): String {
        val prefs = context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        prefs.getString(FALLBACK_KEY, null)?.let { stored ->
            if (stored.isNotBlank()) return stored
        }
        val minted = "fallback-" + java.util.UUID.randomUUID().toString()
        prefs.edit().putString(FALLBACK_KEY, minted).apply()
        Log.w(TAG, "ANDROID_ID unusable — minted a persistent fallback id")
        return minted
    }

    /**
     * Public half of a hardware-backed keypair, base64 X.509, created on first
     * call and reused thereafter.
     *
     * Returns null when the device has no usable keystore. Low-end TV boxes
     * genuinely ship broken or absent TEEs, so this MUST degrade rather than
     * throw — a device that cannot hold a key still deserves to sign in. The
     * backend stores public_key as nullable for exactly this reason.
     */
    fun publicKeyBase64(context: Context): String? = runCatching {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (!ks.containsAlias(KEY_ALIAS)) when (keyPath()) {
            KeyPath.NONE -> {
                Log.i(TAG, "Android ${Build.VERSION.SDK_INT}: no Keystore EC keys, registering without a key")
                return@runCatching null
            }
            KeyPath.LEGACY -> {
                createLegacyKey(context)
                Log.i(TAG, "device keypair created in Keystore (Android ${Build.VERSION.SDK_INT}, legacy spec)")
            }
            // keyPath() already said Android 6+; the check repeats it for Lint.
            KeyPath.MODERN -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                    // Deliberately NOT setUserAuthenticationRequired(true): a TV has
                    // no lock screen in most living rooms, and requiring auth would
                    // make the key unusable on the devices we actually ship to.
                    .build()

                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                    .apply { initialize(spec) }
                    .generateKeyPair()
                Log.i(TAG, "device keypair created in Keystore")
            }
        }

        val cert = ks.getCertificate(KEY_ALIAS) ?: return@runCatching null
        Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
    }.onFailure {
        Log.w(TAG, "no usable keystore (${it.message}) — registering without a key")
    }.getOrNull()

    /**
     * How this Android version makes the device key (Android 5 support, 2026-11-17).
     * MODERN: KeyGenParameterSpec, Android 6+, unchanged. LEGACY: Android 4.4-5.1,
     * the older KeyPairGeneratorSpec, which can also make an EC P-256 key in the
     * Android Keystore (EC arrived in 4.4), so the server receives the same kind
     * of public key. NONE: older still; register without one. Pure, so it is tested.
     */
    enum class KeyPath { MODERN, LEGACY, NONE }

    fun keyPath(sdk: Int = Build.VERSION.SDK_INT): KeyPath = when {
        sdk >= Build.VERSION_CODES.M -> KeyPath.MODERN
        sdk >= Build.VERSION_CODES.KITKAT -> KeyPath.LEGACY
        else -> KeyPath.NONE
    }

    /** Android 4.4-5.1: the same EC P-256 key, made with the pre-Android-6 spec. */
    @Suppress("DEPRECATION")
    private fun createLegacyKey(context: Context) {
        val now = java.util.Calendar.getInstance()
        // 20 years, ending before 2050: older keystores mishandle certificate
        // dates written in the post-2049 format.
        val end = (now.clone() as java.util.Calendar).apply { add(java.util.Calendar.YEAR, 20) }
        val spec = android.security.KeyPairGeneratorSpec.Builder(context.applicationContext)
            .setAlias(KEY_ALIAS)
            .setKeyType("EC")
            .setKeySize(256)
            .setSubject(javax.security.auth.x500.X500Principal("CN=$KEY_ALIAS"))
            .setSerialNumber(java.math.BigInteger.ONE)
            .setStartDate(now.time)
            .setEndDate(end.time)
            .build()
        // "RSA" IS RIGHT EVEN FOR AN EC KEY. Before Android 6 the AndroidKeyStore
        // provider registers its generator only as KeyPairGenerator.RSA; the key
        // type comes from setKeyType("EC") above. getInstance("EC", ...) throws
        // "KeyPairGenerator EC implementation not found" (Android 5.1 emulator,
        // 2026-11-17), which left the TV with no key at all.
        KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
            .apply { initialize(spec) }
            .generateKeyPair()
    }

    /**
     * Which app installed this APK, or null when nothing claimed it (adb, or an
     * installer that set no source).
     *
     * CLIENT-REPORTED, THEREFORE FORGEABLE. Patch the APK and this returns
     * anything. It is a label for the admin view — how did this user arrive —
     * and must never gate an entitlement decision. The backend derives
     * `host_installed` from it on the same understanding.
     *
     * getInstallSourceInfo() needs API 30; minSdk here is 25, hence the
     * deprecated fallback for older TV boxes.
     */
    @Suppress("DEPRECATION")
    fun installSource(context: Context): String? = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            pm.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    /** Human label for the admin view, e.g. "onn 4K Streaming Box". */
    fun deviceName(): String =
        listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .trim()
            .ifBlank { "Android TV" }
            .take(255)
}
