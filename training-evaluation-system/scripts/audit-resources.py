"""审计随应用交付的前端资源和JAR内容，不将文本检查当作原创性鉴定。

仅使用Python标准库：python scripts/audit-resources.py
结果写入target/resource-audit.json，不修改业务代码、数据库或第三方许可。
"""
import hashlib
import json
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATIC = ROOT / "src/main/resources/static"
findings = []
files = []
for path in sorted(STATIC.rglob("*")):
    if not path.is_file():
        continue
    relative = path.relative_to(ROOT).as_posix()
    content = path.read_bytes()
    files.append({"path": relative, "sha256": hashlib.sha256(content).hexdigest()})
    if any(part.lower() in {"vendor", "node_modules"} for part in path.relative_to(STATIC).parts):
        findings.append({"file": relative, "issue": "供应商资源目录，需要人工检查来源"})
    if path.suffix.lower() not in {".html", ".css", ".js"}:
        findings.append({"file": relative, "issue": "非代码资源，需要人工检查来源"})
        continue
    text = content.decode("utf-8")
    patterns = [r'<(?:script|link|img|iframe|source)\b[^>]*(?:src|href)\s*=\s*[\"\']\s*(?:https?:)?//',
                r'@import\s+(?:url\()?\s*[\"\']?(?:https?:)?//',
                r'url\(\s*[\"\']?(?:https?:)?//',
                r'(?:fetch|importScripts)\s*\(\s*[\"\'](?:https?:)?//']
    for pattern in patterns:
        if re.search(pattern, text, re.I):
            findings.append({"file": relative, "issue": "发现外部资源加载表达式"})

jars = sorted((ROOT / "target").glob("training-evaluation-*.jar"))
libraries = []
jar_name = None
if jars:
    jar_name = jars[-1].name
    with zipfile.ZipFile(jars[-1]) as jar:
        entries = jar.namelist()
        libraries = [p.rsplit("/", 1)[-1] for p in entries if p.startswith("BOOT-INF/lib/") and p.endswith(".jar")]
        # 仅检查静态目录；不误删Java依赖中的许可证和版权声明。
        expected = {"BOOT-INF/classes/static/" + p.relative_to(STATIC).as_posix() for p in STATIC.rglob("*") if p.is_file()}
        packaged = {p for p in entries if p.startswith("BOOT-INF/classes/static/") and not p.endswith("/")}
        for extra in sorted(packaged - expected):
            findings.append({"file": extra, "issue": "打包静态资源在当前源码中不存在，可能是旧构建残留"})
        for missing in sorted(expected - packaged):
            findings.append({"file": missing, "issue": "源码资源未进入当前JAR"})

report = {"scope": "本地前端资源、直接外链表达式和JAR静态目录一致性检查",
          "not_originality_certification": True, "frontend_files": files,
          "findings": findings, "jar": jar_name, "third_party_java_libraries": libraries,
          "notes": ["开源Java依赖依然存在，不能宣称整个软件无第三方成分。",
                    "文档调研链接不是运行时资源；保留调研历史不代表代码依赖。",
                    "开发配置保留用户指定的临时穿透地址，不代表生产必须使用第三方服务。",
                    "不能通过关键字扫描证明不存在代码相似性或所有权争议。"]}
(ROOT / "target").mkdir(exist_ok=True)
(ROOT / "target/resource-audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"前端文件：{len(files)}；可疑项：{len(findings)}；JAR内第三方Java库：{len(libraries)}")
for finding in findings:
    print(finding)
raise SystemExit(1 if findings else 0)
