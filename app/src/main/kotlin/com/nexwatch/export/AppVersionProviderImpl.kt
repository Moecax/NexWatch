package com.nexwatch.export

import com.nexwatch.BuildConfig
import com.nexwatch.core.export.AppVersionProvider
import javax.inject.Inject

class AppVersionProviderImpl @Inject constructor() : AppVersionProvider {
    override fun versionName(): String = BuildConfig.VERSION_NAME
}
