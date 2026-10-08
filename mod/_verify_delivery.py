import sys, os, zipfile, tempfile, UnityPy

OUTER = sys.argv[1]           # 江湖金手指-交付包.zip
OUTDIR = sys.argv[2]          # temp dir
MOD_ENTRY = "江湖金手指-交付包/mod/诗酒芳华录-金手指版.zip"

os.makedirs(OUTDIR, exist_ok=True)

print("=== outer zip entries ===")
with zipfile.ZipFile(OUTER) as z:
    for i in z.infolist():
        print("  %-52s %12d" % (i.filename, i.file_size))
    # extract nested mod zip
    nested_path = os.path.join(OUTDIR, "mod.zip")
    with z.open(MOD_ENTRY) as src, open(nested_path, "wb") as dst:
        while True:
            b = src.read(1 << 20)
            if not b:
                break
            dst.write(b)
print("nested mod zip ->", nested_path, os.path.getsize(nested_path))

print("=== nested mod zip entries ===")
with zipfile.ZipFile(nested_path) as z2:
    for i in z2.infolist():
        print("  %-24s %12d" % (i.filename, i.file_size))
    mod_bundle = os.path.join(OUTDIR, "jy_ry_mod")
    with z2.open("jy_ry_mod") as src, open(mod_bundle, "wb") as dst:
        while True:
            b = src.read(1 << 20)
            if not b:
                break
            dst.write(b)
print("bundle ->", mod_bundle, os.path.getsize(mod_bundle))

MARK = "[goldenfinger]"
env = UnityPy.load(mod_bundle)
hotfix_len = None
hotfix_ok = False
preload = None
for obj in env.objects:
    try:
        d = obj.read()
    except Exception:
        continue
    if obj.type.name == "TextAsset":
        name = getattr(d, "m_Name", "") or ""
        s = d.m_Script
        if isinstance(s, bytes):
            s = s.decode("utf-8", "surrogateescape")
        if name == "hotfix":
            hotfix_len = len(s)
            hotfix_ok = MARK in s
            fixed = ("set_skill_level" in s) and ("(level - 1) * 100" in s) and ("role.Exp" in s)
    elif obj.type.name == "MonoBehaviour":
        if (getattr(d, "m_Name", "") or "") == "ModSetting":
            preload = getattr(d, "PreloadedLua", None)

print()
print("hotfix len            :", hotfix_len)
print("hotfix has goldenfinger:", hotfix_ok)
print("PreloadedLua has hotfix:", bool(preload) and ("hotfix" in list(preload)))
print("RESULT:", "PASS" if (hotfix_ok and preload and "hotfix" in list(preload)) else "FAIL")