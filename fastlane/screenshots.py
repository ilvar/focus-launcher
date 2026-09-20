#!/usr/bin/env python3
"""Draws the phone screenshots for the store listing (F-Droid reads them from this folder).

They are drawn, not captured: Focus is text on black, so a drawing shows it exactly, while a real
screenshot shows somebody's apps, calendar and screen time. The content here is invented.

    python3 fastlane/screenshots.py        # needs rsvg-convert (librsvg)
"""
import pathlib, subprocess, tempfile

OUT = pathlib.Path(__file__).parent / "metadata/android/en-US/images/phoneScreenshots"
W, H = 1080, 2340
FG, DIM, FAINT, LINE = "#FFFFFF", "#8E8E8E", "#565656", "#2A2A2A"
FONT = "Helvetica Neue, Helvetica, Arial, sans-serif"


def text(x, y, s, size, fill=FG, anchor="start", weight=400, spacing=0):
    s = s.replace("&", "&amp;").replace("<", "&lt;")
    return (f'<text x="{x}" y="{y}" font-family="{FONT}" font-size="{size}" font-weight="{weight}" '
            f'fill="{fill}" text-anchor="{anchor}" letter-spacing="{spacing}">{s}</text>')


def label(x, y, s):
    return text(x, y, s.upper(), 30, DIM, weight=500, spacing=5)


def page(body):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">'
            f'<rect width="{W}" height="{H}" fill="#000"/>{"".join(body)}</svg>')


def home():
    b = [f'<line x1="540" y1="250" x2="540" y2="560" stroke="{FAINT}" stroke-width="3"/>',
         text(486, 410, "10:24", 168, weight=300, anchor="end"),
         text(486, 478, "Tue, 22 Sept", 42, DIM, anchor="end"),
         text(486, 532, "72%", 36, FAINT, anchor="end"),
         text(594, 330, "Today 14:00", 36, DIM), text(594, 384, "Design review", 45),
         text(594, 462, "Tomorrow 09:30", 36, DIM), text(594, 516, "Standup", 45),
         text(90, 700, "Screen Time", 45, DIM), text(90, 790, "2h 41m", 72), text(90, 846, "11% of today", 39, DIM)]
    for i, app in enumerate(["Mail", "Messages", "Maps", "Notes", "Music"]):
        b.append(text(90, 1330 + i * 150, app, 78))
    b += [text(90, 2230, "Phone", 45, DIM), text(990, 2230, "Camera", 45, DIM, anchor="end")]
    return page(b)


def drawer():
    b = [f'<rect x="60" y="150" width="960" height="120" fill="none" stroke="{LINE}" stroke-width="3"/>',
         text(100, 228, "Search apps", 48, FAINT),
         text(1000, 340, "Sort: A–Z", 39, DIM, anchor="end"),
         label(90, 430, "Installed in the last 24 hours"), text(90, 520, "Podcasts", 60),
         label(90, 640, "All apps")]
    rows = [("Calculator", None), ("Calendar", None), ("Camera", None), ("Chat", None), ("Clock", None),
            ("Files", None), ("Gallery", None), ("Maps", None), ("Messages", None), ("Music", None),
            ("Notes", None), ("Photogram", "12m / 30m"), ("Podcasts", None), ("Videos", "41m / 1h")]
    for i, (name, timer) in enumerate(rows):
        y = 740 + i * 108
        b.append(text(90, y, name, 60))
        if timer:
            b.append(text(930, y, timer, 39, DIM, anchor="end"))
    for i, ch in enumerate("ACFGMNPV"):
        b.append(text(1030, 760 + i * 150, ch, 30, FAINT, anchor="middle"))
    return page(b)


def wall():
    b = [label(90, 560, "Time's up"),
         text(90, 700, "Photogram", 108, weight=300),
         text(90, 800, "31m today. Your limit is 30m.", 45, DIM),
         text(90, 880, "You went past this limit 3 times this week.", 39, FAINT),
         f'<rect x="90" y="1080" width="900" height="150" fill="{FG}"/>',
         text(540, 1176, "Close Photogram", 51, "#000", anchor="middle", weight=500)]
    for i, m in enumerate(["1 min", "5 min", "15 min"]):
        x = 90 + i * 310
        b.append(f'<rect x="{x}" y="1390" width="280" height="130" fill="none" stroke="{FAINT}" stroke-width="3"/>')
        b.append(text(x + 140, 1472, m, 42, anchor="middle"))
    b += [label(90, 1340, "Continue for"), text(90, 1640, "Ignore the limit for today", 45, DIM)]
    return page(b)


def review():
    b = [text(90, 250, "Screen time", 66, weight=500),
         f'<rect x="300" y="310" width="190" height="84" fill="{FG}"/>',
         text(190, 368, "Today", 45, DIM, anchor="middle"), text(395, 368, "Week", 45, "#000", anchor="middle", weight=500),
         text(90, 640, "19h 12m", 162, weight=300), text(90, 720, "2h 44m a day  ·  12% less than last week", 42, DIM),
         label(90, 880, "Day by day")]
    for i, v in enumerate([0.55, 0.8, 0.62, 1.0, 0.7, 0.35, 0.2]):
        x, h = 90 + i * 132, int(260 * v)
        b.append(f'<rect x="{x}" y="{1200 - h}" width="96" height="{h}" fill="{FG if v == 1.0 else DIM}"/>')
        b.append(text(x + 48, 1260, "MTWTFSS"[i], 33, FAINT, anchor="middle"))
    b.append(label(90, 1420, "Where it went"))
    for i, (name, t, note) in enumerate([("Videos", "6h 02m  ·  31%", "mostly evening  ·  7 of 7 days"),
                                         ("Photogram", "4h 40m  ·  24%", "mostly night  ·  past its limit 3×"),
                                         ("Chat", "2h 15m  ·  12%", "mostly afternoon  ·  7 of 7 days")]):
        y = 1530 + i * 190
        b += [text(90, y, name, 54), text(990, y, t, 42, DIM, anchor="end"), text(90, y + 60, note, 36, FAINT)]
    b += [label(90, 2120, "Reflect"), text(90, 2220, "Was this how you wanted to spend your week?", 45, weight=300)]
    return page(b)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for n, svg in enumerate([home(), drawer(), wall(), review()], start=1):
        with tempfile.NamedTemporaryFile("w", suffix=".svg", delete=False) as f:
            f.write(svg)
        subprocess.run(["rsvg-convert", "-w", str(W), "-h", str(H), f.name, "-o", str(OUT / f"{n}.png")], check=True)
        print(OUT / f"{n}.png")
