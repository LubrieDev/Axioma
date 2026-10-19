# Axioma launcher icon

The launcher uses a therefore mark: three warm `#72645F` dots on charcoal
`#1C1C1F`. Active editable source is the Android XML under
`app/src/main/res/drawable/ic_launcher_*.xml`.

Three circles of radius 7 dp are centered at `(54, 35.9)`, `(36, 67.1)` and
`(72, 67.1)` on a 108 x 108 dp canvas. The mark stays inside the launcher safe zone.
Background and monochrome layers support standard and themed launchers.

The manifest selects `mipmap-anydpi/ic_launcher.xml` and its round variant.
Android 8.0/API 26 is the minimum; no legacy bitmap fallback is needed. Android
applies launcher masks, so do not bake rounded corners into the background.

Preferred editable application resources remain in source. Personal import
archives and preview sheets are not required build inputs or APK assets and are
excluded from public candidates; they have been preserved locally. See
[repository policy](../../docs/development/repository-policy.md),
[branding](../branding/README.md) and [licensing](../../docs/development/licensing.md).
