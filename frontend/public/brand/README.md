# MediFlow logo files

| File | Use on |
|---|---|
| `mediflow-logo-light.svg`, `.png` | Light backgrounds: teal cross, white trace |
| `mediflow-logo-dark.svg`, `.png` | Dark or deep teal backgrounds: white cross, teal trace |
| `mediflow-icon.svg`, `.png` | Anywhere that needs its own background: app icons, avatars, favicons |

PNGs are 1024×1024; the logos have a transparent background. In the running app the same files are
served at `/brand/<file>`. Inside React, use `MediFlowMark` from `@mediflow/loader` so the mark
follows the theme.
