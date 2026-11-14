-- Broadcast agencies must NOT auto-become recharge agents.
-- Suspend any rows that were auto-created from agency owners/managers.
UPDATE recharge_agents
SET
  status = 'suspended',
  "listedInDirectory" = false,
  notes = CASE
    WHEN notes IS NULL OR trim(notes) = '' THEN 'unlinked: was auto-created from broadcast agency'
    WHEN notes ILIKE '%unlinked:%' THEN notes
    ELSE notes || ' | unlinked: broadcast agency is separate from recharge agents'
  END
WHERE
  notes ILIKE 'auto: agency%'
  OR notes ILIKE 'auto-activated: agency%'
  OR notes ILIKE '%auto: agency%';
