"""Re-records the sleigh and level-clear previews with all swipes sent as ONE shell script (my emulator only)."""
import os, subprocess, sys, time
sys.path.insert(0, r"C:\jtmp")
import drive

OUT = r"C:\jtmp\vid"
ADB, S = drive.ADB, drive.SERIAL


def tap(x, y, wait=0.6):
    drive.adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)


def door_xy(n):
    col, row = (n - 1) % 4, (n - 1) // 4
    return (162 + col * 251, 686 + row * 288)


def swipe_script(solution_file, gap=0.55, duration=320):
    """One shell command string performing every swipe of a solution, using the current board geometry."""
    drive.X0, drive.Y0, drive.CELL = drive.board_bounds()
    x0, y0, cell = drive.X0, drive.Y0, drive.CELL
    parts = []
    for line in open(solution_file).read().splitlines():
        if not line.strip():
            continue
        orient, row, col, length, delta = line.split()
        row, col, length, delta = int(row), int(col), int(length), int(delta)
        if orient == "H":
            sx, sy = x0 + (col + length / 2) * cell, y0 + (row + 0.5) * cell
            ex, ey = sx + delta * cell, sy
        else:
            sx, sy = x0 + (col + 0.5) * cell, y0 + (row + length / 2) * cell
            ex, ey = sx, sy + delta * cell
        parts.append(f"input swipe {int(sx)} {int(sy)} {int(ex)} {int(ey)} {duration}")
    return f"; sleep {gap}; ".join(parts)


def record(name, seconds, script, tail_wait):
    remote = f"/sdcard/{name}.mp4"
    subprocess.run([ADB, "-s", S, "shell", "rm", "-f", remote], stdout=subprocess.DEVNULL)
    rec = subprocess.Popen([ADB, "-s", S, "shell", "screenrecord", "--time-limit", str(seconds), "--size", "720x1600",
                            "--bit-rate", "5000000", remote], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1.5)
    subprocess.run([ADB, "-s", S, "shell", script], check=True, stdout=subprocess.DEVNULL)
    time.sleep(tail_wait)
    rec.wait(timeout=seconds + 40)
    time.sleep(1.0)
    subprocess.run([ADB, "-s", S, "pull", remote, os.path.join(OUT, name + ".mp4")], check=True, stdout=subprocess.DEVNULL)
    print("recorded", name, flush=True)


drive.adb("shell", "pm", "clear", "com.rushi.blockescape")
subprocess.run([ADB, "-s", S, "shell", "date", "121209002026.00"], check=True, stdout=subprocess.DEVNULL)
drive.launch()
tap(818, 1657, 1.0)            # dismiss how-to-play
tap(540, 528, 4.0)             # open the calendar
tap(*door_xy(1), wait=5.0)     # door 1 -> its board (wait out the door animation)
script = swipe_script(r"C:\jtmp\sol\solution-advent_01.txt")
record("3-sleigh-drives-out", 40, script, tail_wait=6.0)

tap(540, 1247, 2.5)            # "Calendar" on the solved overlay
tap(154, 270, 3.0)             # "< Levels"
tap(203, 828, 5.0)             # level 1 (festive level select: Advent card above the grid)
script = swipe_script(r"C:\jtmp\sol\solution-level_001.txt")
record("4-level-clear", 25, script, tail_wait=5.0)
print("DONE")
