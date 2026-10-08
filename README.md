# 江湖金手指 · 伴侣 App + 游戏内 Mod

把现实生活里的任务/习惯打卡，换算成《群侠传，启动！》(jynew) 游戏内的资源：**武功等级、秘籍物品、银两、主角历练**。

整套方案由两部分组成：

| 组件 | 说明 |
| --- | --- |
| **Android App**（`android/`） | 任务打卡、兑换点管理、兑换游戏资源、显示游戏内角色面板 |
| **游戏内 Mod**（`mod/goldenfinger.lua`） | 注入到游戏 Mod 的 `hotfix` 脚本，读档时结算账本、回写角色数据 |

> App 与游戏**不直接读写存档**（存档是 EasySave3 的 `archive_{n}.dat`，格式复杂、易损坏），而是通过一个共享目录做双向数据同步。这是本项目的核心设计。

---

## 目录结构

```
android/                Android 伴侣 App 源码（Kotlin + Jetpack Compose）
  app/src/main/java/com/traework/jygoldenfinger/
    data/               PlayerState / 默认数据 / 本地持久化
    game/               游戏路径、账本导出、游戏状态读取、Mod 扫描安装
    ui/                 任务 / 金手指 / Mod / 设置 四个界面
mod/                    游戏内 Mod 与注入工具
  goldenfinger.lua      ★ 注入到游戏 Mod 的 Lua 脚本（核心）
  inject_mod.py         把 goldenfinger.lua 追加进 Mod 包的 hotfix（UnityPy）
  verify_logic.py       离线验证：用 lupa 模拟游戏 API 跑一遍结算逻辑
  make_retest_ledger.py 从 App 持久化状态生成测试账本
  _dump_hotfix.py       从 Mod 包里导出 hotfix 脚本（调试用）
  _dump_asset.py        列出包内资源（调试用）
  _scan_lua.py          扫描包内 Lua（调试用）
  _verify_bundle.py     校验 Mod 包是否已注入金手指
  _verify_delivery.py   校验交付包内容
  examples/             账本示例
tools/                  辅助工具
  dump_bundle.py        解析 Unity AssetBundle
  patch_test.py         注入实验
  pack.py               打包交付 zip
docs/
  使用说明.html          面向玩家的图文说明
  xlua_api.txt          游戏暴露的 Lua 接口清单（提取自游戏本体）
```

---

## 工作原理

```
┌──────────────┐   ledger.lua（App 写，Lua 表）   ┌──────────────┐
│  伴侣 App     │ ───────────────────────────────▶│  游戏内 Mod   │
│              │                                  │ (goldenfinger)│
│              │ ◀─────────────────────────────── │              │
└──────────────┘   state.json（Mod 写，JSON）      └──────────────┘
```

### 1. App → 游戏：账本 `ledger.lua`

App 把「待下发指令」写成一张 Lua 表（游戏内 xLua 没有 JSON 解析器，但可以直接 `load()` 一个 Lua 表）：

```lua
return {
  version = 1,
  points = 130,
  pending = {
    { id = "uuid-1", kind = "SKILL",  skill = "普通攻击", level = 3 },
    { id = "uuid-2", kind = "SILVER", amount = 1000 },
    { id = "uuid-3", kind = "EXP",    amount = 200 },
    { id = "uuid-4", kind = "ITEM",   item = 106, amount = 1 },
  },
}
```

指令类型：

| kind | 含义 | 游戏内实现 |
| --- | --- | --- |
| `SKILL` | 设置武功等级（人类等级 1..N） | 直接改写 `role.Wugongs[i].Level` |
| `ITEM` | 发放物品（秘籍=书物品 ID） | `Jyx2LuaBridge.AddItem(itemId, count)` |
| `SILVER` | 发放银两 | `AddItem(174, amount)`（174 = 银两物品 ID） |
| `EXP` | 主角历练 | `role.Exp += n` 并触发升级 |

### 2. 游戏 → App：面板 `state.json`

Mod 在读档/角色初始化后，把当前存档槽位、主角面板、包裹、已学武功、秘籍目录导出为 JSON：

```json
{
  "version": 1, "modId": "jy_ry", "archiveIndex": 0, "money": 4600,
  "team": [0],
  "books": [{ "itemId": 34, "name": "降龙十八掌秘籍", "skillKey": 7, "skillName": "降龙十八掌", "needExp": 1000 }],
  "player": {
    "key": 0, "name": "小虾米", "level": 5, "exp": 300,
    "wugongs": [{ "key": 90, "level": 200, "name": "普通攻击" }],
    "items": [{ "itemId": 106, "count": 1, "name": "玄铁剑", "isBook": false }]
  }
}
```

> `archiveIndex` 用于区分不同存档槽位；`player.wugongs[].level` 是游戏内存储值，**人类等级 = level/100 + 1**。

### 3. 结算与「待下发指令」自动清空

1. 读档时 Mod 读取 `ledger.lua`，逐条执行 `pending`；
2. 执行完成后**只把失败的项写回账本**（保留 `id`），成功的项从账本消失；
3. App 读到账本里已不存在的 `id`，即认为该指令已生效，自动从「待下发」队列移除；
4. 全部成功时账本变成 `pending = {}`，App 队列清零。

这样就不会出现「游戏数据已经改了，App 却一直显示待下发指令」的错觉。

---

## 快速开始（玩家）

1. **安装 App**：安装 `江湖金手指.apk`（需 Android 8.0+），首次启动授予「所有文件访问权限」。
2. **安装 Mod**：把 `诗酒芳华录-金手指版.zip` 用 App 的「Mod」页导入，或手动解出 `jy_ry.xml / jy_ry_mod / jy_ry_maps` 放到游戏 Mod 目录：
   - `/sdcard/jynew/mods/`（推荐）
   - 或 `/sdcard/Android/data/com.jynew.wuxia_launch/files/mods/`
3. **在 App 里打卡赚兑换点**，然后在「金手指」页把兑换点分配给武功，或在「兑换」里换秘籍/银两/历练。
4. **进入游戏读档**，改动即时生效；回到 App 可看到「待下发指令」已清零、角色面板已同步。

> 结算发生在**读档时**。若游戏已在运行，需要**完全退出重开**才会加载新 Mod。

---

## 从源码构建

### Android App

```bash
cd android
gradle assembleDebug          # 需要 Android SDK、JDK 17
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

`android/local.properties` 里的 `sdk.dir` 需指向本机 Android SDK（该文件不入库）。

### 给游戏 Mod 注入金手指

```bash
python -m pip install UnityPy
cd mod
python inject_mod.py <原始_mod包> <输出_mod包> goldenfinger.lua
```

`inject_mod.py` 会把 `goldenfinger.lua` **追加**到 Mod 包的 `hotfix` 脚本末尾（用标记包裹，重复执行会先移除旧注入），不影响 Mod 原有逻辑。注入后脚本会被游戏的 `PreloadedLua` 自动加载。

### 离线验证结算逻辑

不需要真机/模拟器，用 `lupa` 模拟游戏 API 跑一遍：

```bash
python -m pip install lupa
cd mod
python verify_logic.py
```

会校验：武功等级按 `(level-1)*100` 写入、银两/物品走 `AddItem`、历练累加、失败项保留在账本、成功项被移除、`state.json` 正常导出。

---

## 关键实现细节（踩坑记录）

| 问题 | 结论 |
| --- | --- |
| `Jyx2LuaBridge.LearnMagic2(roleId, magicId, noDisplay)` 的第三个参数 | 是「是否显示提示」，**不是等级**；且只能「未学则学会、已学则 +1 级」。设置指定等级必须直接改写 `role.Wugongs[i].Level` |
| 武功等级存储格式 | `Level = (人类等级 - 1) * 100`（3 级 → 200） |
| 物品（背包）存放位置 | `GameRuntimeData.Instance.Items`（`Dictionary<string,(count,time)>`），**不是** `role.Items` |
| 配置表访问 | 优先 `Jyx2.ConfigMgr`（Lua 模块，随游戏初始化必然存在），兜底 `CS.Jyx2.LuaToCsBridge.SkillTable/ItemTable` |
| 同名武功命中 `key=0` | 配置表里存在同名占位项（`Id=0`），按名字查 key 会命中占位项。应先按**角色已学武功**的名字定位，再退回配置表 |
| 银两 | 是物品 ID `174`，用 `AddItem` 发放 |
| 秘籍 | `ItemConfig.ItemType == 2` 且 `Skill > 0`，`Skill` 字段指向它教的武功 |
| App 无法直接改存档 | Android 沙箱 + 无 root，外部 App 读不到游戏进程内存；EasySave3 存档逆向成本高且易损坏 |

### 数据目录（App 与 Mod 必须一致）

```
/sdcard/jynew/goldenfinger/ledger.lua          # App → 游戏
/sdcard/jynew/goldenfinger/state.json          # 游戏 → App
/storage/emulated/0/Android/data/com.jynew.wuxia_launch/files/goldenfinger/   # 备用
```

---

## 常见问题

**Q：App 显示「待下发指令」，但游戏里数据没变？**
A：结算发生在读档时，且需要游戏**重新启动**以加载新 Mod。若游戏一直开着，划掉后台再打开，然后读档。

**Q：兑换的物品没到账？**
A：检查该兑换项的 `gameId` 是否为有效的游戏物品 ID（`> 0`）。无效 ID 会在 App 侧直接拦截。

**Q：某个武功升级后出现重复条目？**
A：历史版本的 `key=0` 占位项问题。当前版本在结算前会调用 `repair_wugongs` 清理无效残留。

---

## 大文件说明

以下内容体积很大（数百 MB ~ 数 GB），**不纳入本仓库**，请自行获取：

- 游戏本体 APK（`base-2.apk`）
- 原始 Mod 包《诗酒芳华录》（`jy_ry_mod` / `jy_ry_maps` / `jy_ry_v040_android.zip`）
- 注入后的 Mod 包、交付包 zip、构建产物（`build/`、`*.apk`、`*.zip`）
- 游戏源码（`jynew`）与其参考脚本

本仓库只保留**可读、可复现的核心代码与文档**。

---

## 依赖与致谢

- 游戏：《群侠传，启动！》(jynew) —— 开源项目
- 原始 Mod：《诗酒芳华录》作者 逍遥（本仓库不包含其资源，仅提供注入脚本）
- 注入依赖：[UnityPy](https://github.com/K0lb3/UnityPy)；离线验证依赖：[lupa](https://github.com/scoder/lupa)