import sys, UnityPy

src = sys.argv[1]
env = UnityPy.load(src)

MARK = "[goldenfinger]"

print("=== TextAssets in bundle ===")
ta_names = []
hotfix_has_gf = False
for obj in env.objects:
    if obj.type.name != "TextAsset":
        continue
    try:
        d = obj.read()
    except Exception as e:
        continue
    name = getattr(d, "m_Name", "") or ""
    s = d.m_Script
    if isinstance(s, bytes):
        s = s.decode("utf-8", "surrogateescape")
    ta_names.append((name, len(s)))
    if name == "hotfix" and MARK in s:
        hotfix_has_gf = True
for n, l in sorted(ta_names):
    print("  %-24s %8d" % (n, l))

print()
print("hotfix contains goldenfinger:", hotfix_has_gf)

print()
print("=== ScriptableObject / MonoBehaviour named ModSetting ===")
for obj in env.objects:
    if obj.type.name not in ("MonoBehaviour",):
        continue
    try:
        d = obj.read()
    except Exception:
        continue
    name = getattr(d, "m_Name", "") or ""
    if name == "ModSetting":
        print("  found ModSetting, keys:", list(d.__dict__.keys())[:40])
        for k, v in d.__dict__.items():
            if "lua" in k.lower() or "preload" in k.lower() or "mod" in k.lower():
                print("   ", k, "=", v)