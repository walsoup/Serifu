#!/usr/bin/env python3
"""Stick-figure sample pages: three panels, one joke, three languages.

Plain stick figures, flat fills, hard aliased edges like an MS Paint canvas.
The plant is plastic. The man waters it, misses, then holds a parasol over it
indoors. Bubbles keep a wide margin around their text: the classifier reads a
tight outline as an SFX ring and skips the translation.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FONT_PATH = "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"
OUT = Path(__file__).resolve().parents[1] / "app/src/main/assets/sample"

INK = (22, 20, 15)
PAPER = (243, 238, 227)
PANEL = (255, 252, 246)
WHITE = (255, 254, 250)
SKIN = (245, 210, 176)
UMBRELLA = (200, 50, 43)
POT = (214, 164, 86)
POT_RIM = (184, 128, 58)
LEAF = (62, 140, 72)
LEAF_DARK = (42, 110, 56)
PETAL = (120, 186, 110)
WATER = (90, 164, 206)
LAMP = (246, 224, 120)

W, H = 1080, 1680
MARGIN = 36
CAPTION_H = 78
GAP = 20
PANEL_TOP = MARGIN + CAPTION_H + 16
PANEL_W = W - MARGIN * 2
PANEL_H = (H - PANEL_TOP - MARGIN - GAP * 2) // 3
FLOOR_FROM_BOTTOM = 46


def font(size: int, index: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(FONT_PATH, size, index=index)


def rot(point, origin, degrees):
    angle = math.radians(degrees)
    x = point[0] - origin[0]
    y = point[1] - origin[1]
    c, s = math.cos(angle), math.sin(angle)
    return (origin[0] + x * c - y * s, origin[1] + x * s + y * c)


def chain(d, points, width=9):
    d.line(points, fill=INK, width=width, joint="curve")


def face(d, cx, cy, mood):
    d.ellipse([cx - 13, cy - 10, cx - 7, cy - 4], fill=INK)
    d.ellipse([cx + 7, cy - 10, cx + 13, cy - 4], fill=INK)
    if mood == "smile":
        d.arc([cx - 14, cy - 2, cx + 14, cy + 16], 20, 160, fill=INK, width=4)
    elif mood == "flat":
        d.line([(cx - 12, cy + 8), (cx + 12, cy + 8)], fill=INK, width=4)
    else:
        d.arc([cx - 14, cy + 8, cx + 14, cy + 24], 200, 340, fill=INK, width=4)


def person(d, x, floor, mood, arms):
    """Stick man standing on `floor`. `arms` maps 'l'/'r' to (elbow, hand)."""
    head = (x, floor - 240)
    r = 40
    neck = (x, floor - 200)
    hip = (x, floor - 104)
    shoulder = (x, floor - 172)
    chain(d, [neck, hip], 9)
    for side in (-1, 1):
        knee = (x + side * 26, floor - 50)
        foot = (x + side * 52, floor - 2)
        chain(d, [hip, knee, foot], 9)
    for key in ("l", "r"):
        elbow, hand = arms[key]
        chain(d, [shoulder, elbow, hand], 9)
        d.ellipse([hand[0] - 10, hand[1] - 10, hand[0] + 10, hand[1] + 10], fill=SKIN, outline=INK, width=4)
    d.ellipse([head[0] - r, head[1] - r, head[0] + r, head[1] + r], fill=SKIN, outline=INK, width=6)
    face(d, head[0], head[1] + 2, mood)
    return head


def plant(d, x, floor, mood):
    """Potted plant with a flower head. Returns the flower centre."""
    center = (x, floor - 214)
    d.line([(x, floor - 96), center], fill=LEAF_DARK, width=10)
    d.polygon([(x - 2, floor - 150), (x - 64, floor - 186), (x - 56, floor - 136)], fill=LEAF, outline=INK)
    d.polygon([(x + 2, floor - 128), (x + 64, floor - 166), (x + 56, floor - 116)], fill=LEAF, outline=INK)
    for k in range(5):
        a = math.radians(-90 + k * 72)
        px = center[0] + 30 * math.cos(a)
        py = center[1] + 30 * math.sin(a)
        d.ellipse([px - 24, py - 24, px + 24, py + 24], fill=PETAL, outline=INK, width=5)
        # Plastic glint: the reason the plant is not alive.
        d.line([(px - 12, py - 10), (px - 4, py - 17)], fill=WHITE, width=5)
    d.ellipse([center[0] - 28, center[1] - 28, center[0] + 28, center[1] + 28], fill=LAMP, outline=INK, width=5)
    face(d, center[0], center[1] + 2, mood)
    d.polygon([(x - 62, floor - 84), (x + 62, floor - 84), (x + 46, floor - 4), (x - 46, floor - 4)], fill=POT, outline=INK)
    d.rounded_rectangle([x - 72, floor - 104, x + 72, floor - 78], radius=8, fill=POT_RIM, outline=INK, width=5)
    return center


def watering_can(d, hand, tilt):
    """Can held at `hand`, rotated by `tilt`. Returns the spout tip."""

    def place(points):
        return [rot((hand[0] + px, hand[1] + py), hand, tilt) for px, py in points]

    body = [(-40, -20), (30, -20), (30, 26), (-40, 26)]
    spout = [(28, -4), (78, -34), (88, -24), (36, 14)]
    handle = [(-40, -20), (-40, -46), (-10, -46), (30, -20)]
    d.polygon(place(body), fill=WATER, outline=INK)
    d.polygon(place(spout), fill=WATER, outline=INK)
    chain(d, place(handle), 7)
    return place([(88, -24)])[0]


def canopy(d, left, right, top, base, hand):
    """Sun parasol: dome, white stripes, pole down to the hand."""
    cx = (left + right) / 2
    half = (right - left) / 2
    d.pieslice([left, top, right, top + 2 * (base - top)], 180, 360, fill=UMBRELLA, outline=INK, width=5)
    for frac in (-0.5, 0.5):
        sx = cx + frac * half
        dome_y = base - (base - top) * math.sqrt(max(0.0, 1 - (2 * frac * 0.5) ** 2))
        d.line([(sx, dome_y + 4), (sx, base - 2)], fill=WHITE, width=6)
    d.line([(left, base), (right, base)], fill=INK, width=5)
    d.line([(cx, base), hand], fill=INK, width=7)


def room(d, panel, lamp_x, floor):
    x0, y0, x1, _ = panel
    d.rectangle(panel, fill=PANEL, outline=INK, width=6)
    d.line([(x0 + 18, floor), (x1 - 18, floor)], fill=INK, width=6)
    d.line([(lamp_x, y0 + 8), (lamp_x, y0 + 44)], fill=INK, width=4)
    d.polygon(
        [(lamp_x - 36, y0 + 44), (lamp_x + 36, y0 + 44), (lamp_x + 22, y0 + 74), (lamp_x - 22, y0 + 74)],
        fill=LAMP,
        outline=INK,
    )


def puddle(d, x, floor):
    d.ellipse([x - 100, floor + 8, x + 100, floor + 30], fill=WATER, outline=INK, width=4)


def drops(d, start, end, n=6):
    for i in range(n):
        t = (i + 0.5) / n
        x = start[0] + (end[0] - start[0]) * t
        y = start[1] + (end[1] - start[1]) * t * t
        r = 8
        d.ellipse([x - r, y - r, x + r, y + r], fill=WATER, outline=INK, width=3)


def bubble(d, text, body_font, center_x, top, toward, panel):
    tw = body_font.getlength(text)
    bbox = body_font.getbbox(text)
    th = bbox[3] - bbox[1]
    w = max(tw + 112, 200)
    h = th + 56
    left = max(panel[0] + 20, min(center_x - w / 2, panel[2] - w - 20))
    box = [left, top, left + w, top + h]
    base_x = max(box[0] + 48, min(toward[0], box[2] - 48))
    tail = [(base_x - 20, box[3] - 4), toward, (base_x + 20, box[3] - 4)]
    d.polygon(tail, fill=WHITE)
    d.line([tail[0], toward, tail[2]], fill=INK, width=5)
    d.rounded_rectangle(box, radius=h / 2, fill=WHITE, outline=INK, width=5)
    d.text(((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), text, font=body_font, fill=INK, anchor="mm")


def sfx(base, text, face_font, at, angle):
    layer = Image.new("RGBA", (520, 180), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for dx, dy in ((-3, 0), (3, 0), (0, -3), (0, 3)):
        draw.text((40 + dx, 40 + dy), text, font=face_font, fill=(*WHITE, 255))
    draw.text((40, 40), text, font=face_font, fill=(*INK, 255))
    spun = layer.rotate(angle, expand=True, resample=Image.Resampling.BICUBIC)
    base.alpha_composite(spun, (int(at[0] - spun.width / 2), int(at[1] - spun.height / 2)))


def page(copy, index, out_name):
    image = Image.new("RGB", (W, H), PAPER)
    draw = ImageDraw.Draw(image)
    title_font = font(40, index)
    body = font(40, index)
    sfx_font = font(64, index)
    draw.rounded_rectangle([MARGIN, MARGIN, W - MARGIN, MARGIN + CAPTION_H], radius=8, fill=WHITE, outline=INK, width=5)
    draw.text((W / 2, MARGIN + CAPTION_H / 2), copy["caption"], font=title_font, fill=INK, anchor="mm")

    panels = []
    y = PANEL_TOP
    for _ in range(3):
        panels.append((MARGIN, y, MARGIN + PANEL_W, y + PANEL_H))
        y += PANEL_H + GAP

    def X(panel, rel):
        return panel[0] + rel

    def Y(panel, rel):
        return panel[1] + rel

    # Panel 1: he asks, she answers.
    p1 = panels[0]
    fl = p1[3] - FLOOR_FROM_BOTTOM
    room(draw, p1, lamp_x=X(p1, 504), floor=fl)
    plant(draw, X(p1, 800), fl, "smile")
    x = X(p1, 290)
    person(draw, x, fl, "smile", {
        "l": ((x - 44, fl - 120), (x - 56, fl - 64)),
        "r": ((x + 56, fl - 150), (x + 112, fl - 186)),
    })
    watering_can(draw, (x + 112, fl - 186), tilt=-30)
    bubble(draw, copy["offer"], body, X(p1, 250), Y(p1, 18), (x - 22, fl - 266), p1)
    bubble(draw, copy["plastic"], body, X(p1, 800), Y(p1, 18), (X(p1, 800), fl - 262), p1)

    # Panel 2: he pours, it all lands on the floor.
    p2 = panels[1]
    fl = p2[3] - FLOOR_FROM_BOTTOM
    room(draw, p2, lamp_x=X(p2, 504), floor=fl)
    puddle(draw, X(p2, 560), fl)
    plant(draw, X(p2, 800), fl, "flat")
    x = X(p2, 290)
    person(draw, x, fl, "smile", {
        "l": ((x - 44, fl - 120), (x - 56, fl - 64)),
        "r": ((x + 64, fl - 150), (x + 122, fl - 180)),
    })
    spout = watering_can(draw, (x + 122, fl - 180), tilt=62)
    drops(draw, spout, (X(p2, 560), fl + 14))
    bubble(draw, copy["dry"], body, X(p2, 800), Y(p2, 18), (X(p2, 800), fl - 262), p2)

    # Panel 3: a parasol, indoors.
    p3 = panels[2]
    fl = p3[3] - FLOOR_FROM_BOTTOM
    room(draw, p3, lamp_x=X(p3, 600), floor=fl)
    puddle(draw, X(p3, 560), fl)
    plant(draw, X(p3, 800), fl, "flat")
    x = X(p3, 540)
    hand = (x + 120, fl - 245)
    person(draw, x, fl, "smile", {
        "l": ((x - 44, fl - 120), (x - 56, fl - 64)),
        "r": ((x + 60, fl - 210), hand),
    })
    canopy(draw, X(p3, 690), X(p3, 830), Y(p3, 150), Y(p3, 182), hand)
    bubble(draw, copy["shade"], body, X(p3, 380), Y(p3, 18), (x - 22, fl - 266), p3)
    bubble(draw, copy["indoors"], body, X(p3, 850), Y(p3, 18), (X(p3, 846), fl - 236), p3)

    rgba = image.convert("RGBA")
    # Steep enough that the line reads as a sound effect, not a speech balloon.
    sfx(rgba, copy["sfx"], sfx_font, (X(p2, 330), Y(p2, 80)), 8)
    out = OUT / out_name
    rgba.convert("RGB").save(out, "PNG", optimize=True)
    print(out, rgba.size)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    pages = [
        (
            0,
            "sample_ja.png",
            {
                "caption": "第1話　水やり",
                "offer": "のど渇いた？",
                "plastic": "プラスチック",
                "dry": "まだ乾いてる",
                "shade": "日よけだよ",
                "indoors": "室内だよ",
                "sfx": "ジャバー",
            },
        ),
        (
            1,
            "sample_ko.png",
            {
                "caption": "1화　물주기",
                "offer": "목말라?",
                "plastic": "플라스틱",
                "dry": "아직 말라",
                "shade": "그늘이야",
                "indoors": "실내야",
                "sfx": "찰방",
            },
        ),
        (
            2,
            "sample_zh.png",
            {
                "caption": "第一集　浇水",
                "offer": "渴了吗？",
                "plastic": "塑料",
                "dry": "没浇到",
                "shade": "打伞",
                "indoors": "在室内",
                "sfx": "哗啦",
            },
        ),
    ]
    for index, name, copy in pages:
        page(copy, index, name)
    for old in ("sample_ja.jpg", "sample_ko.jpg", "sample_zh.jpg"):
        leftover = OUT / old
        if leftover.exists():
            leftover.unlink()
            print("removed", leftover)


if __name__ == "__main__":
    main()
