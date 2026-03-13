# Preset Format

Presets can be exported/imported via the Diagnostics screen using JSON.

## Format
- JSON object with package names as keys.
- Values are floats between 0.0 and 1.0.
- Metadata keys start with `_` and are ignored on import.
- Profile-specific presets use the key format `u<userId>:<package>`.
- Optional marker configs are stored under `_marker_configs` keyed by the same storage key.
- Optional output configs are stored under `_output_configs` keyed by the same storage key.
- Optional mute behaviors are stored under `_mute_behaviors` keyed by the same storage key.
- Optional default mute behavior is stored under `_mute_default`.
- Optional auto-resume overrides are stored under `_resume_overrides`.
- Optional rule groups are stored under `_rule_groups`.
- Optional rules are stored under `_rules`.

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
  "_mute_behaviors": {
    "com.example.music": "mute_pause"
  },
  "_mute_default": "mute_pause",
  "_resume_overrides": {
    "com.example.music": "on",
    "com.example.podcast": "off"
  },
  "_rule_groups": [
    {
      "version": 1,
      "id": "g_music",
      "name": "Music Apps",
      "packages": ["com.example.music", "com.example.radio"]
    }
  ],
  "_rules": [
    {
      "version": 1,
      "id": "r_focus",
      "name": "Lower music during calls",
      "enabled": true,
      "priority": 100,
      "conditions": [
        {"type": "any_active", "packages": ["com.example.voip"]},
        {"type": "time_window", "days": [1,2,3,4,5], "start_min": 540, "end_min": 1020}
      ],
      "actions": [
        {"type": "set_volume", "targets": ["group:g_music"], "volume": 0.3},
        {"type": "audio_focus", "targets": ["com.example.radio"], "mode": "duck", "duck": 0.2}
      ]
    }
  ],
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
- `_mute_behaviors` is optional; invalid entries are ignored on import.
- `_mute_default` is optional; invalid entries are ignored on import.
- `_resume_overrides` is optional; invalid entries are ignored on import.
- `_rule_groups` is optional; invalid entries are ignored on import.
- `_rules` is optional; invalid entries are ignored on import.
- If a profile-specific key is missing, the app falls back to the global package preset.
- Location rule conditions are matched against a current location id derived from live location updates.
