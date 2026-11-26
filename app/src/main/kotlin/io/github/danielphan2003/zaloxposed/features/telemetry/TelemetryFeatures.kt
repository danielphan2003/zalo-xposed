package io.github.danielphan2003.zaloxposed.features.telemetry

import android.content.Context
import io.github.danielphan2003.zaloxposed.core.Env
import io.github.danielphan2003.zaloxposed.core.Feature
import io.github.danielphan2003.zaloxposed.core.step
import io.github.danielphan2003.zaloxposed.targets.ZaloTargets
import java.lang.reflect.Modifier

/** Firebase Crashlytics: collection off, and its logging and reporting entry points do nothing. */
internal object Crashlytics : Feature {
    override val name = "Crashlytics"

    private const val API = "com.google.firebase.crashlytics.FirebaseCrashlytics"
    private val SILENCED = setOf(
        "log", "recordException", "setCustomKey", "setCustomKeys", "setUserId", "sendUnsentReports",
    )

    override fun install(env: Env) {
        env.step("Crashlytics collection switch") {
            val sig = env.targets.method(ZaloTargets.CRASHLYTICS_COLLECTION) ?: return@step false
            env.hooks.replace(env.reflect.method(sig), "Crashlytics collection", false)
        }
        env.step("Crashlytics API") {
            val api = env.reflect.clsOrNull(API) ?: return@step false
            var hooked = 0
            for (m in api.declaredMethods) {
                if (Modifier.isStatic(m.modifiers)) continue
                when {
                    m.name in SILENCED && m.returnType == Void.TYPE ->
                        if (env.hooks.skip(m, "Crashlytics.${m.name}")) hooked++
                    m.name == "setCrashlyticsCollectionEnabled" && m.parameterCount == 1 ->
                        if (env.hooks.intercept(m, "Crashlytics.${m.name}") { chain ->
                                val off: Any = if (m.parameterTypes[0] == java.lang.Boolean.TYPE) false else java.lang.Boolean.FALSE
                                chain.proceed(arrayOf(off))
                            }
                        ) hooked++
                }
            }
            hooked > 0
        }
    }
}

/** Google/Firebase Analytics: report collection as deactivated, as the manifest flag would. */
internal object FirebaseAnalytics : Feature {
    override val name = "Firebase Analytics"

    /** Manifest flags the measurement SDK reads, and the value that turns each one off. */
    val FLAGS = mapOf(
        "firebase_analytics_collection_deactivated" to true,
        "firebase_analytics_collection_enabled" to false,
        "google_analytics_adid_collection_enabled" to false,
        "google_analytics_ssaid_collection_enabled" to false,
        "google_analytics_default_allow_ad_personalization_signals" to false,
        "google_analytics_default_allow_ad_user_data" to false,
        "google_analytics_default_allow_analytics_storage" to false,
        "google_analytics_default_allow_ad_storage" to false,
    )

    override fun install(env: Env) {
        env.step("measurement deactivated") {
            val sig = env.targets.method(ZaloTargets.MEASUREMENT_DEACTIVATED) ?: return@step false
            env.hooks.replace(env.reflect.method(sig), "measurement deactivated", true)
        }
        env.step("measurement manifest flags") {
            val sig = env.targets.method(ZaloTargets.MEASUREMENT_FLAG) ?: return@step false
            env.hooks.intercept(env.reflect.method(sig), "measurement flags") { chain ->
                FLAGS[chain.args.firstOrNull()]?.let { java.lang.Boolean.valueOf(it) } ?: chain.proceed()
            }
        }
    }
}

/** Google's advertising ID: every caller gets the all-zero ID with ad tracking limited. */
internal object AdvertisingId : Feature {
    override val name = "advertising ID"

    private const val CLIENT = "com.google.android.gms.ads.identifier.AdvertisingIdClient"
    const val ZERO_ID = "00000000-0000-0000-0000-000000000000"

    override fun install(env: Env) {
        val client = env.reflect.clsOrNull(CLIENT) ?: run { env.log.w("advertising ID: client not found"); return }
        val info = runCatching {
            env.reflect.cls("$CLIENT\$Info")
                .getDeclaredConstructor(String::class.java, java.lang.Boolean.TYPE)
                .apply { isAccessible = true }
                .newInstance(ZERO_ID, true)
        }.getOrElse {
            env.log.w("advertising ID: cannot build the zero ID", it)
            return
        }
        env.step("AdvertisingIdClient.getAdvertisingIdInfo") {
            env.hooks.replace(client.getDeclaredMethod("getAdvertisingIdInfo", Context::class.java), "getAdvertisingIdInfo", info)
        }
        val gotInfo = env.step("AdvertisingIdClient.getInfo") {
            env.hooks.replace(client.getDeclaredMethod("getInfo"), "getInfo", info)
        }
        if (gotInfo) {
            // With getInfo answered locally, start() need not bind to Play services at all.
            env.step("AdvertisingIdClient.start") {
                env.hooks.skip(client.getDeclaredMethod("start"), "AdvertisingIdClient.start")
            }
        }
    }
}

/** Zalo's own analytics: inserts into its events, screens, sessions and views tables do nothing. */
internal object AnalyticsDatabase : Feature {
    override val name = "analytics database"

    private const val DATABASE = "com.zing.zalo.analytics.db.AnalyticsRoomDatabase"

    override fun install(env: Env) {
        env.step("analytics inserts") {
            val analytics = env.reflect.cls(DATABASE)
            val roomDatabase = analytics.superclass ?: return@step false
            val adapterBase = env.targets.cls(ZaloTargets.ANALYTICS_INSERT_ADAPTER)?.let(env.reflect::cls) ?: return@step false
            val dbField = RoomInserts.databaseField(adapterBase, roomDatabase) ?: return@step false
            val inserts = RoomInserts.insertMethods(adapterBase)
            var hooked = 0
            for (m in inserts) {
                m.isAccessible = true
                val skipped = RoomInserts.skippedResult(m)
                if (env.hooks.intercept(m, "analytics insert ${m.name}") { chain ->
                        if (analytics.isInstance(dbField.get(chain.thisObject))) skipped else chain.proceed()
                    }
                ) hooked++
            }
            env.log.i("analytics database: ${inserts.size} insert methods, $hooked hooked")
            hooked > 0
        }
    }
}

/** Zalo's ad tracking: impression and click events are not recorded, queued or sent. */
internal object AdsTracking : Feature {
    override val name = "ad tracking"

    override fun install(env: Env) {
        for ((key, label) in listOf(
            ZaloTargets.TRACKING_RECEIVER to "ad tracking alarm receiver",
            ZaloTargets.TRACKING_RECORD to "ad tracking recorder",
            ZaloTargets.TRACKING_ENQUEUE to "ad tracking queue",
        )) {
            env.step(label) {
                val sig = env.targets.method(key) ?: return@step false
                env.hooks.skip(env.reflect.method(sig), label)
            }
        }
    }
}
