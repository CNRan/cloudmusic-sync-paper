# 云听歌 Paper 插件（cloudmusic-sync-paper）

把"云听歌"Fabric mod 的服务端逻辑复刻到 Paper 服务端的插件。玩家在服务器内通过网易云点歌，全服装了客户端 mod 的玩家同步聆听——进服自动跟播当前进度，歌词、封面、正在播放 HUD 一应俱全。

## 环境要求

- 服务端：Paper 26.1.x（`api-version: 26.1.2`），Java 25
- 客户端：原"云听歌"Fabric mod（Minecraft 26.1+、Fabric Loader ≥ 0.19.3、Fabric API）

## 工作原理

| 角色 | 职责 |
|---|---|
| **Paper 插件**（本项目） | 维护全服共享播放队列、当前歌曲与进度、播放模式切换、点歌校验与冷却、`/music` 命令转发 |
| **云听歌客户端 mod**（原作者发布） | 搜索 / 点歌 / 歌单 GUI、JLayer 本地播放、歌词与正在播放 HUD、网易云 Cookie 登录 |

插件通过 Paper plugin messaging 通道与客户端 mod 通信，通道名与字段编码完全沿用原 mod 的 `cloudmusic_sync:*` 格式，客户端无需任何改动。

音频不走服务器：客户端收到 `play` 包后**各自直连网易云 CDN** 拉流，包内携带起播时间戳，晚进服的玩家按已播放时长跳帧，从当前进度跟播。网易云 Cookie 只保存在客户端本地，插件不读取、不存储。

## 功能

- 全服共享播放队列（上限 500 首），支持歌单整单导入
- 4 种播放模式：顺序播放 / 列表循环 / 单曲循环 / 随机
- 进服自动同步：等待客户端 mod 就绪（频道注册）后再推送当前歌曲与队列，避免时序丢包
- 未装 mod 提醒：进服 5 秒后仍未检测到 mod 则发送提示（不踢出、不影响正常游玩）
- 服务端校验：歌曲 ID 格式、时长（1 秒 ~ 2 小时）、封面 URL 域名白名单、队列去重
- 冷却限制：点歌 750ms / 次，歌单导入 5s / 次

## 环境要求

- 服务端：Paper 26.1.x（`api-version: 26.1.2`），Java 25
- 客户端：原"云听歌"Fabric mod（Minecraft 26.1、Fabric Loader ≥ 0.19.3、Fabric API）

## 构建

本机需安装 Java 25 与 Maven：

```text
mvn -DskipTests package
```

产物位于 `target/cloudmusic-sync-paper-1.0.0.jar`，放入 Paper 服务端 `plugins` 文件夹重启即可。

## 命令

| 命令 | 作用 |
|---|---|
| `/music` | 打开云听歌主菜单 |
| `/music play <歌名>` | 打开搜索界面 |
| `/music next` | 切换到队列下一首 |
| `/music now` | 查看当前播放歌曲 |
| `/music playlist` | 打开网易云歌单界面 |
| `/music login` | 导入网页版网易云登录会话（Cookie） |
| `/music account` | 查看网易云账号状态 |
| `/music logout` | 退出登录并删除本地凭证 |
| `/music hud` | 打开 HUD 设置 |
| `/music volume [0-100]` | 查看 / 设置音量 |
| `/music mute` | 静音 / 取消静音 |

也可以直接按客户端 mod 绑定的 **O 键**打开菜单。

## 权限

| 权限 | 默认 | 说明 |
|---|---|---|
| `cloudmusic.use` | 所有玩家 | 使用 `/music` 及点歌功能 |
| `cloudmusic.admin` | OP | 预留管理权限，当前版本未强制限制点歌操作 |

## 已知限制

- 播放队列不持久化，服务器重启后清空
- Paper plugin messaging 与 Fabric 客户端自定义 Payload 的兼容性需在目标 Paper 构建与实际客户端上端到端测试；若客户端无法收到消息，需改用 PacketEvents / ProtocolLib 直接发送 Custom Payload 数据包
- 音频由客户端直连网易云获取，VIP / 下架歌曲能否播放取决于播放者自己的网易云登录状态

## 许可证与致谢

本插件的服务端逻辑复刻自 **doorbi** 的"云听歌"（cloudmusic_sync）Fabric mod，该 mod 在其 `fabric.mod.json` 中声明为 **MIT License** 发布。

分发本插件时我们保留保留原作者版权声明与 MIT 许可证全文（见 `LICENSE`）。客户端 mod 请从原作者的发布渠道获取，本仓库不提供客户端 mod 的下载与再分发。

## 目录结构

```
src/main/java/cn/cloudmusic/paper/
├── CloudMusicPlugin.java   入口：命令、事件、mod 检测、tick 驱动
├── MusicManager.java       队列状态机、校验、冷却、播放模式
├── PayloadService.java     通道注册与消息收发
└── Codec.java              VarInt/VarLong/String 编解码（对齐 MC 协议）

源mod源码/                   原Fabric mod反编译参考（仅内部参考，勿分发）
```
