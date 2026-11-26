package io.github.danielphan2003.zaloxposed.features.ads

import android.view.View
import io.github.danielphan2003.zaloxposed.core.Env
import io.github.danielphan2003.zaloxposed.core.Feature
import io.github.danielphan2003.zaloxposed.core.Reflect
import io.github.danielphan2003.zaloxposed.core.step
import io.github.danielphan2003.zaloxposed.targets.ZaloTargets
import org.json.JSONObject
import java.lang.reflect.Modifier

/** Nhật ký feed: drop sponsored items while parsing, and hide any Zinstant ad card left over. */
internal object FeedAds : Feature {
    override val name = "feed ads"

    override fun install(env: Env) {
        env.step("feed pre-check") {
            val sig = env.targets.method(ZaloTargets.FEED_PRE_CHECK) ?: return@step false
            // Zalo keeps the item when this returns true; turn that into false for sponsored items.
            env.hooks.intercept(env.reflect.method(sig), "feed pre-check") { chain ->
                val keep = chain.proceed()
                val item = chain.args.firstOrNull() as? JSONObject
                if (keep == true && item != null && SponsoredJson.isSponsored(item)) false else keep
            }
        }
        env.step("feed Zinstant card") {
            val cls = env.targets.cls(ZaloTargets.FEED_ZINSTANT_VIEW)?.let(env.reflect::cls) ?: return@step false
            hideOnConstruct(env, cls, "feed Zinstant card")
            cls.declaredMethods.filter {
                it.name == "getZInstantLayoutHeight" && it.parameterCount == 0 && it.returnType == Integer.TYPE
            }.forEach { env.hooks.replace(it, "feed Zinstant height", 0) }
            true
        }
    }
}

/** Messages tab: hide the Zinstant ad row and skip loading its content. */
internal object ChatListAds : Feature {
    override val name = "messages tab ads"

    override fun install(env: Env) {
        env.step("messages ad row") {
            val cls = env.targets.cls(ZaloTargets.CHAT_AD_VIEW)?.let(env.reflect::cls) ?: return@step false
            hideOnConstruct(env, cls, "messages ad row")
        }
        env.step("messages ad bind") {
            val sig = env.targets.method(ZaloTargets.CHAT_AD_BIND) ?: return@step false
            env.hooks.intercept(env.reflect.method(sig), "messages ad bind") { chain ->
                (chain.thisObject as? View)?.let(AdViews::hide)
                null
            }
        }
    }
}

/** Stories and communities: switch their ad slots off at the server config flag. */
internal object AdSlotConfig : Feature {
    override val name = "story and community ad slots"

    override fun install(env: Env) {
        env.step("ad slot config") {
            val sig = env.targets.method(ZaloTargets.REMOTE_CONFIG_INT) ?: return@step false
            val keys = ZaloTargets.AD_SLOT_CONFIG_KEYS.toSet()
            env.hooks.intercept(env.reflect.method(sig), "ad slot config") { chain ->
                if (chain.args.firstOrNull() in keys) 0 else chain.proceed()
            }
        }
    }
}

/** Zalo Video: drop ads from the ads response and hide outstream, native and CTA ad overlays. */
internal object ShortVideoAds : Feature {
    override val name = "Zalo Video ads"

    override fun install(env: Env) {
        env.step("video ads response") {
            val cls = env.targets.cls(ZaloTargets.VIDEO_AD_LIST)?.let(env.reflect::cls) ?: return@step false
            var hooked = false
            for (ctor in cls.declaredConstructors) {
                hooked = env.hooks.after(ctor, "video ads response") { chain, _ ->
                    chain.thisObject?.let(AdLists::emptyNestedLists)
                } || hooked
            }
            hooked
        }
        for (name in env.targets.classes(ZaloTargets.VIDEO_AD_VIEWS)) {
            env.step("video ad view ${name.substringAfterLast('.')}") {
                hideOnConstruct(env, env.reflect.cls(name), "video ad view")
            }
        }
    }
}

/** The Adtima ad SDK: its load entry points do nothing, as if every ad zone were empty. */
internal object AdtimaRequests : Feature {
    override val name = "Adtima ad requests"

    private val CLASSES = listOf(
        "com.adtima.ads.ZAdsNative", "com.adtima.ads.ZAdsBundle", "com.adtima.ads.ZAdsBanner",
        "com.adtima.ads.ZAdsInterstitial", "com.adtima.ads.ZAdsIncentivized", "com.adtima.ads.ZAdsVideo",
        "com.adtima.ads.ZAdsAudio", "com.adtima.ads.ZAdsVideoSuite", "com.adtima.ads.videoroll.ZAdsVideoRoll",
        "com.adtima.ads.videoroll.ZAdsVideoRollOne",
    )
    private val ENTRY_POINTS = setOf("loadAds", "preloadAds")

    override fun install(env: Env) {
        var blocked = 0
        for (name in CLASSES) {
            env.step("Adtima ${name.substringAfterLast('.')}") {
                val cls = env.reflect.clsOrNull(name) ?: return@step false
                val methods = cls.declaredMethods.filter {
                    it.name in ENTRY_POINTS && it.returnType == Void.TYPE && !Modifier.isAbstract(it.modifiers)
                }
                methods.forEach { if (env.hooks.skip(it, "Adtima ${cls.simpleName}.${it.name}")) blocked++ }
                methods.isNotEmpty()
            }
        }
        env.log.i("Adtima: blocked $blocked load entry points")
    }
}

/** Hooks every constructor of [cls] (subclasses included, as they call it) to hide the view. */
internal fun hideOnConstruct(env: Env, cls: Class<*>, label: String): Boolean {
    var hooked = false
    for (ctor in cls.declaredConstructors) {
        hooked = env.hooks.after(ctor, label) { chain, _ -> (chain.thisObject as? View)?.let(AdViews::hide) } || hooked
    }
    return hooked
}

/** Empties the lists an ads response holds, directly or one object deep (its section). */
internal object AdLists {
    fun emptyNestedLists(response: Any) {
        for (field in Reflect.instanceFields(response.javaClass)) {
            val value = field.get(response) ?: continue
            if (value is List<*>) {
                field.set(response, ArrayList<Any?>())
                continue
            }
            if (value.javaClass.isPlatformType()) continue
            for (inner in Reflect.instanceFields(value.javaClass)) {
                if (List::class.java.isAssignableFrom(inner.type) && inner.get(value) != null) {
                    inner.set(value, ArrayList<Any?>())
                }
            }
        }
    }

    /** Strings, boxed numbers and other JDK or Kotlin types: never an ads section. */
    private fun Class<*>.isPlatformType() = name.startsWith("java.") || name.startsWith("kotlin.")
}
