# Lutista（本地定制版）

Android 端 LUT 滤镜批量处理 App，支持导入照片、叠加 LUT、调整色彩参数、批量导出，并带边框水印功能。

> 上游仓库：[qh7574/Lutista](https://github.com/qh7574/Lutista)
> 本仓库是上游源码的复刻 + 本地定制分支，包名 `cn.alittlecookie.lutista` 保持不变，方便与上游对照。

技术栈：Kotlin + Jetpack Compose + MIUIX + Room + Koin + Coil，图像处理走 Vulkan NDK（`app/src/main/cpp/vulkan_engine`，着色器 `unified.comp` 由 NDK 的 `glslc` 编译；仅 `arm64-v8a` 编译真实库，其余 ABI 走 `jni_bridge_stub.cpp`）。

---

## 一、本次定制内容

### 1. 底部滤镜行实时预览

底下一排滤镜卡片，原来是"灰底 + 滤镜名首字母"的占位块（`EffectItem.thumbnail` 在上游代码里恒为 `null`）。
现在每张卡片直接显示 **当前照片 × 该滤镜** 的真实渲染缩略图，翻照片、换滤镜、改参数都会跟着更新。

### 2. 整页平铺滤镜选择器

滤镜 Tab 右侧新增一个网格按钮，点开是全屏平铺的滤镜面板：

| 密度 | 网格 | 每页 | 说明 |
|---|---|---|---|
| 大 | 2 × 4 | 8 | 单格最大，4:3 画面比例，适合挑构图感觉 |
| 中（默认） | 3 × 4 | 12 | 4:5 画面比例，信息量与可读性平衡 |
| 小 | 4 × 6 | 24 | 1:1 方形缩略图，只显示选中项的名字，适合快速扫 |

- 左右滑动翻页（`HorizontalPager`），顶部可实时切换三档密度，底部有页码圆点和"每页 N 个"提示。
- 点击格子 = 选中该滤镜并关闭面板，选中态与底部横向行同步；当前选中的格子有描边 + 勾选角标。
- 打开时自动定位到当前选中滤镜所在的那一页；密度写入设置，下次打开沿用。
- 支持"默认"角标（全局默认滤镜）与渲染中的小转圈。

---

## 二、实现要点

参考项目是另一套工程（View / XML + RecyclerView + CPU 渲染），本项目是 Compose + Vulkan 单例引擎，**底层原理不同，所以只借思路、不搬代码**：

1. **基准图只解码一次**：当前照片按最长边 512 解码一份 RGBA（含 EXIF 方向纠正），之后所有滤镜复用这同一份字节，换滤镜不重复解码。
2. **一份最高版本，绘制时缩放**：每个滤镜只渲染并缓存一份 512 长边结果，小格子交给 Compose 绘制时缩放。
   - 好处：切"大 / 中 / 小"密度零重渲，没有"放大后重新渲染"的二次等待，也不会出现模糊过渡。
   - 对比"按档位分别渲染"：后者在 24 格档位要付 24 次渲染，且每次切档都要重来一轮，代价随格数线性增长。
3. **单 worker 串行渲染**：可见格子优先，其余按空闲补齐；滚动/翻页时低优先级请求会被跳过，换照片用 `generation` 丢弃过期结果。
4. **字节级 LRU**：上限 32MB / 96 张，淘汰时只从显示列表移除、不 `recycle`，避免正在绘制的帧崩溃。
5. **渲染锁**：`ImageProcessingEngine` 的三个预览入口统一加锁。Vulkan 引擎是进程内单例，LUT 纹理上传和 `processImage` 必须成对串行，否则主预览和缩略图并发时会用错 LUT。

新增 / 改动文件：

```
新增  app/src/main/java/cn/alittlecookie/lutista/ui/preview/FilterThumbnailStore.kt   缩略图仓库（解码/队列/缓存）
新增  app/src/main/java/cn/alittlecookie/lutista/ui/preview/PreviewConfig.kt          FilterEntity → 预览用 FilterConfig
新增  app/src/main/java/cn/alittlecookie/lutista/ui/screen/FilterGridOverlay.kt       整页平铺面板（三档密度 + 分页）
改动  app/src/main/java/cn/alittlecookie/lutista/ui/screen/PhotoProcessScreen.kt      底部行接入缩略图、网格入口
改动  app/src/main/java/cn/alittlecookie/lutista/domain/engine/ImageProcessingEngine.kt  预览入口加渲染锁
改动  app/src/main/java/cn/alittlecookie/lutista/manager/SettingsManager.kt           持久化网格密度
改动  app/build.gradle.kts                                                           versionCode/versionName
```

---

## 三、构建环境

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21 | `gradle/gradle-daemon-jvm.properties` 固定了 `toolchainVersion=21`，用 17 会直接报 "Cannot find a Java installation ... matching: {languageVersion=21}" |
| Android SDK Platform | 37 | `compileSdk = 37` / `targetSdk = 37` |
| Build-Tools | 37.0.0 | |
| NDK | 29.0.14206865 | |
| CMake | 4.1.2 | |
| Gradle | 9.6.x | 与 `gradle-wrapper.properties` 一致 |

`local.properties` 写 SDK 路径（`sdk.dir=...`，已在 `.gitignore` 中）。

### 构建命令

```bash
# JDK 21 未加入自动探测路径时，用 -D 显式指定
gradle :app:assembleDebug \
  -Dorg.gradle.java.home=/path/to/jdk-21 \
  -Dorg.gradle.java.installations.paths=/path/to/jdk-21 \
  -Dorg.gradle.java.installations.auto-download=false
```

国内网络访问 `dl.google.com` / `repo.maven.apache.org` 很慢时，用仓库自带的镜像脚本（只做本地构建辅助，不改动项目依赖声明）：

```bash
gradle :app:assembleDebug --init-script gradle/aliyun-mirror.init.gradle
```

只出一个 ABI 可以明显缩短构建时间：

```bash
gradle :app:assembleDebug -Pandroid.injected.build.abi=arm64-v8a
```

> PowerShell 下整条参数要加引号，否则会被拆成 `-Pandroid` + `.injected.build.abi=...`，报 `Task '.injected.build.abi=arm64-v8a' not found`。

产物：`app/build/outputs/apk/debug/app-debug.apk`（本仓库做了单 ABI 构建时，APK 落在 `app/build/intermediates/apk/debug/`）。

---

## 四、版本更新记录

### v1.1.0 · 2026-11-19

**新增**
- 底部滤镜行实时预览：每张滤镜卡片显示"当前照片 + 该滤镜"的渲染缩略图，替代原来的首字母占位块。
- 整页平铺滤镜选择器：滤镜 Tab 新增网格入口，支持 大 2×4 / 中 3×4 / 小 4×6 三档密度，左右滑动翻页，点击选中并与底部行同步；密度持久化保存。

**优化**
- `ImageProcessingEngine` 的三个预览入口统一加渲染锁，修复主预览与缩略图并发时 LUT 串扰的隐患。
- 缩略图渲染串行化 + 可见优先 + 空闲补齐 + 32MB/96 张字节级 LRU，翻页不卡、切档零重渲。
- 版本号 `1.0` → `1.1.0`（versionCode 1 → 2）。

**已知限制**
- 实时预览目前只覆盖"滤镜" Tab；"边框水印" Tab 的列表仍为文字卡片。
- 缩略图按滤镜的**已保存参数**渲染，拖动当前滤镜的滑杆时，主预览实时变化而该卡片缩略图保持旧样（避免拖动时反复重渲）。

### v1.0 · 上游版本

上游 `36f3617 优化：manifest更新` 的原始功能：照片导入、LUT 叠加与浓度、曝光/对比度/高光/阴影/饱和度/色温/色调/暗角调整、曲线与 LCH、颗粒、边框水印、批量导出。
