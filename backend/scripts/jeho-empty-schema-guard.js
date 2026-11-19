/**
 * Classify the JEHO-owned schema before any first-install seed is allowed.
 * A schema without the JEHO marker is eligible only when it contains no
 * relations (including sequences/views), routines, or user-defined enum/domain/range.
 */
async function inspectJehoSchemaForProvisioning(client) {
  const marker = await client.query(
    "SELECT to_regclass('"jeho_own"."__jeho_provision_state"') IS NOT NULL AS exists",
  );
  if (marker.rows[0]?.exists) {
    const state = await client.query(
      'SELECT status FROM "jeho_own"."__jeho_provision_state" WHERE id = 1',
    );
    return {
      markerExists: true,
      status: state.rows[0]?.status || null,
      isEmpty: false,
      relations: null,
      customTypes: null,
    };
  }

  const objects = await client.query(`
    SELECT
      (SELECT count(*)::int FROM pg_class c
         JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'jeho_own'
           AND c.relkind IN ('r','p','v','m','S','f')) AS relations,
      (SELECT count(*)::int FROM pg_type t
         JOIN pg_namespace n ON n.oid = t.typnamespace
         WHERE n.nspname = 'jeho_own'
           AND t.typtype IN ('e','d','r')) AS custom_types
  `);
  const relations = Number(objects.rows[0]?.relations || 0);
  const customTypes = Number(objects.rows[0]?.custom_types || 0);
  const routines = Number(objects.rows[0]?.routines || 0);
  return {
    markerExists: false,
    status: null,
    isEmpty: relations === 0 && customTypes === 0 && routines === 0,
    relations,
    customTypes,
    routines,
  };
}

module.exports = { inspectJehoSchemaForProvisioning };
