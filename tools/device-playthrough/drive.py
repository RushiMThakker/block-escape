"""Scripted playthrough of Block Escape on MY emulator only (emulator-5600): swipes along the solver's optimal solutions."""
import re, subprocess, sys, time

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


def moves_count():
    """Read the on-screen 'Moves: N' through the accessibility tree (None if it cannot be read after 3 tries)."""
    for _ in range(3):
        out = adb("shell", "uiautomator", "dump", "/dev/tty", capture=True).decode("utf-8", "replace")
        m = re.search(r'text="Moves: (\d+)"', out)
        if m:
            return int(m.group(1))
        time.sleep(0.6)
    return None


def play(solution_file, pause=0.45, verify=True):
    """Perform one level's optimal moves, checking the move counter after each swipe and retrying a dropped one."""
    global X0, Y0, CELL
    time.sleep(2.5)  # let the banner settle, then measure
    X0, Y0, CELL = board_bounds()
    moves = [l.split() for l in open(solution_file).read().splitlines() if l.strip()]
    base = (moves_count() or 0) if verify else 0
    retries = 0
    for i, (orient, row, col, length, delta) in enumerate(moves):
        row, col, length, delta = int(row), int(col), int(length), int(delta)
        if orient == "H":
            sx = X0 + (col + length / 2) * CELL
            sy = Y0 + (row + 0.5) * CELL
            ex, ey = sx + delta * CELL, sy
        else:
            sx = X0 + (col + 0.5) * CELL
            sy = Y0 + (row + length / 2) * CELL
            ex, ey = sx, sy + delta * CELL
        for attempt in range(4):
            adb("shell", "input", "swipe", str(int(sx)), str(int(sy)), str(int(ex)), str(int(ey)), "300")
            time.sleep(pause)
            if not verify:
                break
            n = moves_count()
            if n is None or n == base + i + 1:
                break
            retries += 1
            time.sleep(0.4)
        else:
            raise RuntimeError(f"move {i + 1} never registered in {solution_file}")
    if retries:
        print(f"  ({retries} swipe(s) needed a retry)", flush=True)
    return len(moves)


if __name__ == "__main__":
    name = sys.argv[1]
    n = play(rf"C:\jtmp\sol\solution-{name}.txt")
    print(f"{name}: {n} moves played")
    time.sleep(1.5)
    if len(sys.argv) > 2:
        shot(sys.argv[2])


def launch(date=None, timeout=45):
    """Force-stop and start the app (optionally with the debug date override); wait until the splash is gone."""
    from PIL import Image
    import io
    adb("shell", "am", "force-stop", "com.rushi.blockescape")
    cmd = ["shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", "com.rushi.blockescape/.MainActivity"]
    if date:
        cmd += ["--es", "debug_date", date]
    adb(*cmd)
    start = time.time()
    while time.time() - start < timeout:
        time.sleep(2)
        im = Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", capture=True))).convert("RGB")
        r, g, b = im.getpixel((540, 600))
        # the splash is a plain warm cream (about 245,235,220) with the icon only in the centre
        if not (abs(r - 245) < 8 and abs(g - 235) < 8 and abs(b - 220) < 10):
            time.sleep(1.5)
            return time.time() - start
    return None
