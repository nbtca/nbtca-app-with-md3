# 拔电关机 MD3

基于 [NBTCA Android 客户端](https://github.com/nbtca/nbtca-app) 的原生界面改版，使用 **Kotlin + Jetpack Compose + Material 3 Expressive**。

- 手机使用底部导航，宽屏切换为侧边导航栏。
- 在「我的 → 外观」可即时预览并切换六套主题配色，显示模式可跟随系统或固定为浅色、深色。Android 12 及以上还可使用壁纸动态配色。
- 应用 ID 为 `space.nbtca.app.md3`，可与官方包并存，两者各自保存登录和缓存。
- 登录使用上游提供的 Logto、Card 授权页和教务会话，业务页面在 Compose 中呈现。
- NBTCA 和 Card 登录页在支持 WebAuthn 的系统 WebView 中启用应用级通行密钥。认证域名 `auth.app.nbtca.space` 的 `/.well-known/assetlinks.json` 需用 `delegate_permission/common.handle_all_urls` 关联 `space.nbtca.app.md3` 和实际用于签名 APK 的证书 SHA-256 指纹。Logto 的「登录体验」还需启用密码钥匙登录。暂时无法用通行密钥时，可在登录页右上角查看其他验证方式。
- 使用 GitHub 通行密钥登录 NBTCA 账号时，从登录页右上角进入系统浏览器的 Auth Tab。为了让浏览器安全返回 App，`https://nbtca.space/.well-known/assetlinks.json` 也需关联当前 APK 的包名与签名证书。

---

## 📱 核心功能特性

1. **资讯广场 (Newsroom)**
   - 直接解析协会官网 RSS（`nbtca.space/rss.xml`），文章正文在客户端由 HTML 转 Markdown
   - 分类筛选（志愿维修、学术讲座、系统公告等）与离线缓存

2. **电脑报修 (Repair)**
   - 宁波工程学院“周末电脑义诊”线上化全流程报修支持
   - 周六常规义诊时间、地点及值班状态实时监测
   - 工单全生命周期追踪（待接单、处理中、已结单、已驳回）
   - 支持报修同学、技术员及管理员多重身份与操作联动

3. **教务系统 (Academic)**
   - 网页登录一次后保存会话，课表 / 考试 / 成绩本地缓存
   - 课表按校历周次翻页（本周优先），支持任意学年学期查询
   - 数据全部存本机，离线可看；退出可清会话

4. **检查更新**
   - 从 `nbtca/nbtca-app-with-md3` 的 GitHub Releases 查询「稳定版 / Beta」渠道
   - 仓库私有期间，可使用有访问权限的 GitHub 账号在浏览器查看发布页

5. **社团工具 (Tools)**
   - **社团日历**：直接解析协会官方活动日程（`events.ics`）
   - **校历**：直接解析 `school.ics`，本地计算当前教学周
   - **服务可用性监控**：直连 `status.nbtca.space/api/status`
   - **快捷入口**：关联官方主站、开源镜像源及交流群组

6. **个人中心 (Profile)**
   - 统一身份认证：基于 **Logto OAuth2 + PKCE** 安全授权登录与 Token 自动续期
   - 角色与权限展现：关联用户协会职务、技术员认证与管理员权限
   - 调试模式：支持快速切换报修同学/技术员/管理员视角，便于本地功能测试与审核演示

7. **Card**
   - 原生社员卡正反面、触摸光泽、两列社员墙与活动券展示
   - 使用 Card 官方 Cloudflare Access / Logto 网页会话；授权后本地缓存会话，图片缓存按会话隔离
   - 卡面资料、颜色实时预览；背景/人像选图后可按 5:7 裁切、缩放、旋转，保存时上传
   - 活动券使用连续视频播放渲染动态字形，离开页面后释放播放资源
   - 数据解析来自 Card 网站现有页面，网站结构变化可能需要同步适配


---

## 🌐 数据来源

客户端不再经过自建 BFF，所有数据直连上游公开服务，并在本地解析、缓存：

| 用途 | 上游 |
| :--- | :--- |
| 资讯列表 / 文章正文 | `nbtca.space/rss.xml`、`nbtca.space/posts/...` |
| 校历 / 协会日历 | `ical.nbtca.space/school.ics`、`ical.nbtca.space/events.ics` |
| 服务状态 | `status.nbtca.space/api/status` |
| 维修工单 | `api.nbtca.space/v2` |
| 登录认证 | `auth.app.nbtca.space`（Logto OIDC） |
| 在线更新 | `nbtca/nbtca-app-with-md3`（GitHub Releases） |

---

## 🛠️ 技术架构

- **语言**：Kotlin 2.1+
- **UI 框架**：Jetpack Compose (Material 3)
- **网络与序列化**：OkHttp 4.12 + Kotlinx.Serialization JSON
- **HTML 解析**：jsoup（文章页转 Markdown）
- **结构化数据**：标准库 XML（RSS）与自研 iCalendar 解析
- **异步处理**：Kotlin Coroutines + Flow / StateFlow
- **图片加载**：Coil Compose
- **目标 SDK**：`minSdk: 26` / `targetSdk: 35` / `compileSdk: 36`
- **构建工具**：Gradle 8.13 (KTS)

---

## 🚀 编译与调试

### 1. 环境准备
- **JDK**：OpenJDK 17 或 21
- **Android SDK**：安装 Android SDK Platform 36，在 `local.properties` 中设置 `sdk.dir`，或设置 `ANDROID_HOME`。

### 2. 编译调试 APK
```bash
# Windows PowerShell
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```
产物输出路径：`app/build/outputs/apk/debug/app-debug.apk`

如需本地安装的精简包，运行 `./gradlew assembleOptimized`（Windows 使用 `.\gradlew.bat assembleOptimized`）。产物位于 `app/build/outputs/apk/optimized/app-optimized.apk`，启用 R8 代码及资源优化，并沿用本机调试签名。

构建、测试和首次提交步骤见 [构建与发布](DEPLOY.md)。

### 3. 运行单元测试
解析器（RSS / iCalendar / HTML→Markdown / status）均有 JVM 单测，fixtures 位于
`app/src/test/resources/fixtures`：
```bash
.\gradlew.bat testDebugUnitTest
```

### 4. 安装至模拟器 / 真机
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n space.nbtca.app.md3/space.nbtca.app.MainActivity
```
