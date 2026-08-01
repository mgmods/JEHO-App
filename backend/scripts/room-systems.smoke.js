const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

const applicationEntity = read('src/database/entities/agency-application.entity.ts');
const agenciesController = read('src/modules/agencies/agencies.controller.ts');
const roomsDto = read('src/modules/rooms/dto/rooms.dto.ts');
const roomsService = read('src/modules/rooms/rooms.service.ts');
const migration = read('scripts/20260721-professional-agencies-persistent-rooms.sql');

for (const status of ['pending', 'changes_requested', 'approved', 'rejected']) {
  assert.match(applicationEntity, new RegExp(`['"]${status}['"]`));
}
assert.match(agenciesController, /@Post\('applications'\)/);
assert.match(agenciesController, /@Get\('applications\/mine'\)/);

const createRoomDto = roomsDto.match(
  /export class CreateRoomDto \{([\s\S]*?)\n\}/,
)?.[1] || '';
for (const removedField of ['password', 'isPublic', 'accessMode', 'entryFeeCoins']) {
  assert.doesNotMatch(createRoomDto, new RegExp(`\\b${removedField}\\??\\s*:`));
}

assert.match(roomsService, /AgencyRole\.OWNER[\s\S]*AgencyRole\.MANAGER[\s\S]*AgencyRole\.HOST/);
assert.match(roomsService, /RoomKind\.AGENCY/);
assert.match(roomsService, /RoomAccessMode\.FREE/);
assert.match(migration, /uq_room_agency_host/i);
assert.match(migration, /WHERE "agencyId" IS NOT NULL/);

console.log('Agency application and persistent room contract smoke checks passed.');
