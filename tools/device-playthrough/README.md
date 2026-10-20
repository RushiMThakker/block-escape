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
