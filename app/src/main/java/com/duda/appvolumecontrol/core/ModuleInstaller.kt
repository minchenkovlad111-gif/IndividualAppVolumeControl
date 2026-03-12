package com.duda.appvolumecontrol.core

import android.content.Context
import android.content.pm.PackageManager
import java.io.File

class ModuleInstaller(private val context: Context) {
    data class InstallResult(
        val success: Boolean,
        val output: String,
        val manager: RootManager
    )

    data class DetectionResult(
        val manager: RootManager,
        val hasMagiskPackage: Boolean,
        val hasMagiskBinary: Boolean,
        val hasKernelSuPackage: Boolean,
        val hasKernelSuBinary: Boolean
    )

    enum class RootManager(val label: String) {
        MAGISK("Magisk"),
        KERNELSU("KernelSU"),
        NONE("None")
    }

    fun detectRootManager(): RootManager {
        return detectRootManagerDetails().manager
    }

    fun detectRootManagerDetails(): DetectionResult {
        val pm = context.packageManager
        val hasMagiskPackage = isPackageInstalled(pm, MAGISK_PACKAGE)
        val hasKernelSuPackage = KERNELSU_PACKAGES.any { isPackageInstalled(pm, it) }
        val hasMagiskBinary = isCommandAvailable("magisk")
        val hasKernelSuBinary = isCommandAvailable("ksud")
        val manager = when {
            hasMagiskPackage || hasMagiskBinary -> RootManager.MAGISK
            hasKernelSuPackage || hasKernelSuBinary -> RootManager.KERNELSU
            else -> RootManager.NONE
        }
        return DetectionResult(
            manager = manager,
            hasMagiskPackage = hasMagiskPackage,
            hasMagiskBinary = hasMagiskBinary,
            hasKernelSuPackage = hasKernelSuPackage,
            hasKernelSuBinary = hasKernelSuBinary
        )
    }

    fun installBundledModule(manager: RootManager): InstallResult {
        if (manager == RootManager.NONE) {
            return InstallResult(false, "No supported root manager detected.", manager)
        }
        val moduleFile = copyModuleToCache() ?: return InstallResult(
            false,
            "Failed to access bundled module.",
            manager
        )
        val command = when (manager) {
            RootManager.MAGISK -> "magisk --install-module ${moduleFile.absolutePath}"
            RootManager.KERNELSU -> "ksud module install ${moduleFile.absolutePath}"
            RootManager.NONE -> ""
        }
        return runSuCommand(command, manager)
    }

    private fun copyModuleToCache(): File? {
        return runCatching {
            val cacheFile = File(context.cacheDir, MODULE_NAME)
            context.assets.open(MODULE_NAME).use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            cacheFile
        }.getOrNull()
    }

    private fun runSuCommand(command: String, manager: RootManager): InstallResult {
        return runCatching {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            val success = exitCode == 0
            InstallResult(success, output.ifBlank { "Exit code: $exitCode" }, manager)
        }.getOrElse { err ->
            InstallResult(false, err.javaClass.simpleName + ": " + (err.message ?: "unknown"), manager)
        }
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return runCatching { pm.getPackageInfo(packageName, 0) }.isSuccess
    }

    private fun isCommandAvailable(command: String): Boolean {
        return runCatching {
            val process = ProcessBuilder("sh", "-c", "command -v $command")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText()
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    companion object {
        const val MODULE_NAME = "IndividualAppAudioVolumeControl-magisk.zip"
        private const val MAGISK_PACKAGE = "com.topjohnwu.magisk"
        private val KERNELSU_PACKAGES = listOf(
            "me.weishu.kernelsu",
            "com.rifsxd.ksunext"
        )
    }
}
