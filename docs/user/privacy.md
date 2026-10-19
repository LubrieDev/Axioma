# Privacy and local data

Axioma evaluates locally in own C++ code. Its manifest requests no Internet
permission. MathJax and SVG font data are bundled for offline rendering; there
is no remote formula/font/evaluation service.

The app has no account, advertising, analytics or cloud synchronization. Its
WebView disables file/content access and DOM storage and intercepts only packaged
renderer resources. A synthetic HTTPS origin is an identifier, not a network service.

## On-device storage

- Result-format preference in application-private SharedPreferences.
- Up to 50 history records, including expressions and result data.
- Activity/editor state for recreation, including an unfinished source draft.

Clear history in its dialog to remove records. Clear all only clears current
input. Android app-data controls remove stored preferences/history. Backup is
disabled by the manifest; this is not a guarantee about rooted devices or access
outside the application's control.

## Clipboard and development

Copy actions put mathematical text in Android's clipboard; access follows the
operating system's rules. Axioma has no clipboard synchronization service.

USB testing uses ADB after device authorization. Logs, screenshots and independent
audit output are developer artifacts, not automatic telemetry. See
[math rendering](../reference/math-rendering.md) for the WebView boundary.
