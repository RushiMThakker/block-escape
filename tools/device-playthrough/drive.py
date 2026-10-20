"""Scripted playthrough of Block Escape on MY emulator only (emulator-5600): swipes along the solver's optimal solutions."""
import subprocess, sys, time

SERIAL = "emulator-5600"
ADB = r"D:\Android\Sdk\platform-tools\adb.exe"
X0, Y0, CELL = 96, 988, 148


def adb(*args, capture=False):
    cmd = [ADB, "-s", SERIAL, *args]
    if capture:
        return subprocess.run(cmd, capture_output=True).stdout
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)


def shot(path):
    with open(path, "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", capture=True))


def board_bounds():
    """Locate the wood board in a fresh screenshot -> (x0, y0, cell)."""
    from PIL import Image
    import io
    im = Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", capture=True))).convert("RGB")
    w, h = im.size
    xs, ys = [], []
    for y in range(800, 2000, 4):
        for x in range(0, w, 4):
            r, g, b = im.getpixel((x, y))
            if 90 <= r <= 170 and 60 <= g <= 115 and 30 <= b <= 75:
                xs.append(x)
                ys.append(y)
    return min(xs), min(ys), (max(xs) - min(xs) + 4) / 6


def play(solution_file, pause=0.45):
    """Perform one level's optimal moves; returns the number of swipes."""
    global X0, Y0, CELL
    time.sleep(2.5)  # let the banner settle, then measure
    X0, Y0, CELL = board_bounds()
    moves = [l.split() for l in open(solution_file).read().splitlines() if l.strip()]
    for orient, row, col, length, delta in moves:
        row, col, length, delta = int(row), int(col), int(length), int(delta)
        if orient == "H":
            sx = X0 + (col + length / 2) * CELL
            sy = Y0 + (row + 0.5) * CELL
            ex, ey = sx + delta * CELL, sy
        else:
            sx = X0 + (col + 0.5) * CELL
            sy = Y0 + (row + length / 2) * CELL
            ex, ey = sx, sy + delta * CELL
        adb("shell", "input", "swipe", str(int(sx)), str(int(sy)), str(int(ex)), str(int(ey)), "220")
        time.sleep(pause)
    return len(moves)


if __name__ == "__main__":
    name = sys.argv[1]
    n = play(rf"C:\jtmp\sol\solution-{name}.txt")
    print(f"{name}: {n} moves played")
    time.sleep(1.5)
    if len(sys.argv) > 2:
        shot(sys.argv[2])
