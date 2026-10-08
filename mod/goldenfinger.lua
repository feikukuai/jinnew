--[[
  江湖金手指 · 游戏内 Mod (jynew / 群侠传，启动！)
  ==================================================================
  由「江湖金手指」App 注入到 Mod 的 hotfix 脚本（随 Mod 一起被游戏预加载）。

  双向同步（走游戏桥接，不改存档文件）：
    1) App → 游戏：读取账本 ledger.lua（App 写出的 Lua 表），把现实任务赚的
       兑换点，兑现成 武功等级 / 秘籍物品 / 银两 / 主角历练（GF.ApplyPending）。
    2) 游戏 → App：把「当前读档槽位 + 主角角色面板 + 包裹 + 已学武功 + 武功秘籍目录」
       导出为 state.json（GF.ExportState），供 App 展示与同步进度。
       不同存档槽位的进度不同，导出时带上 archiveIndex，App 据此区分。

  账本 ledger.lua 结构（由 App 生成）：
    return {
      version = 1,
      points = 100,                      -- 当前兑换点余额
      pending = {
        { kind = "SKILL",  skill = "降龙十八掌", level = 3 },  -- 设置武功等级（1..N 的人类等级）
        { kind = "ITEM",   item = 34, amount = 1 },            -- 发放物品（秘籍=书物品ID）
        { kind = "SILVER", amount = 1000 },                    -- 发放银两（物品ID=174）
        { kind = "EXP",    amount = 200 },                     -- 主角历练
      },
    }

  ---- 以下接口均取自 jynew 源码（LuaCore/Jyx2LuaBridge.cs、GameSave/RoleInstance.cs、
       GameSave/SkillInstance.cs、LuaScripts/Jyx2Configs/Jyx2ConfigMgr.lua）----

  配置表（Lua 模块，随游戏初始化必然存在）：
    Jyx2.ConfigMgr.Skill[skillId] -> {Id,Name,...}
    Jyx2.ConfigMgr.Item[itemId]   -> {Id,Name,ItemType,Skill,NeedExp,...}
  兜底：CS.Jyx2.LuaToCsBridge.SkillTable / .ItemTable（C# 静态字典）

  C# 桥（静态方法，与 Mod 自身 hotfix 的是同一个类）：
    CS.Jyx2.Jyx2LuaBridge.AddItem(itemId, count)      -- 物品/银两（MONEY_ID）
    CS.Jyx2.Jyx2LuaBridge.GetMoneyCount()             -- 读取银两
    CS.Jyx2.Jyx2LuaBridge.ShowToast(msg)
    CS.Jyx2.Jyx2LuaBridge.GetTeamId()

  角色/武功（GameRuntimeData / RoleInstance / SkillInstance）：
    CS.Jyx2.GameRuntimeData.Instance:GetRole(0)       -- 0 == 主角
    role.Wugongs[i].Key / .Level                      -- Level 存储格式 (人类等级-1)*100
    role.Exp / role:CanLevelUp() / role:LevelUp()
    CS.Jyx2.SkillInstance(skillId)                    -- 新建武功实例

  注意：CS.Jyx2.Jyx2LuaBridge.LearnMagic2(roleId, magicId, noDisplay) 的第三个参数是
  「是否显示提示」，并非等级；它只能「未学则学会、已学则 +1 级」，无法设置指定等级。
  因此金手指直接改写 role.Wugongs 中对应武功的 Level。
]]

local cs_coroutine = require('Jyx2Coroutine')

local GF = {}

-- 必须与 App 的 GamePaths.exchangeDirCandidates() 完全一致
local DIRS = {
  "/storage/emulated/0/jynew/goldenfinger",
  "/sdcard/jynew/goldenfinger",
  "/storage/emulated/0/Android/data/com.jynew.wuxia_launch/files/goldenfinger",
}
local LEDGER_NAME = "ledger.lua"
local STATE_NAME  = "state.json"
local MONEY_ITEM_ID = 174   -- 游戏内「银两」物品 ID（GameConst.MONEY_ID）
local ITEM_TYPE_BOOK = 2    -- ItemConfig.ItemType == 2 表示「武功秘籍」

-- 当前读档槽位（由读档钩子写入）
local current_archive = -1

--======================== 通用读写 ========================--
local function read_all(path)
  local ok, f = pcall(io.open, path, "r")
  if ok and f then
    local s = f:read("*a"); f:close()
    if s and #s > 0 then return s end
  end
  local ok2, s2 = pcall(function() return CS.System.IO.File.ReadAllText(path) end)
  if ok2 and s2 then return s2 end
  return nil
end

local function write_all(name, text)
  for _, d in ipairs(DIRS) do
    pcall(function() CS.System.IO.Directory.CreateDirectory(d) end)
    local ok = pcall(function()
      CS.System.IO.File.WriteAllText(d .. "/" .. name, text)
    end)
    if ok then return true end
    local ok2, f = pcall(io.open, d .. "/" .. name, "w")
    if ok2 and f then f:write(text); f:close(); return true end
  end
  return false
end

local function load_ledger()
  for _, d in ipairs(DIRS) do
    local text = read_all(d .. "/" .. LEDGER_NAME)
    if text then
      local loader = load or loadstring
      local chunk = loader(text)
      if chunk then
        local ok, data = pcall(chunk)
        if ok and type(data) == "table" then return data end
      end
    end
  end
  return nil
end

--======================== 桥接对象 ========================--
local BRIDGE = nil
pcall(function() BRIDGE = CS.Jyx2.Jyx2LuaBridge end)

local function show_toast(msg)
  if BRIDGE ~= nil then pcall(function() BRIDGE.ShowToast(msg) end) end
end

local function get_role(roleId)
  local ok, rt = pcall(function() return CS.Jyx2.GameRuntimeData.Instance end)
  if not ok or rt == nil then return nil end
  local ok2, r = pcall(function() return rt:GetRole(roleId) end)
  if ok2 then return r end
  return nil
end

--======================== 配置表访问 ========================--
-- 优先使用游戏 Lua 模块 Jyx2.ConfigMgr（纯 Lua，随游戏初始化必然存在）
local function config_dict(name)
  local ok, mgr = pcall(function() return Jyx2.ConfigMgr end)
  if ok and mgr ~= nil then
    local t = mgr[name]
    if type(t) == "table" then return t end
  end
  -- 兜底：C# 桥的静态字典（需 LuaConfigToCsInit 已执行）
  local ok2, t2 = pcall(function() return CS.Jyx2.LuaToCsBridge[name .. "Table"] end)
  if ok2 and t2 ~= nil then return t2 end
  return nil
end

local skill_table, item_table
local function get_skill_table()
  if skill_table == nil then skill_table = config_dict("Skill") or false end
  return skill_table or nil
end
local function get_item_table()
  if item_table == nil then item_table = config_dict("Item") or false end
  return item_table or nil
end

-- 读取配置字段（兼容 Lua table 与 C# 对象）
local function cfg_field(cfg, field)
  if cfg == nil then return nil end
  local ok, v = pcall(function() return cfg[field] end)
  if ok and v ~= nil then return v end
  return nil
end

local function skill_name(key)
  local cfg = nil
  local t = get_skill_table()
  if t then
    local ok, c = pcall(function() return t[key] end)
    if ok then cfg = c end
  end
  local n = cfg_field(cfg, "Name")
  if n ~= nil and #tostring(n) > 0 then return tostring(n) end
  return tostring(key)
end

-- 武功名 → 技能 Key
local skill_index = nil
local function build_skill_index()
  local idx = {}
  local t = get_skill_table()
  if t == nil then return idx end
  pcall(function()
    for k, s in pairs(t) do
      if type(s) == "table" then
        local nm = cfg_field(s, "Name")
        if nm ~= nil then
          local id = cfg_field(s, "Id")
          -- Id 缺省或为占位 0 时退回表键；同名条目不得让占位 0 覆盖有效 id
          if id == nil or id == 0 then id = k end
          local cur = idx[nm]
          if cur == nil or (cur == 0 and id ~= 0) then idx[nm] = id end
        end
      end
    end
  end)
  return idx
end

local function skill_key_of(name)
  if skill_index == nil then skill_index = build_skill_index() end
  return skill_index[name]
end

-- 在角色「已学武功」里按名字定位 key：优先命中已学的（避免配置表同名占位项 Id=0 冲突）
local function find_learned_key(role, name)
  if role == nil or role.Wugongs == nil then return nil end
  local target = tostring(name)
  local found = nil
  pcall(function()
    for i = 0, role.Wugongs.Count - 1 do
      local sk = role.Wugongs[i]
      if sk ~= nil and tostring(skill_name(sk.Key)) == target then
        if found == nil then
          found = sk.Key
        elseif found == 0 and sk.Key ~= 0 then
          found = sk.Key
        end
      end
    end
  end)
  return found
end

-- 物品 ID → 配置（LItemConfig: Id Name ItemType Skill NeedExp ...）
local function build_item_index()
  local idx = {}
  local t = get_item_table()
  if t == nil then return idx end
  pcall(function()
    for k, it in pairs(t) do
      if type(it) == "table" then
        local id = cfg_field(it, "Id")
        if id ~= nil then idx[id] = it end
      end
    end
  end)
  return idx
end

local item_index = nil
local function item_of(id)
  local t = get_item_table()
  if t then
    local ok, it = pcall(function() return t[id] end)
    if ok and it ~= nil then return it end
  end
  if item_index == nil then item_index = build_item_index() end
  return item_index[id]
end

local function item_name(id)
  local n = cfg_field(item_of(id), "Name")
  if n ~= nil and #tostring(n) > 0 then return tostring(n) end
  return "物品#" .. tostring(id)
end

local function is_book(id)
  local it = item_of(id)
  if it == nil then return false end
  return cfg_field(it, "ItemType") == ITEM_TYPE_BOOK
end

--======================== 方向一：App → 游戏 ========================--
-- 设置武功等级（human level，1..N）。游戏内存储格式：Level = (level-1)*100
local function set_skill_level(role, skillKey, level)
  if level == nil or level < 1 then level = 1 end
  local target = (level - 1) * 100

  if role.Wugongs ~= nil then
    for i = 0, role.Wugongs.Count - 1 do
      local sk = role.Wugongs[i]
      if sk ~= nil and sk.Key == skillKey then
        sk.Level = target
        pcall(function() role:ResetSkillCasts() end)
        return true
      end
    end
  end

  local s = CS.Jyx2.SkillInstance(skillKey)
  s.Level = target
  role.Wugongs:Add(s)
  pcall(function() role:ResetSkillCasts() end)
  return true
end

-- 清理历史错误注入残留：Key<=0 且同名已存在有效项的武功（仅此情形才移除，避免误删）
local function repair_wugongs(role)
  if role == nil or role.Wugongs == nil then return 0 end
  local removed = 0
  pcall(function()
    local i = 0
    while i < role.Wugongs.Count do
      local sk = role.Wugongs[i]
      local bad = false
      if sk ~= nil and (sk.Key == nil or sk.Key <= 0) then
        local nm = tostring(skill_name(sk.Key))
        for j = 0, role.Wugongs.Count - 1 do
          local o = role.Wugongs[j]
          if o ~= nil and o.Key ~= nil and o.Key > 0 and tostring(skill_name(o.Key)) == nm then
            bad = true
            break
          end
        end
      end
      if bad then
        role.Wugongs:RemoveAt(i)
        removed = removed + 1
      else
        i = i + 1
      end
    end
  end)
  if removed > 0 then
    print("[goldenfinger] repair: 已移除无效武功 " .. removed .. " 项")
  end
  return removed
end

local function add_exp(role, exp)
  if exp == nil or exp <= 0 then return end
  role.Exp = role.Exp + exp
  local guard = 0
  while role:CanLevelUp() and guard < 1000 do
    role:LevelUp()
    guard = guard + 1
  end
end

local function apply_one(e)
  local roleId = e.role or 0
  local amount = e.amount or 0

  if e.kind == "SKILL" then
    local role = get_role(roleId)
    if role == nil then error("no role: " .. tostring(roleId)) end
    -- 先按「角色已学武功」的名字定位，再退回配置表；避免同名占位项导致重复新增
    local key = find_learned_key(role, e.skill)
    if key == nil then key = skill_key_of(e.skill) end
    if key == nil then error("unknown skill: " .. tostring(e.skill)) end
    set_skill_level(role, key, e.level)
  elseif e.kind == "ITEM" then
    if BRIDGE ~= nil then BRIDGE.AddItem(e.item, amount) else AddItem(e.item, amount) end
  elseif e.kind == "SILVER" then
    if BRIDGE ~= nil then BRIDGE.AddItem(MONEY_ITEM_ID, amount) else AddItem(MONEY_ITEM_ID, amount) end
  elseif e.kind == "EXP" then
    local role = get_role(roleId)
    if role == nil then error("no role: " .. tostring(roleId)) end
    add_exp(role, amount)
  end
end

-- 把单条指令序列化回 Lua 表（保留 id，供 App 精确清除已结算项）
local function entry_lua(e)
  local parts = { 'id = "' .. tostring(e.id or "") .. '"' }
  parts[#parts + 1] = 'kind = "' .. tostring(e.kind or "") .. '"'
  if e.skill ~= nil then parts[#parts + 1] = 'skill = "' .. tostring(e.skill) .. '"' end
  if e.level ~= nil then parts[#parts + 1] = 'level = ' .. tostring(e.level) end
  if e.item ~= nil then parts[#parts + 1] = 'item = ' .. tostring(e.item) end
  if e.amount ~= nil then parts[#parts + 1] = 'amount = ' .. tostring(e.amount) end
  return '{ ' .. table.concat(parts, ', ') .. ' }'
end

function GF.ApplyPending()
  local data = load_ledger()
  if not data or type(data.pending) ~= "table" or #data.pending == 0 then
    print("[goldenfinger] ApplyPending: 无待下发指令")
    return 0
  end
  local n = 0
  local failed = {}
  for _, e in ipairs(data.pending) do
    local ok, err = pcall(apply_one, e)
    if ok then
      n = n + 1
    else
      failed[#failed + 1] = e
      print("[goldenfinger] 发放失败: " .. tostring(e.kind) .. " " .. tostring(e.skill) .. " :: " .. tostring(err))
    end
  end
  print("[goldenfinger] ApplyPending: 成功 " .. n .. "/" .. #data.pending)
  if n > 0 then
    -- 已生效的项从账本移除，仅保留失败项；App 见到 id 消失即清空对应「待下发」
    local body = {}
    for _, e in ipairs(failed) do body[#body + 1] = "    " .. entry_lua(e) .. "," end
    write_all(LEDGER_NAME, "return {\n  version = 1,\n  pending = {\n" ..
      table.concat(body, "\n") .. "\n  },\n}\n")
    show_toast("江湖金手指：已兑现 " .. n .. " 项兑换点奖励")
  end
  return n
end

--======================== 方向二：游戏 → App ========================--
local function esc(s)
  s = tostring(s or "")
  s = s:gsub("\\", "\\\\"):gsub('"', '\\"'):gsub("\n", "\\n"):gsub("\r", "")
  return '"' .. s .. '"'
end

local function role_json(r)
  local wugongs, items = {}, {}
  pcall(function()
    for i = 0, r.Wugongs.Count - 1 do
      local sk = r.Wugongs[i]
      wugongs[#wugongs + 1] = string.format(
        '{"key":%d,"level":%d,"name":%s}', sk.Key, sk.Level, esc(skill_name(sk.Key)))
    end
  end)
  -- 背包物品在 GameRuntimeData.Instance.Items（Dictionary<string,(count,time)>），不是 role.Items
  pcall(function()
    local bag = CS.Jyx2.GameRuntimeData.Instance.Items
    if bag ~= nil then
      for k, v in pairs(bag) do
        local id = tonumber(k)
        local cnt = v.Item1
        if id ~= nil and cnt ~= nil and cnt > 0 then
          items[#items + 1] = string.format(
            '{"itemId":%d,"count":%d,"name":%s,"isBook":%s}',
            id, cnt, esc(item_name(id)), tostring(is_book(id)))
        end
      end
    end
  end)
  return string.format(
    '{"key":%d,"name":%s,"level":%d,"exp":%d,' ..
    '"hp":%d,"maxHp":%d,"mp":%d,"maxMp":%d,' ..
    '"attack":%d,"qinggong":%d,"defence":%d,' ..
    '"quanzhang":%d,"yujian":%d,"shuadao":%d,"qimen":%d,"anqi":%d,' ..
    '"wuxuechangshi":%d,"pinde":%d,"shengwang":%d,"iq":%d,' ..
    '"wugongs":[%s],"items":[%s]}',
    r.Key, esc(r.Name), r.Level, r.Exp,
    r.Hp, r.MaxHp, r.Mp, r.MaxMp,
    r.Attack, r.Qinggong, r.Defence,
    r.Quanzhang, r.Yujian, r.Shuadao, r.Qimen, r.Anqi,
    r.Wuxuechangshi, r.Pinde, r.Shengwang, r.IQ,
    table.concat(wugongs, ","), table.concat(items, ","))
end

-- 武功秘籍目录（供 App 生成「兑换秘籍」列表）：ItemType==2 且 Skill>0
local function books_json()
  local t = get_item_table()
  if t == nil then return "" end
  local list = {}
  pcall(function()
    for k, it in pairs(t) do
      if type(it) == "table" then
        local itemType = cfg_field(it, "ItemType")
        local skillKey = cfg_field(it, "Skill") or 0
        if itemType == ITEM_TYPE_BOOK and skillKey > 0 then
          local id = cfg_field(it, "Id") or k
          list[#list + 1] = { id = id, it = it, skillKey = skillKey }
        end
      end
    end
  end)
  table.sort(list, function(a, b) return a.id < b.id end)

  local out = {}
  for _, e in ipairs(list) do
    local needExp = cfg_field(e.it, "NeedExp") or 0
    out[#out + 1] = string.format(
      '{"itemId":%d,"name":%s,"skillKey":%d,"skillName":%s,"needExp":%d}',
      e.id, esc(cfg_field(e.it, "Name")), e.skillKey, esc(skill_name(e.skillKey)), needExp)
  end
  return table.concat(out, ",")
end

function GF.ExportState()
  local ok, rt = pcall(function() return CS.Jyx2.GameRuntimeData.Instance end)
  if not ok or rt == nil then return false end
  local player = rt.Player
  if player == nil then return false end

  local team = {}
  pcall(function()
    local ids = rt:GetTeamId()
    for i = 0, ids.Count - 1 do team[#team + 1] = ids[i] end
  end)

  local money = 0
  if BRIDGE ~= nil then
    pcall(function() money = BRIDGE.GetMoneyCount() end)
  end

  local modId = ""
  pcall(function() modId = CS.Jyx2.RuntimeEnvSetup.CurrentModId end)

  local json = string.format(
    '{"version":1,"modId":%s,"archiveIndex":%d,"money":%d,"team":[%s],' ..
    '"books":[%s],"player":%s}\n',
    esc(modId), current_archive, money, table.concat(team, ","),
    books_json(), role_json(player))

  write_all(STATE_NAME, json)
  return true
end

--======================== 挂载 ========================--
local util = require 'xlua.util'

-- 读档/初始化完成后：先兑现兑换点，再导出面板
local function on_loaded(index)
  if type(index) == "number" then current_archive = index end
  pcall(function() repair_wugongs(get_role(0)) end)
  pcall(GF.ApplyPending)
  pcall(GF.ExportState)
end

-- 主角数据初始化后
pcall(function()
  util.hotfix_ex(CS.Jyx2.RoleInstance, "InitData", function(self)
    self:InitData()
    on_loaded(nil)
  end)
end)

-- 读档（多个候选挂载点，兼容不同游戏版本）
pcall(function()
  util.hotfix_ex(CS.Jyx2.GameRuntimeData, "LoadArchive", function(index)
    local r = CS.Jyx2.GameRuntimeData.LoadArchive(index)
    on_loaded(index)
    return r
  end)
end)

pcall(function()
  util.hotfix_ex(CS.StoryEngine, "DoLoadGame", function(index)
    local r = CS.StoryEngine.DoLoadGame(index)
    cs_coroutine.start(function()
      coroutine.yield(CS.UnityEngine.WaitForSeconds(1))
      on_loaded(index)
    end)
    return r
  end)
end)

print("[goldenfinger] loaded (api=Jyx2.ConfigMgr + Jyx2LuaBridge)")
return GF