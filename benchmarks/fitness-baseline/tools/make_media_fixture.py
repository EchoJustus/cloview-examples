"""Create a neutral animation, not an exercise demonstration.

Requires Pillow. An MP4 companion is optional and requires an existing ffmpeg
executable on PATH. No executable is downloaded or bundled by this script.
"""
import argparse
import json
import math
from pathlib import Path
import shutil
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mp4", action="store_true")
    args = parser.parse_args()
    directory = ROOT / "media"
    directory.mkdir(exist_ok=True)
    frames = []
    font = ImageFont.load_default(size=13)
    for index in range(32):
        im = Image.new("RGB", (320, 180), "#f3f5ef")
        draw = ImageDraw.Draw(im)
        draw.rounded_rectangle((4, 4, 315, 175), radius=14,
                               outline="#b5c5bc", width=2)
        draw.text((18, 18), "SYNTHETIC MOTION FIXTURE", fill="#173e32", font=font)
        draw.text((18, 148), "Playback test only", fill="#46685e", font=font)
        draw.text((263, 148), f"{index + 1:02d}/32", fill="#46685e", font=font)
        draw.line((30, 100, 290, 100), fill="#cbd9d2", width=3)
        x = round(160 + 102 * math.sin(index * 2 * math.pi / 32))
        draw.ellipse((x - 14, 86, x + 14, 114), fill="#267b68")
        frames.append(im)
    gif = directory / "synthetic-motion.gif"
    frames[0].save(gif, save_all=True, append_images=frames[1:],
                   duration=90, loop=0, disposal=2, optimize=False)
    with Image.open(gif) as result:
        durations = []
        for i in range(result.n_frames):
            result.seek(i)
            assert result.size == (320, 180)
            durations.append(result.info["duration"])
        assert result.n_frames == 32 and sum(durations) == 2880
    spec = {"synthetic": True, "purpose": "Playback and stable viewport testing; not exercise instruction",
            "gif": {"path": "media/synthetic-motion.gif", "width": 320,
                    "height": 180, "frames": 32, "frame_duration_ms": 90,
                    "loop_duration_ms": 2880, "loop": "infinite"},
            "mp4_included": False}
    if args.mp4:
        executable = shutil.which("ffmpeg")
        if not executable:
            raise SystemExit("ffmpeg is not installed; GIF was generated, no MP4 was created.")
        subprocess.run([executable, "-v", "error", "-y", "-i", str(gif),
                        "-an", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                        "-movflags", "+faststart", "-map_metadata", "-1",
                        str(directory / "synthetic-motion.mp4")], check=True)
        spec["mp4_included"] = True
    (directory / "media-spec.json").write_text(json.dumps(spec, indent=2) + "\n", encoding="utf-8")
    print("GIF: 320x180, 32 frames, 2880 ms; metadata verified.")


if __name__ == "__main__":
    main()
