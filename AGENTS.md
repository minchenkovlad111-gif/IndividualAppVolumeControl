# AGENTS.md

## Project goal
Build a root-required Android app + Magisk module that provides reliable per-app volume control on Android 15 and ideally older versions.

## Key requirements
- Must run as a privileged system app installed via Magisk.
- Must detect active audio sessions and let the user control per-app volume in real time.
- Must also allow preset volumes for installed apps.
- Maintain documentation, TODO list, and current status log.

## Primary sources (for reference)
- https://www.xda-developers.com/app-volume-control-individual-volume-levels-android/
- https://alcatraz323.github.io/audiohq/index_en.html
- https://github.com/Alcatraz323/audiohq_md2
- https://github.com/Alcatraz323/audiohq_module
- https://github.com/yume-chan/VolumeManager
- https://github.com/legendsayantan/ShizuTools/wiki/SoundMaster
- https://xdaforums.com/t/app-soundassistant-from-samsung-no-root-sound-volume-mods.3702305/

## Working rules
- Work autonomously: implement a feature, check build, commit, then move to next.
- Keep `docs/` updated, especially `docs/ARCHITECTURE.md`, `docs/RESEARCH.md`, and `docs/BUILD.md`.
- Keep `TODO.md` current and prioritized.
- Keep `STATUS.md` updated with what is being worked on and the last update date.
- Prefer minimal dependencies and straightforward, stable APIs.
