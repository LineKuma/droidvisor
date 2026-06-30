#!/usr/bin/env python3
"""Watch sdkmanager temp dir and copy fully-written .zip files out before deletion."""
import os, shutil, sys, time, threading

src_dir = "/opt/android-sdk/.temp"
dst_dir = "/tmp/intercepted"
os.makedirs(dst_dir, exist_ok=True)
copied = set()

def watch():
    stable_counts = {}
    last_sizes = {}
    while True:
        for root, dirs, files in os.walk(src_dir):
            for f in files:
                if not f.endswith(".zip"):
                    continue
                src = os.path.join(root, f)
                if src in copied:
                    continue
                try:
                    sz = os.path.getsize(src)
                except OSError:
                    continue
                if sz < 10_000_000:
                    stable_counts[src] = 0
                    last_sizes[src] = sz
                    continue
                prev = last_sizes.get(src)
                if prev is not None and prev == sz:
                    stable_counts[src] = stable_counts.get(src, 0) + 1
                else:
                    stable_counts[src] = 0
                last_sizes[src] = sz
                if stable_counts.get(src, 0) >= 2:
                    dst = os.path.join(dst_dir, f)
                    try:
                        shutil.copy2(src, dst)
                        copied.add(src)
                        print(f"copied {f} ({sz} bytes)", flush=True)
                    except Exception as e:
                        print(f"copy failed: {e}", flush=True)
        time.sleep(0.2)

t = threading.Thread(target=watch, daemon=True)
t.start()
try:
    sys.stdin.read()
except Exception:
    pass
