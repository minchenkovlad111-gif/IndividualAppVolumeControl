# Preset Format

Presets can be exported/imported via the Diagnostics screen using JSON.

## Format
- JSON object with package names as keys.
- Values are floats between 0.0 and 1.0.
- Metadata keys start with `_` and are ignored on import.

Example:
```
{
  "_version": 1,
  "_exported_at": 1710170000000,
  "com.example.music": 0.7,
  "com.example.podcast": 1.0
}
```

## Notes
- Import merges by default; use "Import replace" to clear existing presets first.
- Values outside 0.0–1.0 are clamped.
