import sys, UnityPy

src = sys.argv[1]
needles = sys.argv[2].split(",")
env = UnityPy.load(src)
hits = 0
for obj in env.objects:
    if obj.type.name != "TextAsset":
        continue
    try:
        d = obj.read()
    except Exception:
        continue
    s = d.m_Script
    if isinstance(s, bytes):
        s = s.decode("utf-8", "surrogateescape")
    name = getattr(d, "m_Name", "") or ""
    for n in needles:
        if n and n in s:
            hits += 1
            print("=== NAME=%s  len=%d  needle=%s ===" % (name, len(s), n))
            for i, line in enumerate(s.splitlines(), 1):
                if any(k in line for k in needles):
                    print("%5d: %s" % (i, line.strip()[:200]))
            print()
            break
print("total hit assets:", hits)