#!/usr/bin/env python3
"""am04_info.py - read Septerra Core AM04 image blobs without the game.

Format, per the loader (FUN_0040f000 in the original 1.04 binary), which
relocates eight offsets to pointers at load time:

  +0x00  char[4]  'AM04'
  +0x04  uint32   type/flags
  +0x08  uint32   offset -> section A
  +0x10  uint32   offset -> section B
  +0x18  uint32   offset -> frame record array   (0x230 bytes per frame)
  +0x1C  uint32   number of frames
  +0x20  uint32   offset -> section D
  +0x28  uint32   offset -> section E (row descriptors)
  +0x30  uint32   offset -> section F
  +0x38  uint32   offset -> section G
  +0x40  uint32   offset -> section H (pixel pool)

Each frame record:

  +0x000  uint32       unknown
  +0x004  uint16[256]  RGB565 palette          (4 + 512 = 0x204)
  +0x204  uint32       alternate record pointer (blitter prefers it when non-zero)
  +0x208  uint16       mode 0/1/2
  ...
  +0x230  next record

The 0x204 boundary is the strongest confirmation available: the blitter reads the
palette at record+4 with 256 entries, and the field it treats as an override
begins at exactly 4 + 512.

Usage:
    python3 am04_info.py <file.bin> [--palette out.ppm]
"""

import struct
import sys

RECORD = 0x230
PALETTE_OFF = 0x04


def rgb565(c):
    r = (c >> 11) & 0x1F
    g = (c >> 5) & 0x3F
    b = c & 0x1F
    return ((r << 3) | (r >> 2), (g << 2) | (g >> 4), (b << 3) | (b >> 2))


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
    head["modes"] = []
    for i in range(n):
        r = rec_base + i * RECORD
        head["palettes"].append(struct.unpack_from("<256H", data, r + PALETTE_OFF))
        head["modes"].append(struct.unpack_from("<H", data, r + 0x208)[0])
    return head


def write_palette_ppm(head, path):
    rows = []
    for pal in head["palettes"]:
        line = b"".join(bytes(rgb565(c)) * 16 for c in pal)
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
    print("modes     : %s" % ", ".join(str(m) for m in head["modes"][:16]))
    print("palette[0] first 8: %s" % " ".join("%04x" % c for c in head["palettes"][0][:8]))
    if "--palette" in sys.argv:
        out = sys.argv[sys.argv.index("--palette") + 1]
        print("wrote     : %s" % write_palette_ppm(head, out))
    return 0


if __name__ == "__main__":
    sys.exit(main())
