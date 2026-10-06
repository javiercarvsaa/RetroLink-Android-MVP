#include <jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <dlfcn.h>

#include <algorithm>
#include <atomic>
#include <cstdarg>
#include <cctype>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <map>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "libretro.h"

#define TAG "RetroLinkNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

void* g_core = nullptr;
std::string g_system_dir;
std::string g_save_dir;
std::string g_core_info;
std::string g_last_error;
std::string g_stage = "idle";
std::vector<uint8_t> g_rom;
std::map<std::string, std::string> g_options;
std::map<std::string, std::string> g_frontend_overrides;

struct PlayerInput {
    int mask = 0;
    int x = 0;
    int y = 0;
};
PlayerInput g_input[4];

std::mutex g_audio_mutex;
std::mutex g_lifecycle_mutex;
constexpr size_t AUDIO_MAX_SAMPLES = 192000;
std::vector<int16_t> g_audio_ring(AUDIO_MAX_SAMPLES);
size_t g_audio_head = 0;
size_t g_audio_tail = 0;
size_t g_audio_count = 0;

retro_hw_render_callback g_hw{};
bool g_hw_valid = false;
bool g_context_live = false;
retro_pixel_format g_pixel_format = RETRO_PIXEL_FORMAT_XRGB8888;
retro_system_av_info g_av{};
bool g_loaded = false;
std::atomic<uint64_t> g_video_frames{0};
uintptr_t g_frontend_fbo = 0;

using retro_init_fn = void (*)(void);
using retro_deinit_fn = void (*)(void);
using retro_api_version_fn = unsigned (*)(void);
using retro_get_system_info_fn = void (*)(retro_system_info*);
using retro_get_system_av_info_fn = void (*)(retro_system_av_info*);
using retro_set_environment_fn = void (*)(retro_environment_t);
using retro_set_video_refresh_fn = void (*)(retro_video_refresh_t);
using retro_set_audio_sample_fn = void (*)(retro_audio_sample_t);
using retro_set_audio_sample_batch_fn = void (*)(retro_audio_sample_batch_t);
using retro_set_input_poll_fn = void (*)(retro_input_poll_t);
using retro_set_input_state_fn = void (*)(retro_input_state_t);
using retro_set_controller_port_device_fn = void (*)(unsigned, unsigned);
using retro_reset_fn = void (*)(void);
using retro_run_fn = void (*)(void);
using retro_load_game_fn = bool (*)(const retro_game_info*);
using retro_unload_game_fn = void (*)(void);
using retro_cheat_set_fn = void (*)(unsigned, bool, const char*);
using retro_cheat_reset_fn = void (*)(void);

retro_init_fn p_init = nullptr;
retro_deinit_fn p_deinit = nullptr;
retro_api_version_fn p_api_version = nullptr;
retro_get_system_info_fn p_get_system_info = nullptr;
retro_get_system_av_info_fn p_get_system_av_info = nullptr;
retro_set_environment_fn p_set_environment = nullptr;
retro_set_video_refresh_fn p_set_video_refresh = nullptr;
retro_set_audio_sample_fn p_set_audio_sample = nullptr;
retro_set_audio_sample_batch_fn p_set_audio_sample_batch = nullptr;
retro_set_input_poll_fn p_set_input_poll = nullptr;
retro_set_input_state_fn p_set_input_state = nullptr;
retro_set_controller_port_device_fn p_set_controller_port_device = nullptr;
retro_reset_fn p_reset = nullptr;
retro_run_fn p_run = nullptr;
retro_load_game_fn p_load_game = nullptr;
retro_unload_game_fn p_unload_game = nullptr;
retro_cheat_set_fn p_cheat_set = nullptr;
retro_cheat_reset_fn p_cheat_reset = nullptr;

void core_log(enum retro_log_level level, const char* fmt, ...) {
    char buffer[2048];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, ap);
    va_end(ap);
    if (level == RETRO_LOG_ERROR) LOGE("%s", buffer);
    else if (level == RETRO_LOG_WARN) LOGW("%s", buffer);
    else LOGI("%s", buffer);
}

uintptr_t frontend_framebuffer() {
    return g_frontend_fbo;
}

void update_frontend_fbo() {
    GLint fbo = 0;
    glGetIntegerv(GL_FRAMEBUFFER_BINDING, &fbo);
    g_frontend_fbo = static_cast<uintptr_t>(fbo);
}

bool dummy_rumble(unsigned, enum retro_rumble_effect, uint16_t) { return false; }
bool clear_thread_waits_cb(unsigned, void*) { return true; }

retro_proc_address_t frontend_proc(const char* sym) {
    if (!sym) return nullptr;
    void* p = reinterpret_cast<void*>(eglGetProcAddress(sym));
    if (!p) p = dlsym(RTLD_DEFAULT, sym);
    return reinterpret_cast<retro_proc_address_t>(p);
}

std::string trim(std::string s) {
    auto not_space = [](unsigned char c) { return !std::isspace(c); };
    s.erase(s.begin(), std::find_if(s.begin(), s.end(), not_space));
    s.erase(std::find_if(s.rbegin(), s.rend(), not_space).base(), s.end());
    return s;
}

std::string choose_default(const char* legacy) {
    if (!legacy) return {};
    std::string raw(legacy);
    auto semi = raw.find(';');
    std::string values = semi == std::string::npos ? raw : raw.substr(semi + 1);
    values = trim(values);
    auto pipe = values.find('|');
    std::string first = trim(pipe == std::string::npos ? values : values.substr(0, pipe));
    return first;
}

bool environment_cb(unsigned cmd, void* data) {
    switch (cmd) {
        case RETRO_ENVIRONMENT_GET_OVERSCAN:
            *reinterpret_cast<bool*>(data) = false;
            return true;
        case RETRO_ENVIRONMENT_GET_CAN_DUPE:
            *reinterpret_cast<bool*>(data) = true;
            return true;
        case RETRO_ENVIRONMENT_SET_PIXEL_FORMAT: {
            auto fmt = *reinterpret_cast<retro_pixel_format*>(data);
            if (fmt == RETRO_PIXEL_FORMAT_XRGB8888 || fmt == RETRO_PIXEL_FORMAT_RGB565 || fmt == RETRO_PIXEL_FORMAT_0RGB1555) {
                g_pixel_format = fmt;
                return true;
            }
            return false;
        }
        case RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            *reinterpret_cast<const char**>(data) = g_system_dir.c_str();
            return true;
        case RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            *reinterpret_cast<const char**>(data) = g_save_dir.c_str();
            return true;
#ifdef RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY
        case RETRO_ENVIRONMENT_GET_CORE_ASSETS_DIRECTORY:
            *reinterpret_cast<const char**>(data) = g_system_dir.c_str();
            return true;
#endif
        case RETRO_ENVIRONMENT_GET_LOG_INTERFACE: {
            auto* cb = reinterpret_cast<retro_log_callback*>(data);
            cb->log = core_log;
            return true;
        }
#ifdef RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE
        case RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE: {
            auto* r = reinterpret_cast<retro_rumble_interface*>(data);
            if (!r) return false;
            r->set_rumble_state = dummy_rumble;
            return true;
        }
#endif
#ifdef RETRO_ENVIRONMENT_GET_CLEAR_ALL_THREAD_WAITS_CB
        case RETRO_ENVIRONMENT_GET_CLEAR_ALL_THREAD_WAITS_CB:
            if (data) *reinterpret_cast<retro_environment_t*>(data) = clear_thread_waits_cb;
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_POLL_TYPE_OVERRIDE
        case RETRO_ENVIRONMENT_POLL_TYPE_OVERRIDE:
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_SET_CONTROLLER_INFO
        case RETRO_ENVIRONMENT_SET_CONTROLLER_INFO:
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO
        case RETRO_ENVIRONMENT_SET_SUBSYSTEM_INFO:
            return true;
#endif
        case RETRO_ENVIRONMENT_SET_HW_RENDER: {
            auto* hw = reinterpret_cast<retro_hw_render_callback*>(data);
            if (!hw) return false;
            if (hw->context_type != RETRO_HW_CONTEXT_OPENGLES2 &&
                hw->context_type != RETRO_HW_CONTEXT_OPENGLES3 &&
                hw->context_type != RETRO_HW_CONTEXT_OPENGLES_VERSION) {
                LOGE("HW context no soportado: %d", static_cast<int>(hw->context_type));
                return false;
            }
            update_frontend_fbo();
            hw->get_current_framebuffer = frontend_framebuffer;
            hw->get_proc_address = frontend_proc;
            g_hw = *hw;
            g_hw_valid = true;
            LOGI("HW render solicitado: type=%d version=%u.%u", static_cast<int>(g_hw.context_type), g_hw.version_major, g_hw.version_minor);
            return true;
        }
#ifdef RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER
        case RETRO_ENVIRONMENT_GET_PREFERRED_HW_RENDER:
            *reinterpret_cast<unsigned*>(data) = static_cast<unsigned>(RETRO_HW_CONTEXT_OPENGLES3);
            return true;
#endif
        case RETRO_ENVIRONMENT_SET_VARIABLES: {
            auto* vars = reinterpret_cast<retro_variable*>(data);
            if (!vars) return false;
            for (; vars->key; ++vars) {
                std::string key(vars->key);
                std::string val = choose_default(vars->value);
                std::string raw = vars->value ? vars->value : "";
                if (key.find("-rdp-plugin") != std::string::npos && raw.find("gliden64") != std::string::npos)
                    val = "gliden64";
                else if (key.find("-rsp-plugin") != std::string::npos && raw.find("hle") != std::string::npos)
                    val = "hle";
                else if (key.find("-cpucore") != std::string::npos && raw.find("dynamic_recompiler") != std::string::npos)
                    val = "dynamic_recompiler";
                else if (key.find("-43screensize") != std::string::npos && raw.find("320x240") != std::string::npos)
                    val = "320x240";
                else if (key.find("-169screensize") != std::string::npos && raw.find("640x360") != std::string::npos)
                    val = "640x360";
                else if (key.find("-aspect") != std::string::npos && raw.find("4:3") != std::string::npos)
                    val = "4:3";
                else if (key.find("-ThreadedRenderer") != std::string::npos && raw.find("True") != std::string::npos)
                    val = "True";
                else if (key.find("-EnableNativeResFactor") != std::string::npos && raw.find("0") != std::string::npos)
                    val = "0";
                else if (key.find("-MultiSampling") != std::string::npos && raw.find("0") != std::string::npos)
                    val = "0";
                else if (key.find("-FXAA") != std::string::npos && raw.find("0") != std::string::npos)
                    val = "0";
                else if (key.find("-HybridFilter") != std::string::npos && raw.find("False") != std::string::npos)
                    val = "False";
                auto ov = g_frontend_overrides.find(key);
                if (ov != g_frontend_overrides.end() && !ov->second.empty()) val = ov->second;
                g_options[key] = val;
            }
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE: {
            auto* var = reinterpret_cast<retro_variable*>(data);
            if (!var || !var->key) return false;
            auto it = g_options.find(var->key);
            var->value = (it == g_options.end() || it->second.empty()) ? nullptr : it->second.c_str();
            return true;
        }
        case RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            *reinterpret_cast<bool*>(data) = false;
            return true;
#ifdef RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION
        case RETRO_ENVIRONMENT_GET_CORE_OPTIONS_VERSION:
            *reinterpret_cast<unsigned*>(data) = 0; // obliga al fallback legacy SET_VARIABLES
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_GET_INPUT_BITMASKS
        case RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            *reinterpret_cast<bool*>(data) = false;
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_GET_LANGUAGE
        case RETRO_ENVIRONMENT_GET_LANGUAGE:
            *reinterpret_cast<unsigned*>(data) = RETRO_LANGUAGE_ENGLISH;
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_GET_USERNAME
        case RETRO_ENVIRONMENT_GET_USERNAME:
            *reinterpret_cast<const char**>(data) = "RetroLink";
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_SET_GEOMETRY
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            if (data) g_av.geometry = *reinterpret_cast<retro_game_geometry*>(data);
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO:
            if (data) g_av = *reinterpret_cast<retro_system_av_info*>(data);
            return true;
#endif
#ifdef RETRO_ENVIRONMENT_SET_MESSAGE
        case RETRO_ENVIRONMENT_SET_MESSAGE: {
            auto* msg = reinterpret_cast<retro_message*>(data);
            if (msg && msg->msg) LOGI("CORE MSG: %s", msg->msg);
            return true;
        }
#endif
#ifdef RETRO_ENVIRONMENT_SET_MINIMUM_AUDIO_LATENCY
        case RETRO_ENVIRONMENT_SET_MINIMUM_AUDIO_LATENCY:
            return true;
#endif
        default:
            return false;
    }
}

void video_cb(const void* data, unsigned width, unsigned height, size_t pitch) {
    (void)width; (void)height; (void)pitch;
    // En hardware-rendering, RETRO_HW_FRAME_BUFFER_VALID llega como puntero no nulo.
    // NULL representa frame duplicado. Esto permite separar VI/core FPS de FPS visuales reales.
    if (data != nullptr) g_video_frames.fetch_add(1, std::memory_order_relaxed);
}

size_t audio_batch_cb(const int16_t* data, size_t frames) {
    if (!data || frames == 0) return frames;
    std::lock_guard<std::mutex> lock(g_audio_mutex);
    size_t samples = frames * 2;
    if (samples >= AUDIO_MAX_SAMPLES) {
        data += (samples - AUDIO_MAX_SAMPLES);
        samples = AUDIO_MAX_SAMPLES;
        g_audio_head = g_audio_tail = g_audio_count = 0;
    }
    size_t free_space = AUDIO_MAX_SAMPLES - g_audio_count;
    if (samples > free_space) {
        size_t drop = samples - free_space;
        g_audio_head = (g_audio_head + drop) % AUDIO_MAX_SAMPLES;
        g_audio_count -= drop;
    }
    size_t first = std::min(samples, AUDIO_MAX_SAMPLES - g_audio_tail);
    std::memcpy(g_audio_ring.data() + g_audio_tail, data, first * sizeof(int16_t));
    size_t second = samples - first;
    if (second) std::memcpy(g_audio_ring.data(), data + first, second * sizeof(int16_t));
    g_audio_tail = (g_audio_tail + samples) % AUDIO_MAX_SAMPLES;
    g_audio_count += samples;
    return frames;
}

void audio_sample_cb(int16_t left, int16_t right) {
    int16_t pair[2] = {left, right};
    audio_batch_cb(pair, 1);
}

void input_poll_cb() {}

int16_t axis_to_libretro(int v) {
    v = std::max(-127, std::min(127, v));
    return static_cast<int16_t>(v * 258);
}

int16_t input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port >= 4) return 0;
    const auto& in = g_input[port];
    constexpr int UP = 1 << 0;
    constexpr int DOWN = 1 << 1;
    constexpr int LEFT = 1 << 2;
    constexpr int RIGHT = 1 << 3;
    constexpr int A = 1 << 4;
    constexpr int B = 1 << 5;
    constexpr int Z = 1 << 6;
    constexpr int C_UP = 1 << 7;
    constexpr int L = 1 << 8;
    constexpr int R = 1 << 9;
    constexpr int START = 1 << 10;
    constexpr int C_DOWN = 1 << 11;
    constexpr int C_LEFT = 1 << 12;
    constexpr int C_RIGHT = 1 << 13;

    if (device == RETRO_DEVICE_JOYPAD) {
        switch (id) {
            case RETRO_DEVICE_ID_JOYPAD_UP: return (in.mask & UP) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_DOWN: return (in.mask & DOWN) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_LEFT: return (in.mask & LEFT) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_RIGHT: return (in.mask & RIGHT) ? 1 : 0;
            // Mupen64Plus-Next usa el layout RetroPad: N64 A=B, N64 B=Y, Z=L2.
            case RETRO_DEVICE_ID_JOYPAD_B: return (in.mask & A) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_Y: return (in.mask & B) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_L2: return (in.mask & Z) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_L: return (in.mask & L) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_R: return (in.mask & R) ? 1 : 0;
            case RETRO_DEVICE_ID_JOYPAD_START: return (in.mask & START) ? 1 : 0;
            default: return 0;
        }
    }

    if (device == RETRO_DEVICE_ANALOG) {
        if (index == RETRO_DEVICE_INDEX_ANALOG_LEFT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X) return axis_to_libretro(in.x);
            if (id == RETRO_DEVICE_ID_ANALOG_Y) return static_cast<int16_t>(-axis_to_libretro(in.y));
        }
        if (index == RETRO_DEVICE_INDEX_ANALOG_RIGHT) {
            if (id == RETRO_DEVICE_ID_ANALOG_X) {
                if (in.mask & C_LEFT) return -32767;
                if (in.mask & C_RIGHT) return 32767;
                return 0;
            }
            if (id == RETRO_DEVICE_ID_ANALOG_Y) {
                if (in.mask & C_UP) return -32767;
                if (in.mask & C_DOWN) return 32767;
                return 0;
            }
        }
    }
    return 0;
}

bool load_symbol(void** out, const char* name) {
    *out = dlsym(g_core, name);
    if (!*out) {
        g_last_error = std::string("Falta símbolo libretro: ") + name;
        return false;
    }
    return true;
}

bool load_api(const std::string& path) {
    g_stage = "dlopen core";
    // Primero por nombre: funciona tanto con librerías extraídas como con
    // librerías cargables directamente desde el APK mediante el namespace
    // del ClassLoader. Luego intentamos la ruta absoluta como respaldo.
    dlerror();
    g_core = dlopen("libretro_n64.so", RTLD_NOW | RTLD_LOCAL);
    std::string first_error;
    if (!g_core) {
        const char* e = dlerror();
        if (e) first_error = e;
    }
    if (!g_core && !path.empty()) {
        dlerror();
        g_core = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
        if (!g_core) {
            const char* e = dlerror();
            g_last_error = std::string("No se pudo cargar core N64") +
                    (e ? std::string(": ") + e : std::string()) +
                    (first_error.empty() ? std::string() : std::string(" · nombre: ") + first_error);
            return false;
        }
    }
    if (!g_core) {
        g_last_error = first_error.empty() ? "No se pudo cargar core N64" :
                std::string("No se pudo cargar core N64: ") + first_error;
        return false;
    }
#define LOAD(name, field) if (!load_symbol(reinterpret_cast<void**>(&field), name)) return false
    LOAD("retro_init", p_init);
    LOAD("retro_deinit", p_deinit);
    LOAD("retro_api_version", p_api_version);
    LOAD("retro_get_system_info", p_get_system_info);
    LOAD("retro_get_system_av_info", p_get_system_av_info);
    LOAD("retro_set_environment", p_set_environment);
    LOAD("retro_set_video_refresh", p_set_video_refresh);
    LOAD("retro_set_audio_sample", p_set_audio_sample);
    LOAD("retro_set_audio_sample_batch", p_set_audio_sample_batch);
    LOAD("retro_set_input_poll", p_set_input_poll);
    LOAD("retro_set_input_state", p_set_input_state);
    LOAD("retro_set_controller_port_device", p_set_controller_port_device);
    LOAD("retro_reset", p_reset);
    LOAD("retro_run", p_run);
    LOAD("retro_load_game", p_load_game);
    LOAD("retro_unload_game", p_unload_game);
    LOAD("retro_cheat_set", p_cheat_set);
    LOAD("retro_cheat_reset", p_cheat_reset);
#undef LOAD
    return true;
}

bool read_file(const std::string& path, std::vector<uint8_t>& out) {
    std::ifstream in(path, std::ios::binary | std::ios::ate);
    if (!in) return false;
    auto size = in.tellg();
    if (size <= 0 || size > 128 * 1024 * 1024) return false;
    out.resize(static_cast<size_t>(size));
    in.seekg(0, std::ios::beg);
    return static_cast<bool>(in.read(reinterpret_cast<char*>(out.data()), size));
}

void clear_api() {
    p_init = nullptr; p_deinit = nullptr; p_api_version = nullptr;
    p_get_system_info = nullptr; p_get_system_av_info = nullptr;
    p_set_environment = nullptr; p_set_video_refresh = nullptr;
    p_set_audio_sample = nullptr; p_set_audio_sample_batch = nullptr;
    p_set_input_poll = nullptr; p_set_input_state = nullptr;
    p_set_controller_port_device = nullptr; p_reset = nullptr;
    p_run = nullptr; p_load_game = nullptr; p_unload_game = nullptr;
    p_cheat_set = nullptr; p_cheat_reset = nullptr;
}

void shutdown_core() {
    // v0.6.0: teardown idempotente. El shutdown se ejecuta en el hilo GL y
    // nunca puede correr dos veces en paralelo (SALIR + onDestroy, por ejemplo).
    std::lock_guard<std::mutex> lifecycle_lock(g_lifecycle_mutex);
    if (!g_core && !g_loaded && !g_hw_valid) {
        g_stage = "shutdown_idle";
        return;
    }

    g_stage = "shutdown_unload";
    // El core debe descargar el juego mientras su contexto gráfico sigue vivo.
    if (g_loaded && p_unload_game) p_unload_game();

    g_stage = "shutdown_context";
    if (g_hw_valid && g_context_live && g_hw.context_destroy) g_hw.context_destroy();
    g_context_live = false;

    g_stage = "shutdown_deinit";
    if (p_deinit) p_deinit();
    g_loaded = false;
    g_hw_valid = false;
    g_video_frames.store(0, std::memory_order_relaxed);
    g_rom.clear();
    g_options.clear();
    {
        std::lock_guard<std::mutex> lock(g_audio_mutex);
        g_audio_head = g_audio_tail = g_audio_count = 0;
    }

    g_stage = "shutdown_dlclose";
    if (g_core) dlclose(g_core);
    g_core = nullptr;
    clear_api();
    g_stage = "shutdown_done";
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeClearFrontendOptions(JNIEnv*, jclass) {
    g_frontend_overrides.clear();
}

extern "C" JNIEXPORT void JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeSetFrontendOption(JNIEnv* env, jclass,
                                                              jstring key, jstring value) {
    if (!key || !value) return;
    const char* k = env->GetStringUTFChars(key, nullptr);
    const char* v = env->GetStringUTFChars(value, nullptr);
    if (k && v) g_frontend_overrides[k] = v;
    if (k) env->ReleaseStringUTFChars(key, k);
    if (v) env->ReleaseStringUTFChars(value, v);
}

extern "C" JNIEXPORT jstring JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeInit(JNIEnv* env, jclass,
                                                 jstring corePath,
                                                 jstring romPath,
                                                 jstring systemDir,
                                                 jstring saveDir) {
    shutdown_core();
    g_last_error.clear();
    g_stage = "start";
    g_video_frames.store(0, std::memory_order_relaxed);
    update_frontend_fbo();

    const char* core_c = env->GetStringUTFChars(corePath, nullptr);
    const char* rom_c = env->GetStringUTFChars(romPath, nullptr);
    const char* sys_c = env->GetStringUTFChars(systemDir, nullptr);
    const char* save_c = env->GetStringUTFChars(saveDir, nullptr);
    std::string core = core_c ? core_c : "";
    std::string rom = rom_c ? rom_c : "";
    g_system_dir = sys_c ? sys_c : "";
    g_save_dir = save_c ? save_c : "";
    if (core_c) env->ReleaseStringUTFChars(corePath, core_c);
    if (rom_c) env->ReleaseStringUTFChars(romPath, rom_c);
    if (sys_c) env->ReleaseStringUTFChars(systemDir, sys_c);
    if (save_c) env->ReleaseStringUTFChars(saveDir, save_c);

    g_stage = "load core";
    if (!load_api(core)) {
        std::string err = g_last_error;
        shutdown_core();
        return env->NewStringUTF(err.c_str());
    }
    g_stage = "read rom";
    if (!read_file(rom, g_rom)) {
        g_last_error = "No se pudo leer la ROM desde almacenamiento interno";
        std::string err = g_last_error;
        shutdown_core();
        return env->NewStringUTF(err.c_str());
    }

    g_stage = "bind callbacks";
    p_set_environment(environment_cb);
    p_set_video_refresh(video_cb);
    p_set_audio_sample(audio_sample_cb);
    p_set_audio_sample_batch(audio_batch_cb);
    p_set_input_poll(input_poll_cb);
    p_set_input_state(input_state_cb);

    if (p_api_version() != RETRO_API_VERSION) {
        std::ostringstream ss;
        ss << "API libretro incompatible: core=" << p_api_version() << " frontend=" << RETRO_API_VERSION;
        g_last_error = ss.str();
        std::string err = g_last_error;
        shutdown_core();
        return env->NewStringUTF(err.c_str());
    }

    g_stage = "retro_init";
    p_init();

    retro_system_info info{};
    p_get_system_info(&info);
    g_core_info = std::string(info.library_name ? info.library_name : "Core") + " " +
                  std::string(info.library_version ? info.library_version : "");

    g_stage = "retro_load_game";
    retro_game_info game{};
    game.path = rom.c_str();
    game.data = g_rom.data();
    game.size = g_rom.size();
    game.meta = nullptr;
    if (!p_load_game(&game)) {
        g_last_error = "El core N64 rechazó la ROM";
        std::string err = g_last_error;
        if (p_deinit) p_deinit();
        if (g_core) dlclose(g_core);
        g_core = nullptr;
        clear_api();
        g_rom.clear();
        return env->NewStringUTF(err.c_str());
    }
    g_loaded = true;

    for (unsigned p = 0; p < 4; ++p) p_set_controller_port_device(p, RETRO_DEVICE_JOYPAD);
    p_get_system_av_info(&g_av);

    if (g_hw_valid && g_hw.context_reset) {
        g_stage = "context_reset";
        update_frontend_fbo();
        g_hw.context_reset();
        g_context_live = true;
    }

    g_stage = "ready";
    LOGI("Core listo: %s | %.3f FPS | %.0f Hz | fbo=%lu", g_core_info.c_str(), g_av.timing.fps, g_av.timing.sample_rate, (unsigned long)g_frontend_fbo);
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT void JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeSetInput(JNIEnv*, jclass, jint player, jint mask, jint x, jint y) {
    if (player < 1 || player > 4) return;
    auto& in = g_input[player - 1];
    in.mask = mask;
    in.x = std::max(-127, std::min(127, static_cast<int>(x)));
    in.y = std::max(-127, std::min(127, static_cast<int>(y)));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeRunFrame(JNIEnv*, jclass) {
    if (!g_loaded || !p_run) return JNI_FALSE;
    p_run();
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeReset(JNIEnv*, jclass) {
    if (g_loaded && p_reset) p_reset();
}

extern "C" JNIEXPORT void JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeSetCheat(JNIEnv* env, jclass, jint index, jboolean enabled, jstring code) {
    if (!g_loaded || !p_cheat_set || !code) return;
    const char* c = env->GetStringUTFChars(code, nullptr);
    if (!c) return;
    p_cheat_set(static_cast<unsigned>(std::max(0, static_cast<int>(index))), enabled == JNI_TRUE, c);
    env->ReleaseStringUTFChars(code, c);
}

extern "C" JNIEXPORT void JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeResetCheats(JNIEnv*, jclass) {
    if (p_cheat_reset) p_cheat_reset();
}

extern "C" JNIEXPORT void JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeContextReset(JNIEnv*, jclass) {
    if (g_loaded && g_hw_valid && g_hw.context_reset) {
        g_hw.context_reset();
        g_context_live = true;
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeDrainAudio(JNIEnv* env, jclass, jshortArray out) {
    if (!out) return 0;
    jsize cap = env->GetArrayLength(out);
    if (cap <= 0) return 0;
    static thread_local std::vector<jshort> tmp;
    int n = 0;
    {
        std::lock_guard<std::mutex> lock(g_audio_mutex);
        n = std::min<int>(cap, static_cast<int>(g_audio_count));
        tmp.resize(n);
        size_t first = std::min<size_t>(static_cast<size_t>(n), AUDIO_MAX_SAMPLES - g_audio_head);
        if (first) std::memcpy(tmp.data(), g_audio_ring.data() + g_audio_head, first * sizeof(int16_t));
        size_t second = static_cast<size_t>(n) - first;
        if (second) std::memcpy(tmp.data() + first, g_audio_ring.data(), second * sizeof(int16_t));
        g_audio_head = (g_audio_head + static_cast<size_t>(n)) % AUDIO_MAX_SAMPLES;
        g_audio_count -= static_cast<size_t>(n);
    }
    if (n > 0) env->SetShortArrayRegion(out, 0, static_cast<jsize>(n), tmp.data());
    return static_cast<jint>(n);
}

extern "C" JNIEXPORT jlong JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeGetVideoFrameCount(JNIEnv*, jclass) {
    return static_cast<jlong>(g_video_frames.load(std::memory_order_relaxed));
}

extern "C" JNIEXPORT jdouble JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeGetFps(JNIEnv*, jclass) {
    return g_av.timing.fps > 1.0 ? g_av.timing.fps : 60.0;
}

extern "C" JNIEXPORT jint JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeGetSampleRate(JNIEnv*, jclass) {
    return static_cast<jint>(g_av.timing.sample_rate > 8000.0 ? g_av.timing.sample_rate : 44100.0);
}

extern "C" JNIEXPORT jstring JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeGetCoreInfo(JNIEnv* env, jclass) {
    return env->NewStringUTF(g_core_info.c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeGetStage(JNIEnv* env, jclass) {
    return env->NewStringUTF(g_stage.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_cl_retrolink_app_NativeLibretro_nativeShutdown(JNIEnv*, jclass) {
    shutdown_core();
}
