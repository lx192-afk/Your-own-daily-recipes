"""Draw the app icon (packaging/daily-menu.ico): a green rounded tile with a bowl and steam."""
import pathlib
import math

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parents[1]
S = 256
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
d.rounded_rectangle([8, 8, S - 8, S - 8], radius=56, fill=(143, 185, 154, 255))
# bowl
d.pieslice([48, 70, 208, 230], start=0, end=180, fill=(255, 255, 255, 255))
d.rectangle([40, 142, 216, 154], fill=(255, 255, 255, 255))
# steam: three gentle S-curves
for x0 in (100, 128, 156):
    pts = [(x0 + 9 * math.sin(i / 40 * 2 * math.pi), 56 + i * 1.6) for i in range(41)]
    d.line(pts, fill=(255, 255, 255, 235), width=9, joint="curve")
out = ROOT / "packaging/daily-menu.ico"
img.save(out, sizes=[(256, 256), (64, 64), (48, 48), (32, 32), (16, 16)])
img.save(ROOT / "packaging/daily-menu.png")
print("wrote", out)
