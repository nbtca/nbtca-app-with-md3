# 构建与发布

## 1. 本地构建

```bash
# Windows PowerShell
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 2. 单元测试

解析器与状态模型有 JVM 单测，fixtures 位于 `app/src/test/resources/fixtures`：

```bash
.\gradlew.bat testDebugUnitTest
```

## 3. 初始化仓库

首次推送前，确认 `nbtca/nbtca-app-with-md3` 为私有仓库。首次提交时检查暂存区，确保没有签名文件和本地配置。

```powershell
git init -b main
git add .
git add --chmod=+x gradlew
git status --short
git commit -m "Initial MD3 import"
git remote add origin https://github.com/nbtca/nbtca-app-with-md3.git
git push -u origin main
```

## 4. 工作流

- `.github/workflows/ci.yml`：push/PR 时运行单元测试并构建 Debug APK。
- `.github/workflows/release.yml`：手动运行或推送 `v*` tag 时构建并发布 Release APK。

## 5. 旧 BFF 下线（一次性）

App 从 v1.1.0 起不再依赖自建 BFF；v1.0.0 及更早版本的资讯、工具与校历仍指向
`https://bff.nbtca.space`。确认旧版本不再需要后，按下面步骤下线：

```bash
# 1. 停止并删除容器（公网机）
docker stop nbtca-app-bff
docker rm nbtca-app-bff
docker image rm ghcr.io/nbtca/nbtca-app-bff:latest

# 2. 从 /opt/axonhub/Caddyfile 删除 bff.nbtca.space 这一段（不要动其他站点）
#    然后重启 Caddy
docker restart axonhub-caddy-1

# 3. Cloudflare 删除 bff 的 A 记录

# 4. 验证已下线
curl -sS https://bff.nbtca.space/health || echo "bff offline"
```

> 旧版本在 BFF 下线后仍可使用 Saturday 报修；资讯会走离线兜底，工具页会提示网络不可用。
> 如需平滑过渡，可在应用商店/公告中提示升级，再执行第 1 步。
