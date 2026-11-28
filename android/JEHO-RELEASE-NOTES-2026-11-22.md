# JEHO Android release notes — 2026-11-22

Merged into `MG-JEHO`:

- PR #244: exact lucky-gift seat targeting, sender-only X counter, and local room audio mute across the existing ZEGO playback paths.
- PR #245: keep the room microphone active when JEHO is merely backgrounded; hand off capture only after Android reports external recording has silenced JEHO, then restore the prior mic state on return when the same room is still active.
- PR #246: apply the existing listener-local room speaker mute to LiveKit remote audio and reapply it after reconnect.
- PR #247: prevent legacy voice-wave animation updates from overriding an equipped purchased voice-wave cosmetic.

No backend, Firebase, CP dashboard, storage, or RTC provider configuration changes are included in these Android fixes.

Final merged source commit before these notes: `746561f23c9e176b578d02d3e95fe4efebaf62e0`.
