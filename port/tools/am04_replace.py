#!/usr/bin/env python3
"""am04_replace.py - replace one sprite frame inside an AM04 file, in place.

Keeps the file structurally identical: same sections, same offsets, same run
descriptors, same size. Only the index bytes belonging to the chosen entry are
rewritten, so the engine loads it exactly as it loaded the original.

Usage:
    python3 am04_replace.py FILE --entry 9 --pattern stripes --out NEW.bin
    python3 am04_replace.py FILE --entry 9 --palette 16,4,17 --out NEW.bin
"""
import argparse
import struct


def sections(data):
    get = lambda o: struct.unpack_from('<I', data, o)[0]
    return {'pal': get(0x18), 'D': get(0x20), 'E': get(0x28),
            'F': get(0x30), 'G': get(0x38)}


def entry_geometry(data, sec, entry):
    o = sec['D'] + entry * 0x18
    packed = struct.unpack_from('<I', data, o)[0]
    h, w = packed >> 16, packed & 0xFFFF
    row_start = struct.unpack_from('<i', data, o + 0x10)[0]
    entry_w = struct.unpack_from('<i', data, o + 0x14)[0]
    return (entry_w or w), h, row_start


def pixels(data, sec, entry):
    """Yield (file_offset, x, y) for every index byte this entry draws."""
    w, h, row_start = entry_geometry(data, sec, entry)
    out = []
    for y in range(h):
        ro = sec['G'] + (row_start + y) * 8
        if ro + 8 > len(data):
            break
        first, runs = struct.unpack_from('<II', data, ro)
        if runs > 4096:
            continue
        x = 0
        for r in range(runs):
            do = sec['F'] + (first + r) * 8
            if do + 8 > len(data):
                break
            off, skip, count = struct.unpack_from('<IHH', data, do)
            x += skip
            for i in range(count):
                p = sec['E'] + off + i
                if p >= len(data):
                    break
                out.append((p, x, y))
                x += 1
    return out, w, h


def vivid_indices(data, sec, count):
    """Pick the most saturated palette entries, leaving index 0 alone."""
    pal = [struct.unpack_from('<I', data, sec['pal'] + i * 4)[0] for i in range(256)]
    def sat(c):
        r, g, b = c & 255, (c >> 8) & 255, (c >> 16) & 255
        return max(r, g, b) - min(r, g, b)
    ranked = sorted(range(1, 256), key=lambda i: -sat(pal[i]))
    return ranked[:count]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('file')
    ap.add_argument('--entry', type=int, required=True)
    ap.add_argument('--pattern', default='stripes')
    ap.add_argument('--palette', default='')
    ap.add_argument('--out', required=True)
    a = ap.parse_args()

    data = bytearray(open(a.file, 'rb').read())
    if data[:4] != b'AM04':
        raise SystemExit('not an AM04 file')

    sec = sections(data)
    pts, w, h = pixels(data, sec, a.entry)
    if not pts:
        raise SystemExit('entry %d draws nothing' % a.entry)

    if a.palette:
        cols = [int(v) for v in a.palette.split(',')]
    else:
        cols = vivid_indices(data, sec, 4)

    from collections import Counter
    dominant = Counter(data[off] for (off, x, y) in pts).most_common(1)[0][0]
    mask = (a.pattern != 'fill')

    changed = 0
    for (off, x, y) in pts:
        if mask and data[off] == dominant:
            continue
        if a.pattern == 'stripes':
            idx = cols[((x + y) // 3) % len(cols)]
        elif a.pattern == 'checker':
            idx = cols[((x // 4) + (y // 4)) % len(cols)]
        else:
            idx = cols[0]
        if data[off] != idx:
            changed += 1
        data[off] = idx

    open(a.out, 'wb').write(data)
    print('%s  entry %d (%dx%d, %d pixels, %d changed) indices=%s' %
          (a.out, a.entry, w, h, len(pts), changed, cols))


if __name__ == '__main__':
    main()
