package io.github.danielphan2003.zaloxposed.core

import android.content.pm.ApplicationInfo
import io.github.danielphan2003.zaloxposed.targets.Targets
import java.io.File

/** What a feature needs to install its hooks. */
internal class Env(
    val hooks: Hooks,
    val reflect: Reflect,
    val targets: Targets,
    val log: Log,
    val appInfo: ApplicationInfo,
) {
    /** The module's private directory inside Zalo's data, excluded from Android backups. */
    val dataDir: File get() = File(appInfo.dataDir, "no_backup/zalo-xposed")
}

/**
 * One self-contained change. [install] may throw; the caller logs it and moves on, so a feature
 * whose targets are gone never takes the others down with it.
 */
internal interface Feature {
    val name: String
    fun install(env: Env)
}

/** Runs [block] for one hook target, logging instead of throwing. Returns true on success. */
internal inline fun Env.step(label: String, block: () -> Boolean): Boolean = try {
    block().also { if (!it) log.w("$label: skipped") }
} catch (t: Throwable) {
    log.w("$label: skipped", t)
    false
}
