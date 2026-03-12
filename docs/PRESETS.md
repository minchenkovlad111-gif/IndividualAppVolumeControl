# Preset Format

Presets can be exported/imported via the Diagnostics screen using JSON.

## Format
- JSON object with package names as keys.
- Values are floats between 0.0 and 1.0.
- Metadata keys start with `_` and are ignored on import.
- Profile-specific presets use the key format `u<userId>:<package>`.
- Optional marker configs are stored under `_marker_configs` keyed by the same storage key.
- Optional output configs are stored under `_output_configs` keyed by the same storage key.

Example:
```
{
  "_version": 1,
  "_exported_at": 1710170000000,
  "_preset_count": 2,
  "_marker_configs": {
    "com.example.music": {
      "version": 1,
      "markers": [
        {"percent": 70, "color": "#FF9800"}
      ],
      "segments": [
        {"start": 20, "end": 40, "color": "#33FF9800"}
      ]
    }
  },
  "_output_configs": {
    "com.example.music": {
      "type": 8,
      "address": "00:11:22:33:44:55",
      "name": "BT Headphones"
    }
  },
  "com.example.music": 0.7,
  "com.example.podcast": 1.0,
  "u10:com.example.workmusic": 0.6
}
```

## Notes
- Import merges by default; use "Import replace" to clear existing presets first.
- Values outside 0.0–1.0 are clamped.
- `_preset_count` is optional metadata for readability and is ignored on import.
- `_marker_configs` is optional; invalid entries are ignored on import.
- `_output_configs` is optional; invalid entries are ignored on import.
- If a profile-specific key is missing, the app falls back to the global package preset.
