# Axioma interface icons

Keep editable SVG artwork here and active Android VectorDrawable counterparts in
`app/src/main/res/drawable/`. Both versions must retain the same paths, viewport,
stroke widths, and rounded caps and joins when edited. No icon font or external
icon library is used.

`backspace.svg` uses a softly pointed left edge, restrained rounded corners, and a
small inset cross. The 1.65-unit strokes on its 28 x 24 canvas keep it close to
Axioma's existing navigation strokes. Android tints the vector with the current
key foreground color and centers it inside the full accessible button area.

## Icon family

All icons use a 28 x 24 canvas, 1.65-unit strokes, and rounded caps and joins.
The native resources have a nominal size of 34 x 29.143 dp and use the current
foreground color in both light and dark themes.

| Editable SVG | Android vector | Purpose |
| --- | --- | --- |
| `backspace.svg` | `ic_backspace.xml` | Delete the selection or previous character |
| `clear_all.svg` | `ic_clear_all.xml` | Clear all, with own geometric A and C strokes |
| `keyboard.svg` | `ic_keyboard.xml` | Open the in-app expression keyboard |
| `settings.svg` | `ic_settings.xml` | Open Settings from the overflow menu, with an own six-tooth gear |
| `cursor_left.svg` | `ic_cursor_left.xml` | Move the source cursor left |
| `cursor_right.svg` | `ic_cursor_right.xml` | Move the source cursor right |
| `shift.svg` | `ic_shift.xml` | Toggle keyboard letter case |
| `enter.svg` | `ic_enter.xml` | Insert a line break |
| `unit_degrees.svg` | `ic_unit_degrees.xml` | Angle unit: theta with a small D |
| `unit_gradians.svg` | `android/ic_unit_gradians.xml` | Angle unit: theta with a small G |
| `unit_radians.svg` | `ic_unit_radians.xml` | Angle unit: theta with a small R |

The angle family uses LMath's theta-and-unit concept as a reference, with newly
drawn paths and no imported LMath assets. Degrees and radians are connected to
the current calculator's mode toggle, including mode restoration from history.
The gradians artwork is prepared for future engine support; its Android vector
stays in this artwork folder until the mode is implemented and is not packaged
in the APK yet. It is not offered as an active calculation mode.

Action icons use native vectors. Mathematical notation, including the `f(x)`
function toggle, continues to use the shared MathJax renderer.
