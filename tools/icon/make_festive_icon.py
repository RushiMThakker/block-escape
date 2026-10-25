"""Generates the festive launcher icon (adaptive layers, flat fallback, splash icon) and an SVG for the 512 px
Play Store icon from ONE description of the artwork, so every size matches.

The sleigh uses the same normalised shapes as the in-game sleigh (ui/festive/SeasonalArt.kt). The classic wood-and-car
icon is kept as *_classic.xml next to the live files so it can be put back in January.

Run:  python make_festive_icon.py <res/drawable dir> <svg output path>
"""
import math
import pathlib
import shutil
import sys

RES = pathlib.Path(sys.argv[1])
SVG_OUT = pathlib.Path(sys.argv[2])

# Where the sleigh sits on the 108 x 108 adaptive canvas (inside the 18..90 safe zone).
L, T, SW, SH = 25.0, 35.0, 62.0, 38.0


def P(nx, ny):
    return (L + nx * SW, T + ny * SH)


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def pt(p):
    return f"{f(p[0])},{f(p[1])}"


def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0 Z"


def rrect(x, y, w, h, r):
    return (f"M{f(x + r)},{f(y)} H{f(x + w - r)} Q{f(x + w)},{f(y)} {f(x + w)},{f(y + r)} V{f(y + h - r)} "
            f"Q{f(x + w)},{f(y + h)} {f(x + w - r)},{f(y + h)} H{f(x + r)} Q{f(x)},{f(y + h)} {f(x)},{f(y + h - r)} "
            f"V{f(y + r)} Q{f(x)},{f(y)} {f(x + r)},{f(y)} Z")


class Shape:
    def __init__(self, d, fill=None, fill_alpha=1.0, stroke=None, stroke_width=0.0, stroke_alpha=1.0, cap="butt", join="miter"):
        self.d, self.fill, self.fill_alpha = d, fill, fill_alpha
        self.stroke, self.stroke_width, self.stroke_alpha = stroke, stroke_width, stroke_alpha
        self.cap, self.join = cap, join

    def xml(self, indent="    "):
        a = [f'android:pathData="{self.d}"']
        if self.fill:
            a.append(f'android:fillColor="{self.fill}"')
            if self.fill_alpha < 1:
                a.append(f'android:fillAlpha="{f(self.fill_alpha)}"')
        if self.stroke:
            a.append(f'android:strokeColor="{self.stroke}"')
            a.append(f'android:strokeWidth="{f(self.stroke_width)}"')
            if self.stroke_alpha < 1:
                a.append(f'android:strokeAlpha="{f(self.stroke_alpha)}"')
            if self.cap != "butt":
                a.append(f'android:strokeLineCap="{self.cap}"')
            if self.join != "miter":
                a.append(f'android:strokeLineJoin="{self.join}"')
        return f"{indent}<path\n" + "".join(f"{indent}    {x}\n" for x in a).rstrip("\n") + " />\n"

    def svg(self):
        a = [f'd="{self.d}"', f'fill="{self.fill or "none"}"']
        if self.fill and self.fill_alpha < 1:
            a.append(f'fill-opacity="{f(self.fill_alpha)}"')
        if self.stroke:
            a.append(f'stroke="{self.stroke}" stroke-width="{f(self.stroke_width)}" stroke-linecap="{self.cap}" stroke-linejoin="{self.join}"')
            if self.stroke_alpha < 1:
                a.append(f'stroke-opacity="{f(self.stroke_alpha)}"')
        return "<path " + " ".join(a) + "/>"


# ------------------------------------------------------------------ background: a night-blue board with a string of lights
def background():
    s = [
        Shape("M0,0h108v108h-108z", fill="#14284D"),
        Shape("M0,0h108v32h-108z", fill="#1D3A6E"),
        Shape("M0,32h108v5h-108z", fill="#070E22", fill_alpha=0.7),
        Shape("M0,71h108v5h-108z", fill="#070E22", fill_alpha=0.7),
        Shape("M0,76h108v32h-108z", fill="#0C1630"),
    ]
    # wire in two swags and warm bulbs hanging from it
    def swag(x0, y0, cx, cy, x1, y1, t):
        x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * cx + t * t * x1
        y = (1 - t) ** 2 * y0 + 2 * (1 - t) * t * cy + t * t * y1
        return x, y
    s.append(Shape("M-2,11 Q27,23 54,11 Q81,23 110,11", stroke="#070E22", stroke_width=1.1, stroke_alpha=0.85, cap="round"))
    colours = ["#FFE2A0", "#F4C95D", "#FF9E8A", "#9CD3FF", "#B7E8B0", "#FFE2A0"]
    n = 0
    for (x0, y0, cx, cy, x1, y1) in ((-2, 11, 27, 23, 54, 11), (54, 11, 81, 23, 110, 11)):
        for t in (0.17, 0.5, 0.83):
            x, y = swag(x0, y0, cx, cy, x1, y1, t)
            c = colours[n % len(colours)]
            s.append(Shape(circle(x, y + 2.6, 4.6), fill=c, fill_alpha=0.22))
            s.append(Shape(circle(x, y + 2.6, 2.4), fill=c))
            n += 1
    # a few snowflakes round the edge
    for (x, y, r, a) in ((13, 46, 1.2, 0.7), (95, 50, 1.0, 0.6), (18, 88, 1.3, 0.7), (72, 93, 1.0, 0.6), (98, 86, 1.2, 0.7),
                         (40, 94, 0.9, 0.5), (9, 64, 0.9, 0.5), (60, 47, 0.8, 0.45)):
        s.append(Shape(circle(x, y, r), fill="#F2F8FF", fill_alpha=a))
    return s


# ------------------------------------------------------------------ foreground: exit glow + the red sleigh
def sleigh_body_d():
    return (f"M{pt(P(0.10, 0.10))} Q{pt(P(0.02, 0.28))} {pt(P(0.11, 0.52))} L{pt(P(0.17, 0.78))} L{pt(P(0.83, 0.78))} "
            f"C{pt(P(0.94, 0.74))} {pt(P(0.99, 0.54))} {pt(P(0.955, 0.30))} L{pt(P(0.89, 0.33))} Z")


def runner_d():
    return (f"M{pt(P(0.05, 0.95))} L{pt(P(0.78, 0.95))} C{pt(P(0.93, 0.95))} {pt(P(1.0, 0.84))} {pt(P(0.985, 0.62))} "
            f"Q{pt(P(0.975, 0.50))} {pt(P(0.93, 0.55))}")


def line_d(a, b):
    return f"M{pt(a)} L{pt(b)}"


def foreground():
    s = [
        # the exit: three nested rounded shapes, from a faint gold halo to a warm-white core, so it reads as a lit
        # doorway on the dark board (the wood-tuned olive-gold of the classic icon looked muddy here)
        Shape("M86,36 L104,36 A18,18 0 0 1 104,72 L86,72 A18,18 0 0 1 86,36 Z", fill="#FFD54F", fill_alpha=0.22),
        Shape("M89,42 L104,42 A12,12 0 0 1 104,66 L89,66 A12,12 0 0 1 89,42 Z", fill="#FFD54F", fill_alpha=0.5),
        Shape("M93,48 L104,48 A6,6 0 0 1 104,60 L93,60 A6,6 0 0 1 93,48 Z", fill="#FFF0C8", fill_alpha=0.95),
        Shape(runner_d(), stroke="#C9962B", stroke_width=3.6, cap="round", join="round"),
        Shape(line_d(P(0.30, 0.76), P(0.30, 0.94)), stroke="#C9962B", stroke_width=2.4, cap="round"),
        Shape(line_d(P(0.66, 0.76), P(0.66, 0.94)), stroke="#C9962B", stroke_width=2.4, cap="round"),
        Shape(sleigh_body_d(), fill="#E53935", stroke="#3D0F0C", stroke_width=2.2, join="round"),
        Shape(rrect(*P(0.24, 0.38), SW * 0.34, SH * 0.24, 1.6), fill="#8E1B1B"),
        Shape(line_d(P(0.10, 0.10), P(0.89, 0.33)), stroke="#F4C95D", stroke_width=1.7, cap="round"),
        Shape(line_d(P(0.15, 0.67), P(0.91, 0.67)), stroke="#F4C95D", stroke_width=1.7, cap="round"),
        Shape(circle(*P(0.955, 0.30), 2.7), fill="#FFF0C8", fill_alpha=0.4),
        Shape(circle(*P(0.955, 0.30), 1.5), fill="#FFF0C8"),
    ]
    return s


def monochrome():
    return [
        Shape(sleigh_body_d(), fill="#FFFFFF"),
        Shape(runner_d(), stroke="#FFFFFF", stroke_width=3.6, cap="round", join="round"),
        Shape(line_d(P(0.30, 0.76), P(0.30, 0.94)), stroke="#FFFFFF", stroke_width=2.4, cap="round"),
        Shape(line_d(P(0.66, 0.76), P(0.66, 0.94)), stroke="#FFFFFF", stroke_width=2.4, cap="round"),
    ]


def vector(shapes, comment, size=108, view=108):
    body = "".join(x.xml() for x in shapes)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<!--\n{comment}\n-->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
            f'    android:viewportWidth="{view}"\n    android:viewportHeight="{view}">\n\n{body}</vector>\n')


NOTE = ("    GENERATED by tools/icon/make_festive_icon.py (festive v0.6 icon). The classic wood-and-car art is kept as\n"
        "    *_classic.xml in this folder; copy those back over the live files to restore it in January.")

# keep the classic art next to the live files (only the first time)
for name in ("ic_launcher_background", "ic_launcher_foreground", "ic_launcher_monochrome", "ic_launcher_flat", "ic_splash_icon"):
    live, backup = RES / f"{name}.xml", RES / f"{name}_classic.xml"
    if live.exists() and not backup.exists():
        shutil.copyfile(live, backup)

(RES / "ic_launcher_background.xml").write_text(vector(background(), "    Adaptive-icon background (festive): night-blue board with a string of lights and snow.\n" + NOTE), encoding="utf-8")
(RES / "ic_launcher_foreground.xml").write_text(vector(foreground(), "    Adaptive-icon foreground (festive): the red sleigh and the gold exit glow.\n" + NOTE), encoding="utf-8")
(RES / "ic_launcher_monochrome.xml").write_text(vector(monochrome(), "    Themed-icon silhouette (festive): the sleigh.\n" + NOTE), encoding="utf-8")
(RES / "ic_launcher_flat.xml").write_text(vector(background() + foreground(), "    Flat fallback for API 24-25 (festive): background and foreground in one.\n" + NOTE), encoding="utf-8")

# splash icon: the same flat art inside the existing 240 dp splash canvas wrapper
inner = "".join(x.xml(indent="            ") for x in background() + foreground())
(RES / "ic_splash_icon.xml").write_text(
    '<?xml version="1.0" encoding="utf-8"?>\n<!--\n    Splash icon (festive): the flat launcher art on the 240 dp splash canvas, confined to the centred 150 dp region.\n'
    + NOTE + '\n-->\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="240dp"\n    android:height="240dp"\n'
    '    android:viewportWidth="240"\n    android:viewportHeight="240">\n    <group\n        android:translateX="27"\n        android:translateY="27">\n'
    '        <group\n            android:pivotX="18"\n            android:pivotY="18"\n            android:scaleX="2.0833"\n            android:scaleY="2.0833">\n'
    + inner + '        </group>\n    </group>\n</vector>\n', encoding="utf-8")

# SVG for the 512 px Play Store icon (full bleed; Play applies its own mask)
svg = ('<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">'
       + "".join(x.svg() for x in background() + foreground()) + "</svg>")
SVG_OUT.write_text(svg, encoding="utf-8")
print("festive icon written; svg", SVG_OUT)
