"""End-to-end Advent flow on the signed RELEASE build, driven by the device clock (my own emulator only)."""
import io, subprocess, sys, time
sys.path.insert(0, r"C:\jtmp")
import drive
from PIL import Image

SHOTS = r"C:\jtmp\shots\adv_"
ADVENT_CARD = (540, 528)
CAL_BUTTON = (540, 1247)   # "Calendar" on the solved overlay of an Advent board


def door_xy(n):
    col, row = (n - 1) % 4, (n - 1) // 4
    return (162 + col * 251, 686 + row * 288)


def set_date(mmddhhmmyyyy):
    subprocess.run([drive.ADB, "-s", drive.SERIAL, "shell", "date", mmddhhmmyyyy], check=True, stdout=subprocess.DEVNULL)


def tap(xy, wait=1.0):
    drive.adb("shell", "input", "tap", str(xy[0]), str(xy[1]))
    time.sleep(wait)


def background_then_resume(date):
    drive.adb("shell", "input", "keyevent", "3")          # HOME: the app goes to the background
    time.sleep(1.5)
    set_date(date)                                        # the clock rolls over while it is away
    time.sleep(1.0)
    # Same intent the launcher uses, so the existing task is brought back instead of a second instance.
    drive.adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", "com.rushi.blockescape/.MainActivity")
    time.sleep(3.0)


def overlay_visible():
    im = Image.open(io.BytesIO(drive.adb("exec-out", "screencap", "-p", capture=True))).convert("RGB")
    r, g, b = im.getpixel(CAL_BUTTON)
    # festive buttons are the frost-blue primary (about 156,195,238)
    return abs(r - 156) < 30 and abs(g - 195) < 30 and abs(b - 238) < 30


def play_door(n):
    tap(door_xy(n), wait=3.0)
    moves = drive.play(rf"C:\jtmp\sol\solution-advent_{n:02d}.txt")
    time.sleep(1.8)
    ok = overlay_visible()
    drive.shot(f"{SHOTS}win_{n:02d}.png")
    print(f"door {n:2d}: {moves} moves, solved overlay={'YES' if ok else 'NO'}", flush=True)
    if not ok:
        sys.exit(1)
    tap(CAL_BUTTON, wait=1.5)


# ---- Dec 5: five doors open
drive.adb("shell", "pm", "clear", "com.rushi.blockescape")
set_date("120509002026.00")
drive.launch()
tap((818, 1657))                      # dismiss how-to-play
tap(ADVENT_CARD, wait=3.0)
drive.shot(f"{SHOTS}a_dec05_start.png")
play_door(1)                          # catch-up: an earlier day
play_door(5)                          # today's door
drive.shot(f"{SHOTS}b_dec05_after.png")
tap(door_xy(9), wait=1.0)             # locked: must stay on the calendar
drive.shot(f"{SHOTS}c_locked_tap.png")

# ---- Dec 9 while the app was in the background
background_then_resume("120909002026.00")
drive.shot(f"{SHOTS}d_dec09.png")
play_door(9)

# ---- Dec 24: the finale is today's door
background_then_resume("122409002026.00")
drive.shot(f"{SHOTS}e_dec24.png")
play_door(24)
drive.shot(f"{SHOTS}f_dec24_after.png")

# ---- Dec 31: every door open, catch-up on an old one
background_then_resume("123109002026.00")
drive.shot(f"{SHOTS}g_dec31.png")
play_door(2)

# ---- Jan 7 while sitting on the calendar: it must fall back to level select with no Advent card
background_then_resume("010709002027.00")
drive.shot(f"{SHOTS}h_jan07.png")
print("DONE")
