package io.github.danielphan2003.zaloxposed

import android.app.Application
import io.github.danielphan2003.zaloxposed.core.Env
import io.github.danielphan2003.zaloxposed.core.Hooks
import io.github.danielphan2003.zaloxposed.core.Log
import io.github.danielphan2003.zaloxposed.core.Reflect
import io.github.danielphan2003.zaloxposed.features.ads.AdSlotConfig
import io.github.danielphan2003.zaloxposed.features.ads.AdtimaRequests
import io.github.danielphan2003.zaloxposed.features.ads.ChatListAds
import io.github.danielphan2003.zaloxposed.features.ads.FeedAds
import io.github.danielphan2003.zaloxposed.features.ads.ShortVideoAds
import io.github.danielphan2003.zaloxposed.targets.TargetResolver
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.io.File

/** Entry point, named in META-INF/xposed/java_init.list. */
class ZaloModule : XposedModule() {

    private var processName: String? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        processName = param.processName
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != ZALO || !param.isFirstPackage) return
        // Zalo's ads, telemetry and messaging all run in its main process; the :animation and
        // :video_processing helpers need nothing.
        val process = processName ?: runCatching { Application.getProcessName() }.getOrNull()
        if (process != null && process != ZALO) return

        val log = Log(this)
        try {
            log.i("zalo-xposed ${BuildConfig.MODULE_VERSION} on $frameworkName $frameworkVersion (API $apiVersion)")
            val appInfo = param.applicationInfo
            val dataDir = File(appInfo.dataDir, "no_backup/zalo-xposed")
            val targets = TargetResolver.resolve(
                appInfo, param.classLoader, BuildConfig.MODULE_VERSION, File(dataDir, "targets.txt"), log,
            )
            val env = Env(Hooks(this, log), Reflect(param.classLoader), targets, log, appInfo)
            for (feature in FEATURES) {
                try {
                    feature.install(env)
                    log.i("${feature.name}: installed")
                } catch (t: Throwable) {
                    log.w("${feature.name}: skipped", t)
                }
            }
        } catch (t: Throwable) {
            log.e("startup failed; Zalo runs unmodified", t)
        }
    }

    companion object {
        const val ZALO = "com.zing.zalo"

        private val FEATURES = listOf(
            // Ads
            FeedAds, ChatListAds, AdSlotConfig, ShortVideoAds, AdtimaRequests,
        )
    }
}
