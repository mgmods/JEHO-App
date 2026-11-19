/**
 * Classify the JEHO-owned schema before any first-install seed is allowed.
 * A schema without the JEHO marker is eligible only when it contains no
 * relations (including sequences/views), routines, or user-defined enum/domain/range.
 *
 * The production startup passes a node-postgres Client (result.rows), while
 * the disposable integration test passes a TypeORM DataSource/QueryRunner
 * (result is already the rows array). Normalize both supported result shapes.
 */
function resultRows(result) {
  if (Array.isArray(result)) return result;
  if (Array.isArray(result?.rows)) return result.rows;
  throw new TypeError('Unsupported PostgreSQL query result shape in JEHO schema guard.');
}

async function inspectJehoSchemaForProvisioning(client) {
  const markerResult = await client.query(
    `SELECT to_regclass('"jeho_own"."__jeho_provision_state"') IS NOT NULL AS exists`,
  );
  const markerRows = resultRows(markerResult);
  if (markerRows[0]?.exists) {
    const stateResult = await client.query(
      'SELECT status FROM "jeho_own"."__jeho_provision_state" WHERE id = 1',
    );
    const stateRows = resultRows(stateResult);
    return {
      markerExists: true,
      status: stateRows[0]?.status || null,
      isEmpty: false,
      relations: null,
      customTypes: null,
    };
  }

  const objectsResult = await client.query(`
    SELECT
      (SELECT count(*)::int FROM pg_class c
         JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'jeho_own'
           AND c.relkind IN ('r','p','v','m','S','f','c')) AS relations,
      (SELECT count(*)::int FROM pg_type t
         JOIN pg_namespace n ON n.oid = t.typnamespace
         WHERE n.nspname = 'jeho_own'
           AND t.typtype IN ('e','d','r','c')) AS custom_types,
      (SELECT count(*)::int FROM pg_proc p
         JOIN pg_namespace n ON n.oid = p.pronamespace
         WHERE n.nspname = 'jeho_own') AS routines
  `);
  const objectRows = resultRows(objectsResult);
  const relations = Number(objectRows[0]?.relations || 0);
  const customTypes = Number(objectRows[0]?.custom_types || 0);
  const routines = Number(objectRows[0]?.routines || 0);
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
