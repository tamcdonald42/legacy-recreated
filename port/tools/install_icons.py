#!/usr/bin/env python3
"""Convert a folder of replacement art into pack overlays and write the manifest.

Keeps the boring part of an art pack mechanical: any PNG dropped in the art folder with
the right name becomes an .scl in the pack and a manifest line. Re-running is safe; the
manifest is rewritten from the map, so removed art stops being installed.

A map file describes what each name replaces, one per line:

    NAME      KEY       ENTRY
    sword     01001F47  0
    disc      01001F4B  7
    disc      01001F4B  6        # same art, second entry (idle vs hovered)

Art is expected at <art>/<name>.png, at the sprite's own size (43x43, 73x37, ...).

Usage:
    install_icons.py --art art/inventory --pack H:/ghidra/pack/scale --map art/inventory/icons.txt
"""

import argparse
import os
import sys


def load_converter(tools_dir):
    import importlib.util
    path = os.path.join(tools_dir, "png_scl.py")
    spec = importlib.util.spec_from_file_location("png_scl", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def read_map(path):
    rows = []
    with open(path, "r", encoding="utf-8") as fh:
        for lineno, line in enumerate(fh, 1):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) < 3:
                raise SystemExit("%s:%d: expected NAME KEY ENTRY" % (path, lineno))
            name, key, entry = parts[0], parts[1], parts[2]
            rows.append((name, key.upper(), int(entry)))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--art", required=True, help="folder holding <name>.png")
    ap.add_argument("--pack", required=True, help="pack scale folder (writable)")
    ap.add_argument("--map", required=True, help="map file: NAME KEY ENTRY")
    ap.add_argument("--scale", type=int, default=1,
                    help="1 = native size (layout safe); 2+ overflows the sprite cell")
    args = ap.parse_args()

    tools_dir = os.path.dirname(os.path.abspath(__file__))
    png_scl = load_converter(tools_dir)

    rows = read_map(args.map)
    os.makedirs(args.pack, exist_ok=True)

    manifest = []
    missing = 0
    for name, key, entry in rows:
        src = os.path.join(args.art, name + ".png")
        if not os.path.exists(src):
            print("skip  %-12s (no %s)" % (name, os.path.basename(src)))
            missing += 1
            continue
        dst_name = name.upper() + ".scl"
        dst = os.path.join(args.pack, dst_name)
        info = png_scl.to_scl(src, dst, scale=args.scale)
        print("ok    %-12s %s entry %-3d <- %dx%d" % (name, key, entry,
                                                      info["width"], info["height"]))
        manifest.append("%s %d %s" % (key, entry, dst_name))

    with open(os.path.join(args.pack, "scale.txt"), "w", newline="\r\n") as fh:
        for line in manifest:
            fh.write(line + "\r\n")

    print("\nwrote %d manifest lines to %s" % (len(manifest),
                                               os.path.join(args.pack, "scale.txt")))
    if missing:
        print("%d entries skipped for missing art" % missing)
    return 0


if __name__ == "__main__":
    sys.exit(main())
