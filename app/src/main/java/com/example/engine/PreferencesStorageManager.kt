package com.example.engine

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AppPreferences
import com.example.model.AppUiMode
import com.example.model.Camera3DKeyframe
import com.example.model.CameraEasingCurve
import com.example.model.CameraPostEffectSettings
import com.example.model.CameraPresetTemplate
import com.example.model.ColorPaletteConfig
import com.example.model.GestureAction
import com.example.model.KeyboardShortcutBinding
import com.example.model.KodanimaTheme
import com.example.model.MultiFingerGesture
import com.example.model.NetworkTransportMode
import com.example.model.PressureCurve
import com.example.model.PressureMode
import com.example.model.ShortcutAction
import com.example.model.ShortcutRegistry
import com.example.model.TabletDriverSettings
import com.example.model.TimelineLayoutMode
import com.example.model.TouchGestureRegistry

/**
 * Manages persistent storage of application user preferences on disk via SharedPreferences.
 * Ensures themes, UI mode, UI scaling, virtual cursor colors, and tablet driver settings
 * are preserved across application launches and restarts.
 */
object PreferencesStorageManager {

    private const val PREFS_NAME = "kodanima_app_prefs"

    // Keys
    private const val KEY_UI_MODE = "pref_ui_mode"
    private const val KEY_THEME_ID = "pref_theme_id"
    private const val KEY_TIMELINE_LAYOUT = "pref_timeline_layout"
    private const val KEY_UI_SCALE = "pref_ui_scale"
    private const val KEY_CHUNK_CULLING = "pref_chunk_culling"
    private const val KEY_GPU_ACCEL = "pref_gpu_accel"
    private const val KEY_LOW_POWER = "pref_low_power"
    private const val KEY_AUTO_COMPRESS = "pref_auto_compress"
    private const val KEY_PROJECTS_DIR = "pref_projects_dir"
    private const val KEY_AUTO_SAVE_SEC = "pref_auto_save_sec"
    private const val KEY_SHOW_FPS = "pref_show_fps"

    // Tablet Keys
    private const val KEY_TAB_CURVE = "pref_tab_curve"
    private const val KEY_TAB_MODE = "pref_tab_mode"
    private const val KEY_TAB_CUSTOM_LEVELS = "pref_tab_custom_levels"
    private const val KEY_TAB_HOVER = "pref_tab_hover"
    private const val KEY_TAB_VIRTUAL_CURSOR = "pref_tab_virtual_cursor"
    private const val KEY_TAB_CURSOR_COLOR = "pref_tab_cursor_color"
    private const val KEY_TAB_CURSOR_PATH = "pref_tab_cursor_path"
    private const val KEY_TAB_INVERT_X = "pref_tab_invert_x"
    private const val KEY_TAB_INVERT_Y = "pref_tab_invert_y"
    private const val KEY_TAB_STYLUS_ERASER = "pref_tab_stylus_eraser"
    private const val KEY_TAB_PALM_REJECTION = "pref_tab_palm_rejection"
    private const val KEY_TAB_KEYBOARD_SHORTCUTS = "pref_tab_keyboard_shortcuts"
    private const val KEY_TAB_MOUSE_ZOOM = "pref_tab_mouse_zoom"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Loads the saved preferences from disk, falling back to defaults.
     */
    fun loadPreferences(context: Context): AppPreferences {
        val sp = getPrefs(context)

        val uiModeStr = sp.getString(KEY_UI_MODE, AppUiMode.MOBILE.name) ?: AppUiMode.MOBILE.name
        val uiMode = try { AppUiMode.valueOf(uiModeStr) } catch (_: Exception) { AppUiMode.MOBILE }

        val themeId = sp.getInt(KEY_THEME_ID, KodanimaTheme.ROXO_BLACK.id)
        val theme = KodanimaTheme.fromId(themeId)

        val tlLayoutId = sp.getInt(KEY_TIMELINE_LAYOUT, TimelineLayoutMode.HERITAGE_PROFISSIONAL.id)
        val timelineLayoutMode = TimelineLayoutMode.values().firstOrNull { it.id == tlLayoutId }
            ?: TimelineLayoutMode.HERITAGE_PROFISSIONAL

        val uiScale = sp.getFloat(KEY_UI_SCALE, 0.85f).coerceIn(0.6f, 1.3f)
        val chunkCulling = sp.getBoolean(KEY_CHUNK_CULLING, true)
        val gpuAccel = sp.getBoolean(KEY_GPU_ACCEL, true)
        val lowPower = sp.getBoolean(KEY_LOW_POWER, false)
        val autoCompress = sp.getBoolean(KEY_AUTO_COMPRESS, true)
        val projectsDir = sp.getString(KEY_PROJECTS_DIR, "") ?: ""
        val autoSaveSec = sp.getInt(KEY_AUTO_SAVE_SEC, 30)
        val showFps = sp.getBoolean(KEY_SHOW_FPS, true)

        // Tablet
        val curveStr = sp.getString(KEY_TAB_CURVE, PressureCurve.LINEAR.name) ?: PressureCurve.LINEAR.name
        val pressureCurve = try { PressureCurve.valueOf(curveStr) } catch (_: Exception) { PressureCurve.LINEAR }

        val modeStr = sp.getString(KEY_TAB_MODE, PressureMode.AUTO.name) ?: PressureMode.AUTO.name
        val pressureMode = try { PressureMode.valueOf(modeStr) } catch (_: Exception) { PressureMode.AUTO }

        val customLevels = sp.getInt(KEY_TAB_CUSTOM_LEVELS, 8192)
        val hover = sp.getBoolean(KEY_TAB_HOVER, true)
        val virtualCursor = sp.getBoolean(KEY_TAB_VIRTUAL_CURSOR, true)
        val cursorColor = sp.getLong(KEY_TAB_CURSOR_COLOR, 0xFF987EDC)
        val cursorPath = sp.getString(KEY_TAB_CURSOR_PATH, null)
        val invertX = sp.getBoolean(KEY_TAB_INVERT_X, false)
        val invertY = sp.getBoolean(KEY_TAB_INVERT_Y, false)
        val stylusEraser = sp.getBoolean(KEY_TAB_STYLUS_ERASER, true)
        val palmRejection = sp.getBoolean(KEY_TAB_PALM_REJECTION, true)
        val shortcuts = sp.getBoolean(KEY_TAB_KEYBOARD_SHORTCUTS, true)
        val mouseZoom = sp.getBoolean(KEY_TAB_MOUSE_ZOOM, true)

        val tabletSettings = TabletDriverSettings(
            pressureCurve = pressureCurve,
            pressureMode = pressureMode,
            customPressureLevels = customLevels,
            enableStylusHover = hover,
            enableVirtualCursor = virtualCursor,
            cursorColor = cursorColor,
            customCursorPath = cursorPath,
            invertX = invertX,
            invertY = invertY,
            autoDetectStylusEraser = stylusEraser,
            palmRejectionStrict = palmRejection,
            keyboardShortcutsEnabled = shortcuts,
            mouseScrollZoomEnabled = mouseZoom
        )

        return AppPreferences(
            uiMode = uiMode,
            theme = theme,
            timelineLayoutMode = timelineLayoutMode,
            tabletSettings = tabletSettings,
            uiScale = uiScale,
            enableChunkCulling = chunkCulling,
            enableGpuAcceleration = gpuAccel,
            lowPowerMode = lowPower,
            autoCompressOnClose = autoCompress,
            customProjectsDirectory = projectsDir,
            autoSaveIntervalSeconds = autoSaveSec,
            timelineSnapAssistant = sp.getBoolean("pref_timeline_snap", true),
            eraserUsesPressure = sp.getBoolean("pref_eraser_pressure", true),
            eraserBrushPresetId = sp.getInt("pref_eraser_preset_id", 1),
            isMultiplayerEnabled = sp.getBoolean("pref_multiplayer_enabled", true),
            networkTransportMode = try {
                NetworkTransportMode.valueOf(
                    sp.getString("pref_network_transport", NetworkTransportMode.WIFI_LAN.name)
                        ?: NetworkTransportMode.WIFI_LAN.name
                )
            } catch (_: Exception) { NetworkTransportMode.WIFI_LAN },
            hasPromptedInitialPermissions = sp.getBoolean("pref_prompted_initial_perms", false),
            showCanvasCollaboratorsHud = sp.getBoolean("pref_show_canvas_collaborators_hud", true),
            autoPromptFriendBrushes = sp.getBoolean("pref_auto_prompt_friend_brushes", true),
            zenModeSettings = com.example.model.ZenModeSettings(
                hideTopBar = sp.getBoolean("pref_zen_hide_top", true),
                hideTimeline = sp.getBoolean("pref_zen_hide_timeline", true),
                hideToolbar = sp.getBoolean("pref_zen_hide_toolbar", true),
                hideBrushBar = sp.getBoolean("pref_zen_hide_brushes", true)
            ),
            keyboardShortcuts = decodeKeyboardShortcuts(sp.getString("pref_keyboard_shortcuts_json", null)),
            touchGestures = decodeTouchGestures(sp.getString("pref_touch_gestures_json", null)),
            savedCustomResolutions = decodeCustomResolutions(sp.getString(KEY_CUSTOM_RESOLUTIONS_JSON, null)),
            colorPalette = decodeColorPalette(sp.getString(KEY_COLOR_PALETTE_JSON, null)),
            savedCameraTemplates = decodeCameraTemplates(sp.getString(KEY_CAMERA_TEMPLATES_JSON, null))
        )
    }

    private const val KEY_CUSTOM_RESOLUTIONS_JSON = "pref_custom_resolutions_json"
    private const val KEY_COLOR_PALETTE_JSON = "pref_color_palette_json"
    private const val KEY_CAMERA_TEMPLATES_JSON = "pref_camera_templates_json"

    private fun decodeColorPalette(rawJson: String?): ColorPaletteConfig {
        if (rawJson.isNullOrBlank()) return ColorPaletteConfig()
        return try {
            val obj = org.json.JSONObject(rawJson)
            val cols = obj.optInt("cols", 6).coerceIn(2, 12)
            val rows = obj.optInt("rows", 3).coerceIn(1, 8)
            val arr = obj.optJSONArray("swatches")
            val swatches = if (arr != null) {
                (0 until arr.length()).map { arr.optLong(it, 0xFF000000L) }
            } else {
                ColorPaletteConfig().swatches
            }
            ColorPaletteConfig(cols = cols, rows = rows, swatches = swatches)
        } catch (_: Exception) {
            ColorPaletteConfig()
        }
    }

    private fun encodeColorPalette(cfg: ColorPaletteConfig): String {
        val obj = org.json.JSONObject()
        obj.put("cols", cfg.cols.coerceIn(2, 12))
        obj.put("rows", cfg.rows.coerceIn(1, 8))
        val arr = org.json.JSONArray()
        cfg.swatches.forEach { arr.put(it) }
        obj.put("swatches", arr)
        return obj.toString()
    }

    private fun decodeCameraTemplates(rawJson: String?): List<CameraPresetTemplate> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(rawJson)
            val list = ArrayList<CameraPresetTemplate>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val kArr = obj.optJSONArray("keyframes")
                val keyframes = ArrayList<Camera3DKeyframe>()
                if (kArr != null) {
                    for (k in 0 until kArr.length()) {
                        val ko = kArr.optJSONObject(k) ?: continue
                        val easing = try {
                            CameraEasingCurve.valueOf(ko.optString("easing", CameraEasingCurve.BEZIER_SUAVE.name))
                        } catch (_: Exception) {
                            CameraEasingCurve.BEZIER_SUAVE
                        }
                        keyframes.add(
                            Camera3DKeyframe(
                                frameIndex = ko.optInt("frameIndex", 0),
                                x = ko.optDouble("x", 0.0).toFloat(),
                                y = ko.optDouble("y", 0.0).toFloat(),
                                zoom = ko.optDouble("zoom", 1.0).toFloat(),
                                rotationZ = ko.optDouble("rotationZ", 0.0).toFloat(),
                                pitchX = ko.optDouble("pitchX", 0.0).toFloat(),
                                yawY = ko.optDouble("yawY", 0.0).toFloat(),
                                depthSeparation = ko.optDouble("depthSeparation", 38.0).toFloat(),
                                fovDegrees = ko.optDouble("fovDegrees", 50.0).toFloat().coerceIn(18f, 120f),
                                easing = easing
                            )
                        )
                    }
                }
                val po = obj.optJSONObject("postEffects")
                val post = if (po != null) {
                    CameraPostEffectSettings(
                        vignette = po.optDouble("vignette", 0.0).toFloat(),
                        bloom = po.optDouble("bloom", 0.0).toFloat(),
                        chromaticAberration = po.optDouble("chromaticAberration", 0.0).toFloat(),
                        filmGrain = po.optDouble("filmGrain", 0.0).toFloat(),
                        sepia = po.optDouble("sepia", 0.0).toFloat(),
                        radialBlur = po.optDouble("radialBlur", 0.0).toFloat(),
                        contrastBoost = po.optDouble("contrastBoost", 0.0).toFloat()
                    )
                } else CameraPostEffectSettings()
                list.add(
                    CameraPresetTemplate(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Predefinição de Câmera"),
                        keyframes = keyframes,
                        postEffects = post,
                        captureWidth = obj.optInt("captureWidth", 1280).coerceIn(160, 4096),
                        captureHeight = obj.optInt("captureHeight", 720).coerceIn(160, 4096),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeCameraTemplates(templates: List<CameraPresetTemplate>): String {
        val arr = org.json.JSONArray()
        templates.forEach { t ->
            val obj = org.json.JSONObject()
            obj.put("id", t.id)
            obj.put("name", t.name)
            obj.put("captureWidth", t.captureWidth)
            obj.put("captureHeight", t.captureHeight)
            obj.put("createdAt", t.createdAt)
            val kArr = org.json.JSONArray()
            t.keyframes.forEach { kf ->
                val ko = org.json.JSONObject()
                ko.put("frameIndex", kf.frameIndex)
                ko.put("x", kf.x.toDouble())
                ko.put("y", kf.y.toDouble())
                ko.put("zoom", kf.zoom.toDouble())
                ko.put("rotationZ", kf.rotationZ.toDouble())
                ko.put("pitchX", kf.pitchX.toDouble())
                ko.put("yawY", kf.yawY.toDouble())
                ko.put("depthSeparation", kf.depthSeparation.toDouble())
                ko.put("fovDegrees", kf.fovDegrees.toDouble())
                ko.put("easing", kf.easing.name)
                kArr.put(ko)
            }
            obj.put("keyframes", kArr)
            val po = org.json.JSONObject()
            po.put("vignette", t.postEffects.vignette.toDouble())
            po.put("bloom", t.postEffects.bloom.toDouble())
            po.put("chromaticAberration", t.postEffects.chromaticAberration.toDouble())
            po.put("filmGrain", t.postEffects.filmGrain.toDouble())
            po.put("sepia", t.postEffects.sepia.toDouble())
            po.put("radialBlur", t.postEffects.radialBlur.toDouble())
            po.put("contrastBoost", t.postEffects.contrastBoost.toDouble())
            obj.put("postEffects", po)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeCustomResolutions(rawJson: String?): List<com.example.model.ProjectResolution> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(rawJson)
            val list = ArrayList<com.example.model.ProjectResolution>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val w = obj.optInt("width", 1280).coerceIn(128, 4096)
                val h = obj.optInt("height", 720).coerceIn(128, 4096)
                val name = obj.optString("name", "").ifBlank { "Personalizada ${w}x${h}" }
                list.add(
                    com.example.model.ProjectResolution(
                        width = w,
                        height = h,
                        name = name,
                        isCustom = true
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeCustomResolutions(resolutions: List<com.example.model.ProjectResolution>): String {
        val arr = org.json.JSONArray()
        resolutions.forEach { res ->
            val obj = org.json.JSONObject()
            obj.put("width", res.width.coerceIn(128, 4096))
            obj.put("height", res.height.coerceIn(128, 4096))
            obj.put("name", res.name)
            obj.put("isCustom", true)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodeTouchGestures(rawJson: String?): Map<MultiFingerGesture, GestureAction> {
        val base = TouchGestureRegistry.defaultMap().toMutableMap()
        if (rawJson.isNullOrBlank()) return base
        try {
            val obj = org.json.JSONObject(rawJson)
            MultiFingerGesture.entries.forEach { gesture ->
                val actName = obj.optString(gesture.name, "")
                if (actName.isNotBlank()) {
                    try {
                        base[gesture] = GestureAction.valueOf(actName)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        return base
    }

    private fun encodeTouchGestures(map: Map<MultiFingerGesture, GestureAction>): String {
        val root = org.json.JSONObject()
        map.forEach { (gesture, action) ->
            root.put(gesture.name, action.name)
        }
        return root.toString()
    }

    private fun decodeKeyboardShortcuts(rawJson: String?): Map<ShortcutAction, KeyboardShortcutBinding> {
        val base = ShortcutRegistry.defaultMap().toMutableMap()
        if (rawJson.isNullOrBlank()) return base
        try {
            val obj = org.json.JSONObject(rawJson)
            ShortcutAction.entries.forEach { action ->
                val item = obj.optJSONObject(action.name)
                if (item != null) {
                    val code = item.optInt("keyCode", action.defaultKeyCode)
                    val ctrl = item.optBoolean("ctrl", action.defaultCtrl)
                    val shift = item.optBoolean("shift", action.defaultShift)
                    base[action] = KeyboardShortcutBinding(
                        action = action,
                        keyCode = code,
                        ctrl = ctrl,
                        shift = shift
                    )
                }
            }
        } catch (_: Exception) {}
        return base
    }

    private fun encodeKeyboardShortcuts(map: Map<ShortcutAction, KeyboardShortcutBinding>): String {
        val root = org.json.JSONObject()
        map.forEach { (action, binding) ->
            val obj = org.json.JSONObject()
            obj.put("keyCode", binding.keyCode)
            obj.put("ctrl", binding.ctrl)
            obj.put("shift", binding.shift)
            root.put(action.name, obj)
        }
        return root.toString()
    }

    /**
     * Saves user preferences immediately to disk.
     */
    fun savePreferences(context: Context, preferences: AppPreferences) {
        val sp = getPrefs(context)
        val tab = preferences.tabletSettings
        val zen = preferences.zenModeSettings

        sp.edit()
            .putString(KEY_UI_MODE, preferences.uiMode.name)
            .putInt(KEY_THEME_ID, preferences.theme.id)
            .putInt(KEY_TIMELINE_LAYOUT, preferences.timelineLayoutMode.id)
            .putFloat(KEY_UI_SCALE, preferences.uiScale)
            .putBoolean(KEY_CHUNK_CULLING, preferences.enableChunkCulling)
            .putBoolean(KEY_GPU_ACCEL, preferences.enableGpuAcceleration)
            .putBoolean(KEY_LOW_POWER, preferences.lowPowerMode)
            .putBoolean(KEY_AUTO_COMPRESS, preferences.autoCompressOnClose)
            .putString(KEY_PROJECTS_DIR, preferences.customProjectsDirectory)
            .putInt(KEY_AUTO_SAVE_SEC, preferences.autoSaveIntervalSeconds)
            .putBoolean(KEY_SHOW_FPS, preferences.showFpsCounter)
            .putBoolean("pref_timeline_snap", preferences.timelineSnapAssistant)
            .putBoolean("pref_eraser_pressure", preferences.eraserUsesPressure)
            .putInt("pref_eraser_preset_id", preferences.eraserBrushPresetId)
            .putBoolean("pref_multiplayer_enabled", preferences.isMultiplayerEnabled)
            .putString("pref_network_transport", preferences.networkTransportMode.name)
            .putBoolean("pref_prompted_initial_perms", preferences.hasPromptedInitialPermissions)
            .putBoolean("pref_show_canvas_collaborators_hud", preferences.showCanvasCollaboratorsHud)
            .putBoolean("pref_auto_prompt_friend_brushes", preferences.autoPromptFriendBrushes)
            .putBoolean("pref_zen_hide_top", zen.hideTopBar)
            .putBoolean("pref_zen_hide_timeline", zen.hideTimeline)
            .putBoolean("pref_zen_hide_toolbar", zen.hideToolbar)
            .putBoolean("pref_zen_hide_brushes", zen.hideBrushBar)
            // Tablet
            .putString(KEY_TAB_CURVE, tab.pressureCurve.name)
            .putString(KEY_TAB_MODE, tab.pressureMode.name)
            .putInt(KEY_TAB_CUSTOM_LEVELS, tab.customPressureLevels)
            .putBoolean(KEY_TAB_HOVER, tab.enableStylusHover)
            .putBoolean(KEY_TAB_VIRTUAL_CURSOR, tab.enableVirtualCursor)
            .putLong(KEY_TAB_CURSOR_COLOR, tab.cursorColor)
            .putString(KEY_TAB_CURSOR_PATH, tab.customCursorPath)
            .putBoolean(KEY_TAB_INVERT_X, tab.invertX)
            .putBoolean(KEY_TAB_INVERT_Y, tab.invertY)
            .putBoolean(KEY_TAB_STYLUS_ERASER, tab.autoDetectStylusEraser)
            .putBoolean(KEY_TAB_PALM_REJECTION, tab.palmRejectionStrict)
            .putBoolean(KEY_TAB_KEYBOARD_SHORTCUTS, tab.keyboardShortcutsEnabled)
            .putBoolean(KEY_TAB_MOUSE_ZOOM, tab.mouseScrollZoomEnabled)
            .putString("pref_keyboard_shortcuts_json", encodeKeyboardShortcuts(preferences.keyboardShortcuts))
            .putString("pref_touch_gestures_json", encodeTouchGestures(preferences.touchGestures))
            .putString(KEY_CUSTOM_RESOLUTIONS_JSON, encodeCustomResolutions(preferences.savedCustomResolutions))
            .putString(KEY_COLOR_PALETTE_JSON, encodeColorPalette(preferences.colorPalette))
            .putString(KEY_CAMERA_TEMPLATES_JSON, encodeCameraTemplates(preferences.savedCameraTemplates))
            .commit()
    }

    // --- Multiplayer Account & Friends Persistence ---
    private const val KEY_USER_ID = "pref_user_id"
    private const val KEY_USER_NAME = "pref_user_name"
    private const val KEY_USER_AVATAR_URI = "pref_user_avatar_uri"
    private const val KEY_USER_AVATAR_BASE64 = "pref_user_avatar_base64"
    private const val KEY_USER_AVATAR_COLOR = "pref_user_avatar_color"
    private const val KEY_FRIENDS_JSON = "pref_friends_json"
    private const val KEY_FRIEND_REQUESTS_JSON = "pref_friend_requests_json"

    fun loadUserAccount(context: Context): com.example.model.UserAccount {
        val sp = getPrefs(context)
        val id = sp.getString(KEY_USER_ID, null) ?: run {
            val newId = com.example.model.UserAccount.generateDeviceId()
            sp.edit().putString(KEY_USER_ID, newId).commit()
            newId
        }
        val name = sp.getString(KEY_USER_NAME, "Animador ${id.takeLast(4)}") ?: "Animador ${id.takeLast(4)}"
        val avatarUri = sp.getString(KEY_USER_AVATAR_URI, null)
        val avatarBase64 = sp.getString(KEY_USER_AVATAR_BASE64, null)
        val avatarColor = sp.getLong(KEY_USER_AVATAR_COLOR, 0xFF00E5FF)
        return com.example.model.UserAccount(
            id = id,
            name = name,
            avatarUri = avatarUri,
            avatarBase64 = avatarBase64,
            avatarColor = avatarColor
        )
    }

    fun saveUserAccount(context: Context, account: com.example.model.UserAccount) {
        val sp = getPrefs(context)
        sp.edit()
            .putString(KEY_USER_ID, account.id)
            .putString(KEY_USER_NAME, account.name)
            .putString(KEY_USER_AVATAR_URI, account.avatarUri)
            .putString(KEY_USER_AVATAR_BASE64, account.avatarBase64)
            .putLong(KEY_USER_AVATAR_COLOR, account.avatarColor)
            .commit()
    }

    fun loadFriendsList(context: Context): List<com.example.model.FriendEntry> {
        val sp = getPrefs(context)
        val jsonStr = sp.getString(KEY_FRIENDS_JSON, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.model.FriendEntry>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val statusStr = obj.optString("status", com.example.model.FriendStatus.ONLINE.name)
                val status = try { com.example.model.FriendStatus.valueOf(statusStr) } catch (_: Exception) { com.example.model.FriendStatus.ONLINE }
                list.add(
                    com.example.model.FriendEntry(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        avatarUri = if (obj.has("avatarUri") && !obj.isNull("avatarUri")) obj.getString("avatarUri") else null,
                        avatarBase64 = if (obj.has("avatarBase64") && !obj.isNull("avatarBase64")) obj.getString("avatarBase64") else null,
                        avatarColor = obj.optLong("avatarColor", 0xFF987EDC),
                        status = status,
                        currentRoomId = if (obj.has("currentRoomId") && !obj.isNull("currentRoomId")) obj.getString("currentRoomId") else null,
                        lastKnownIp = if (obj.has("lastKnownIp") && !obj.isNull("lastKnownIp")) obj.getString("lastKnownIp") else null,
                        lastKnownControlPort = obj.optInt("lastKnownControlPort", 0),
                        lastSeen = obj.optLong("lastSeen", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFriendsList(context: Context, friends: List<com.example.model.FriendEntry>) {
        val sp = getPrefs(context)
        try {
            val array = org.json.JSONArray()
            friends.forEach { f ->
                val obj = org.json.JSONObject()
                obj.put("id", f.id)
                obj.put("name", f.name)
                obj.put("avatarUri", f.avatarUri ?: org.json.JSONObject.NULL)
                obj.put("avatarBase64", f.avatarBase64 ?: org.json.JSONObject.NULL)
                obj.put("avatarColor", f.avatarColor)
                obj.put("status", f.status.name)
                obj.put("currentRoomId", f.currentRoomId ?: org.json.JSONObject.NULL)
                obj.put("lastKnownIp", f.lastKnownIp ?: org.json.JSONObject.NULL)
                obj.put("lastKnownControlPort", f.lastKnownControlPort)
                obj.put("lastSeen", f.lastSeen)
                array.put(obj)
            }
            sp.edit().putString(KEY_FRIENDS_JSON, array.toString()).commit()
        } catch (_: Exception) {}
    }

    fun loadFriendRequests(context: Context): List<com.example.model.FriendRequest> {
        val sp = getPrefs(context)
        val jsonStr = sp.getString(KEY_FRIEND_REQUESTS_JSON, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.model.FriendRequest>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    com.example.model.FriendRequest(
                        id = obj.getString("id"),
                        senderId = obj.getString("senderId"),
                        senderName = obj.getString("senderName"),
                        senderAvatarUri = if (obj.has("senderAvatarUri") && !obj.isNull("senderAvatarUri")) obj.getString("senderAvatarUri") else null,
                        senderAvatarBase64 = if (obj.has("senderAvatarBase64") && !obj.isNull("senderAvatarBase64")) obj.getString("senderAvatarBase64") else null,
                        senderAvatarColor = obj.optLong("senderAvatarColor", 0xFFBE699A),
                        senderIp = if (obj.has("senderIp") && !obj.isNull("senderIp")) obj.getString("senderIp") else null,
                        senderControlPort = obj.optInt("senderControlPort", 0),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFriendRequests(context: Context, requests: List<com.example.model.FriendRequest>) {
        val sp = getPrefs(context)
        try {
            val array = org.json.JSONArray()
            requests.forEach { r ->
                val obj = org.json.JSONObject()
                obj.put("id", r.id)
                obj.put("senderId", r.senderId)
                obj.put("senderName", r.senderName)
                obj.put("senderAvatarUri", r.senderAvatarUri ?: org.json.JSONObject.NULL)
                obj.put("senderAvatarBase64", r.senderAvatarBase64 ?: org.json.JSONObject.NULL)
                obj.put("senderAvatarColor", r.senderAvatarColor)
                obj.put("senderIp", r.senderIp ?: org.json.JSONObject.NULL)
                obj.put("senderControlPort", r.senderControlPort)
                obj.put("timestamp", r.timestamp)
                array.put(obj)
            }
            sp.edit().putString(KEY_FRIEND_REQUESTS_JSON, array.toString()).commit()
        } catch (_: Exception) {}
    }

    // --- Native Brush Customization Overrides ---
    private const val KEY_BRUSH_OVERRIDES_JSON = "pref_brush_overrides_json"

    fun loadBrushOverrides(context: Context): Map<Int, com.example.model.BrushOverride> {
        val sp = getPrefs(context)
        val raw = sp.getString(KEY_BRUSH_OVERRIDES_JSON, null) ?: return emptyMap()
        return try {
            val arr = org.json.JSONArray(raw)
            val map = LinkedHashMap<Int, com.example.model.BrushOverride>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val parsed = com.example.model.BrushOverride.fromJsonOrNull(obj)
                if (parsed != null) {
                    map[parsed.brushId] = parsed
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveBrushOverride(context: Context, override: com.example.model.BrushOverride): Map<Int, com.example.model.BrushOverride> {
        val map = loadBrushOverrides(context).toMutableMap()
        map[override.brushId] = override
        persistBrushOverrides(context, map)
        return map
    }

    fun resetBrushOverride(context: Context, brushId: Int): Map<Int, com.example.model.BrushOverride> {
        val map = loadBrushOverrides(context).toMutableMap()
        map.remove(brushId)
        persistBrushOverrides(context, map)
        return map
    }

    private fun persistBrushOverrides(context: Context, map: Map<Int, com.example.model.BrushOverride>) {
        val sp = getPrefs(context)
        try {
            val arr = org.json.JSONArray()
            map.values.forEach { arr.put(it.toJson()) }
            sp.edit().putString(KEY_BRUSH_OVERRIDES_JSON, arr.toString()).commit()
        } catch (_: Exception) {}
    }
}
