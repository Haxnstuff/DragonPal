#!/usr/bin/env python3
"""Strip the MethodParameters attribute from .class files.

javac 21 emits MethodParameters with name_index=0 (null names) for synthetic
inner-class constructors, which crashes R8/d8 with
  "NullPointerException: Cannot invoke String.length() because <parameter1> is null".
The attribute is reflection-only metadata; removing it is safe.
"""
import struct, sys, os

def parse_cp(data):
    count = struct.unpack_from(">H", data, 8)[0]
    idx = 10
    entries = [None] * count  # 1-indexed
    i = 1
    while i < count:
        start = idx
        tag = data[idx]
        idx += 1
        if tag == 1:                 # Utf8
            ln = struct.unpack_from(">H", data, idx)[0]
            idx += 2 + ln
        elif tag in (3, 4):          # Integer, Float
            idx += 4
        elif tag in (5, 6):          # Long, Double -> two slots
            idx += 8
        elif tag in (7, 8, 16, 19, 20):  # Class, String, MethodType, Module, Package
            idx += 2
        elif tag in (9, 10, 11, 12, 17, 18):  # refs, NameAndType, Dynamic, InvokeDynamic
            idx += 4
        elif tag == 15:              # MethodHandle
            idx += 3
        else:
            raise ValueError("unknown cp tag %d at %d" % (tag, start))
        entries[i] = (tag, start, idx)
        i += 2 if tag in (5, 6) else 1
    return entries, idx

def utf8_str(data, start):
    ln = struct.unpack_from(">H", data, start + 1)[0]
    return data[start + 3: start + 3 + ln].decode("utf-8", "replace")

def find_utf8(data, entries, name):
    for i in range(1, len(entries)):
        e = entries[i]
        if e and e[0] == 1 and utf8_str(data, e[1]) == name:
            return i
    return 0

def strip_file(path):
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"\xca\xfe\xba\xbe":
        return False
    entries, cp_end = parse_cp(data)
    mp_idx = find_utf8(data, entries, "MethodParameters")
    if mp_idx == 0:
        return False

    out = bytearray(data[:cp_end + 6])   # header through this/super
    idx = cp_end + 6

    iface_count = struct.unpack_from(">H", data, idx)[0]
    out += data[idx: idx + 2 + iface_count * 2]
    idx += 2 + iface_count * 2

    fcount = struct.unpack_from(">H", data, idx)[0]
    out += data[idx: idx + 2]; idx += 2
    for _ in range(fcount):
        out += data[idx: idx + 6]; idx += 6
        ac = struct.unpack_from(">H", data, idx)[0]
        out += data[idx: idx + 2]; idx += 2
        for _ in range(ac):
            alen = struct.unpack_from(">I", data, idx + 2)[0]
            out += data[idx: idx + 6 + alen]; idx += 6 + alen

    mcount = struct.unpack_from(">H", data, idx)[0]
    out += data[idx: idx + 2]; idx += 2
    for _ in range(mcount):
        out += data[idx: idx + 6]; idx += 6
        ac = struct.unpack_from(">H", data, idx)[0]
        idx += 2
        kept = []
        for _ in range(ac):
            name_idx = struct.unpack_from(">H", data, idx)[0]
            alen = struct.unpack_from(">I", data, idx + 2)[0]
            if name_idx == mp_idx:
                idx += 6 + alen          # drop MethodParameters
            else:
                kept.append((idx, idx + 6 + alen))
                idx += 6 + alen
        out += struct.pack(">H", len(kept))
        for (s, e) in kept:
            out += data[s:e]

    ccount = struct.unpack_from(">H", data, idx)[0]
    out += data[idx: idx + 2]; idx += 2
    for _ in range(ccount):
        alen = struct.unpack_from(">I", data, idx + 2)[0]
        out += data[idx: idx + 6 + alen]; idx += 6 + alen

    if bytes(out) != data:
        with open(path, "wb") as f:
            f.write(bytes(out))
        return True
    return False

if __name__ == "__main__":
    total = 0
    for root in sys.argv[1:]:
        for dirpath, _, files in os.walk(root):
            for fn in files:
                if fn.endswith(".class"):
                    if strip_file(os.path.join(dirpath, fn)):
                        total += 1
    print("stripped %d file(s)" % total)
