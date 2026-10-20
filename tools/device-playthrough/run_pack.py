import io, sys, time
sys.path.insert(0, r"C:\jtmp")
import drive
from PIL import Image

NEXT = (540, 1195)


def win_visible():
    im = Image.open(io.BytesIO(drive.adb("exec-out", "screencap", "-p", capture=True))).convert("RGB")
    r, g, b = im.getpixel(NEXT)
    # Next Level button is the warm brown pill (about 139,90,43) on the dimmed board.
    return abs(r - 139) < 25 and abs(g - 90) < 25 and abs(b - 43) < 25, im


first, last = int(sys.argv[1]), int(sys.argv[2])
for n in range(first, last + 1):
    name = f"level_{n:03d}"
    if n > first:
        drive.adb("shell", "input", "tap", str(NEXT[0]), str(NEXT[1]))
    moves = drive.play(rf"C:\jtmp\sol\solution-{name}.txt")
    time.sleep(1.5)
    ok, im = win_visible()
    print(f"{name}: {moves} moves, win overlay={'YES' if ok else 'NO'}", flush=True)
    if n in (11, 20) or not ok:
        im.save(rf"C:\jtmp\shots\run_{name}.png")
    if not ok:
        sys.exit(1)
print("DONE", flush=True)
