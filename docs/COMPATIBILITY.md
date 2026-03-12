# Compatibility

Use this table to track observed behavior across devices and Android versions.

| Device | Android | Privileged install | Hidden API VMRuntime | Hidden API policy | Per-app volume working | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| (example) | 15 | yes/no | ok/fail | ok/fail | yes/no | |

## Steps
1. Install the app via Magisk module and reboot.
2. Open Diagnostics and record hidden API results + permission status.
3. Play audio from two apps at once and adjust per-app sliders.
4. Record whether volume changes apply correctly.
5. Press volume keys and confirm the overlay appears; capture overlay signal counts from Diagnostics.
6. Use "Copy compatibility row" in Diagnostics and paste into the table.

Tip: The copied row already includes the `|` delimiters for this table.

## Known issues
- Add device-specific notes here.
