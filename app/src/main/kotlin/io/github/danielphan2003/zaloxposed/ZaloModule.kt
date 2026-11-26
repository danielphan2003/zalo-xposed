package io.github.danielphan2003.zaloxposed

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/** Entry point, named in META-INF/xposed/java_init.list. */
class ZaloModule : XposedModule() {

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != ZALO || !param.isFirstPackage) return
        log(Log.INFO, TAG, "zalo-xposed ${BuildConfig.MODULE_VERSION} loaded")
    }

    companion object {
        const val ZALO = "com.zing.zalo"
        private const val TAG = "ZaloXposed"
    }
}
