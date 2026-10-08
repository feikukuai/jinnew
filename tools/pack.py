import os
import zipfile

ROOT = r"C:\Users\Administrator\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\work-mode-projects\6ac725999cd9129260da41d5"
BUILD = os.path.join(ROOT, "delivery_build")
EXTRACT = os.path.join(ROOT, "downloads", "extract")

MOD_ZIP = os.path.join(BUILD, "诗酒芳华录-金手指版.zip")
APK = os.path.join(ROOT, "jy-goldenfinger", "app", "build", "outputs", "apk", "debug", "app-debug.apk")
BASE_APK = r"C:\Users\Administrator\Downloads\base-2.apk"
DELIVERY = os.path.join(BUILD, "江湖金手指-交付包.zip")

# 1) Mod 压缩包：jy_ry.xml + 已注入的 jy_ry_mod + jy_ry_maps（deflate，与原包一致）
with zipfile.ZipFile(MOD_ZIP, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    z.write(os.path.join(EXTRACT, "jy_ry.xml"), "jy_ry.xml")
    z.write(os.path.join(BUILD, "jy_ry_mod"), "jy_ry_mod")
    z.write(os.path.join(EXTRACT, "jy_ry_maps"), "jy_ry_maps")
print("[ok] mod zip:", os.path.getsize(MOD_ZIP))

# 2) 交付包：大文件用 STORED（快速、与原包一致），工具/说明用 deflate
with zipfile.ZipFile(DELIVERY, "w") as z:
    z.write(APK, "江湖金手指-交付包/app/江湖金手指.apk", compress_type=zipfile.ZIP_STORED)
    z.write(MOD_ZIP, "江湖金手指-交付包/mod/诗酒芳华录-金手指版.zip", compress_type=zipfile.ZIP_STORED)
    z.write(BASE_APK, "江湖金手指-交付包/game/base-2.apk", compress_type=zipfile.ZIP_STORED)
    z.write(os.path.join(ROOT, "goldenfinger", "goldenfinger.lua"),
            "江湖金手指-交付包/tools/goldenfinger.lua", compress_type=zipfile.ZIP_DEFLATED)
    z.write(os.path.join(ROOT, "goldenfinger", "inject_mod.py"),
            "江湖金手指-交付包/tools/inject_mod.py", compress_type=zipfile.ZIP_DEFLATED)
    z.write(os.path.join(ROOT, "apk_probe", "dump_bundle.py"),
            "江湖金手指-交付包/tools/dump_bundle.py", compress_type=zipfile.ZIP_DEFLATED)
    z.write(os.path.join(BUILD, "guide_src.html"),
            "江湖金手指-交付包/使用说明.html", compress_type=zipfile.ZIP_DEFLATED)
print("[ok] delivery:", os.path.getsize(DELIVERY))