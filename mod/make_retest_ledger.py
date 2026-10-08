"""从 App 持久化的指令队列生成带 id 的测试账本（模拟 LedgerExporter 的输出）。

用途：在真机上复测「游戏结算后 App 自动清空待下发指令」。
itemId<=0 的无效物品指令不写入账本，避免向游戏下发不存在的物品。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "goldenfinger", "out", "app_state.json")
DST = os.path.join(ROOT, "goldenfinger", "out", "retest_ledger_ids.lua")


def q(s):
    return '"' + str(s).replace("\\", "\\\\").replace('"', '\\"') + '"'


def main():
    d = json.load(open(SRC, encoding="utf-8"))
    cmds = d.get("commands", [])
    out = [
        "-- 江湖金手指账本 · 由伴侣 App 自动生成，请勿手改",
        "return {",
        "  version = 1,",
        "  points = %d," % d.get("points", 0),
        "  pending = {",
    ]
    written, skipped = 0, []
    for c in cmds:
        t = c["type"]
        if t == "SKILL":
            out.append("    { id = %s, kind = \"SKILL\", skill = %s, level = %d },"
                       % (q(c["id"]), q(c["skill"]), c["amount"]))
        elif t == "ITEM":
            if c["itemId"] <= 0:
                skipped.append(c)
                continue
            out.append("    { id = %s, kind = \"ITEM\", item = %d, amount = %d },"
                       % (q(c["id"]), c["itemId"], c["amount"]))
        elif t == "SILVER":
            out.append("    { id = %s, kind = \"SILVER\", amount = %d }," % (q(c["id"]), c["amount"]))
        elif t == "EXP":
            out.append("    { id = %s, kind = \"EXP\", amount = %d }," % (q(c["id"]), c["amount"]))
        else:
            skipped.append(c)
            continue
        written += 1
    out += ["  },", "}"]
    text = "\n".join(out) + "\n"
    open(DST, "w", encoding="utf-8").write(text)
    print("total %d, written %d, skipped %d" % (len(cmds), written, len(skipped)))
    for s in skipped:
        print("  skip:", s["skill"], "itemId=", s["itemId"])
    print(text)


if __name__ == "__main__":
    main()