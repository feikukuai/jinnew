"""通过 GitHub Git Data API 推送仓库内容。

本机到 github.com:443 的 git 通道被网络阻断（连接重置），但 api.github.com 可用，
因此改用 REST API 直接创建 blob/tree/commit 并更新 ref。

用法：
    python push_via_api.py            # 推送
    python push_via_api.py --dry-run  # 只列出将要上传的文件
"""
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

OWNER = "feikukuai"
REPO = "jinnew"
BRANCH = "main"
API = "https://api.github.com"

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
# ROOT = 工作区根目录（repo_jinnew 的上一级）
REPO_DIR = os.path.join(ROOT, "repo_jinnew")

SKIP_DIRS = {"build", ".gradle", ".idea", "out", "sample_mod_extract", "__pycache__"}
SKIP_FILES = {"local.properties"}


def token():
    out = subprocess.check_output(["gh", "auth", "token"], text=True)
    return out.strip()


TOKEN = token()


def api(method, path, payload=None):
    url = API + path
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    req.add_header("User-Agent", "jinnew-pusher")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        raise SystemExit("[x] %s %s -> %s\n%s" % (method, path, e.code, body[:800]))


def collect():
    """返回 [(本地绝对路径, 仓库内相对路径)]"""
    items = []

    def add(local, remote):
        if not os.path.isfile(local):
            raise SystemExit("[x] 缺少文件: " + local)
        items.append((local, remote))

    def walk(src_dir, remote_prefix, skip_extra=()):
        for cur, dirs, files in os.walk(src_dir):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS and d not in skip_extra]
            for f in files:
                if f in SKIP_FILES:
                    continue
                full = os.path.join(cur, f)
                rel = os.path.relpath(full, src_dir).replace("\\", "/")
                items.append((full, remote_prefix + "/" + rel))

    add(os.path.join(REPO_DIR, "README.md"), "README.md")
    add(os.path.join(REPO_DIR, ".gitignore"), ".gitignore")

    walk(os.path.join(ROOT, "jy-goldenfinger"), "android")
    walk(os.path.join(ROOT, "goldenfinger"), "mod")

    add(os.path.join(ROOT, "delivery_build", "test_ledger.lua"), "mod/examples/test_ledger.lua")
    add(os.path.join(ROOT, "delivery_build", "retest_ledger.lua"), "mod/examples/retest_ledger.lua")

    add(os.path.join(ROOT, "apk_probe", "dump_bundle.py"), "tools/dump_bundle.py")
    add(os.path.join(ROOT, "apk_probe", "patch_test.py"), "tools/patch_test.py")
    add(os.path.join(ROOT, "delivery_build", "pack.py"), "tools/pack.py")

    add(os.path.join(ROOT, "delivery_build", "guide_src.html"), "docs/使用说明.html")
    add(os.path.join(ROOT, "apk_probe", "xlua_api.txt"), "docs/xlua_api.txt")

    add(os.path.join(REPO_DIR, "push_via_api.py"), "tools/push_via_api.py")

    return items


def main():
    dry = "--dry-run" in sys.argv
    items = collect()
    items.sort(key=lambda x: x[1])

    total = sum(os.path.getsize(p) for p, _ in items)
    print("待上传 %d 个文件，共 %.1f KB" % (len(items), total / 1024.0))
    for p, r in items:
        print("  %8d  %s" % (os.path.getsize(p), r))
    if dry:
        return 0

    ref = api("GET", "/repos/%s/%s/git/ref/heads/%s" % (OWNER, REPO, BRANCH))
    head_sha = ref["object"]["sha"]
    head = api("GET", "/repos/%s/%s/git/commits/%s" % (OWNER, REPO, head_sha))
    base_tree = head["tree"]["sha"]
    print("\n当前 %s = %s" % (BRANCH, head_sha[:7]))

    tree_entries = []
    for local, remote in items:
        raw = open(local, "rb").read()
        blob = api("POST", "/repos/%s/%s/git/blobs" % (OWNER, REPO), {
            "content": base64.b64encode(raw).decode("ascii"),
            "encoding": "base64",
        })
        tree_entries.append({
            "path": remote,
            "mode": "100644",
            "type": "blob",
            "sha": blob["sha"],
        })
        print("  blob %s  %s" % (blob["sha"][:7], remote))

    tree = api("POST", "/repos/%s/%s/git/trees" % (OWNER, REPO), {
        "base_tree": base_tree,
        "tree": tree_entries,
    })
    print("tree = %s" % tree["sha"][:7])

    message = """refactor(ui): 任务页纯任务化，金手指页改为「兑换 / 分配武功」页签

- 任务页只保留任务打卡与兑换点余额，兑换入口全部移到金手指页
- 金手指页顶部固定兑换点余额 + 可折叠「主角状态」卡
- 金手指页用页签切换「兑换」（秘籍/资源）与「分配武功」，避免秘籍过多把分配区挤到页面底部
- 秘籍与包裹列表默认只显示前 6 项，支持展开/收起
- 分区标题统一为强调型样式（左侧竖条 + 分隔线）
- 破坏性操作增加二次确认；修复设置页版本号与快速上手文案
- 同步更新使用说明文档"""
    commit = api("POST", "/repos/%s/%s/git/commits" % (OWNER, REPO), {
        "message": message,
        "tree": tree["sha"],
        "parents": [head_sha],
    })
    print("commit = %s" % commit["sha"][:7])

    api("PATCH", "/repos/%s/%s/git/refs/heads/%s" % (OWNER, REPO, BRANCH), {
        "sha": commit["sha"],
    })
    print("\n[ok] 已推送到 https://github.com/%s/%s/commit/%s" % (OWNER, REPO, commit["sha"]))
    return 0


if __name__ == "__main__":
    sys.exit(main())