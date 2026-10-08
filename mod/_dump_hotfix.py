import sys, UnityPy

src = sys.argv[1]
out = sys.argv[2]
env = UnityPy.load(src)
for obj in env.objects:
    if obj.type.name == "TextAsset":
        d = obj.read()
        name = getattr(d, "m_Name", "") or ""
        if name == "hotfix":
            s = d.m_Script
            if isinstance(s, bytes):
                s = s.decode("utf-8", "surrogateescape")
            with open(out, "w", encoding="utf-8") as f:
                f.write(s)
            print("hotfix len:", len(s))
            print("has goldenfinger:", "goldenfinger" in s)
            print("has MARK_END:", "江湖金手指 注入结束" in s)
            sys.exit(0)
print("hotfix not found")
sys.exit(2)