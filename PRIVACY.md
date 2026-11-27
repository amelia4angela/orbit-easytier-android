# Orbit 隐私政策

最后更新：2026-11-28

Orbit（EasyTier VPN 客户端，包名 `com.easytier.easytiervpn`）是一个去中心化的组网工具。
这份政策说明 App 在你手机上会读写什么、联网做什么。内容按 App 的真实行为编写，
不包含任何营销式承诺。

## 1. 数据只保存在本机

- 你的网络配置（TOML）、节点信息、运行日志，全部写入**本机存储**：App 私有目录
  （`/data/data/com.easytier.easytiervpn/`）以及你授权的外部目录 `/EasyTier/configs/`。
- Orbit 没有云端账号体系，不存在「登录同步」。配置文件不会上传到任何服务器。
- 卸载 App 或清除数据后，这些内容随本机数据一并删除（外部目录下的配置需你自行删除）。

## 2. 不收集、不上传个人数据

- 没有统计 SDK、广告 SDK、推送 SDK，也没有崩溃上报服务。
- 不读取设备标识（IMEI / Android ID / 广告 ID）、不读取位置、通讯录、通话记录、短信、相册。
- App 主动发起的网络请求只有两类，均不附带任何个人标识或 Cookie：
  1. **检查更新**：访问 GitHub Releases 接口
     （`api.github.com/repos/amelia4angela/orbit-easytier-android/releases/latest`），
     只读取版本号与更新说明。
  2. **拉取本隐私政策**：从 GitHub 原始文件地址读取本文档
     （`raw.githubusercontent.com/amelia4angela/orbit-easytier-android/main/PRIVACY.md`），
     国内直连失败时依次改用镜像 `ghfast.top`、`raw.gitmirror.com`。下载到的内容会缓存在本机，
     仅用于离线查看与版本比对。
- 上述请求由你自己的网络发出，目标服务器（GitHub 及其镜像）会看到你的 IP，这是任何
  HTTP 访问都不可避免的部分；Orbit 本身不记录、不转发这些信息。

## 3. 自定义 DNS 直连自建服务

- 在配置里填写 DNS 服务器后，DNS 查询会**直接发往该地址**（通常是你自建的
  AdGuard Home / dnsmasq 等），Orbit 不中转、不缓存、不记录查询内容。
- DNS 留空时沿用系统 DNS，不会额外指定任何第三方解析器。
- Orbit 不内置任何 DNS 服务商，也不使用 DoH 公共解析端点代替你的设置。

## 4. 隧道流量说明

- 组网流量只在你加入的 EasyTier 网络内部传输，转发路径由 EasyTier 的 P2P 打洞与
  你配置的节点决定。
- 未配置中继（relay）或出口节点（exit node）时，流量不经过任何第三方服务器。
- 如果你（或网络管理员）配置了公共中继、出口节点或他人的服务器，那么这部分流量会
  经过该服务器，其运营者理论上可以看到流量元数据。这属于你在配置中主动选择的行为，
  与 Orbit 应用本身无关——Orbit 不运行任何自建的中转服务器。
- 全流量代理模式（TUN + 默认路由）会把设备流量导入你配置的网络，请只加入你信任的网络。

## 5. 权限用途

| 权限 | 用途 |
| --- | --- |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 建立隧道连接、检查更新、拉取隐私政策 |
| 存储权限（含 `MANAGE_EXTERNAL_STORAGE`） | 读写 `/EasyTier/configs/` 下的配置文件，导入/导出 `.toml` |
| `POST_NOTIFICATIONS` | 显示连接状态与断线告警通知 |
| `FOREGROUND_SERVICE`（`SPECIAL_USE`） | VPN 运行时保持前台服务，避免被系统回收 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 申请电池优化白名单，减少后台断连 |
| `REQUEST_INSTALL_PACKAGES` | 应用内下载更新后调起系统安装器 |
| VPN 授权（系统弹窗） | 创建虚拟网卡（TUN），不申请则无法组网 |

以上权限均可拒绝；拒绝后对应功能不可用，其余功能不受影响。权限状态与入口在
「设置 → 常规 → 权限管理」中集中展示。

## 6. 开源组件

- Orbit 客户端本体：GPL-3.0，仓库 `github.com/amelia4angela/orbit-easytier-android`。
- EasyTier 内核：`github.com/EasyTier/EasyTier`（Rust）。
- AndroidX / Jetpack Compose：Apache-2.0。
- Kotlin 标准库与协程：Apache-2.0。

第三方组件的数据处理行为由其自身许可证与上游项目决定，本项目未对其做任何修改以外的封装。

## 7. 联系方式

隐私相关问题、数据删除请求或安全披露，请在 GitHub 仓库提交 Issue：

https://github.com/amelia4angela/orbit-easytier-android/issues

## 8. 政策更新机制

- 本政策的权威版本就是仓库根目录的 `PRIVACY.md`（main 分支），修改通过仓库提交记录可追溯。
- App 每次启动会在后台拉取一次最新文本，并计算内容哈希与上次你同意的版本比较：
  有变化时弹出「隐私政策已更新」提示，由你决定是否查看。
- 你也可以随时在「设置 → 关于 → 《隐私政策》」中查看当前生效的全文。
- 拉取失败（无网络、镜像不可用）时，App 使用内置的离线副本继续展示，并标注「当前为离线版本」。

<!-- lang:en -->

# Orbit Privacy Policy

Last updated: 2026-11-28

Orbit (an EasyTier VPN client, package `com.easytier.easytiervpn`) is a decentralized mesh
networking tool. This policy describes what the app reads, writes and transmits on your device.
It is written from the app's actual behaviour, not from marketing copy.

## 1. Data stays on your device

- Network configurations (TOML), node information and runtime logs are written to **local
  storage only**: the app-private directory (`/data/data/com.easytier.easytiervpn/`) and the
  external folder you authorise, `/EasyTier/configs/`.
- Orbit has no cloud account and no login sync. Configuration files are never uploaded.
- Uninstalling the app or clearing its data removes this content (files under the external
  folder have to be deleted by you).

## 2. No personal data collected or uploaded

- No analytics, ads or push SDK, and no crash reporting service.
- No device identifiers (IMEI / Android ID / advertising ID), no location, contacts, call logs,
  SMS or photos are read.
- The app makes only two kinds of outbound requests, neither carrying personal identifiers or
  cookies:
  1. **Update check** — the GitHub Releases API
     (`api.github.com/repos/amelia4angela/orbit-easytier-android/releases/latest`), reading only
     the version tag and release notes.
  2. **Fetching this policy** — the GitHub raw file
     (`raw.githubusercontent.com/amelia4angela/orbit-easytier-android/main/PRIVACY.md`), falling
     back to the mirrors `ghfast.top` and `raw.gitmirror.com` when the direct host is
     unreachable. The downloaded text is cached locally for offline reading and hash comparison.
- Those requests leave from your own network, so the target servers (GitHub and its mirrors) see
  your IP address — unavoidable for any HTTP access. Orbit itself neither logs nor relays it.

## 3. Custom DNS goes straight to your own server

- If you specify a DNS server, queries are sent **directly** to that address (typically your own
  AdGuard Home / dnsmasq). Orbit does not proxy, cache or log them.
- With the field left empty, the system DNS resolver is used and no third-party resolver is
  injected.
- Orbit bundles no DNS provider and does not substitute public DoH endpoints for your setting.

## 4. About tunnel traffic

- Mesh traffic only travels inside the EasyTier network you joined; the path is decided by
  EasyTier's P2P hole punching and the peers you configure.
- Without a configured relay or exit node, your traffic does not pass through any third-party
  server.
- If you (or a network administrator) configure a public relay, an exit node or someone else's
  server, that traffic traverses that server and its operator can observe traffic metadata.
  That is a choice made in your configuration — Orbit runs no relay servers of its own.
- Full-tunnel mode (TUN + default route) sends device traffic into the network you configured;
  only join networks you trust.

## 5. Why permissions are used

| Permission | Purpose |
| --- | --- |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Establish the tunnel, check updates, fetch this policy |
| Storage (incl. `MANAGE_EXTERNAL_STORAGE`) | Read/write configs under `/EasyTier/configs/`, import/export `.toml` |
| `POST_NOTIFICATIONS` | Show connection status and disconnect alerts |
| `FOREGROUND_SERVICE` (`SPECIAL_USE`) | Keep the VPN foreground service alive while running |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Request a battery-optimisation exemption to reduce background drops |
| `REQUEST_INSTALL_PACKAGES` | Launch the system installer after an in-app update download |
| VPN consent (system dialog) | Create the TUN interface; without it the mesh cannot run |

Every permission can be denied. The related feature then stops working; nothing else is
affected. All of them are listed under Settings → General → Permissions.

## 6. Open-source components

- Orbit client itself: GPL-3.0, `github.com/amelia4angela/orbit-easytier-android`.
- EasyTier core: `github.com/EasyTier/EasyTier` (Rust).
- AndroidX / Jetpack Compose: Apache-2.0.
- Kotlin standard library and coroutines: Apache-2.0.

Those projects govern their own data handling; this project adds no wrappers beyond the client.

## 7. Contact

For privacy questions, data-deletion requests or security disclosures, open an issue at:

https://github.com/amelia4angela/orbit-easytier-android/issues

## 8. How this policy changes

- The authoritative version is `PRIVACY.md` in the repository root (main branch); every change is
  traceable in the commit history.
- On each launch the app fetches the latest text in the background and hashes it against the
  version you last accepted. When it differs, a "Privacy policy updated" prompt appears and you
  decide whether to review it.
- The currently effective text is always reachable at Settings → About → 《Privacy Policy》.
- If fetching fails (offline, mirrors down), the app falls back to its bundled copy and marks it
  as an offline version.
