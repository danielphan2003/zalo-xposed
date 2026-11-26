package io.github.danielphan2003.zaloxposed.targets

import android.content.pm.ApplicationInfo
import io.github.danielphan2003.zaloxposed.core.Log
import io.github.danielphan2003.zaloxposed.dex.ApkDex
import io.github.danielphan2003.zaloxposed.dex.DexScanner
import java.io.File

/** Finds Zalo's APK on the device and resolves targets from it, through the on-disk cache. */
internal object TargetResolver {

    fun resolve(appInfo: ApplicationInfo, classLoader: ClassLoader, moduleVersion: String, cacheFile: File, log: Log): Targets {
        val apks = candidateApks(appInfo, classLoader)
        val cache = TargetCache(cacheFile)
        val key = TargetCache.key(apks, moduleVersion)
        cache.load(key)?.let {
            log.i("targets: ${it.entries.size} from cache")
            return it
        }
        val started = System.nanoTime()
        val dexes = apks.flatMap { apk -> runCatching { ApkDex.read(apk) }.getOrElse { emptyList() } }
        val result = ZaloTargets.resolve(DexScanner(dexes))
        val ms = (System.nanoTime() - started) / 1_000_000
        log.i("targets: resolved ${result.targets.entries.size} from ${dexes.size} dex in $ms ms")
        result.problems.forEach { log.w("targets: $it") }
        cache.save(key, result.targets)
        return result.targets
    }

    /**
     * The APKs whose code Zalo runs. Under LSPatch the installed APK holds only the loader, and the
     * original Zalo APK is extracted to Zalo's cache; [ApplicationInfo.sourceDir] and the class
     * loader both point at it by the time modules load. Both are checked, keeping files with dex.
     */
    private fun candidateApks(appInfo: ApplicationInfo, classLoader: ClassLoader): List<File> {
        val paths = LinkedHashSet<String>()
        paths += classLoaderPaths(classLoader)
        appInfo.sourceDir?.let(paths::add)
        appInfo.splitSourceDirs?.let(paths::addAll)
        return paths.map(::File).filter { it.isFile && ApkDex.hasDex(it) }
    }

    private fun classLoaderPaths(classLoader: ClassLoader): List<String> = runCatching {
        val base = Class.forName("dalvik.system.BaseDexClassLoader")
        if (!base.isInstance(classLoader)) return emptyList()
        val pathList = base.getDeclaredField("pathList").apply { isAccessible = true }.get(classLoader)
        val elements = pathList.javaClass.getDeclaredField("dexElements").apply { isAccessible = true }.get(pathList) as Array<*>
        elements.mapNotNull { element ->
            element ?: return@mapNotNull null
            val pathField = element.javaClass.declaredFields.firstOrNull { it.type == File::class.java && it.name == "path" }
                ?: element.javaClass.declaredFields.firstOrNull { it.type == File::class.java }
            (pathField?.apply { isAccessible = true }?.get(element) as? File)?.path
        }
    }.getOrDefault(emptyList())
}
