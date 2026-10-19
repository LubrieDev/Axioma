# Axioma startup branding

- `axioma-greek.svg`: the exact Greek brand name, `Αξίωμα`, including the tonos
  above iota. All six letters use own rounded paths; no text node, font download,
  external font outlines, or runtime font dependency is required.
- `axioma-splash.svg`: the therefore symbol above the Greek wordmark on a
  transparent 288 x 288 canvas. Use it on charcoal `#1C1C1F`.
- `app/src/main/res/drawable/ic_splash_logo.xml`: native vector counterpart of
  the combined SVG, with the same letter paths, scale, and positions.

The three warm `#72645F` dots retain the supplied equilateral arrangement. The
wordmark uses `#F1ECE7` and rounded strokes, matching the interface icon family.
The Greek name is intentional brand artwork; project documentation and code
remain English. The launcher icon remains the supplied three-dot adaptive icon.

## Launch presentation

The starting activity uses `Theme.Axioma.Starting`. Android 12 and later display
the combined native vector using the platform splash screen; earlier supported
versions use a centered starting-window drawable. The combined mark stays inside
the native splash icon's circular safe region. Its 288 dp canvas follows the
[Android splash screen specification](https://developer.android.com/develop/ui/views/launch/splash-screen).

`LaunchScreen` holds the launch presentation until 500 milliseconds after activity
creation, using a monotonic clock. Platform startup can take longer. It does not
block the main thread, add a second launch activity, or delay restored activities
and orientation changes. On Android 12+, the existing system splash is removed
through its exit callback. On older devices, a pre-draw listener keeps the
starting window until the deadline. The activity switches to the normal app
theme before building the calculator UI.
