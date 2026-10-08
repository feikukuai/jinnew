"""
江湖金手指 · Mod 注入器

把 goldenfinger.lua 追加进目标 mod 的 hotfix 脚本（该脚本由 mod 的 PreloadedLua 预加载），
从而在游戏内自动读取账本并发放历练点奖励。

用法:
    python inject_mod.py <原始_mod_包> <输出_mod_包> [goldenfinger.lua]

示例:
    python inject_mod.py sample_mod sample_mod_patched goldenfinger.lua

原理与安全性:
    - 只读取/改写 bundle 里的 TextAsset（纯文本），不触碰美术资源与配置表。
    - 采用「追加」而非「覆盖」，保留 mod 原有的 hotfix 逻辑。
    - 原始包不会被修改；输出为新文件，可直接替换游戏 mods 目录下的同名文件。
"""

import sys
import os
import UnityPy

MARK = "-- === 江湖金手指 注入开始 ==="
MARK_END = "-- === 江湖金手指 注入结束 ==="


def find_hotfix(env):
    for obj in env.objects:
        if obj.type.name == "TextAsset":
            d = obj.read()
            if (getattr(d, "m_Name", "") or "") == "hotfix":
                return obj, d
    return None, None


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(1)

    src = sys.argv[1]
    dst = sys.argv[2]
    lua_path = sys.argv[3] if len(sys.argv) > 3 else os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "goldenfinger.lua")

    with open(lua_path, "r", encoding="utf-8") as f:
        lua_src = f.read()

    env = UnityPy.load(src)
    obj, data = find_hotfix(env)
    if obj is None:
        print("[x] 该 mod 包内没有找到名为 hotfix 的脚本，无法注入。")
        print("    可用以下方式确认包内脚本名：python dump_bundle.py <包> ")
        sys.exit(2)

    cur = data.m_Script
    if isinstance(cur, bytes):
        cur = cur.decode("utf-8", "surrogateescape")

    if MARK in cur:
        print("[!] 该包已注入过，先移除旧注入再重新写入。")
        start = cur.index(MARK)
        end = cur.index(MARK_END) + len(MARK_END)
        cur = cur[:start] + cur[end:]

    merged = cur.rstrip() + "\n\n" + MARK + "\n" + lua_src + "\n" + MARK_END + "\n"
    data.m_Script = merged
    data.save()

    blob = env.file.save(packer="original")
    if isinstance(blob, dict):
        blob = list(blob.values())[0]
    with open(dst, "wb") as f:
        f.write(blob)

    # 回读校验
    env2 = UnityPy.load(dst)
    _, d2 = find_hotfix(env2)
    s2 = d2.m_Script
    if isinstance(s2, bytes):
        s2 = s2.decode("utf-8", "surrogateescape")
    ok = "goldenfinger" in s2 and MARK_END in s2
    print("[ok] 已输出: %s (%d bytes)" % (dst, len(blob)))
    print("[ok] 回读校验: %s" % ("通过" if ok else "失败"))


if __name__ == "__main__":
    main()