#!/usr/bin/env python3
"""
Build the symbol -> subsystem map that CallTree.java uses.

The SR-Septerra shim headers are the authoritative list of what the game calls across
the asm/C boundary, and the header name tells you which subsystem each symbol belongs to.
Names are emitted raw and with the SRW suffixes stripped (_asm2c / _c2asm / _c), because
Ghidra shows them either way.

Writes <out>/shim_symbols.csv  (symbol,base,subsystem)
Run from the SR-Septerra source dir or pass it as argv[1].
"""
import collections
import os
import re
import sys

HERE = sys.argv[1] if len(sys.argv) > 1 else "H:/ghidra/reference/SR/games/Septerra Core/SR-Septerra"
OUT = sys.argv[2] if len(sys.argv) > 2 else "H:/ghidra/work/ghidra-out/shim_symbols.csv"

SUBSYSTEM = {
    "WinApi-ddraw.h": "graphics", "WinApi-dsound.h": "audio", "WinApi-gdi32.h": "gdi",
    "WinApi-kernel32.h": "os", "WinApi-user32.h": "ui", "WinApi-winmm.h": "timer",
    "QTML.h": "video", "MSS.h": "audio", "Game-DataFiles.h": "assets",
    "Game-Registry.h": "config", "Game-Config.h": "config", "Game-SoundEngine.h": "audio",
    "CLIB.h": "crt", "Game-Memory.h": "memory", "Game-Patch.h": "patch", "platform.h": "crt",
}

DECL = re.compile(r"\b(CCALL|STDCALL)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(")


def base(n):
    for suf in ("_asm2c", "_c2asm", "_c"):
        if n.endswith(suf):
            return n[: -len(suf)]
    return n


def main():
    found = {}
    for header, sub in SUBSYSTEM.items():
        path = os.path.join(HERE, header)
        if not os.path.exists(path):
            continue
        for line in open(path, encoding="latin1"):
            if line.lstrip().startswith(("//", "*")):
                continue
            for m in DECL.finditer(line):
                found.setdefault(m.group(2), sub)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("symbol,base,subsystem\n")
        for name, sub in sorted(found.items()):
            f.write(f"{name},{base(name)},{sub}\n")

    print(f"wrote {OUT}: {len(found)} symbols")
    for sub, n in collections.Counter(found.values()).most_common():
        print(f"  {sub:<10} {n}")


if __name__ == "__main__":
    main()
