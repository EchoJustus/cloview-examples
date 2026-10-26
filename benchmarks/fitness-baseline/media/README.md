# Neutral playback fixture

`synthetic-motion.gif` is generated geometric motion, not a person or an exercise instruction. It tests animation progression, loop handling, aspect ratio and stable layout without distributing the original portrait-based clips.

The GIF has a fixed 320x180 canvas, 32 frames, 90 milliseconds per frame and a 2.88-second infinite loop. The frame counter and moving circle allow a reviewer to distinguish an animated player from a static thumbnail. The border must not move or resize while frames advance. Letterboxing is acceptable; accidental stretching, cropping or layout jumping is not.

Use it as the selected file in a native file-picker import case, then verify that the application creates and uses a managed local copy. The business fixture may omit media paths; attaching this file is an explicit test setup action.

No MP4 is included in this package. Test the MP4 extension with a neutral local clip and record its codec, dimensions, duration and hash. The helper can optionally generate a companion from this GIF if ffmpeg is already installed:

```text
python tools/make_media_fixture.py
python tools/make_media_fixture.py --mp4
```

The helper requires Pillow; the benchmark's JSON checks require only Python's standard library. Regenerating media changes the package integrity manifest and must be done in a separate working copy. The GIF container metadata/frame count were checked during preparation; actual playback must still be tested on each claimed host.
