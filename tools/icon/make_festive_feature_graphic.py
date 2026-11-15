"""Builds the festive 1024 x 500 Play Store feature graphic as an SVG, reusing the exact festive icon artwork
(sleigh, lights, snow) from make_festive_icon.py, so the store icon and the feature graphic match.

The classic graphic (store-assets/feature-graphic.jpg) is left untouched for January.

Run:  python make_festive_feature_graphic.py <svg output path>
Then render it to a 1024 x 500 JPEG (see render step in store-assets notes): headless Chrome screenshot + Pillow.
"""
import pathlib
import sys

OUT = pathlib.Path(sys.argv[1])

# Load only the artwork functions of the icon script (everything before its file-writing side effects).
src = (pathlib.Path(__file__).parent / "make_festive_icon.py").read_text(encoding="utf-8")
src = src[: src.index("\nNOTE = ")]
ns = {"__file__": "make_festive_icon.py"}
saved = sys.argv
sys.argv = ["x", ".", "unused.svg"]
exec(compile(src, "make_festive_icon.py", "exec"), ns)
sys.argv = saved

icon_svg = "".join(s.svg() for s in ns["background"]() + ns["foreground"]())

W, H = 1024, 500
TILE, TX, TY = 300, 70, 100  # same tile size and place as the classic graphic

# Lights across the top: one wire with gentle swags, bulbs hanging from it.
bulbs = []
colours = ["#FFE2A0", "#F4C95D", "#FF9E8A", "#9CD3FF", "#B7E8B0"]
wire = "M-20,34 Q107,70 234,34 Q361,70 488,34 Q615,70 742,34 Q869,70 996,34 Q1060,52 1044,40"
k = 0
for seg in range(8):
    x0 = -20 + seg * 127
    for t in (0.25, 0.5, 0.75):
        x = (1 - t) ** 2 * x0 + 2 * (1 - t) * t * (x0 + 63.5) + t * t * (x0 + 127)
        y = (1 - t) ** 2 * 34 + 2 * (1 - t) * t * 70 + t * t * 34
        if 0 < x < W:
            c = colours[k % len(colours)]
            bulbs.append(f'<circle cx="{x:.1f}" cy="{y + 9:.1f}" r="15" fill="{c}" fill-opacity="0.2"/>'
                         f'<circle cx="{x:.1f}" cy="{y + 9:.1f}" r="7.5" fill="{c}"/>')
            k += 1

# Snow: a fixed scatter (deterministic, so re-runs give the same image).
flakes = [(140, 440, 3.2), (420, 470, 2.4), (520, 120, 2.0), (610, 430, 3.0), (720, 150, 2.2), (860, 455, 2.8),
          (960, 140, 2.4), (995, 380, 2.0), (395, 150, 2.6), (30, 260, 2.2), (455, 395, 1.8), (785, 415, 2.0),
          (905, 110, 1.8), (575, 470, 1.6), (680, 105, 1.6), (40, 470, 2.6)]
snow = "".join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="#F2F8FF" fill-opacity="0.65"/>' for x, y, r in flakes)

svg = f"""<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">
<defs>
  <linearGradient id="sky" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#1D3A6E"/><stop offset="1" stop-color="#0C1630"/>
  </linearGradient>
  <radialGradient id="glow" cx="0.3" cy="0.55" r="0.6">
    <stop offset="0" stop-color="#2B4F8F" stop-opacity="0.55"/><stop offset="1" stop-color="#2B4F8F" stop-opacity="0"/>
  </radialGradient>
  <clipPath id="tile"><rect x="{TX}" y="{TY}" width="{TILE}" height="{TILE}" rx="44"/></clipPath>
  <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
    <feDropShadow dx="0" dy="10" stdDeviation="14" flood-color="#000" flood-opacity="0.45"/>
  </filter>
</defs>
<rect width="{W}" height="{H}" fill="url(#sky)"/>
<rect width="{W}" height="{H}" fill="url(#glow)"/>
{snow}
<path d="{wire}" fill="none" stroke="#070E22" stroke-width="3" stroke-opacity="0.9" stroke-linecap="round"/>
{''.join(bulbs)}
<g filter="url(#shadow)"><rect x="{TX}" y="{TY}" width="{TILE}" height="{TILE}" rx="44" fill="#14284D"/></g>
<g clip-path="url(#tile)"><g transform="translate({TX},{TY}) scale({TILE / 108})">{icon_svg}</g></g>
<text x="410" y="248" font-family="Georgia, 'Times New Roman', serif" font-weight="700" font-size="64"
      fill="#FDF0DC">BLOCK ESCAPE</text>
<text x="414" y="300" font-family="Georgia, 'Times New Roman', serif" font-size="30" fill="#E8DCC6">Advent calendar: a new puzzle every day</text>
</svg>
"""
OUT.write_text(svg, encoding="utf-8")
print("wrote", OUT)
