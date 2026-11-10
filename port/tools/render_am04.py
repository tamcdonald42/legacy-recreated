#!/usr/bin/env python3
"""render_am04.py - render a sprite from an AM04 file, outside the game.

Transcribed from the blitter FUN_00404b20 (original 1.04). Field mapping was
corrected by reading FUN_00409050, which builds the five pointers passed to it:

    param_1[0] = &image[0x18]   -> *param_1 = palette records (0x230 stride)
    param_1[1] = image[0x20]    -> section D: 0x18-byte entries
    param_1[2] = image[0x38]    -> section G: row table, 8 bytes/row
    param_1[3] = image[0x30]    -> section F: run descriptors, 8 bytes/run
    param_1[4] = image[0x28]    -> section E: pixel indices

Row table entry  (8 bytes): +0 uint32 first descriptor, +4 uint32 run count
Descriptor entry (8 bytes): +0 uint32 offset into E, +4 uint16 skip, +6 uint16 count

Usage:
    python3 render_am04.py FILE.bin --entry N --height H --out OUT.png
"""
import argparse
import struct
import zlib


def read_sections(data):
    names = {'A': 0x08, 'B': 0x10, 'pal': 0x18, 'D': 0x20, 'E': 0x28, 'F': 0x30, 'G': 0x38}
    return {k: struct.unpack_from('<I', data, v)[0] for k, v in names.items()}


def write_png(path, w, h, rows):
    raw = b''.join(b'\x00' + bytes(r) for r in rows)
    def chunk(tag, payload):
        c = struct.pack('>I', len(payload)) + tag + payload
        return c + struct.pack('>I', zlib.crc32(tag + payload) & 0xFFFFFFFF)
    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(raw, 9))
    png += chunk(b'IEND', b'')
    open(path, 'wb').write(png)


def palette_rgb(data, sec, index):
    rec = sec['pal'] + index * 0x400
    out = []
    for i in range(256):
        c = struct.unpack_from('<I', data, rec + i * 4)[0]
        # colour lives in the top three bytes (the low byte is padding), so read it
        # as 0xRRGGBB00 - reading from the bottom shifts every channel by one
        out.append(((c >> 24) & 0xFF, (c >> 16) & 0xFF, (c >> 8) & 0xFF))
    return out


def render(data, entry, height, pal_index=0, width=None):
    sec = read_sections(data)
    pal = palette_rgb(data, sec, pal_index)
    D, E, F, G = sec['D'], sec['E'], sec['F'], sec['G']

    row_start = struct.unpack_from('<i', data, D + entry * 0x18 + 0x10)[0]
    entry_w = struct.unpack_from('<i', data, D + entry * 0x18 + 0x14)[0]

    rows = []
    maxw = 0
    for y in range(height):
        row_off = G + (row_start + y) * 8
        if row_off + 8 > len(data):
            break
        first, runs = struct.unpack_from('<II', data, row_off)
        if runs == 0 or runs > 4096:
            break

        canvas = {}
        x = 0
        ok = True

        for r in range(runs):
            d_off = F + (first + r) * 8
            if d_off + 8 > len(data):
                ok = False
                break
            offset, skip, count = struct.unpack_from('<IHH', data, d_off)
            x += skip
            for i in range(count):
                p = E + offset + i
                if p >= len(data):
                    ok = False
                    break
                canvas[x] = pal[data[p]]
                x += 1
            if not ok:
                break

        if not canvas:
            rows.append(None)
            continue

        w = max(max(canvas) + 1, entry_w if 0 < entry_w < 4096 else 0)
        if w > maxw:
            maxw = w
        line = [0] * (w * 3)
        for px, rgb in canvas.items():
            line[px * 3:px * 3 + 3] = list(rgb)
        rows.append(line)

    if width:
        maxw = max(maxw, width)
    if maxw == 0:
        raise SystemExit('no rows rendered - entry/height likely wrong')
    out = []
    for r in rows:
        if r is None:
            out.append([0] * (maxw * 3))
        else:
            pad = maxw * 3 - len(r)
            out.append(r + [0] * pad)
    return maxw, len(out), out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('file')
    ap.add_argument('--entry', type=int, default=0)
    ap.add_argument('--height', type=int, default=64)
    ap.add_argument('--palette', type=int, default=0)
    ap.add_argument('--width', type=int, default=0)
    ap.add_argument('--out', default='sprite.png')
    a = ap.parse_args()

    data = open(a.file, 'rb').read()
    w, h, rows = render(data, a.entry, a.height, a.palette, a.width or None)
    write_png(a.out, w, h, rows)
    print('%s  %dx%d  entry=%d' % (a.out, w, h, a.entry))


if __name__ == '__main__':
    main()
