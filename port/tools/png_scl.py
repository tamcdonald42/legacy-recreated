#!/usr/bin/env python3
"""Convert between ordinary image files and the pack's overlay sprite format (.scl).

The game can only draw sprites it knows about, so replacement art is delivered as an
overlay: a named sprite (data file key + entry) plus the pixels to draw in its place.
This tool is the bridge an artist needs - edit a PNG in anything, convert it, drop it
in the pack.

Format (.scl), little endian:

    +0x00  'SCL1'
    +0x04  uint16  width
    +0x06  uint16  height
    +0x08  uint16  scale     1 = draw as authored, 2 = each source pixel becomes 2x2
    +0x0A  uint16  pixels     width*height, RGB565, row major

A pixel of 0x0000 is not drawn (the hook skips it), which is how transparency works.
PNG alpha below --alpha-threshold converts to 0x0000.

Usage:
    png_scl.py to-scl art.png art.scl [--scale N] [--alpha-threshold N]
    png_scl.py to-png art.scl art.png
    png_scl.py info art.scl

Requires ffmpeg on PATH (used for PNG encode/decode so there is no Pillow dependency).
"""

import argparse
import os
import struct
import subprocess
import sys
import tempfile

MAGIC = b"SCL1"


def _run(args):
    proc = subprocess.run(args, capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError((proc.stderr or b"").decode("utf-8", "replace").strip()[-300:]
                           or "ffmpeg failed")
    return proc.stdout


def _probe(path):
    out = _run(["ffprobe", "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height", "-of", "csv=p=0", path])
    w, h = out.decode().strip().split(",")[:2]
    return int(w), int(h)


def to_scl(src, dst, scale=1, alpha_threshold=128):
    """Image file -> .scl. Any format ffmpeg can read."""
    w, h = _probe(src)
    raw = _run(["ffmpeg", "-v", "error", "-i", src, "-f", "rawvideo",
                "-pix_fmt", "rgba", "-"])
    expected = w * h * 4
    if len(raw) < expected:
        raise RuntimeError("short read from %s (%d of %d bytes)" % (src, len(raw), expected))

    body = bytearray(MAGIC)
    body += struct.pack("<HHH", w, h, scale)

    transparent = 0
    for i in range(0, expected, 4):
        r, g, b, a = raw[i], raw[i + 1], raw[i + 2], raw[i + 3]
        if a < alpha_threshold:
            body += b"\x00\x00"
            transparent += 1
            continue
        body += struct.pack("<H", ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3))

    with open(dst, "wb") as fh:
        fh.write(bytes(body))

    return {"width": w, "height": h, "scale": scale,
            "bytes": len(body), "transparent": transparent}


def read_scl(path):
    with open(path, "rb") as fh:
        data = fh.read()
    if len(data) < 10 or data[:4] != MAGIC:
        raise RuntimeError("%s is not an .scl file" % path)
    w, h, scale = struct.unpack_from("<HHH", data, 4)
    pixels = data[10:]
    if len(pixels) < w * h * 2:
        raise RuntimeError("%s is truncated: %d of %d pixel bytes"
                           % (path, len(pixels), w * h * 2))
    return w, h, scale, pixels


def to_png(src, dst):
    """ .scl -> image file, so art can be inspected or edited further."""
    w, h, scale, pixels = read_scl(src)

    with tempfile.TemporaryDirectory() as td:
        raw_path = os.path.join(td, "art.rgba")
        with open(raw_path, "wb") as fh:
            for i in range(w * h):
                value = struct.unpack_from("<H", pixels, i * 2)[0]
                if value == 0:
                    fh.write(b"\x00\x00\x00\x00")
                    continue
                r = ((value >> 11) & 0x1F) * 255 // 31
                g = ((value >> 5) & 0x3F) * 255 // 63
                b = (value & 0x1F) * 255 // 31
                fh.write(bytes((r, g, b, 255)))
        _run(["ffmpeg", "-v", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba",
              "-s", "%dx%d" % (w, h), "-i", raw_path, "-pix_fmt", "rgb24", dst])

    return {"width": w, "height": h, "scale": scale}


def main(argv):
    parser = argparse.ArgumentParser(description="pack overlay sprite converter")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p1 = sub.add_parser("to-scl", help="PNG (or any image) -> .scl")
    p1.add_argument("src")
    p1.add_argument("dst")
    p1.add_argument("--scale", type=int, default=1,
                    help="1 = native size (layout safe), 2+ enlarges on screen")
    p1.add_argument("--alpha-threshold", type=int, default=128,
                    help="alpha below this becomes transparent")

    p2 = sub.add_parser("to-png", help=".scl -> PNG")
    p2.add_argument("src")
    p2.add_argument("dst")

    p3 = sub.add_parser("info", help="describe an .scl")
    p3.add_argument("src")

    args = parser.parse_args(argv)

    try:
        if args.cmd == "to-scl":
            info = to_scl(args.src, args.dst, args.scale, args.alpha_threshold)
            print("%s -> %s" % (args.src, args.dst))
            print("  %(width)dx%(height)d, scale %(scale)d, %(bytes)d bytes, "
                  "%(transparent)d transparent pixels" % info)
            if args.scale != 1:
                print("  note: scale %d draws %dx larger than the sprite cell and will "
                      "overflow its layout" % (args.scale, args.scale))
        elif args.cmd == "to-png":
            info = to_png(args.src, args.dst)
            print("%s -> %s  (%dx%d, scale %d)"
                  % (args.src, args.dst, info["width"], info["height"], info["scale"]))
        else:
            w, h, scale, pixels = read_scl(args.src)
            opaque = sum(1 for i in range(w * h)
                         if struct.unpack_from("<H", pixels, i * 2)[0] != 0)
            print("%s: %dx%d, scale %d, %d/%d opaque pixels"
                  % (args.src, w, h, scale, opaque, w * h))
    except Exception as exc:
        print("error: %s" % exc, file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
