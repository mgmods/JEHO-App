-- Agency auto-staff should not get full room admin (kick/ban/seats/settings).
-- Room host keeps full control; appointed mods with canManageRoom stay elevated.
UPDATE room_moderators rm
SET
  "canKick" = false,
  "canBan" = false,
  "canManageSeats" = false,
  "canInvite" = false,
  "canControlGames" = false,
  "canManageMusic" = false,
  "canChangeFrames" = false,
  "canMute" = true
FROM rooms r
WHERE rm."roomId" = r.id
  AND r."agencyId" IS NOT NULL
  AND COALESCE(rm."canManageRoom", false) = false;
