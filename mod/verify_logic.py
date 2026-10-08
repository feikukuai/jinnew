"""离线验证 goldenfinger.lua 的指令执行逻辑（用 lupa 模拟游戏 API）。

验证点：
  - SILVER  -> BRIDGE.AddItem(174, amount)
  - ITEM    -> BRIDGE.AddItem(itemId, amount)
  - SKILL   -> role.Wugongs 中对应武功 Level = (humanLevel-1)*100
  - EXP     -> role.Exp 增加
  - 结算后清空 ledger，并弹出 Toast
  - ExportState 产出 state.json（含 archiveIndex / books / player）
"""
import io
import sys
import lupa

ROOT = r"C:\Users\Administrator\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6ac725999cd9129260da41d5"

PRELUDE = r"""
-- ==== 模拟游戏运行环境 ====
local calls = { additem = {}, toast = {}, writes = {} }

local function new_list()
  local l = { Count = 0 }
  function l:Add(v) l[l.Count] = v; l.Count = l.Count + 1 end
  return l
end

-- 主角
local player = {
  Key = 0, Name = "主角", Level = 5, Exp = 100,
  Hp = 100, MaxHp = 200, Mp = 50, MaxMp = 100,
  Attack = 30, Qinggong = 20, Defence = 25,
  Quanzhang = 10, Yujian = 11, Shuadao = 12, Qimen = 13, Anqi = 14,
  Wuxuechangshi = 15, Pinde = 16, Shengwang = 17, IQ = 18,
  Wugongs = new_list(), Items = new_list(),
  Xiulianwupin = 0, ExpForItem = 0,
}
-- 已学「野球拳」1级
player.Wugongs:Add({ Key = 1, Level = 0 })
function player:CanLevelUp() return false end
function player:LevelUp() self.Level = self.Level + 1 end
function player:ResetSkillCasts() end

local runtime = { Player = player }
function runtime:GetRole(id) if id == 0 then return player end return nil end
function runtime:GetTeamId() local l = new_list(); l:Add(0); return l end

-- 配置表
local SkillCfg = {
  [1] = { Id = 1, Name = "野球拳" },
  [7] = { Id = 7, Name = "降龙十八掌" },
  ItemNum = 2,
}
local ItemCfg = {
  [106] = { Id = 106, Name = "玄铁重剑", ItemType = 1 },
  [34]  = { Id = 34, Name = "降龙十八掌秘籍", ItemType = 2, Skill = 7, NeedExp = 1000 },
  [174] = { Id = 174, Name = "银两", ItemType = 0 },
  ItemNum = 3,
}

Jyx2 = { ConfigMgr = { Skill = SkillCfg, Item = ItemCfg } }

local BRIDGE = {}
function BRIDGE.AddItem(id, count) calls.additem[#calls.additem+1] = { id = id, count = count } end
function BRIDGE.GetMoneyCount() return 5000 end
function BRIDGE.ShowToast(msg) calls.toast[#calls.toast+1] = msg end
function BRIDGE.GetTeamId() local l = new_list(); l:Add(0); return l end

local LEDGER_TEXT = [==[
return {
  version = 1, points = 100,
  pending = {
    { id = "c1", kind = "SKILL", skill = "野球拳", level = 3 },
    { id = "c2", kind = "SILVER", amount = 1000 },
    { id = "c3", kind = "EXP", amount = 200 },
    { id = "c4", kind = "ITEM", item = 106, amount = 1 },
    { id = "c5", kind = "SKILL", skill = "不存在的武功", level = 2 },
  },
}
]==]

local SysIO = {
  File = {
    ReadAllText = function(path)
      if string.find(path, "ledger.lua") then return LEDGER_TEXT end
      error("no file: " .. tostring(path))
    end,
    WriteAllText = function(path, text) calls.writes[#calls.writes+1] = { path = path, text = text } end,
  },
  Directory = { CreateDirectory = function(p) end },
}

CS = {
  Jyx2 = {
    GameRuntimeData = { Instance = runtime },
    RoleInstance = {},
    SkillInstance = function(key) return { Key = key, Level = 0 } end,
    Jyx2LuaBridge = BRIDGE,
    RuntimeEnvSetup = { CurrentModId = "jy_ry" },
    LuaToCsBridge = { SkillTable = SkillCfg, ItemTable = ItemCfg },
  },
  StoryEngine = { DoLoadGame = function(i) return true end },
  UnityEngine = { WaitForSeconds = function(s) return s end },
  System = { IO = SysIO },
}

package.loaded["Jyx2Coroutine"] = { start = function(fn) end }
package.loaded["xlua.util"] = { hotfix_ex = function(...) end }

-- 暴露给 python
return { calls = calls, player = player }
"""


def main():
    L = lupa.LuaRuntime(unpack_returned_tuples=True)
    env = L.execute(PRELUDE)

    with io.open(ROOT + r"\goldenfinger\goldenfinger.lua", encoding="utf-8") as f:
        src = f.read()

    # 载入 goldenfinger.lua，拿到 GF 表
    chunk = L.eval("function(src) return load(src) end")(src)
    GF = chunk()
    print("GF loaded:", GF is not None)

    # 执行指令
    n = GF.ApplyPending()
    print("ApplyPending 返回:", n)

    calls = env["calls"]
    player = env["player"]

    additem = []
    i = 1
    while True:
        v = calls["additem"][i]
        if v is None:
            break
        additem.append((v["id"], v["count"]))
        i += 1

    toast = []
    i = 1
    while True:
        v = calls["toast"][i]
        if v is None:
            break
        toast.append(v)
        i += 1

    print("AddItem 调用:", additem)
    print("Toast:", toast)
    print("野球拳 Level (期望 200 = (3-1)*100):", player["Wugongs"][0]["Level"])
    print("主角 Exp (期望 300):", player["Exp"])

    # ExportState
    ok = GF.ExportState()
    print("ExportState:", ok)
    writes = []
    i = 1
    while True:
        v = calls["writes"][i]
        if v is None:
            break
        writes.append(v)
        i += 1
    state = [w for w in writes if w["path"].endswith("state.json")]
    ledger_clear = [w for w in writes if w["path"].endswith("ledger.lua")]
    print("ledger 重写次数:", len(ledger_clear))
    ledger_text = ledger_clear[0]["text"] if ledger_clear else ""
    if ledger_clear:
        print("结算后 ledger.lua:")
        print(ledger_text)
    if state:
        print("state.json 内容:")
        print(state[0]["text"])

    # 断言
    ok_all = True
    def check(cond, msg):
        global ok_all
        print(("  PASS " if cond else "  FAIL ") + msg)
        if not cond:
            ok_all = False

    print("---- 断言 ----")
    check(n == 4, "5 条指令中 4 条成功、1 条失败")
    check((174, 1000) in additem, "SILVER -> AddItem(174,1000)")
    check((106, 1) in additem, "ITEM -> AddItem(106,1)")
    check(player["Wugongs"][0]["Level"] == 200, "SKILL 等级 = (3-1)*100 = 200")
    check(player["Exp"] == 300, "EXP 增加 200")
    check(len(toast) == 1, "结算 Toast 弹出 1 次")
    check(len(ledger_clear) >= 1, "结算后重写 ledger")
    check('id = "c5"' in ledger_text, "失败项 c5 保留在账本中（App 可重试）")
    check('id = "c1"' not in ledger_text, "成功项 c1 已从账本移除（App 自动清空）")
    check(bool(state), "ExportState 写出 state.json")
    print("RESULT:", "ALL PASS" if ok_all else "HAS FAILURE")
    return 0 if ok_all else 1


if __name__ == "__main__":
    sys.exit(main())