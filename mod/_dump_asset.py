import sys, UnityPy

src = sys.argv[1]
target = sys.argv[2]
out = sys.argv[3]
env = UnityPy.load(src)
for obj in env.objects:
    if obj.type.name != "TextAsset":
        continue
    try:
        d = obj.read()
    except Exception:
        continue
    name = getattr(d, "m_Name", "") or ""
    if name == target:
        s = d.m_Script
        if isinstance(s, bytes):
            s = s.decode("utf-8", "surrogateescape")
        with open(out, "w", encoding="utf-8") as f:
            f.write(s)
        print("dumped %s len=%d -> %s" % (name, len(s), out))
        sys.exit(0)
print("not found:", target)
sys.exit(2)