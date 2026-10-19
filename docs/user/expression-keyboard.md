# In-app expression keyboard

The keyboard button beside the angle control opens an explicit source editor at
the bottom of Axioma. Tapping a formula or choosing Edit expression opens the
same editor. The keyboard uses native Android controls and own vector icons;
it does not launch or depend on a system IME.

## Layout and editing

- A number row remains available in both layouts.
- The alphabet layout contains all 27 letters in QWERTY order, with ñ after L
  and Shift and backspace beside Z–M. Shift toggles letter case, including Ñ.
- A bottom-left `\{}[]` key opens symbols; it changes to `ABC` to return to letters.
  The symbols layout contains backslash, grouping delimiters, vertical bar, operators,
  punctuation, and the TeX characters `% $ & # _ ^ ~`. Comma, semicolon, colon,
  period, quotes, and angle brackets are included.
- The bottom row follows a phone keyboard: layout switch, comma, a wide space
  bar, period, and return. Cursor arrows live in the compact editor toolbar.
  Holding an arrow moves to the beginning or end of the source.
- Native selection and clipboard actions work in the source field. Typing
  replaces a selection; backspace deletes it or the previous Unicode code point.
- Apply transfers the source and selection to the calculator. Cancel discards
  the draft. Draft text, selection, case, and layout survive activity recreation.

Source keys insert literal characters. They do not rewrite adjacent operators
or add automatic parentheses, which would interfere with LaTeX. The existing
2048-character input limit applies. Layout rows become shorter in landscape;
the editor can scroll on small windows.

Literal keyboard characters are ordinary interface text. The calculator's
expression, result, mathematical keys, and history continue to use MathJax.
The raw source appears only in the explicit editor. Entering a command does not
add its mathematical operation to the CAS: evaluation still accepts the own
scalar parser's supported subset. See [logarithms](../reference/logarithms.md).
