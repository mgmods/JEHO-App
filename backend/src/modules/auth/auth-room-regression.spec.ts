import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { UserStatus } from '../database/entities/user.entity';
import { AuthService } from './auth/auth.service';
import { RealtimeGateway } from './realtime/realtime.gateway';

test('Google social login verifies the ID token and links the verified subject', async () => {
  const verified = {
    sub: 'google-subject-123',
    email: 'member@example.test',
    name: 'Verified Member',
    picture: 'https://example.test/avatar.png',
  };
  const user: any = {
    id: 'user-1',
    username: 'member',
    publicId: '123456',
    status: UserStatus.ACTIVE,
    displayName: 'Old name',
    avatarUrl: null,
  };
  const saved: any[] = [];
  const service: any = Object.create(AuthService.prototype);
  service.verifyGoogleIdToken = async (token: string) => {
    assert.equal(token, 'valid-google-id-token');
    return verified;
  };
  service.usersRepo = {
    findOne: async (options: any) => {
      if (options.where.googleId) return user;
      return null;
    },
    save: async (value: any) => { saved.push({ ...value }); return value; },
  };
  service.issueTokens = async (value: any) => ({ userId: value.id, issued: true });

  const result = await service.socialLogin({
    provider: 'google',
    token: 'valid-google-id-token',
    email: 'untrusted@example.test',
    displayName: 'Untrusted name',
  });

  assert.deepEqual(result, { userId: 'user-1', issued: true });
  assert.equal(user.googleId, verified.sub);
  assert.equal(user.email, verified.email);
  assert.equal(user.displayName, verified.name);
  assert.equal(user.avatarUrl, verified.picture);
  assert.equal(user.emailVerified, true);
  assert.equal(saved.length, 1);
});

test('Google social login rejects a failed token verification before account lookup', async () => {
  const service: any = Object.create(AuthService.prototype);
  let accountLookups = 0;
  service.verifyGoogleIdToken = async () => {
    throw new Error('signature rejected');
  };
  service.usersRepo = {
    findOne: async () => { accountLookups += 1; return null; },
  };

  await assert.rejects(
    service.socialLogin({ provider: 'google', token: 'invalid-google-id-token' }),
    (error: any) => error?.status === 401,
  );
  assert.equal(accountLookups, 0);
});

test('realtime connection rejects clients without a valid JWT', async () => {
  const gateway: any = Object.create(RealtimeGateway.prototype);
  gateway.jwtService = { verifyAsync: async () => { throw new Error('invalid'); } };
  gateway.configService = { get: () => 'test-secret' };
  gateway.usersRepo = { findOne: async () => null };
  const client: any = {
    id: 'socket-1',
    handshake: { auth: { token: 'bad-token' }, headers: {} },
    data: {},
    disconnectCalls: 0,
    disconnect() { this.disconnectCalls += 1; },
  };

  await gateway.handleConnection(client);
  assert.equal(client.disconnectCalls, 1);
  assert.equal(client.userId, undefined);
});

test('realtime room join refuses a room that has already been closed', async () => {
  const gateway: any = Object.create(RealtimeGateway.prototype);
  gateway.roomDisconnectCleanupTimers = new Map();
  gateway.roomsRepo = { findOne: async () => ({ id: 'room-1', status: 'closed' }) };
  const client: any = {
    userId: 'user-1',
    data: { roomProfiles: {} },
    joinCalls: [],
    join(room: string) { this.joinCalls.push(room); },
  };

  const result = await gateway.onRoomJoin(client, { roomId: 'room-1' });
  assert.deepEqual(result, { error: 'Room is closed' });
  assert.deepEqual(client.joinCalls, []);
});

test('realtime room leave removes the socket and broadcasts the updated count', async () => {
  const events: any[] = [];
  const gateway: any = Object.create(RealtimeGateway.prototype);
  gateway.emitRoomMembers = async () => ({ viewerCount: 2 });
  gateway.server = {
    to: (room: string) => ({
      emit: (event: string, payload: any) => events.push({ room, event, payload }),
    }),
  };
  const client: any = {
    userId: 'user-1',
    username: 'member',
    data: { roomProfiles: { 'room-1': { userId: 'user-1' } } },
    leaveCalls: [],
    leave(room: string) { this.leaveCalls.push(room); },
    to: (room: string) => ({
      emit: (event: string, payload: any) => events.push({ room, event, payload }),
    }),
  };

  const result = await gateway.onRoomLeave(client, { roomId: 'room-1' });
  assert.deepEqual(result, { left: 'room-1', viewerCount: 2 });
  assert.deepEqual(client.leaveCalls, ['room:room-1']);
  assert.equal(client.data.roomProfiles['room-1'], undefined);
  assert.equal(events.length, 2);
  assert.equal(events[0].event, 'room:user_left');
  assert.equal(events[1].event, 'room:event');
});
