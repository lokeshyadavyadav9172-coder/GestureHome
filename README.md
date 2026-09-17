# GestureHome — Android starter (iQOO Hackathon, Smart Living)

Touchless control for people whom voice assistants and touchscreens leave out.
Hand gestures, head tilts and long blinks are recognized **entirely on the phone**
and mapped to device actions. No frames, no landmarks and no audio ever leave the device.

## What already works in this starter

- CameraX front-camera pipeline feeding a background inference thread.
- **MediaPipe Tasks Vision** gesture recognizer (`gesture_recognizer.task`) — open palm,
  closed fist, thumb up/down, victory, pointing up.
- **MediaPipe Face Landmarker** (`face_landmarker.task`) with blendshapes — head tilt left/right
  and deliberate long blink (natural blinks are filtered out by a frame counter).
- Debounce/cooldown so one gesture fires one action.
- A per-user binding map (`DeviceStore.bindings`) — gestures are assignable, not hard-coded,
  because motor ability differs from person to person.
- Device backend targeting **this phone**: torch, screen brightness, media volume, silent mode.
  Zero extra hardware needed for the demo.
- Live action log for the on-stage demo.

## Default bindings

| Gesture | Action |
| --- | --- |
| Open palm | Toggle torch |
| Closed fist | Everything off |
| Thumb up / down | Media volume ±15% |
| Head tilt right / left | Screen brightness ±15% |
| Long blink (~0.5s) | Toggle silent mode |

## Run it

1. Open the folder in Android Studio (Ladybug or newer) and let Gradle sync.
2. Plug in the iQOO phone, enable USB debugging, press Run.
3. Grant camera access, hold the phone at arm's length, try an open palm.

Requires JDK 17, `minSdk 26`. The two `.task` models are bundled in
`app/src/main/assets/` and kept uncompressed via `noCompress += "task"`.

## Where to extend

- `devices/DeviceController.kt` — implement `DeviceBackend` to hit Google Home,
  Matter/Thread, Tuya or a local ESP8266 smart plug over HTTP. The rest of the app is unchanged.
- `gesture/GestureAnalyzer.kt` — tune `minConfidence`, `cooldownMs`, switch
  `Delegate.CPU` to `Delegate.GPU`, or move to `RunningMode.LIVE_STREAM` for a few more fps.
- `devices/DeviceStore.kt` — persist bindings with DataStore, and add a guided
  "record your own gesture" calibration flow (strong pitch point for accessibility).
- Add a foreground service if you want recognition to continue with the screen off.

## Why on-device ML (the judging argument)

- **Privacy:** a camera watching a living room cannot be a cloud stream.
- **Latency:** an interface has to respond in tens of milliseconds to feel like a switch.
- **Offline:** turning on a light must not depend on the internet.
