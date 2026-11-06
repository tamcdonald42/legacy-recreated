#!/usr/bin/env python3
"""am04_sheet.py - render every sprite entry in an AM04 file to one PNG grid.

Same layout knowledge as render_am04.py: section E holds pixel indices, F holds
(skip, count) runs, G holds per-row run references, D holds one 0x18-byte entry
per frame with the row index at +0x10 and the width at +0x14.

Usage: python3 am04_sheet.py FILE.bin OUT.png [--cols 13] [--cell 80]
"""
import struct
import sys
import zlib


def png(path, w, h, rows):
    raw = b''.join(b'\x00' + bytes(r) for r in rows)

    def ck(tag, payload):
        return (struct.pack('>I', len(payload)) + tag + payload +
                struct.pack('>I', zlib.crc32(tag + payload) & 0xFFFFFFFF))

    open(path, 'wb').write(
        b'\x89PNG\r\n\x1a\n' +
        ck(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) +
        ck(b'IDAT', zlib.compress(raw, 9)) + ck(b'IEND', b''))


def main():
    src, dst = sys.argv[1], sys.argv[2]
    cols = 13
    cell = 80
    if '--cols' in sys.argv:
        cols = int(sys.argv[sys.argv.index('--cols') + 1])
    if '--cell' in sys.argv:
        cell = int(sys.argv[sys.argv.index('--cell') + 1])

    f = open(src, 'rb').read()
    if f[:4] != b'AM04':
        raise SystemExit('not AM04')

    def sec(o):
        return struct.unpack_from('<I', f, o)[0]

    D, E, F, G, P = sec(0x20), sec(0x28), sec(0x30), sec(0x38), sec(0x18)
    pal = []
    for i in range(256):
        c = struct.unpack_from('<I', f, P + i * 4)[0]
        pal.append((c & 255, (c >> 8) & 255, (c >> 16) & 255))

    nent = max(0, (E - D) // 0x18)
    cells = []
    for e in range(nent):
        o = D + e * 0x18
        packed = struct.unpack_from('<I', f, o)[0]
        hh, ww = packed >> 16, packed & 0xFFFF
        row_start = struct.unpack_from('<i', f, o + 0x10)[0]
        ew = struct.unpack_from('<i', f, o + 0x14)[0]
        if not (0 < hh < 300) or not (0 < ew < 300) or row_start < 0:
            cells.append(None)
            continue
        img = [[(0, 0, 0)] * ew for _ in range(hh)]
        for y in range(hh):
            ro = G + (row_start + y) * 8
            if ro + 8 > len(f):
                break
            first, runs = struct.unpack_from('<II', f, ro)
            x = 0
            for r in range(runs):
                do = F + (first + r) * 8
                if do + 8 > len(f):
                    break
                off, sk, ct = struct.unpack_from('<IHH', f, do)
                x += sk
                for i in range(ct):
                    p = E + off + i
                    if p >= len(f) or x >= ew:
                        break
                    img[y][x] = pal[f[p]]
                    x += 1
        cells.append((ww, hh, img))

    rows_n = max(1, (len(cells) + cols - 1) // cols)
    canvas = [[(16, 16, 24)] * (cell * cols) for _ in range(cell * rows_n)]
    for idx, c in enumerate(cells):
        if not c:
            continue
        ww, hh, img = c
        cx = (idx % cols) * cell + 2
        cy = (idx // cols) * cell + 2
        for y in range(min(hh, len(img), cell - 4)):
            for x in range(min(ww, len(img[y]), cell - 4)):
                canvas[cy + y][cx + x] = img[y][x]

    out = []
    for row in canvas:
        line = []
        for px in row:
            line += list(px)
        out.append(line)
    png(dst, cell * cols, cell * rows_n, out)
    print('%s  %dx%d  %d entries (%d drawn)' %
          (dst, cell * cols, cell * rows_n, len(cells), sum(1 for c in cells if c)))


if __name__ == '__main__':
    main()
