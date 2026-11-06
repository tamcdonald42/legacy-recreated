#!/usr/bin/env python3
"""am04_info.py - read Septerra Core AM04 image blobs without the game.

Format, per the loader (FUN_0040f000 in the original 1.04 binary), which
relocates eight offsets to pointers at load time:

  +0x00  char[4]  'AM04'
  +0x04  uint32   type/flags
  +0x08  uint32   offset -> section A
  +0x10  uint32   offset -> section B
  +0x18  uint32   offset -> frame record array   (0x400 bytes per frame,
                                   i.e. 256 colour entries of 4 bytes)
  +0x1C  uint32   number of frames
  +0x20  uint32   offset -> section D
  +0x28  uint32   offset -> section E (row descriptors)
  +0x30  uint32   offset -> section F
  +0x38  uint32   offset -> section G
  +0x40  uint32   offset -> section H (pixel pool)

Each frame record:

  +0x000  uint32[256]  palette, 0x00BBGGRR per entry  (1024 bytes = 0x400)
  +0x400  next record

Verified against the live engine: the loaded copy matches the file byte for byte
except eight relocated offsets, the frame array pointer (replaced at runtime with
the engine's own converted copy) and the type dword (forced to 0xFFFFFFFF). Record
span / frame count is exactly 0x400 on every file checked.

Usage:
    python3 am04_info.py <file.bin> [--palette out.ppm]
"""

import struct
import sys

RECORD = 0x400   # 256 entries x 4 bytes (0x00BBGGRR)
PALETTE_OFF = 0x00


def rgb(c):
    """0x00BBGGRR -> (r, g, b)"""
    return (c & 0xFF, (c >> 8) & 0xFF, (c >> 16) & 0xFF)


def read_am04(data):
    if data[:4] != b"AM04":
        raise ValueError("not an AM04 blob (magic %r)" % data[:4])
    head = {
        "type": struct.unpack_from("<I", data, 0x04)[0],
        "frames": struct.unpack_from("<I", data, 0x1C)[0],
        "sections": {},
    }
    for name, off in (("A", 0x08), ("B", 0x10), ("records", 0x18), ("D", 0x20),
                      ("E", 0x28), ("F", 0x30), ("G", 0x38), ("H", 0x40)):
        head["sections"][name] = struct.unpack_from("<I", data, off)[0]
    rec_base = head["sections"]["records"]
    n = head["frames"]
    if n == 0 or rec_base + n * RECORD > len(data):
        raise ValueError("frame array runs past end of file (%d frames at 0x%x, file 0x%x)"
                         % (n, rec_base, len(data)))
    head["palettes"] = []
    for i in range(n):
        r = rec_base + i * RECORD
        head["palettes"].append(struct.unpack_from("<256I", data, r))
    return head


def write_palette_ppm(head, path):
    rows = []
    for pal in head["palettes"]:
        line = b"".join(bytes(rgb(c)) * 16 for c in pal)
        rows.extend([line] * 16)
    w, h = 256 * 16, len(rows)
    with open(path, "wb") as f:
        f.write(b"P6\n%d %d\n255\n" % (w, h))
        for line in rows:
            f.write(line)
    return path


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    path = sys.argv[1]
    data = open(path, "rb").read()
    head = read_am04(data)
    print("file      : %s (%d bytes)" % (path, len(data)))
    print("type      : 0x%x" % head["type"])
    print("frames    : %d" % head["frames"])
    print("sections  :")
    for name, off in head["sections"].items():
        note = "  (empty)" if off == 0 else ("  (OUTSIDE FILE)" if off >= len(data) else "")
        print("   %-8s 0x%06x%s" % (name, off, note))
    distinct = len(set(head["palettes"][0]))
    print("palette[0] : first 8 = %s, %d distinct" %
          (" ".join("%06x" % (c & 0xFFFFFF) for c in head["palettes"][0][:8]), distinct))
    if "--palette" in sys.argv:
        out = sys.argv[sys.argv.index("--palette") + 1]
        print("wrote     : %s" % write_palette_ppm(head, out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
