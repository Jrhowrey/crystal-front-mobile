# Building Your Signed Android APK & Google Play Store Bundle (`.aab`)

All required files to compile **Crystal Front: Mobile Command v2.0** (`com.crystalfront.mobilecommand`) into a signed Android `.apk` and a Google Play Store `.aab` are generated and pre-configured inside `crystal_front_android_playstore_project.zip`.

## Hardware, AOSP & Kernel Optimizations Included in v2.0

* **WebGL 2.0 (GLSL ES 3.0) Snapdragon Game Super Resolution (SGSR) & TBDR Pipeline**:
  * **Mali IDVS Split Vertex Streams**: Stream 0 binds tightly packed positions (`aPos`) separate from Stream 1 (`aUV`), ensuring culled triangles never fetch varying attributes.
  * **Adreno Dual-Rate FP16 ALU (`mediump`)**: All color, luminance, SGSR edge-adaptive contrast sharpening, and Karis HDR tone-mapping run in `mediump` (`float16_t`), while UV coordinates remain `highp`.
  * **Explicit Tile Invalidation**: Calls `gl.invalidateFramebuffer` (`VK_ATTACHMENT_STORE_OP_DONT_CARE`) so depth/stencil never spills from Adreno GMEM or Mali Tile-RAM to LPDDR5 RAM.
* **Native C++20 NDK, Vulkan TBDR & Linux Kernel Subsystem (`app/src/main/cpp/crystal_front_native_engine.cpp`)**:
  * **EAS CPU Topology Discovery & Thread Pinning**: Parses `/sys/devices/system/cpu/cpu*/cpu_capacity` and pins simulation/render threads to Prime/Performance cores (`sched_setaffinity`) while isolating async I/O on Efficiency cores.
  * **ADPF & AOSP `GameManager`**: Reports actual frame work durations (`APerformanceHint_reportActualWorkDuration`) to drive Linux kernel **UtilClamp (`uclamp.min`)** via `PowerHAL`, and dynamically scales SGSR resolution when thermal status reaches `ATHERMAL_STATUS_MODERATE`.
  * **Lock-Free LSE Atomics, `futex` Bitsets & 2MB THP Arenas**: Compiled with `-march=armv8.2-a+lse+fp16+dotprod`, using `mmap` + `madvise(MADV_HUGEPAGE)` + `prctl(PR_SET_VMA_ANON_NAME, "EngineRenderHeap")` and 4-wide ARM NEON SIMD (`<arm_neon.h>`) Structure-of-Arrays (SoA) frustum culling.

## Option 1: Update Your Desktop `gamedev` Folder & Build Locally

1. Extract `crystal_front_android_playstore_project.zip` into your Desktop `gamedev` folder (replacing the previous `CrystalFrontAndroid` files).
2. Open `CrystalFrontAndroid` in **Android Studio** and run `./build_apk_and_aab.sh` (or `build_apk_and_aab.bat` on Windows).
3. Collect your compiled outputs:
   * **Signed APK**: `app/build/outputs/apk/release/app-release.apk`
   * **Play Store AAB**: `app/build/outputs/bundle/release/app-release.aab`

## Option 2: Build in the Cloud with GitHub Actions

1. Push `CrystalFrontAndroid` to a GitHub repository.
2. In the **Actions** tab, trigger **Build Signed Android APK & Play Store AAB**.
3. Download the compiled release APK and AAB bundle directly from GitHub!
