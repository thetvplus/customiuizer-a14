"""Implementation module for query_aux_data context; not another CLI or database.

Source links are a bounded navigation aid, never a Kotlin resolver or a runtime
call graph. Current source and canonical catalogs remain the owners of facts.
"""
from __future__ import annotations

import bisect
import contextlib
import hashlib
import json
from pathlib import Path
import re
import time
import xml.etree.ElementTree as ET

try:
    from . import rom_catalog as rom
    from .catalog_contract_probe import balanced_blocks, field, symbol_field
    from .extract_process_matrix import extract_class_body, parse_install_hooks
    from .settings_schema import read_element, ANDROID_NS
except ImportError:
    import rom_catalog as rom
    from catalog_contract_probe import balanced_blocks, field, symbol_field
    from extract_process_matrix import extract_class_body, parse_install_hooks
    from settings_schema import read_element, ANDROID_NS

VERSION = 8
JAVA = "app/src/main/java"
FEATURES = "mods/utils/feature/"
SOURCE_PATHS = [JAVA, "app/settings", "app/src/main/res/xml", "app/src/main/res/values/strings.xml",
                "app/src/main/res/values-zh-rCN/strings.xml", "app/src/test/java",
                "app/src/main/res/values/arrays.xml",
                "docs/rom-intelligence/A14_PROCESS_MATRIX.json",
                "rom-contracts/hyperos1-a14-core.json", "tools/dexsql"]


def source_paths(root):
    result = rom.run(["git", "-C", root, "ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", *SOURCE_PATHS])
    return sorted({p for p in result.stdout.split("\0") if p and (root / p).is_file()})


def scrub_comments(text):
    # Keep byte/line offsets and strings, blank comments. Used only for lexical navigation.
    token = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
    return token.sub(lambda m: re.sub(r"[^\n]", " ", m[0]) if m[0].startswith(("//", "/*")) else m[0], text)


def facts(path, text):
    clean = scrub_comments(text)
    lines = [m.start() for m in re.finditer("\n", text)]
    def line(at):
        return bisect.bisect_right(lines, at) + 1
    syntax = re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                    lambda m: re.sub(r"[^\n]", " ", m[0]), clean)
    stack, braces = [], {}
    for i, char in enumerate(syntax):
        if char == "{":
            stack.append(i)
        elif char == "}" and stack:
            braces[stack.pop()] = i
    declarations = list(re.finditer(r"\b(class|object|interface|fun)\s+([A-Za-z_]\w*)", syntax))
    symbols = [dict(name=m[2], kind=m[1], line=line(m.start())) for m in declarations]
    containers = []
    for i, match in enumerate(declarations):
        if match[1] != "fun":
            start = syntax.find("{", match.end())
            next_declaration = declarations[i + 1].start() if i + 1 < len(declarations) else len(syntax)
            if start in braces and start < next_declaration:
                containers.append((start, braces[start], match[2]))
    for symbol, match in zip(symbols, declarations):
        parents = [(stop-start, owner) for start, stop, owner in containers if start < match.start() < stop]
        symbol["owner"] = min(parents)[1] if parents else None
    calls = [dict(owner=m[1], method=m[2], line=line(m.start())) for m in re.finditer(
        r"\b([A-Z][\w]*)\.([a-zA-Z_]\w*)\s*\(", clean)]
    targets = [dict(descriptor="L" + m[1].replace(".", "/") + ";", line=line(m.start()))
               for m in re.finditer(r'"((?:com\.(?:android|miui|mi)\.|android\.)[A-Za-z_$][\w.$]+)"', clean)]
    def snippets(pattern):
        return [dict(line=i, text=s.strip()[:200]) for i, s in enumerate(clean.splitlines(), 1)
                if re.search(pattern, s)]
    return dict(path=path, symbols=symbols, calls=calls, targets=targets,
                preferences=sorted(set(re.findall(r'"((?:system|launcher|controls|various)_[a-z0-9_]+)"', clean))),
                loader=snippets(r"(?:classLoader|ClassLoader|plugin.*[Ll]oader)"),
                lifecycle=snippets(r"(?:fun |void )\w*(?:[Aa]ttach|[Dd]etach|[Dd]estroy|[Dd]ispose|[Rr]elease|[Cc]lose)\w*\s*\(|WeakReference|remove.*Listener|unregister"),
                failure=snippets(r"getThrowable\(|catch\s*\(|FatalErrors|require\(|check\("),
                hooks=snippets(r"(?:hookMethod|findAndHook|hookAllMethods|hookAllConstructors)\s*\("),
                imports=re.findall(r"^import\s+(tv\.withaibuild\.customiuizer\.[\w.]+)", clean, re.M))


def snapshot(db, root, refresh=False):
    """Incremental source facts inside the existing external SQLite catalog."""
    root = root.resolve()
    with db:
        db.execute("""CREATE TABLE IF NOT EXISTS code_sources(
            repo TEXT NOT NULL,path TEXT NOT NULL,signature TEXT NOT NULL,payload TEXT NOT NULL,
            PRIMARY KEY(repo,path))""")
    old = {r["path"]: r for r in db.execute("SELECT * FROM code_sources WHERE repo=?", (str(root),))}
    paths = source_paths(root)
    parser_hash = hashlib.sha256(b"".join((Path(__file__).parent / name).read_bytes()
        for name in ("agent_context.py", "extract_process_matrix.py", "catalog_contract_probe.py"))).hexdigest()
    data, parsed, hits, signatures, pending = {}, 0, 0, [], []
    for path in paths:
        file = root / path
        if not file.resolve().is_relative_to(root):
            raise ValueError("Source path escapes --repo-root")
        signature = rom.dump([VERSION, parser_hash, *rom.stamp(file)])
        signatures.append([path, signature])
        if not refresh and path in old and old[path]["signature"] == signature:
            data[path] = json.loads(old[path]["payload"])
            hits += 1
            continue
        text = file.read_text(encoding="utf-8")
        if path.startswith("app/settings/"):
            tree = read_element(file)
            ns = "{" + ANDROID_NS + "}"
            payload = [dict(key=n.get(ns + "key"), title=n.get(ns + "title"), entries=n.get(ns + "entries"))
                       for n in tree.iter() if n.get(ns + "key")]
        elif path.endswith(".json"):
            payload = json.loads(text)
        elif path.endswith(".xml"):
            tree = ET.fromstring(text)
            if "/values" in path:
                payload = {n.get("name"): (["".join(i.itertext()) for i in n.findall("item")]
                           if n.tag.endswith("array") else "".join(n.itertext())) for n in tree if n.get("name")}
            else:
                payload = [dict(key=n.get("{http://schemas.android.com/apk/res/android}key"),
                                title=n.get("{http://schemas.android.com/apk/res/android}title"),
                                entries=n.get("{http://schemas.android.com/apk/res/android}entries")) for n in tree.iter()
                           if n.get("{http://schemas.android.com/apk/res/android}key")]
        elif "app/src/test/" in path:
            payload = dict(path=path, testTargets=sorted(set(re.findall(r"\b[A-Z][A-Za-z]+(?:Hooks|Access|Controller|Background|Activities|Feature)\b", scrub_comments(text)))))
        elif path.endswith((".kt", ".java")):
            payload = facts(path, text)
            if FEATURES in path and path.endswith("Features.kt"):
                payload["specs"] = []
                hooks = parse_install_hooks(text)
                for block in balanced_blocks(scrub_comments(text), "LazyFeatureSpec"):
                    factory = re.search(r"factory\s*=\s*\{\s*(\w+)", block)
                    owner = factory[1] if factory else None
                    body = extract_class_body(text, owner) if owner else ""
                    payload["specs"].append(dict(key=field(block, "preferenceKey"), name=field(block, "name"),
                        featureObject=symbol_field(block, "id"), owner=owner, install=hooks.get(owner),
                        ownerLine=next((s["line"] for s in payload["symbols"] if s["name"] == owner), None),
                        bodyCalls=re.findall(r"\b([A-Z]\w*)\.(\w+)\s*\(", scrub_comments(hooks.get(owner) or ""))))
        elif path.endswith(".sql"):
            payload = dict(classes=sorted(set(re.findall(r"L(?:com/|android/)[\w/$]+;", text))))
        else:
            continue
        if rom.dump([VERSION, parser_hash, *rom.stamp(file)]) != signature:
            raise ValueError(f"Source changed during indexing: {path}")
        pending.append((str(root), path, signature, rom.dump(payload)))
        data[path], parsed = payload, parsed + 1
    with db:
        db.executemany("INSERT OR REPLACE INTO code_sources VALUES(?,?,?,?)", pending)
        for removed in old.keys() - set(paths):
            db.execute("DELETE FROM code_sources WHERE repo=? AND path=?", (str(root), removed))
    return data, dict(parsed=parsed, reused=hits, source_stamp=hashlib.sha256(rom.dump(signatures).encode()).hexdigest())


def compact_fact(value, detail=False):
    result = dict(path=value["path"], evidence="SOURCE_FACT")
    for key in ("targets", "loader", "lifecycle", "failure", "hooks"):
        limit = 12 if detail else 2
        result[key] = value[key][:limit]
        if len(value[key]) > limit:
            result[key + "More"] = len(value[key]) - limit
    return result


def select_rom(db, target, device=None, region=None):
    candidates = [dict(r) for r in db.execute("SELECT id,device,region,scope FROM roms WHERE scope='A14_PRODUCTION' ORDER BY id")]
    if device:
        candidates = [r for r in candidates if r["device"] == device]
    if region:
        candidates = [r for r in candidates if r["region"] == region]
    if target not in ("LAUNCHER", "SYSTEM_UI", "SYSTEM_SERVER") or not candidates:
        return dict(selected=[], reason="No unambiguous component mapping; use explicit ROM Catalog selectors")
    # Default main CN baseline; explicit hardware/region wins. Never fan out to every ROM.
    candidates.sort(key=lambda r: (r["device"] != "fuxi", r["region"] != "CN", r["id"]))
    chosen = candidates[0]
    component = {"LAUNCHER": "launcher", "SYSTEM_UI": "systemui", "SYSTEM_SERVER": "services.jar"}[target]
    rows = rom.select_files(db, chosen["id"], component)
    return dict(selected=[dict(rom=r["rom"], component=r["component"], path=r["path"], sha256=r["sha256"],
                               package=r["package"], scope=r["scope"], evidence="ARTIFACT_METADATA") for r in rows[:2]],
                reason="Explicit device/region, otherwise fuxi CN baseline; static selection is not runtime support")


def query(args):
    root = args.repo_root.resolve()
    with contextlib.closing(rom.connect(args.index_dir.resolve())) as db:
        saved = db.execute("SELECT value FROM meta WHERE key='root'").fetchone()
        if not saved:
            raise ValueError("ROM Catalog metadata missing; initialize the existing Catalog with update")
        rom.outside_sources(args.index_dir.resolve(), Path(saved[0]))
        data, reuse = snapshot(db, root, args.refresh)
        if data["rom-contracts/hyperos1-a14-core.json"].get("androidApi") != 34:
            raise ValueError("context accepts A14 source catalogs only; cross-version reference cannot enter production links")
        matrix = data["docs/rom-intelligence/A14_PROCESS_MATRIX.json"]["features"]
        code = {p: f for p, f in data.items() if p.startswith(JAVA) and p.endswith((".kt", ".java"))}
        definitions = {}
        for path, f in code.items():
            for symbol in f["symbols"]:
                if symbol["kind"] in ("class", "object", "interface"):
                    definitions.setdefault(symbol["name"], []).append((path, symbol["line"]))
        specs = [(p, s) for p, f in code.items() for s in f.get("specs", [])]
        labels = {}
        titles = data.get("app/src/main/res/values/strings.xml", {}) | data.get("app/src/main/res/values-zh-rCN/strings.xml", {})
        for path, rows in data.items():
            if path.startswith(("app/settings/", "app/src/main/res/xml")):
                for pref in rows:
                    key = (pref["key"] or "").removeprefix("pref_key_")
                    title = pref["title"] or ""
                    labels.setdefault(key, []).append(dict(name=titles.get(title.removeprefix("@string/"), title), path=path))
                    entries = data.get("app/src/main/res/values/arrays.xml", {}).get((pref.get("entries") or "").removeprefix("@array/"), [])
                    if entries:
                        labels[key][-1]["choices"] = [titles.get(e.removeprefix("@string/"), e) for e in entries]
        needle = (args.contains or args.source or "").casefold()
        canonical = args.key.removeprefix("pref_key_") if args.key else None
        primary_key = canonical and any(r["preferenceKey"] == canonical for r in matrix)
        selected = []
        for row in matrix:
            key = row["preferenceKey"]
            if primary_key and canonical != key:
                continue
            if args.feature and args.feature not in (row["featureIdName"], str(row["featureIdInt"])):
                continue
            if args.target and args.target != row["target"]:
                continue
            match_specs = [(p, s) for p, s in specs if s["key"] == key and Path(p).stem in row["installer"]]
            owner_calls = {(owner, method) for _, s in match_specs for owner, method in s["bodyCalls"]}
            if not match_specs:
                owner_calls |= set(re.findall(r"\b([A-Z]\w*)\.(\w+)\s*\(", row["installHook"]))
            owners = {owner for owner, _ in owner_calls if owner in definitions}
            # Include direct project dependencies of entry owners for reverse navigation.
            owner_paths = {p for owner in owners for p, _ in definitions.get(owner, []) if FEATURES not in p}
            dependency_names = {call["owner"] for p in owner_paths for call in code[p]["calls"]}
            related = owners | dependency_names
            linked_keys = {k for p in owner_paths for k in code[p]["preferences"]}
            searchable = json.dumps([row, list(related), labels.get(key, []),
                [labels.get(k, []) for k in linked_keys],
                [[s["name"] for s in code[p]["symbols"]] for p in owner_paths],
                [code[p]["targets"] for p in owner_paths]], ensure_ascii=False).casefold()
            if args.key and args.key.removeprefix("pref_key_") != key:
                # Secondary preference keys can map via the exact source file of a resolved owner.
                if canonical not in linked_keys:
                    continue
            if needle and needle not in searchable and not any(needle in p.casefold() for p in owner_paths):
                continue
            selected.append((row, match_specs, owner_calls, owner_paths))
        summary = args.summary or not any((args.key, args.feature, args.contains, args.source))
        result = dict(dataset="context", repo=str(root), evidence="SOURCE_FACT; lexical dependencies are CANDIDATE; no runtime validation",
                      matched=len(selected), offset=args.offset, nextOffset=None, index=reuse, records=[])
        registered_keys = {r["preferenceKey"] for r in matrix}
        unregistered = {s["key"] for _,s in specs if s["key"] and s["key"] not in registered_keys}
        if unregistered:
            result["matrixWarning"] = dict(unmappedPreferenceKeys=sorted(unregistered)[:5], more=max(0,len(unregistered)-5),
                action="Inspect current registration and regenerate the existing Process Matrix; no automatic compatibility conclusion")
        if summary:
            return result
        page = selected[args.offset:args.offset + args.limit]
        if len(selected) > args.offset + len(page):
            result["nextOffset"] = args.offset + len(page)
        for row, match_specs, calls, paths in page:
            related_paths = set(paths)
            for p in paths:
                for call in code[p]["calls"]:
                    related_paths.update(q for q, _ in definitions.get(call["owner"], []))
                related_paths.update(q for name in code[p]["imports"] for q, _ in definitions.get(name.rsplit(".", 1)[-1], []))
            related_paths -= paths
            contracts = [t for t in data["rom-contracts/hyperos1-a14-core.json"]["targets"] if t["sourceFile"] in paths]
            loaders = [dict(path=p, **entry) for p in sorted(paths) for entry in code[p]["loader"]]
            test_owners = {owner for owner, _ in calls} | {Path(p).stem for p in paths}
            tests = [p for p, f in data.items() if "app/src/test/" in p and test_owners.intersection(f.get("testTargets", []))]
            actual_install = " ".join(s["install"] or "UNKNOWN" for _, s in match_specs) or row["installHook"]
            record = dict(feature=row["featureIdName"], preferenceKey=row["preferenceKey"], labels=labels.get(row["preferenceKey"], [])[:2],
                owners=[dict(symbol=s["owner"], path=p, line=s["ownerLine"], evidence="SOURCE_FACT") for p, s in match_specs],
                installer=row["installer"], phase=row["phase"], install=actual_install, process=row["allowedProcess"],
                matrixStale=bool(match_specs and actual_install != row["installHook"]),
                entrypoints=[dict(owner=owner,method=method,path=p,line=s["line"],evidence="SOURCE_FACT")
                             for owner, method in sorted(calls) for p, _ in definitions.get(owner, []) if p in paths
                             for s in code[p]["symbols"] if s["kind"] == "fun" and s["name"] == method and s.get("owner") == owner][:12],
                callers=[dict(path=p,line=call["line"],owner=call["owner"],method=call["method"],evidence="CANDIDATE_LEXICAL")
                         for p,f in code.items() for call in f["calls"] if (call["owner"],call["method"]) in calls][:4],
                classLoader=dict(evidence="CANDIDATE; expressions only, not runtime loader identity", sources=loaders[:(8 if args.full else 2)]),
                source=[compact_fact(code[p], args.full) for p in sorted(paths) if not FEATURES in p],
                dependencies=[dict(path=p, evidence="CANDIDATE_LEXICAL") for p in sorted(related_paths)[:(20 if args.full else 5)]],
                dependenciesMore=max(0, len(related_paths) - (20 if args.full else 5)),
                contracts=contracts[:3], rom=select_rom(db, row["target"], args.device, args.region),
                verification=dict(level="Quick initially; Standard for generator/shared data consumers; Deep for runtime/ownership changes",
                    candidates=tests[:5], evidence="CANDIDATE: choose affected tests after reading actual diff; do not run APK for retrieval", more=max(0,len(tests)-5)),
                review=["Trace selected installer and actual callers before editing", "Read lifecycle and native exception/rollback branches at cited lines", "DEX_STATIC does not establish runtime ClassLoader, dispatch or device acceptance"])
            for target in record["rom"]["selected"]:
                target["processAdmission"] = "DECLARED_SOURCE_ROUTE" if target["package"] in row["allowedProcess"].split("; ") else "NOT_ESTABLISHED; static component selection does not add a production Hook route"
            target_classes = {t["descriptor"] for p in paths for t in code[p]["targets"]}
            record["queries"] = [p for p, f in data.items() if p.startswith("tools/dexsql/") and p.endswith(".sql") and target_classes.intersection(f["classes"])][:5]
            if canonical and canonical != row["preferenceKey"]:
                record["keyAssociation"] = "CANDIDATE_SOURCE_REFERENCE; not a direct Feature registration key"
            if args.dex_class or args.dex_method:
                selected_roms = record["rom"]["selected"]
                if not selected_roms:
                    raise ValueError("No production ROM selected for DEX evidence")
                chosen = selected_roms[0]
                dex_rows = rom.select_files(db, chosen["rom"], chosen["path"])
                engine = rom.Engine(db=db)
                kind, needle = ("method", args.dex_method) if args.dex_method else ("class", args.dex_class)
                record["dex"] = rom.search(db, dex_rows, engine, kind, needle, limit=5, exact=True)
                record["dex"]["evidence"] = "DEX_STATIC; not runtime validation"
                if args.dex_calls:
                    record["dexCalls"] = rom.search(db, dex_rows, engine, args.dex_calls, needle, limit=5, exact=True)
            if args.fields or not args.full:
                fields = args.fields.split(",") if args.fields else ["feature", "preferenceKey", "owners", "installer", "phase", "entrypoints", "process", "rom", "queries"]
                if not args.fields:
                    fields += (["matrixStale"] if record["matrixStale"] else []) + (["keyAssociation"] if "keyAssociation" in record else [])
                if not args.fields and (args.dex_class or args.dex_method):
                    fields += ["dex"] + (["dexCalls"] if args.dex_calls else [])
                unknown = set(fields) - set(record)
                if unknown:
                    raise ValueError("unknown context fields: " + ",".join(sorted(unknown)))
                record = {key: record[key] for key in fields}
            result["records"].append(record)
        return result
