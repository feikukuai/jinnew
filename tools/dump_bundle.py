import sys, os
import UnityPy

path = sys.argv[1]
outdir = sys.argv[2] if len(sys.argv) > 2 else None
if outdir:
    os.makedirs(outdir, exist_ok=True)

env = UnityPy.load(path)
counts = {}
texts = []
for obj in env.objects:
    t = obj.type.name
    counts[t] = counts.get(t, 0) + 1
    if t in ("TextAsset", "MonoBehaviour", "ScriptableObject"):
        try:
            data = obj.read()
            name = getattr(data, "m_Name", "") or getattr(data, "name", "")
        except Exception as e:
            name = "<err:%s>" % e
        texts.append((t, name))
        if t == "TextAsset" and outdir:
            try:
                raw = data.m_Script
                if isinstance(raw, str):
                    raw = raw.encode("utf-8", "surrogateescape")
                safe = name.replace("/", "_").replace("\\", "_")
                with open(os.path.join(outdir, safe), "wb") as f:
                    f.write(raw)
            except Exception:
                pass

print("=== object type counts ===")
for k in sorted(counts):
    print("%-24s %d" % (k, counts[k]))
print("=== TextAsset / Mono / ScriptableObject names (first 200) ===")
for t, n in texts[:200]:
    print("%-16s %s" % (t, n))
print("total listed:", len(texts))