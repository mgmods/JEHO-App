import {

  Injectable,

  NotFoundException,

  BadRequestException,

  ForbiddenException,

  Optional,

} from '@nestjs/common';

import { InjectRepository } from '@nestjs/typeorm';

import { DataSource, EntityManager, In, IsNull, Repository } from 'typeorm';

import { CasualMatch, CasualGameKind } from '../../database/entities/casual-match.entity';

import { User } from '../../database/entities/user.entity';
import { Wallet } from '../../database/entities/wallet.entity';
import {
  CurrencyType,
  TransactionType,
  WalletTransaction,
} from '../../database/entities/wallet-transaction.entity';
import { AppSetting } from '../../database/entities/app-setting.entity';
import { Room } from '../../database/entities/room.entity';

import { RealtimeGateway } from '../realtime/realtime.gateway';

import { CasualActionDto } from './dto/casual.dto';
import { TasksService } from '../tasks/tasks.service';
import { RoomGameAccessService } from './room-game-access.service';



const STALE_PLAYING_MS = 10 * 60_000;

const STALE_WAITING_MS = 45_000;

const HEARTBEAT_TIMEOUT_MS = 30_000;



@Injectable()

export class CasualMatchService {

  constructor(

    @InjectRepository(CasualMatch)

    private readonly matchesRepo: Repository<CasualMatch>,

    @InjectRepository(User)

    private readonly usersRepo: Repository<User>,

    private readonly dataSource: DataSource,
    private readonly tasksService: TasksService,
    private readonly roomAccess: RoomGameAccessService,

    @Optional() private readonly realtime?: RealtimeGateway,

  ) {}



  async enqueue(userId: string, kind: CasualGameKind, roomId?: string) {

    await this.roomAccess.assertParticipant(roomId, userId);
    await this.cancelStaleForUser(userId, kind);



    const active = await this.matchesRepo.findOne({

      where: [

        { player1Id: userId, kind, status: 'playing' as const, roomId: roomId || IsNull() },

        { player2Id: userId, kind, status: 'playing' as const, roomId: roomId || IsNull() },

        { player1Id: userId, kind, status: 'waiting' as const, roomId: roomId || IsNull() },

      ],

      order: { updatedAt: 'DESC' },

    });

    if (active && (active.status === 'playing' || active.player2Id)) {

      return this.toDto(active, userId);

    }



    return this.dataSource.transaction(async (em) => {

      const repo = em.getRepository(CasualMatch);

      const mine = await repo.findOne({

        where: {
          player1Id: userId,
          kind,
          status: 'waiting',
          player2Id: IsNull(),
          roomId: roomId || IsNull(),
        },

        lock: { mode: 'pessimistic_write' },

      });

      if (mine) return this.toDto(mine, userId);



      const waiting = await repo

        .createQueryBuilder('m')

        .setLock('pessimistic_write')

        .where('m.kind = :kind', { kind })

        .andWhere('m.status = :status', { status: 'waiting' })

        .andWhere('m.player2Id IS NULL')
        .andWhere(roomId ? 'm.roomId = :roomId' : 'm.roomId IS NULL', {
          roomId,
        })

        .andWhere('m.player1Id != :userId', { userId })

        .orderBy('m.createdAt', 'ASC')

        .getOne();



      if (waiting) {

        const age = Date.now() - waiting.updatedAt.getTime();

        const hostOffline =
          this.realtime != null &&
          !(await this.realtime.isOnlineGlobal(waiting.player1Id));

        if (age > STALE_WAITING_MS || hostOffline) {

          waiting.status = 'cancelled';

          await repo.save(waiting);

        } else {

          waiting.player2Id = userId;

          waiting.status = 'playing';

          waiting.turn = 1;

          waiting.lastHeartbeatAt = new Date();

          const saved = await repo.save(waiting);

          const dto = await this.toDto(saved, userId);

          await this.notifyPlayers(saved, 'match:found');

          return dto;

        }

      }



      const match = repo.create({

        kind,

        status: 'waiting',

        player1Id: userId,

        player2Id: null,
        roomId: roomId || null,

        turn: 1,

        state: null,

        winner: null,

        stateVersion: 0,

        lastHeartbeatAt: new Date(),

      });

      const saved = await repo.save(match);

      return this.toDto(saved, userId);

    });

  }



  async cancelQueue(userId: string, kind?: CasualGameKind) {

    const waiting = await this.matchesRepo.find({

      where: { player1Id: userId, status: 'waiting' },

    });

    for (const m of waiting) {

      if (kind && m.kind !== kind) continue;

      m.status = 'cancelled';

      await this.matchesRepo.save(m);

    }

    return { ok: true };

  }



  async heartbeat(matchId: string, userId: string) {

    const match = await this.matchesRepo.findOne({ where: { id: matchId } });

    if (!match) throw new NotFoundException('Match not found');

    if (match.player1Id !== userId && match.player2Id !== userId) {

      throw new ForbiddenException('Not a player in this match');

    }
    await this.roomAccess.assertParticipant(match.roomId || undefined, userId);

    match.lastHeartbeatAt = new Date();

    await this.matchesRepo.save(match);

    return { ok: true };

  }



  async leaveMatch(matchId: string, userId: string) {

    return this.action(matchId, userId, { action: 'forfeit', payload: {} });

  }



  async getMatch(matchId: string, userId: string) {

    const match = await this.matchesRepo.findOne({ where: { id: matchId } });

    if (!match) throw new NotFoundException('Match not found');

    if (match.player1Id !== userId && match.player2Id !== userId) {

      throw new ForbiddenException('Not a player in this match');

    }
    await this.roomAccess.assertParticipant(match.roomId || undefined, userId);

    return this.toDto(match, userId);

  }

  async getBossStatus(userId: string) {
    const settings = await this.bossSettings();
    const row = await this.matchesRepo.manager.findOne(AppSetting, {
      where: { key: 'games.boss.dailyState' },
    });
    const state = this.normalizeBossState(row?.value, settings.maxHp);
    return this.bossDto(state, userId, settings);
  }

  async attackBoss(userId: string) {
    return this.dataSource.transaction(async (manager) => {
      const settings = await this.bossSettings(manager);
      let row = await manager.findOne(AppSetting, {
        where: { key: 'games.boss.dailyState' },
        lock: { mode: 'pessimistic_write' },
      });
      if (!row) {
        row = manager.create(AppSetting, {
          key: 'games.boss.dailyState',
          value: '',
          description: 'Server-authoritative daily boss state',
        });
        row = await manager.save(row);
      }

      const state = this.normalizeBossState(row.value, settings.maxHp);
      const used = Number(state.attackers[userId] || 0);
      if (state.hp <= 0) {
        return { ...this.bossDto(state, userId, settings), damage: 0 };
      }
      if (used >= settings.maxAttacks) {
        throw new BadRequestException('Daily boss attacks exhausted');
      }

      const damage =
        settings.minDamage +
        Math.floor(Math.random() * (settings.maxDamage - settings.minDamage + 1));
      state.attackers[userId] = used + 1;
      state.hp = Math.max(0, state.hp - damage);
      state.lastHitBy = userId;
      state.lastDamage = damage;

      let rewardCoins = 0;
      if (state.hp === 0 && !state.winnerId) {
        state.winnerId = userId;
        rewardCoins = settings.rewardCoins;
        let wallet = await manager.findOne(Wallet, {
          where: { userId },
          lock: { mode: 'pessimistic_write' },
        });
        if (!wallet) {
          wallet = await manager.save(manager.create(Wallet, { userId }));
        }
        wallet.coins = Number(wallet.coins) + rewardCoins;
        await manager.save(wallet);
        await manager.save(
          manager.create(WalletTransaction, {
            userId,
            type: TransactionType.LUCKY_REWARD,
            currency: CurrencyType.COINS,
            amount: rewardCoins,
            balanceAfter: Number(wallet.coins),
            referenceType: `daily_boss_${state.date}`,
            referenceId: null,
            description: `Defeated daily boss ${state.date}`,
            metadata: { date: state.date, damage },
          }),
        );
      }

      row.value = JSON.stringify(state);
      await manager.save(row);
      return {
        ...this.bossDto(state, userId, settings),
        damage,
        earnedCoins: rewardCoins,
      };
    });
  }



  async action(matchId: string, userId: string, dto: CasualActionDto) {
    return this.dataSource.transaction(async (manager) => {
    const match = await manager.findOne(CasualMatch, {
      where: { id: matchId },
      lock: { mode: 'pessimistic_write' },
    });

    if (!match) throw new NotFoundException('Match not found');

    if (match.player1Id !== userId && match.player2Id !== userId) {

      throw new ForbiddenException('Not a player in this match');

    }
    await this.roomAccess.assertParticipant(match.roomId || undefined, userId);

    if (match.status === 'cancelled' || match.status === 'finished') {

      throw new BadRequestException('Match already ended');

    }



    if (dto.action === 'forfeit') {
      if (match.status === 'waiting' || !match.player2Id) {
        throw new BadRequestException('Cannot forfeit before match starts');
      }

      match.status = 'finished';
      match.winner = match.player1Id === userId ? '2' : '1';

      match.state = {

        ...(match.state || {}),

        endReason: 'forfeit',

        leftUserId: userId,

      };

      match.stateVersion = Number(match.stateVersion || 0) + 1;

      const saved = await manager.save(match);
      const out = await this.toDto(saved, userId);

      await this.notifyPlayers(saved, 'match:update');

      return out;

    }



    if (dto.action === 'state') {
      if (match.status === 'waiting') {
        throw new BadRequestException('Waiting for opponent');
      }
      const role = match.player1Id === userId ? 1 : 2;
      if (match.turn !== role) {
        throw new ForbiddenException('Not your turn');
      }

      const incomingVersion = Number((dto.payload || {}).version ?? 0);

      if (incomingVersion > 0 && incomingVersion <= Number(match.stateVersion || 0)) {

        return this.toDto(match, userId);

      }

      if (JSON.stringify(dto.payload || {}).length > 256_000) {
        throw new BadRequestException('Game state is too large');
      }
      match.state = this.mergePlayerSnapshot(
        match.state,
        dto.payload || {},
        role,
        match.kind,
      );

      match.stateVersion = Number(match.stateVersion || 0) + 1;

      if (typeof match.state.turn === 'number') {

        match.turn = match.state.turn as number;

      }

      match.lastHeartbeatAt = new Date();

      const winner = match.state.winner;

      if (

        winner !== null &&

        winner !== undefined &&

        winner !== 0 &&

        winner !== '0' &&

        winner !== ''

      ) {

        this.validateWinnerState(match, role);

        match.winner = String(winner);
        match.status = 'finished';
        delete match.state.pendingWinner;

      }

      const saved = await manager.save(match);
      const out = await this.toDto(saved, userId);

      await this.notifyPlayers(saved, 'match:update');

      return out;

    }



    throw new BadRequestException('Unknown action');

    });
  }

  async roomLeaderboard(roomId: string) {
    const matches = await this.matchesRepo.find({
      where: { roomId, status: 'finished' },
      order: { updatedAt: 'DESC' },
      take: 1000,
    });
    const wins = new Map<string, number>();
    for (const match of matches) {
      const role = Number(match.winner);
      const winnerId =
        role === 1 ? match.player1Id : role === 2 ? match.player2Id : null;
      if (winnerId) wins.set(winnerId, (wins.get(winnerId) || 0) + 1);
    }
    const ids = [...wins.keys()];
    const users = ids.length
      ? await this.usersRepo.find({ where: { id: In(ids) } })
      : [];
    const byId = new Map(users.map((user) => [user.id, user]));
    const items = ids
      .map((userId) => ({
        userId,
        wins: wins.get(userId) || 0,
        displayName:
          byId.get(userId)?.displayName ||
          byId.get(userId)?.username ||
          'لاعب',
        publicId: byId.get(userId)?.publicId || null,
        avatarUrl: byId.get(userId)?.avatarUrl || null,
      }))
      .sort((a, b) => b.wins - a.wins)
      .map((item, index) => ({ rank: index + 1, ...item }));
    return { roomId, items, totalMatches: matches.length };
  }



  private async cancelStaleForUser(userId: string, kind: CasualGameKind) {

    const staleBefore = new Date(Date.now() - STALE_PLAYING_MS);

    const playing = await this.matchesRepo.find({

      where: [

        { player1Id: userId, kind, status: 'playing' as const },

        { player2Id: userId, kind, status: 'playing' as const },

      ],

    });

    for (const m of playing) {

      const heartbeatStale =

        !m.lastHeartbeatAt || m.lastHeartbeatAt.getTime() < staleBefore.getTime();

      const updatedStale = m.updatedAt.getTime() < staleBefore.getTime();

      if (heartbeatStale && updatedStale) {

        m.status = 'cancelled';

        await this.matchesRepo.save(m);

      }

    }

  }

  private async grantWinnerReward(
    matchId: string,
    existingManager?: EntityManager,
  ): Promise<void> {
    const grant = async (manager: EntityManager) => {
      const match = await manager.findOne(CasualMatch, {
        where: { id: matchId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!match || match.status !== 'finished') return;
      const state = { ...(match.state || {}) };
      if (state._rewardGranted === true) return;

      const winnerRole = Number(match.winner);
      const winnerId =
        winnerRole === 1 ? match.player1Id : winnerRole === 2 ? match.player2Id : null;
      if (!winnerId) {
        state._rewardGranted = true;
        state.rewardCoins = 0;
        match.state = state;
        await manager.save(match);
        return;
      }

      const key = `games.reward.${match.kind}.coins`;
      const row = await manager.findOne(AppSetting, { where: { key } });
      const configured = Number(row?.value);
      const globalRow = Number.isFinite(configured)
        ? null
        : await manager.findOne(AppSetting, {
            where: { key: 'games.win_reward_coins' },
          });
      const globalConfigured = Number(globalRow?.value);
      const fallback = match.kind === 'ludo' ? 120 : match.kind === 'domino' ? 80 : 60;
      const rewardCoins = Number.isFinite(configured)
        ? Math.max(1, Math.min(100_000, Math.floor(configured)))
        : Number.isFinite(globalConfigured)
          ? Math.max(1, Math.min(100_000, Math.floor(globalConfigured)))
          : fallback;

      let wallet = await manager.findOne(Wallet, {
        where: { userId: winnerId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!wallet) {
        wallet = await manager.save(manager.create(Wallet, { userId: winnerId }));
      }
      wallet.coins = Number(wallet.coins) + rewardCoins;
      await manager.save(wallet);
      await manager.save(
        manager.create(WalletTransaction, {
          userId: winnerId,
          type: TransactionType.LUCKY_REWARD,
          currency: CurrencyType.COINS,
          amount: rewardCoins,
          balanceAfter: Number(wallet.coins),
          referenceType: `casual_game_${match.kind}`,
          referenceId: match.id,
          description: `Winner reward for ${match.kind}`,
          metadata: { matchId: match.id, kind: match.kind },
        }),
      );

      state._rewardGranted = true;
      state.rewardCoins = rewardCoins;
      state.rewardWinnerId = winnerId;
      match.state = state;
      await manager.save(match);
      const room = match.roomId
        ? await manager.findOne(Room, { where: { id: match.roomId } })
        : null;
      for (const playerId of [match.player1Id, match.player2Id].filter(Boolean)) {
        void this.tasksService
          .recordProgress(playerId!, 'game', 1, {
            roomId: match.roomId || undefined,
            agencyId: room?.agencyId || undefined,
          })
          .catch(() => undefined);
      }
      void this.tasksService
        .recordProgress(winnerId, 'win', 1, {
          roomId: match.roomId || undefined,
          agencyId: room?.agencyId || undefined,
        })
        .catch(() => undefined);
    };
    if (existingManager) {
      await grant(existingManager);
    } else {
      await this.dataSource.transaction(grant);
    }
  }

  private mergePlayerSnapshot(
    current: Record<string, unknown> | null,
    incoming: Record<string, unknown>,
    actorRole: number,
    kind: CasualGameKind,
  ) {
    const previous = current || {};
    const merged: Record<string, unknown> = { ...previous, ...incoming };
    const ownIndex = actorRole - 1;
    const opponentIndex = actorRole === 1 ? 1 : 0;

    if (Array.isArray(previous.hands)) {
      const previousHands = [...previous.hands];
      const incomingHands = Array.isArray(incoming.hands) ? incoming.hands : [];
      const nextHands = [...previousHands];
      if (Array.isArray(incomingHands[ownIndex])) {
        nextHands[ownIndex] = incomingHands[ownIndex];
      }
      nextHands[opponentIndex] = previousHands[opponentIndex];
      merged.hands = nextHands;
    }
    if (
      Array.isArray(previous.boneyard) &&
      !Array.isArray(incoming.boneyard)
    ) {
      merged.boneyard = previous.boneyard;
    }
    if (kind === 'ludo' && Array.isArray(previous.tokens)) {
      const previousTokens = [...previous.tokens];
      const incomingTokens = Array.isArray(incoming.tokens)
        ? incoming.tokens
        : [];
      const nextTokens = [...previousTokens];
      if (Array.isArray(incomingTokens[ownIndex])) {
        nextTokens[ownIndex] = incomingTokens[ownIndex];
      }
      nextTokens[opponentIndex] = previousTokens[opponentIndex];
      merged.tokens = nextTokens;
    }
    return merged;
  }

  private validateWinnerState(match: CasualMatch, actorRole: number) {
    const state = match.state || {};
    const winner = Number(state.winner);
    const ageMs = Date.now() - match.createdAt.getTime();
    const version = Number(match.stateVersion || 0);
    if (winner !== 1 && winner !== 2 && winner !== 3) {
      throw new BadRequestException('Invalid game winner');
    }

    if (match.kind === 'ono') {
      const hands = Array.isArray(state.hands) ? state.hands : [];
      const hand = hands[actorRole - 1];
      if (
        winner !== actorRole ||
        !Array.isArray(hand) ||
        hand.length !== 0 ||
        version < 6 ||
        ageMs < 15_000
      ) {
        throw new BadRequestException('Unverified ONO result');
      }
      return;
    }

    if (match.kind === 'ludo') {
      const tokens = Array.isArray(state.tokens) ? state.tokens : [];
      const mine = tokens[actorRole - 1];
      const finished =
        Array.isArray(mine) &&
        mine.length === 4 &&
        mine.every((position) => Number(position) === 57);
      if (
        winner !== actorRole ||
        !finished ||
        version < 20 ||
        ageMs < 45_000
      ) {
        throw new BadRequestException('Unverified Ludo result');
      }
      return;
    }

    const hands = Array.isArray(state.hands) ? state.hands : [];
    const mine = hands[actorRole - 1];
    const normalWin =
      winner === actorRole && Array.isArray(mine) && mine.length === 0;
    const blockedDraw = winner === 3 && state.blocked === true;
    if ((!normalWin && !blockedDraw) || version < 6 || ageMs < 15_000) {
      throw new BadRequestException('Unverified Domino result');
    }
  }

  private async bossSettings(manager = this.matchesRepo.manager) {
    const keys = [
      'games.boss.maxHp',
      'games.boss.maxAttacks',
      'games.boss.minDamage',
      'games.boss.maxDamage',
      'games.boss.rewardCoins',
    ];
    const rows = await manager.find(AppSetting, { where: { key: In(keys) } });
    const values = new Map(rows.map((row) => [row.key, Number(row.value)]));
    const bounded = (key: string, fallback: number, min: number, max: number) => {
      const value = values.get(key);
      return Number.isFinite(value)
        ? Math.max(min, Math.min(max, Math.floor(value!)))
        : fallback;
    };
    const minDamage = bounded('games.boss.minDamage', 8, 1, 10_000);
    return {
      maxHp: bounded('games.boss.maxHp', 5000, 100, 10_000_000),
      maxAttacks: bounded('games.boss.maxAttacks', 20, 1, 1000),
      minDamage,
      maxDamage: Math.max(
        minDamage,
        bounded('games.boss.maxDamage', 18, minDamage, 20_000),
      ),
      rewardCoins: bounded('games.boss.rewardCoins', 50, 1, 200),
    };
  }

  private normalizeBossState(raw: string | undefined, maxHp: number) {
    const date = new Date().toISOString().slice(0, 10);
    try {
      const parsed = raw ? JSON.parse(raw) : null;
      if (parsed && parsed.date === date && Number.isFinite(Number(parsed.hp))) {
        return {
          date,
          hp: Math.max(0, Math.min(maxHp, Number(parsed.hp))),
          maxHp,
          attackers:
            parsed.attackers && typeof parsed.attackers === 'object'
              ? parsed.attackers
              : {},
          winnerId: parsed.winnerId || null,
          lastHitBy: parsed.lastHitBy || null,
          lastDamage: Number(parsed.lastDamage || 0),
        };
      }
    } catch {
      // Reset malformed or stale state.
    }
    return {
      date,
      hp: maxHp,
      maxHp,
      attackers: {} as Record<string, number>,
      winnerId: null as string | null,
      lastHitBy: null as string | null,
      lastDamage: 0,
    };
  }

  private bossDto(
    state: ReturnType<CasualMatchService['normalizeBossState']>,
    userId: string,
    settings: Awaited<ReturnType<CasualMatchService['bossSettings']>>,
  ) {
    const attacksUsed = Number(state.attackers[userId] || 0);
    return {
      date: state.date,
      hp: state.hp,
      maxHp: state.maxHp,
      defeated: state.hp <= 0,
      winnerId: state.winnerId,
      attacksUsed,
      attacksRemaining: Math.max(0, settings.maxAttacks - attacksUsed),
      maxAttacks: settings.maxAttacks,
      rewardCoins: settings.rewardCoins,
    };
  }



  private maskStateForViewer(

    state: Record<string, unknown> | null,

    viewerUserId: string,

    match: CasualMatch,

  ) {

    if (!state) return null;

    const copy: Record<string, unknown> = {
      ...state,
      version: match.stateVersion,
    };

    const role =

      match.player1Id === viewerUserId ? 1 : match.player2Id === viewerUserId ? 2 : 0;

    if (role && Array.isArray(copy.hands) && copy.hands.length >= 2) {

      const hands = [...(copy.hands as unknown[])];

      const hidden = role === 1 ? 1 : 0;

      hands[hidden] = Array.isArray(hands[hidden]) ? (hands[hidden] as unknown[]).length : 0;

      copy.hands = hands;

    }

    if (role && Array.isArray(copy.boneyard)) {

      copy.boneyardCount = (copy.boneyard as unknown[]).length;

      delete copy.boneyard;

    }

    copy.viewerRole = role;

    return copy;

  }



  private async toDto(match: CasualMatch, viewerUserId?: string) {

    const ids = [match.player1Id, match.player2Id].filter(Boolean) as string[];

    const users = ids.length

      ? await this.usersRepo.find({ where: { id: In(ids) } })

      : [];

    const map = new Map(users.map((u) => [u.id, u]));

    const slim = (id: string | null) => {

      if (!id) return null;

      const u = map.get(id);

      return {

        id,

        displayName: u?.displayName || u?.username || 'لاعب',

        avatarUrl: u?.avatarUrl || null,

      };

    };

    const state =

      viewerUserId != null

        ? this.maskStateForViewer(match.state, viewerUserId, match)

        : match.state;

    return {

      id: match.id,

      kind: match.kind,
      authority: 'client_synchronized',
      rewardsEnabled: false,

      status: match.status,

      turn: match.turn,

      state,

      stateVersion: match.stateVersion,

      winner: match.winner,

      player1Id: match.player1Id,

      player2Id: match.player2Id,

      player1: slim(match.player1Id),

      player2: slim(match.player2Id),

      createdAt: match.createdAt,

      updatedAt: match.updatedAt,

    };

  }



  private async notifyPlayers(match: CasualMatch, event: string) {

    try {
      if (!this.realtime) return;
      const playerIds = [match.player1Id, match.player2Id].filter(
        (id): id is string => !!id,
      );
      for (const playerId of playerIds) {
        const payload = await this.toDto(match, playerId);
        this.realtime.emitToUser(playerId, event, payload);
      }

    } catch {

      // optional

    }

  }

}

