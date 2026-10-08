import sys
import UnityPy

src, dst = sys.argv[1], sys.argv[2]
env = UnityPy.load(src)

patched = 0
for obj in env.objects:
    if obj.type.name == "TextAsset":
        d = obj.read()
        if d.m_Name == "hotfix":
            orig = d.m_Script
            if isinstance(orig, bytes):
                orig = orig.decode("utf-8", "surrogateescape")
            d.m_Script = orig + "\n-- === jygoldenfinger injected ===\nprint('goldenfinger loaded')\n"
            d.save()
            patched += 1

print("patched objects:", patched)

data = env.file.save(packer="original")
if isinstance(data, dict):
    for name, blob in data.items():
        with open(dst, "wb") as f:
            f.write(blob)
        print("saved:", name, len(blob))
else:
    with open(dst, "wb") as f:
        f.write(data)
    print("saved bytes:", len(data))

# verify round-trip
env2 = UnityPy.load(dst)
for obj in env2.objects:
    if obj.type.name == "TextAsset":
        d = obj.read()
        if d.m_Name == "hotfix":
            s = d.m_Script
            if isinstance(s, bytes):
                s = s.decode("utf-8", "surrogateescape")
            print("roundtrip contains marker:", "jygoldenfinger" in s)
            print("tail:", repr(s[-90:]))