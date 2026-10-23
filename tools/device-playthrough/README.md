# Scripted on-device playthrough

Plays the game on an emulator by swiping along the solver's optimal solutions, so a RELEASE build
(which is not debuggable and cannot have its progress seeded) can be played end to end.

1. Start YOUR OWN emulator on a dedicated port and pin every adb call to it (other sessions share the adb
   server): `emulator -avd unblock_test -read-only -port 5600 ... ` then `adb -s emulator-5600 ...`.
   `drive.py` has `SERIAL = "emulator-5600"` at the top; change it if you use another port.
2. Write the solutions: `gradlew testDebugUnitTest --tests "*LevelToolsTest.tool runner" -PgenMode=solve
   -PgenOut=C:\jtmp\sol -PgenLevel=all` (also accepts a comma list such as `level_020,advent_24`).
3. Install the release APK, open level 1, then `python run_pack.py 1 25` (first arg = first level of the
   run, which must already be open on screen; later levels are reached with the "Next Level" button).

`drive.py` measures the wood board from a screenshot before each level, so layout changes do not break it.

## Advent / date testing (v0.6)

A debuggable build honours `adb shell am start ... --es debug_date 2026-12-05`; a RELEASE build ignores it
(`Season.resolveToday`, covered by SeasonTest), so test the release build with the device clock instead, on YOUR OWN
emulator only: `adb -s emulator-5600 root`, `settings put global auto_time 0`, `date MMDDhhmmYYYY.ss`.

- `advent_flow.py` plays Advent doors 1, 5, 9, 24 and 2 across the dates Dec 5, 9, 24, 31 and Jan 7, moving the
  clock while the app is in the background (the app must re-read the date on resume).
- `videos2.py` records the sleigh and level-clear previews (all swipes sent as one shell script so the recorder
  does not time out on a slow host).
- Seed progress on a release build by writing `shared_prefs/block_escape_progress.xml` as root (chown + restorecon).
- Resume with the MAIN/LAUNCHER intent, not a plain component start (that creates a second instance).
