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
| 大 | 2 × 3 | 6 | 单格最大，格子高度撑到极限，适合挑构图感觉 |
| 中（默认） | 3 × 4 | 12 | 信息量与可读性平衡 |
| 小 | 5 × 5 | 24 | 每排 5 个铺满屏宽，只显示选中项的名字，适合快速扫 |

- 三档同时改"列数 + 行数"：格子大小由行高决定，只改列数会出现"大档和中档一样大、横向还少一个"的问题。
- 小档按 5×5 开格子，但每页仍只放 24 个（第 25 格留空），兼顾"每排 5 个"和"一屏 24 个"。

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
- 整页平铺滤镜选择器：滤镜 Tab 新增网格入口，支持 大 2×3 / 中 3×4 / 小 5×5 三档密度，左右滑动翻页，点击选中并与底部行同步；密度持久化保存。

**优化**
- `ImageProcessingEngine` 的三个预览入口统一加渲染锁，修复主预览与缩略图并发时 LUT 串扰的隐患。
- 缩略图渲染串行化 + 可见优先 + 空闲补齐 + 32MB/96 张字节级 LRU，翻页不卡、切档零重渲。
- 整页平铺面板补齐状态栏 / 导航栏安全区，标题不再被状态栏压住。
- 缩略图比例跟随原图：底部横排与三档网格的图片区都按原图宽高比计算并 `ContentScale.Fit` 显示，不再被裁切；格子尺寸等于图片尺寸、紧凑排列并整体居中，选中框紧贴图片，格子内部不再出现左右留白。
- 长按查看原图不再闪屏：主预览的两个状态改用同一个 Compose `Image` 槽位切换 `ImageBitmap`，不再经过 Coil 的异步重载（原实现切状态时图片会先空一帧）。
- 三档密度重定（真机反馈驱动）：原来只改列数、不改行数，格子大小实际由行高决定，导致"大档和中档格子一样大、大档横向还少一个"；改为 大 2×3 / 中 3×4 / 小 5×5 同时改行列，三档格子尺寸依次递减。
- 小档改为每排 5 个（原 4×6 左右留白过大），网格按 5×5 开格子但每页仍只放 24 个（第 25 格留空），兼顾"每排 5 个"与"一屏 24 个"。
- 版本号 `1.0` → `1.1.0`（versionCode 1 → 2）。

**已知限制**
- 实时预览目前只覆盖"滤镜" Tab；"边框水印" Tab 的列表仍为文字卡片。
- 缩略图按滤镜的**已保存参数**渲染，拖动当前滤镜的滑杆时，主预览实时变化而该卡片缩略图保持旧样（避免拖动时反复重渲）。
- 横屏 / 平板上行高受限时，图片按比例居中、两侧留白（不裁切是优先取舍）。

### 验证记录（v1.1.0）

在 MuMu 模拟器（Android 15，x86_64）上实测通过：

| 验证项 | 结果 |
|---|---|
| 底部滤镜行实时预览 | 每张卡片显示"当前照片 + 该滤镜"，ACROS 偏灰、ASTIA 偏暖黄、ClassicChrome 偏冷蓝，滤镜差异肉眼可辨 |
| 整页平铺入口 | 滤镜 Tab 右侧网格按钮可打开全屏面板 |
| 中档 3×4 | 12 个/页，共 21 个滤镜 → 2 页，页码圆点与"每页 12 个"正确 |
| 大档 2×3 | 6 个/页 → 4 页；格子明显大于中档（竖构图实测约 330×460 对 280×390），翻页后新格子缩略图自动渲出 |
| 小档 5×5 | 每排 5 个、每页 24 个 → 1 页，左右留白基本消失，未选中的格子只留图不带名字 |
| 点击选中 | 面板关闭、主预览切换到该滤镜、底部横向行自动滚到并高亮该卡片 |
| 切换密度 | 不重新渲染，仅重排；密度写入设置 |
| 等比不裁切 | 竖屏 1440×2560 下，底部横排与 大/中/小三档格子的图片区都按原图 4:3 显示，"LUT" 字样完整可见，不再被裁边 |
| 选中框贴合 | 格子尺寸等于图片尺寸，选中描边紧贴图片四边；宽屏 + 小档 4×6 时行高受限，整块等比缩小后紧凑居中，格子内部无左右留白 |
| 长按查看原图 | 长按后显示无滤镜原图、松手回到滤镜效果，两个状态都是完整画面，过程中不再出现空白帧 |
| 竖构图照片复查 | 1200×1600 竖构图照片在竖屏下三档实测：大档 2×3（格子最大、4 页）、中档 3×4（2 页）、小档 5×5（每页 24 个、基本铺满屏宽），图片比例与原始一致、无裁切，选中框紧贴图片 |

> 想在模拟器上复现这套验证：默认 `x86_64` 走的是 stub（无 Vulkan，缩略图渲不出来）。临时把 `app/src/main/cpp/CMakeLists.txt` 的 `if(ANDROID_ABI STREQUAL "arm64-v8a")` 改成同时包含 `x86_64`，再 `assembleDebug` 装到模拟器即可；测完记得改回去。

### v1.0 · 上游版本

上游 `36f3617 优化：manifest更新` 的原始功能：照片导入、LUT 叠加与浓度、曝光/对比度/高光/阴影/饱和度/色温/色调/暗角调整、曲线与 LCH、颗粒、边框水印、批量导出。
