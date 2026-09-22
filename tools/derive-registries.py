#!/usr/bin/env python3
"""
Derives a registry capture (codecs.nbt + tags.nbt entry) for one protocol from a vanilla client jar,
for versions nobody has captured from a live server yet.

    python tools/derive-registries.py --jar <client.jar> --report <registries.json> --protocol 777 \
        [--codecs husk-server/src/main/resources/codecs.nbt] [--tags husk-server/src/main/resources/tags.nbt]

<registries.json> is the vanilla data-generator report for the same version:
    java -cp <client jar + its libraries + jopt-simple> net.minecraft.data.Main --reports --output out

What it builds:
  - codecs[protocol]: every synchronized registry (read from RegistryDataLoader's bytecode via javap)
    with its entries sorted by key, as {type, value: [{name, id, element}]}. Elements are the jar's
    JSON converted naively to NBT — fine for keys-only sync (known packs) and for reading
    dimension heights; not guaranteed byte-identical to what a vanilla server would send.
  - tags[protocol]: for the registries tagged in the newest existing capture, every tag in the jar,
    resolved to entry ids (data registries: the index above; built-in ones: the report's ids).

Verify against a real capture first: run it for a version you already have captured and --compare.
"""
import argparse
import gzip
import io
import json
import re
import struct
import subprocess
import zipfile


# ---- NBT (just what the captures use) --------------------------------------------------------

def _skip(d, p, t):
    if t in (1,): return p + 1
    if t == 2: return p + 2
    if t in (3, 5): return p + 4
    if t in (4, 6): return p + 8
    if t == 7: return p + 4 + struct.unpack(">i", d[p:p + 4])[0]
    if t == 8: return p + 2 + struct.unpack(">H", d[p:p + 2])[0]
    if t == 9:
        et, n = d[p], struct.unpack(">i", d[p + 1:p + 5])[0]
        p += 5
        for _ in range(n): p = _skip(d, p, et)
        return p
    if t == 10:
        while True:
            et = d[p]; p += 1
            if et == 0: return p
            p += 2 + struct.unpack(">H", d[p:p + 2])[0]
            p = _skip(d, p, et)
    if t == 11: return p + 4 + 4 * struct.unpack(">i", d[p:p + 4])[0]
    if t == 12: return p + 4 + 8 * struct.unpack(">i", d[p:p + 4])[0]
    raise ValueError(f"tag type {t}")


def _root_entries(d):
    """(name, start, end) spans of the root compound's children."""
    p = 3 + struct.unpack(">H", d[1:3])[0]
    out = []
    while d[p] != 0:
        start, t = p, d[p]
        n = struct.unpack(">H", d[p + 1:p + 3])[0]
        name = d[p + 3:p + 3 + n].decode()
        end = _skip(d, p + 3 + n, t)
        out.append((name, start, end))
        p = end
    return out, p


def _str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _type(v):
    if isinstance(v, bool): return 1
    if isinstance(v, int): return 3 if -2**31 <= v < 2**31 else 4
    if isinstance(v, float): return 6
    if isinstance(v, str): return 8
    if isinstance(v, IntArray): return 11
    if isinstance(v, list): return 9
    if isinstance(v, dict): return 10
    raise TypeError(type(v))


class IntArray(list):
    pass


def _payload(v):
    t = _type(v)
    if t == 1: return struct.pack(">b", 1 if v else 0)
    if t == 3: return struct.pack(">i", v)
    if t == 4: return struct.pack(">q", v)
    if t == 6: return struct.pack(">d", v)
    if t == 8: return _str(v)
    if t == 11: return struct.pack(">i", len(v)) + b"".join(struct.pack(">i", x) for x in v)
    if t == 10:
        return b"".join(bytes([_type(x)]) + _str(k) + _payload(x) for k, x in v.items()) + b"\x00"
    # list: NBT lists are homogeneous; mixed lists wrap each element as {"": value} (as vanilla does)
    types = {_type(x) for x in v}
    if len(types) > 1:
        v, types = [{"": x} for x in v], {10}
    et = types.pop() if types else 0
    return bytes([et]) + struct.pack(">i", len(v)) + b"".join(_payload(x) for x in v)


def put_root_entry(path, name, value):
    """Adds/replaces the root compound's child `name` in a gzipped NBT file."""
    d = gzip.open(path).read()
    entries, end = _root_entries(d)
    for n, s, e in entries:
        if n == name:
            d = d[:s] + d[e:]
            end -= e - s
            break
    d = d[:end] + bytes([10]) + _str(name) + _payload(value) + d[end:]
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as g:
        g.write(d)
    open(path, "wb").write(buf.getvalue())


def root_names(path):
    return [n for n, _, _ in _root_entries(gzip.open(path).read())[0]]


# ---- derivation ------------------------------------------------------------------------------

def synced_registries(jar):
    """Registries.X field names in RegistryDataLoader.SYNCHRONIZED_REGISTRIES, in order."""
    out = subprocess.run(["javap", "-c", "-p", "-cp", jar, "net.minecraft.resources.RegistryDataLoader"],
                         capture_output=True, text=True, check=True).stdout
    init = out[out.index("static {};"):]
    fields, current = {}, []
    for line in init.splitlines():
        m = re.search(r"getstatic .*Registries\.(\w+):", line)
        if m: current.append(m.group(1))
        m = re.search(r"putstatic .*Field (\w+):", line)
        if m:
            fields[m.group(1)] = current
            current = []
    return fields["SYNCHRONIZED_REGISTRIES"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--jar", required=True)
    ap.add_argument("--report", required=True, help="vanilla registries.json report")
    ap.add_argument("--protocol", required=True)
    ap.add_argument("--codecs", default="husk-server/src/main/resources/codecs.nbt")
    ap.add_argument("--tags", default="husk-server/src/main/resources/tags.nbt")
    args = ap.parse_args()

    jar = zipfile.ZipFile(args.jar)
    files = [n for n in jar.namelist() if n.startswith("data/minecraft/") and n.endswith(".json")]
    report = json.load(open(args.report, encoding="utf-8"))

    def entries_under(path):  # keys of data/minecraft/<path>/**.json, sorted
        prefix = f"data/minecraft/{path}/"
        return sorted("minecraft:" + n[len(prefix):-5] for n in files if n.startswith(prefix))

    codec, index = {}, {}
    for field in synced_registries(args.jar):
        name = field.lower()
        path = next((p for p in (name, "worldgen/" + name) if any(f.startswith(f"data/minecraft/{p}/") for f in files)), None)
        if path is None:
            raise SystemExit(f"no data folder for synchronized registry {field}")
        keys = entries_under(path)
        registry_id = "minecraft:" + path
        values = []
        for i, key in enumerate(keys):
            element = json.loads(jar.read(f"data/minecraft/{path}/{key[len('minecraft:'):]}.json"))
            values.append({"name": key, "id": i, "element": element})
        codec[registry_id] = {"type": registry_id, "value": values}
        index[registry_id] = {k: i for i, k in enumerate(keys)}

    # Tag the same registries as the newest existing capture does.
    tag_registries = sorted(_tag_registry_names(args.tags))
    tags = {}
    for registry_id in tag_registries:
        path = registry_id[len("minecraft:"):]
        prefix = f"data/minecraft/tags/{path}/"
        raw = {"minecraft:" + n[len(prefix):-5]: json.loads(jar.read(n))["values"]
               for n in files if n.startswith(prefix)}
        if registry_id in index:
            ids = index[registry_id]
        else:
            ids = {k: v["protocol_id"] for k, v in report[registry_id]["entries"].items()}

        resolved = {}

        def resolve(tag, seen=()):
            if tag in resolved: return resolved[tag]
            out = []
            for v in raw.get(tag, []):
                ref, required = (v["id"], v.get("required", True)) if isinstance(v, dict) else (v, True)
                if ref.startswith("#"):
                    if ref[1:] in seen: raise SystemExit(f"tag cycle at {ref}")
                    out += resolve(ref[1:], seen + (tag,))
                elif ref in ids:
                    out.append(ids[ref])
                elif required:
                    print(f"  warning: {registry_id} tag {tag}: unknown entry {ref}")
            resolved[tag] = list(dict.fromkeys(out))
            return resolved[tag]

        tags[registry_id] = {t: IntArray(resolve(t)) for t in sorted(raw)}

    put_root_entry(args.codecs, args.protocol, codec)
    put_root_entry(args.tags, args.protocol, tags)
    print(f"protocol {args.protocol}: {len(codec)} registries, {sum(len(t) for t in tags.values())} tags")


def _tag_registry_names(tags_path):
    d = gzip.open(tags_path).read()
    entries, _ = _root_entries(d)
    newest = max(entries, key=lambda e: int(e[0]) if e[0].isdigit() else -1)
    _, s, _e = newest
    # children of the newest protocol's compound
    p = s + 3 + struct.unpack(">H", d[s + 1:s + 3])[0]
    names = []
    while d[p] != 0:
        t = d[p]
        n = struct.unpack(">H", d[p + 1:p + 3])[0]
        names.append(d[p + 3:p + 3 + n].decode())
        p = _skip(d, p + 3 + n, t)
    return names


if __name__ == "__main__":
    main()
