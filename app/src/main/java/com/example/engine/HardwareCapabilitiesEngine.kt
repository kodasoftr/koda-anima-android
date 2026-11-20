package com.example.engine

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Multi-core bounded dispatchers tuned to mobile big.LITTLE CPU topology
 * to maximize throughput while preventing UI starvation and battery overheating.
 */
object KodanimaDispatchers {
    private val cpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

    @OptIn(ExperimentalCoroutinesApi::class)
    val backgroundRender: CoroutineDispatcher =
        Dispatchers.Default.limitedParallelism(max(2, min(4, cpuCores / 2)))

    val networkIo: CoroutineDispatcher = Dispatchers.IO
}

enum class CpuAbiArchitecture(val labelPt: String, val is64Bit: Boolean) {
    ARM64_V8A("ARM64-v8a (64-bit • 31 regs + ASIMD NEON)", true),
    ARMEABI_V7A("ARMv7-a (32-bit • Thumb-2 + NEON)", false),
    X86_64("x86_64 (64-bit SIMD)", true),
    X86("x86 (32-bit)", false),
    UNKNOWN("ARM Genérico", false)
}

enum class GpuVendorFamily(val displayName: String, val isTileBasedDeferred: Boolean) {
    MALI_VALHALL("ARM Mali Valhall (TBDR • AFBC)", true),
    MALI_BIFROST_MIDGARD("ARM Mali Bifrost/Midgard (TBDR)", true),
    IMMORTALIS("ARM Immortalis (TBDR + Ray)", true),
    ADRENO("Qualcomm Adreno (FlexRender)", true),
    POWERVR("Imagination PowerVR (TBDR)", true),
    XCLIPSE("Samsung Xclipse (RDNA)", false),
    SOFTWARE_OR_EMU("Renderizador Padrão / Emulador", false)
}

data class HardwareRenderProfile(
    val cpuAbi: CpuAbiArchitecture = CpuAbiArchitecture.ARM64_V8A,
    val cpuCoreCount: Int = 4,
    val totalRamMb: Long = 4096L,
    val isLowRamDevice: Boolean = false,
    val gpuRenderer: String = "Hardware GPU (Skia)",
    val gpuVendor: String = "Android",
    val gpuVersion: String = "OpenGL ES 3.x",
    val gpuFamily: GpuVendorFamily = GpuVendorFamily.ADRENO,
    val supportsVulkan: Boolean = false,
    val vulkanVersionString: String = "N/A",
    val bitmapPoolMaxBytes: Int = 32 * 1024 * 1024,
    val maxCacheDimensionPx: Int = 1920,
    val particleDensityMultiplier: Float = 1.0f,
    val maxUndoHistory: Int = 30,
    val preferTightSaveLayerBounds: Boolean = true,
    val avoidShadowLayerOnActiveCanvas: Boolean = true,
    val supportsRenderNode: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
)

/**
 * Hardware Capabilities & Architecture Optimizer for Kodanima 2D.
 *
 * Inspects real ARMv7-a / ARM64-v8a CPU architecture, RAM budget, Vulkan support,
 * and exact GPU driver (ARM Mali-G57 Valhall, Adreno, PowerVR, Immortalis, Xclipse)
 * using legitimate standard Android APIs (100% Google Play compliant — zero DCL).
 */
object HardwareCapabilitiesEngine {

    @Volatile
    var currentProfile: HardwareRenderProfile = buildFallbackProfile()
        private set

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            currentProfile = probeDeviceHardware(context.applicationContext)
            KodanimaBitmapPool.configureMaxBytes(currentProfile.bitmapPoolMaxBytes)
            initialized = true
        }
    }

    private fun buildFallbackProfile(): HardwareRenderProfile {
        val abi = detectCpuAbi()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        return HardwareRenderProfile(
            cpuAbi = abi,
            cpuCoreCount = cores,
            bitmapPoolMaxBytes = if (abi.is64Bit) 40 * 1024 * 1024 else 20 * 1024 * 1024,
            maxCacheDimensionPx = if (abi.is64Bit) 1920 else 1280,
            particleDensityMultiplier = if (abi.is64Bit) 0.9f else 0.7f,
            maxUndoHistory = if (abi.is64Bit) 30 else 16
        )
    }

    private fun detectCpuAbi(): CpuAbiArchitecture {
        @Suppress("UNCHECKED_CAST")
        val abis = Build.SUPPORTED_ABIS as? Array<String>
        val primaryAbi = abis?.firstOrNull()?.lowercase(Locale.ROOT) ?: ""
        @Suppress("UNCHECKED_CAST")
        val abis64 = Build.SUPPORTED_64_BIT_ABIS as? Array<String>
        return when {
            primaryAbi.contains("arm64") -> CpuAbiArchitecture.ARM64_V8A
            primaryAbi.contains("armeabi-v7a") || primaryAbi.contains("armeabi") -> CpuAbiArchitecture.ARMEABI_V7A
            primaryAbi.contains("x86_64") -> CpuAbiArchitecture.X86_64
            primaryAbi.contains("x86") -> CpuAbiArchitecture.X86
            else -> if (!abis64.isNullOrEmpty()) CpuAbiArchitecture.ARM64_V8A else CpuAbiArchitecture.ARMEABI_V7A
        }
    }

    private fun probeDeviceHardware(context: Context): HardwareRenderProfile {
        val abi = detectCpuAbi()
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

        // Memory detection
        var totalRamMb = 4096L
        var isLowRam = false
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                totalRamMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(512L)
                isLowRam = am.isLowRamDevice || totalRamMb <= 2200L
            }
        } catch (_: Throwable) {}

        // Vulkan support check (Android 7.0+ API 24+)
        var supportsVulkan = false
        var vulkanVerStr = "OpenGL ES"
        try {
            val pm = context.packageManager
            val hasVulkanFeature = pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION) ||
                pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
            if (hasVulkanFeature) {
                supportsVulkan = true
                val sysFeatures = pm.systemAvailableFeatures
                val vkFeature = sysFeatures.firstOrNull {
                    it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
                }
                if (vkFeature != null && vkFeature.version != 0) {
                    val major = (vkFeature.version ushr 22) and 0x3FF
                    val minor = (vkFeature.version ushr 12) and 0x3FF
                    vulkanVerStr = "Vulkan $major.$minor"
                } else {
                    vulkanVerStr = "Vulkan 1.x"
                }
            }
        } catch (_: Throwable) {}

        // Safe offscreen 1x1 EGL probe to obtain genuine GPU renderer name (Mali-G57, Adreno, PowerVR, etc.)
        val glInfo = probeOpenGlRendererSafely()
        val renderer = glInfo.first
        val vendor = glInfo.second
        val version = glInfo.third
        val gpuFamily = classifyGpuFamily(renderer, vendor)

        // Tune pool & particle budget specifically for CPU ABI + RAM + GPU architecture
        val poolMb = when {
            isLowRam || !abi.is64Bit -> 18
            totalRamMb >= 7500L -> 64
            totalRamMb >= 5000L -> 48
            else -> 32
        }

        // Mali-G57 (Valhall) & PowerVR are TBDR GPUs: moderate particle density + tight saveLayer keeps tiles in on-chip SRAM
        val particleMult = when {
            isLowRam -> 0.55f
            gpuFamily == GpuVendorFamily.MALI_VALHALL -> if (abi.is64Bit) 0.85f else 0.70f
            gpuFamily == GpuVendorFamily.MALI_BIFROST_MIDGARD -> 0.65f
            gpuFamily == GpuVendorFamily.POWERVR -> 0.65f
            gpuFamily == GpuVendorFamily.ADRENO -> if (abi.is64Bit) 0.95f else 0.75f
            gpuFamily == GpuVendorFamily.IMMORTALIS || gpuFamily == GpuVendorFamily.XCLIPSE -> 1.0f
            !abi.is64Bit -> 0.70f
            else -> 0.90f
        }

        val maxCacheDim = when {
            isLowRam -> 1024
            !abi.is64Bit -> 1440 // Crisp HD cache within 32-bit ARMv7-a 4GB address space
            totalRamMb >= 5500L -> 2560
            totalRamMb >= 3800L -> 2048
            else -> 1920
        }

        val maxUndo = when {
            isLowRam -> 12
            !abi.is64Bit -> 18
            else -> 30
        }

        return HardwareRenderProfile(
            cpuAbi = abi,
            cpuCoreCount = cores,
            totalRamMb = totalRamMb,
            isLowRamDevice = isLowRam,
            gpuRenderer = renderer,
            gpuVendor = vendor,
            gpuVersion = version,
            gpuFamily = gpuFamily,
            supportsVulkan = supportsVulkan,
            vulkanVersionString = vulkanVerStr,
            bitmapPoolMaxBytes = poolMb * 1024 * 1024,
            maxCacheDimensionPx = maxCacheDim,
            particleDensityMultiplier = particleMult,
            maxUndoHistory = maxUndo,
            preferTightSaveLayerBounds = true,
            avoidShadowLayerOnActiveCanvas = gpuFamily.isTileBasedDeferred || isLowRam,
            supportsRenderNode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        )
    }

    fun classifyGpuFamily(rendererRaw: String, vendorRaw: String): GpuVendorFamily {
        val r = rendererRaw.lowercase(Locale.ROOT)
        val v = vendorRaw.lowercase(Locale.ROOT)
        return when {
            r.contains("immortalis") -> GpuVendorFamily.IMMORTALIS
            r.contains("mali-g57") || r.contains("mali-g68") || r.contains("mali-g77") ||
                r.contains("mali-g78") || r.contains("mali-g310") || r.contains("mali-g510") ||
                r.contains("mali-g610") || r.contains("mali-g710") || r.contains("mali-g715") ||
                r.contains("mali-g720") || r.contains("valhall") -> GpuVendorFamily.MALI_VALHALL
            r.contains("mali") -> GpuVendorFamily.MALI_BIFROST_MIDGARD
            r.contains("adreno") || v.contains("qualcomm") -> GpuVendorFamily.ADRENO
            r.contains("powervr") || v.contains("imagination") -> GpuVendorFamily.POWERVR
            r.contains("xclipse") || v.contains("samsung") && r.contains("rdna") -> GpuVendorFamily.XCLIPSE
            r.contains("swiftshader") || r.contains("llvmpipe") || r.contains("android emulator") ->
                GpuVendorFamily.SOFTWARE_OR_EMU
            else -> GpuVendorFamily.ADRENO
        }
    }

    /**
     * Safely probes GL_RENDERER/GL_VENDOR/GL_VERSION with a 1x1 offscreen EGL pbuffer
     * and tears down every EGL handle cleanly in `finally` to prevent native GPU leaks.
     */
    private fun probeOpenGlRendererSafely(): Triple<String, String, String> {
        var display: EGLDisplay? = null
        var context: EGLContext? = null
        var surface: EGLSurface? = null
        try {
            val noDisplay = EGL14.EGL_NO_DISPLAY
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == null || display == noDisplay) {
                return Triple("Skia Hardware GPU", "Android", "OpenGL ES 3.x")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                return Triple("Skia Hardware GPU", "Android", "OpenGL ES 3.x")
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfig = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfig, 0) ||
                numConfig[0] <= 0 || configs[0] == null
            ) {
                return Triple("Skia Hardware GPU", "Android", "OpenGL ES 3.x")
            }

            val pbufferAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val noSurface = EGL14.EGL_NO_SURFACE
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbufferAttribs, 0)
            if (surface == null || surface == noSurface) {
                return Triple("Skia Hardware GPU", "Android", "OpenGL ES 3.x")
            }

            val noContext = EGL14.EGL_NO_CONTEXT
            val ctxAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL14.EGL_NONE
            )
            context = EGL14.eglCreateContext(display, configs[0], noContext, ctxAttribs, 0)
            if (context == null || context == noContext) {
                val ctxAttribs2 = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                context = EGL14.eglCreateContext(display, configs[0], noContext, ctxAttribs2, 0)
            }
            if (context == null || context == noContext) {
                return Triple("Skia Hardware GPU", "Android", "OpenGL ES 3.x")
            }

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                return Triple("Skia Hardware GPU", "Android", "OpenGL ES 3.x")
            }

            val glRenderer = GLES20.glGetString(GLES20.GL_RENDERER)?.takeIf { it.isNotBlank() } ?: "Skia Hardware GPU"
            val glVendor = GLES20.glGetString(GLES20.GL_VENDOR)?.takeIf { it.isNotBlank() } ?: "Android"
            val glVersion = GLES20.glGetString(GLES20.GL_VERSION)?.takeIf { it.isNotBlank() } ?: "OpenGL ES 3.x"
            return Triple(glRenderer, glVendor, glVersion)
        } catch (_: Throwable) {
            return Triple("Skia Hardware GPU", "Android", "OpenGL ES 3.x")
        } finally {
            try {
                if (display != null && display != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (surface != null && surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                    if (context != null && context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display)
                }
            } catch (_: Throwable) {}
        }
    }
}
