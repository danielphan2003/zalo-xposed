package io.github.danielphan2003.zaloxposed.targets

import io.github.danielphan2003.zaloxposed.dex.CallBeforeStringQuery
import io.github.danielphan2003.zaloxposed.dex.DexScanner
import io.github.danielphan2003.zaloxposed.dex.MethodHit
import io.github.danielphan2003.zaloxposed.dex.MethodQuery
import io.github.danielphan2003.zaloxposed.dex.MethodSig

/**
 * Finds every hook target in Zalo's dex files by features that survive R8 renaming: string
 * constants, call shapes, and names that libraries, Room, layouts or the manifest force Zalo to
 * keep. Each target is found on its own, so one that goes missing only turns off the hooks that
 * need it.
 */
internal object ZaloTargets {

    /** Bump when any query changes, so cached results from an older module are not reused. */
    const val SPEC_VERSION = 1

    // Ads
    const val FEED_PRE_CHECK = "ads.feed.preCheck"
    const val FEED_ZINSTANT_VIEW = "ads.feed.zinstantView"
    const val CHAT_AD_VIEW = "ads.chat.view"
    const val CHAT_AD_BIND = "ads.chat.bind"
    const val REMOTE_CONFIG_INT = "ads.remoteConfig.int"
    const val VIDEO_AD_LIST = "ads.video.complexAds"
    const val VIDEO_AD_VIEWS = "ads.video.views"
    const val TRACKING_RECEIVER = "tracking.receiver"
    const val TRACKING_RECORD = "tracking.record"
    const val TRACKING_ENQUEUE = "tracking.enqueue"

    // Telemetry
    const val CRASHLYTICS_COLLECTION = "telemetry.crashlytics.collection"
    const val MEASUREMENT_DEACTIVATED = "telemetry.measurement.deactivated"
    const val MEASUREMENT_FLAG = "telemetry.measurement.flag"
    const val ANALYTICS_INSERT_ADAPTER = "telemetry.analytics.insertAdapter"

    // Recall
    const val RECALL_APPLY = "recall.apply"
    const val RECALL_DB_WRITE = "recall.dbWrite"
    const val MESSAGE_TEXT_FIELD = "recall.message.textField"
    const val MESSAGE_IS_MINE = "recall.message.isMine"
    const val MESSAGE_BUILD = "recall.message.build"

    /** Story and community ad slots switch on through these server config keys. */
    val AD_SLOT_CONFIG_KEYS = listOf("social@story@story_ads@enable", "community.community_ads.enable")

    private const val JSON_OBJECT = "org.json.JSONObject"
    private const val STRING = "java.lang.String"
    private const val CORE_UTILITY = "com.zing.zalocore.CoreUtility"

    private const val FEED_ZINSTANT_VIEW_NAME = "com.zing.zalo.social.presentation.timeline.components.ads.FeedItemZInstantAds"
    private const val CHAT_AD_VIEW_NAME = "com.zing.zalo.ui.widget.ZinstantAdItemView"
    private const val COMPLEX_ADS_NAME = "com.zing.zalo.shortvideo.domain.entity.ads.ComplexAds"
    private val VIDEO_AD_VIEW_NAMES = listOf(
        "com.zing.zalo.shortvideo.ui.ads.outstream.OutstreamAdsLayout",
        "com.zing.zalo.shortvideo.ui.component.rv.item.AdsNativeLayout",
        "com.zing.zalo.shortvideo.ui.component.rv.item.AdsTemplateLayout",
    )

    class Result(val targets: Targets, val problems: List<String>)

    fun resolve(scanner: DexScanner): Result {
        val out = LinkedHashMap<String, String>()
        val problems = ArrayList<String>()

        val first = scanner.scan(
            listOf(
                MethodQuery(
                    FEED_PRE_CHECK, returnType = "boolean", params = listOf(JSON_OBJECT), isStatic = true,
                    strings = listOf("pre_check"),
                    invokes = listOf("Lorg/json/JSONArray;->get(I)Ljava/lang/Object;"),
                ),
                MethodQuery("feedZinstantViewByString", strings = listOf("zinstantMediaType")),
                MethodQuery("chatAdViewByString", stringPrefixes = listOf("ZINSTANT_AD_CHECK_TRACK_ACTIVE_VIEW_")),
                MethodQuery("storyAdsKeyUser", strings = listOf(AD_SLOT_CONFIG_KEYS[0])),
                MethodQuery("communityAdsKeyUser", strings = listOf(AD_SLOT_CONFIG_KEYS[1])),
                MethodQuery(
                    TRACKING_RECEIVER, name = "onReceive", returnType = "void",
                    params = listOf("android.content.Context", "android.content.Intent"),
                    strings = listOf(
                        "com.zing.zalo.ads.receiver.AdsTrackingManager.HitUrls",
                        "com.zing.zalo.ads.receiver.AdsTrackingManager.SubmitBatch",
                    ),
                ),
                MethodQuery("trackingManagerAnchor", strings = listOf("Failed to set alarm for retrying to hit URLs")),
                MethodQuery(
                    "crashlyticsArbiterInit", name = "<init>",
                    strings = listOf("com.google.firebase.crashlytics", "firebase_crashlytics_collection_enabled"),
                ),
                MethodQuery(
                    MEASUREMENT_DEACTIVATED, returnType = "boolean", paramCount = 0, isStatic = false,
                    strings = listOf("firebase_analytics_collection_deactivated"),
                ),
                MethodQuery("analyticsEventsInsertSql", stringPrefixes = listOf("INSERT OR ABORT INTO `events` (")),
                MethodQuery(
                    RECALL_DB_WRITE, returnType = "void", paramCount = 3, isStatic = false,
                    strings = listOf("updateMessageUndo->updateChatMsgAPI", "isForceUpdateFields=true"),
                ),
            ),
            listOf(CallBeforeStringQuery("getMessage", "getMessage(...)")),
        )
        val hits = first.hits

        fun single(id: String, list: List<MethodHit> = hits.getValue(id)): MethodHit? =
            list.singleOrNull() ?: run {
                problems += "$id: expected one match, found ${list.size}${if (list.isEmpty()) "" else " $list"}"
                null
            }

        fun method(key: String, hit: MethodHit?) {
            if (hit != null) out[key] = Targets.method(hit.sig)
        }

        fun cls(key: String, name: String?) {
            if (name != null) out[key] = Targets.cls(name)
        }

        // Feed: the pre-check that decides whether a sponsored item is kept.
        method(FEED_PRE_CHECK, single(FEED_PRE_CHECK))

        // Feed: the Zinstant ad card. Layouts inflate it by name, so the name is kept; fall back to
        // the class whose bind code reads the "zinstantMediaType" field.
        cls(FEED_ZINSTANT_VIEW, preferNamed(scanner, FEED_ZINSTANT_VIEW_NAME, hits.getValue("feedZinstantViewByString"), problems))

        // Messages tab: the Zinstant ad row and its bind method (row data, the tab, a visibility flag).
        val chatView = preferNamed(scanner, CHAT_AD_VIEW_NAME, hits.getValue("chatAdViewByString"), problems)
        cls(CHAT_AD_VIEW, chatView)
        if (chatView != null) {
            method(CHAT_AD_BIND, single(CHAT_AD_BIND, scanner.methodsOf(chatView).filter {
                !it.isStatic && it.ref.name != "<init>" && it.returnType == "void" && it.params.size == 3 &&
                    it.params[2] == "boolean"
            }))
        }

        // Story and community ad slots: the static (key, default) -> int config getter both use.
        val storyCalls = configGetterCalls(hits.getValue("storyAdsKeyUser"))
        val communityCalls = configGetterCalls(hits.getValue("communityAdsKeyUser"))
        val shared = (storyCalls intersect communityCalls).toList()
        if (shared.size == 1) out[REMOTE_CONFIG_INT] = Targets.method(shared.single())
        else problems += "$REMOTE_CONFIG_INT: expected one shared config getter, found $shared"

        // Short videos: the ads response, and the ad overlay views (kept: inflated from layouts).
        cls(VIDEO_AD_LIST, COMPLEX_ADS_NAME.takeIf(scanner::hasClass) ?: null.also {
            problems += "$VIDEO_AD_LIST: $COMPLEX_ADS_NAME not found"
        })
        val videoViews = VIDEO_AD_VIEW_NAMES.filter(scanner::hasClass)
        if (videoViews.size < VIDEO_AD_VIEW_NAMES.size) problems += "$VIDEO_AD_VIEWS: missing ${VIDEO_AD_VIEW_NAMES - videoViews.toSet()}"
        if (videoViews.isNotEmpty()) out[VIDEO_AD_VIEWS] = Targets.classes(videoViews)

        // Ads tracking: the alarm receiver, the event recorder and the queue it writes through.
        method(TRACKING_RECEIVER, single(TRACKING_RECEIVER))
        single("trackingManagerAnchor")?.let { anchor ->
            val methods = scanner.methodsOf(anchor.className)
            method(TRACKING_RECORD, single(TRACKING_RECORD, methods.filter {
                !it.isStatic && it.returnType == "void" && it.params == listOf(
                    "int", "int", "int", "long", STRING, STRING, STRING, "java.util.ArrayList",
                )
            }))
            method(TRACKING_ENQUEUE, single(TRACKING_ENQUEUE, methods.filter { m ->
                !m.isStatic && m.returnType == "void" && m.params.size == 1 && !m.params[0].startsWith("java.") &&
                    m.invokes.any { it.name == "execute" && it.params == listOf("Ljava/lang/Runnable;") }
            }))
        }

        // Crashlytics: DataCollectionArbiter's "is automatic collection enabled" check.
        single("crashlyticsArbiterInit")?.let { init ->
            method(CRASHLYTICS_COLLECTION, single(CRASHLYTICS_COLLECTION, scanner.methodsOf(init.className).filter {
                !it.isStatic && !it.isAbstract && it.returnType == "boolean" && it.params.isEmpty()
            }))
        }

        // Firebase Analytics: the "collection deactivated" switch, and the manifest flag reader it calls.
        single(MEASUREMENT_DEACTIVATED)?.let { deactivated ->
            method(MEASUREMENT_DEACTIVATED, deactivated)
            val reader = deactivated.invokes.filter {
                it.className == deactivated.ref.className && it.params == listOf("Ljava/lang/String;") &&
                    it.returnType == "Ljava/lang/Boolean;"
            }.distinct()
            if (reader.size == 1) out[MEASUREMENT_FLAG] = Targets.method(MethodSig.of(reader.single()))
            else problems += "$MEASUREMENT_FLAG: expected one flag reader, found $reader"
        }

        // Zalo analytics: Room's shared insert adapter base, the superclass of the events adapter.
        val adapterBases = hits.getValue("analyticsEventsInsertSql").mapNotNull { scanner.superclassOf(it.className) }.distinct()
        if (adapterBases.size == 1) out[ANALYTICS_INSERT_ADAPTER] = Targets.cls(adapterBases.single())
        else problems += "$ANALYTICS_INSERT_ADAPTER: expected one adapter base, found $adapterBases"

        // Recall: the database write that turns a message into a recall placeholder, the message
        // class it takes, and the use case that calls it.
        val dbWrite = single(RECALL_DB_WRITE)
        if (dbWrite != null) {
            method(RECALL_DB_WRITE, dbWrite)
            resolveRecall(scanner, dbWrite, first.callsBeforeString.getValue("getMessage"), out, problems)
        }

        return Result(Targets(out), problems)
    }

    private fun resolveRecall(
        scanner: DexScanner,
        dbWrite: MethodHit,
        getMessageCalls: Map<io.github.danielphan2003.zaloxposed.dex.MethodRef, Int>,
        out: MutableMap<String, String>,
        problems: MutableList<String>,
    ) {
        val messageClass = dbWrite.params[0]
        val messageDescriptor = dbWrite.ref.params[0]
        val messageMethods = scanner.methodsOf(messageClass)

        // The text field: read by the getter Kotlin null-checks as "getMessage(...)".
        val getter = getMessageCalls.filterKeys {
            it.className == messageDescriptor && it.params.isEmpty() && it.returnType == "Ljava/lang/String;"
        }.maxByOrNull { it.value }?.key
        val getterHit = getter?.let { g -> messageMethods.firstOrNull { it.ref == g } }
        val textField = getterHit?.fields?.filter {
            !it.static && !it.write && it.ref.className == messageDescriptor && it.ref.type == "Ljava/lang/String;"
        }?.map { it.ref }?.distinct()
        if (textField?.size == 1) {
            val f = textField.single()
            out[MESSAGE_TEXT_FIELD] = Targets.field(messageClass, f.name, STRING)
        } else {
            problems += "$MESSAGE_TEXT_FIELD: getter=$getter fields=$textField"
        }

        // "Sent by me": compares one field with the signed-in user's id.
        val isMine = messageMethods.filter { m ->
            !m.isStatic && m.returnType == "boolean" && m.params.isEmpty() &&
                m.invokes.map { it.toString() } == listOf("Ljava/lang/String;->equals(Ljava/lang/Object;)Z") &&
                m.fields.any { it.static && !it.write && it.ref.className == "L${CORE_UTILITY.replace('.', '/')};" }
        }
        if (isMine.size == 1) out[MESSAGE_IS_MINE] = Targets.method(isMine.single().sig)
        else problems += "$MESSAGE_IS_MINE: expected one, found $isMine"

        // Second pass: the use case that applies an incoming recall, and the message builder.
        val ctors = messageMethods.filter { it.ref.name == "<init>" && it.params.size == 2 && it.params[0] == "int" }
            .map { it.ref.toString() }
        val second = scanner.scan(
            listOfNotNull(
                MethodQuery(RECALL_APPLY, returnType = "void", paramCount = 1, isStatic = false, invokes = listOf(dbWrite.ref.toString())),
                if (ctors.isEmpty()) null else MethodQuery(
                    MESSAGE_BUILD, returnType = messageClass, paramCount = 0, isStatic = false, invokesAnyOf = ctors,
                    filter = { it.className != messageClass },
                ),
            ),
        )
        second.hits[RECALL_APPLY].orEmpty().let { list ->
            if (list.size == 1) out[RECALL_APPLY] = Targets.method(list.single().sig)
            else problems += "$RECALL_APPLY: expected one, found $list"
        }
        second.hits[MESSAGE_BUILD].orEmpty().let { list ->
            if (list.size == 1) out[MESSAGE_BUILD] = Targets.method(list.single().sig)
            else problems += "$MESSAGE_BUILD: expected one, found $list (constructors $ctors)"
        }
    }

    /** Static `(String, int) -> int` methods called by every hit. */
    private fun configGetterCalls(users: List<MethodHit>): Set<MethodSig> = users.map { user ->
        user.invokes.filter { it.params == listOf("Ljava/lang/String;", "I") && it.returnType == "I" }
            .map { MethodSig.of(it) }.toSet()
    }.reduceOrNull { a, b -> a intersect b }.orEmpty()

    /** [name] when the dex defines it; otherwise the single class declaring all [fallback] hits. */
    private fun preferNamed(scanner: DexScanner, name: String, fallback: List<MethodHit>, problems: MutableList<String>): String? {
        if (scanner.hasClass(name)) return name
        val classes = fallback.map { it.className }.distinct()
        return classes.singleOrNull().also {
            if (it == null) problems += "$name: not found by name, fallback found $classes"
        }
    }
}
