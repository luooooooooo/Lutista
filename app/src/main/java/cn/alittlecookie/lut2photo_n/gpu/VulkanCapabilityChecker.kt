package cn.alittlecookie.lut2photo_n.gpu

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import cn.alittlecookie.lut2photo_n.manager.LogManager

object VulkanCapabilityChecker {
    private const val TAG = "VulkanCapability"

    fun isVulkanAvailable(context: Context, logger: LogManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            logger.d(TAG, "API level < 29, Vulkan 1.1 not guaranteed")
            return false
        }

        val hasVulkanFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL) &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x00401000) // Vulkan 1.1
        if (!hasVulkanFeature) {
            logger.d(TAG, "Device does not support Vulkan 1.1 compute")
            return false
        }

        return try {
            System.loadLibrary("vulkan_engine")
            true
        } catch (e: UnsatisfiedLinkError) {
            logger.w(TAG, "Failed to load vulkan_engine native library", e)
            false
        }
    }
}
