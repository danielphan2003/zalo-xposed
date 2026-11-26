package io.github.danielphan2003.zaloxposed.features.recall

import io.github.danielphan2003.zaloxposed.core.Env
import io.github.danielphan2003.zaloxposed.core.Feature
import io.github.danielphan2003.zaloxposed.core.Reflect
import io.github.danielphan2003.zaloxposed.targets.ZaloTargets
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale

/**
 * When someone else recalls a message, Zalo's recall use case would wipe its text, delete its
 * local files and store a placeholder. Instead this keeps the message as it is, saves its text in
 * a file only this phone has, and puts a "recalled" label in front of the text, both now and
 * whenever Zalo builds the message again (reopening the chat, restarting the app). Recalls of
 * your own messages still go through.
 */
internal object RecallKeeper : Feature {
    override val name = "view recalled messages"

    override fun install(env: Env) {
        val t = env.targets
        val applySig = t.method(ZaloTargets.RECALL_APPLY) ?: error("recall use case not found")
        val (owner, fieldName, fieldType) = t.field(ZaloTargets.MESSAGE_TEXT_FIELD) ?: error("message text field not found")
        val isMineSig = t.method(ZaloTargets.MESSAGE_IS_MINE) ?: error("sent-by-me check not found")

        val messageClass = env.reflect.cls(owner)
        val textField = env.reflect.field(owner, fieldName, fieldType)
        val isMine = env.reflect.method(isMineSig) as Method
        val idField = messageIdField(messageClass) ?: error("message id field not found")
        val store = RecallStore(File(env.dataDir, "recalled.tsv"))

        fun keyOf(message: Any): String? = idField.get(message)?.toString()?.takeIf { it.isNotEmpty() }

        env.hooks.intercept(env.reflect.method(applySig), "recall use case") { chain ->
            val payload = chain.args.firstOrNull()
            val message = payload?.let { p ->
                Reflect.instanceFields(p.javaClass).firstNotNullOfOrNull { f -> f.get(p)?.takeIf(messageClass::isInstance) }
            }
            val key = message?.let(::keyOf)
            if (message == null || key == null || isMine.invoke(message) == true) {
                chain.proceed()
            } else {
                val text = textField.get(message) as? String
                store.put(key, text.orEmpty())
                if (!text.isNullOrEmpty()) textField.set(message, RecallMarker.mark(text, Locale.getDefault()))
                env.log.i("kept a recalled message")
                null
            }
        }.also { if (!it) error("recall use case hook refused") }

        val buildSig = t.method(ZaloTargets.MESSAGE_BUILD)
        if (buildSig == null) {
            env.log.w("recall: message builder not found; labels only show until the chat reloads")
            return
        }
        env.hooks.after(env.reflect.method(buildSig), "message builder") { _, result ->
            if (result == null || store.isEmpty()) return@after
            val original = keyOf(result)?.let(store::get) ?: return@after
            val current = textField.get(result) as? String
            val base = if (current.isNullOrEmpty()) original else current
            if (base.isNotEmpty()) textField.set(result, RecallMarker.mark(base, Locale.getDefault()))
        }
    }

    /** The message's one id field, typed by Zalo's `MessageId` class (a kept Parcelable name). */
    private fun messageIdField(messageClass: Class<*>): Field? =
        Reflect.instanceFields(messageClass).filter { it.type.name.endsWith(".MessageId") }.singleOrNull()
}
