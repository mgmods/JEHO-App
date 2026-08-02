import { BadRequestException, ForbiddenException, Injectable, Logger, NotFoundException } from '@nestjs/common';

import { InjectRepository } from '@nestjs/typeorm';

import { Repository } from 'typeorm';

import { randomBytes, createHmac } from 'crypto';

import { AppSetting } from '../../database/entities/app-setting.entity';

import { Room } from '../../database/entities/room.entity';

import { SlotGameSession } from '../../database/entities/slot-game-session.entity';

import { User } from '../../database/entities/user.entity';

import { UserProfile } from '../../database/entities/user-profile.entity';

import { Wallet } from '../../database/entities/wallet.entity';

import { RealtimeGateway } from '../realtime/realtime.gateway';

import { findMikooGame } from './mikoo-games.catalog';



export type SlotSessionDto = {

  sessionId: string;

  gameId: string;

  bridge: string;

  code: string;

  userId: string;

  roomId: string;

  appId: number;

  gsp: number;

  appChannel: string;

  language: string;

  gameMode: string;

  currencyIcon: string;

  gameType: number;

  containerUrl: string;

  /** BaiShun HTTP game_route base (DOMAIN). */
  routeUrl: string;

  hashGameConfig: string;

  /** Live coin balance for this user (wallet.coins). */
  balance: number;

  displayName: string;

  avatarUrl: string;

};



@Injectable()

export class SlotGamesService {

  private readonly logger = new Logger(SlotGamesService.name);

  constructor(

    @InjectRepository(SlotGameSession)

    private readonly sessions: Repository<SlotGameSession>,

    @InjectRepository(AppSetting)

    private readonly settings: Repository<AppSetting>,

    @InjectRepository(User)

    private readonly users: Repository<User>,

    @InjectRepository(UserProfile)

    private readonly profiles: Repository<UserProfile>,

    @InjectRepository(Wallet)

    private readonly wallets: Repository<Wallet>,

    @InjectRepository(Room)

    private readonly rooms: Repository<Room>,

    private readonly realtime: RealtimeGateway,

  ) {}



  private async readSetting(key: string, fallback: string) {

    const row = await this.settings.findOne({ where: { key } });

    const v = row?.value?.trim();

    return v || fallback;

  }



  private async bsConfig() {

    const appId = Number(await this.readSetting('games.bsgame.app_id', '9786548770'));

    const gsp = Number(await this.readSetting('games.bsgame.gsp', '101'));

    const appChannel = await this.readSetting('games.bsgame.app_channel', 'jehochat');

    const secret = await this.readSetting('games.bsgame.code_secret', 'jeho-slot-local');

    const origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr';

    const defaultWs = `${this.toWsBase(origin)}/games/ws`;

    const wsBase = await this.readSetting('games.bsgame.ws_base', defaultWs);

    return {

      appId: Number.isFinite(appId) ? Math.floor(appId) : 9786548770,

      gsp: Number.isFinite(gsp) ? Math.floor(gsp) : 101,

      appChannel,

      secret,

      wsBase: this.toWsBase(wsBase).replace(/\/$/, ''),

    };

  }



  private toWsBase(url: string) {

    return url

      .trim()

      .replace(/\/$/, '')

      .replace(/^https:\/\//i, 'wss://')

      .replace(/^http:\/\//i, 'ws://');

  }



  private signCode(payload: string, secret: string) {

    return createHmac('sha256', secret).update(payload).digest('hex').slice(0, 32);

  }



  private async resolveLanguage(userId: string) {

    const profile = await this.profiles.findOne({ where: { userId } });

    const lang = profile?.language?.trim().toLowerCase() || 'ar';

    if (lang.startsWith('ar')) return '2';

    if (lang.startsWith('en')) return '1';

    return '2';

  }



  private async resolvePublicUserId(userId: string) {

    const user = await this.users.findOne({ where: { id: userId } });

    if (!user) throw new BadRequestException('User not found');

    const publicId = user.publicId?.trim();

    if (!publicId || !/^\d+$/.test(publicId)) {

      throw new BadRequestException('User publicId is required for Mikoo games');

    }

    return { publicId, user };

  }



  private isUuid(v?: string | null) {
    if (!v) return false;
    return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
      v.trim(),
    );
  }

  /** Never expose internal UUID as in-game nickname. */
  private sanitizeDisplayName(user?: Pick<User, 'displayName' | 'username'> | null) {
    const raw = (user?.displayName || user?.username || 'Player').trim();
    if (!raw || this.isUuid(raw) || /^[0-9a-f-]{20,}$/i.test(raw)) {
      const u = (user?.username || '').trim();
      if (u && !this.isUuid(u) && !/^[0-9a-f-]{20,}$/i.test(u)) return u;
      return 'Player';
    }
    return raw;
  }

  private async resolveNumericRoomId(roomId?: string | null) {
    if (!roomId || !this.isUuid(roomId)) return '0';

    const room = await this.rooms.findOne({
      where: { id: roomId },
      relations: ['host'],
    });

    if (!room?.host?.publicId) return '0';

    const hostPublicId = room.host.publicId.trim();

    return /^\d+$/.test(hostPublicId) ? hostPublicId : '0';
  }



  private buildContainerUrl(wsBase: string, game: ReturnType<typeof findMikooGame>) {

    if (!game) return wsBase;

    const path = game.wsPath || game.id;

    return `${wsBase}/${path}`;

  }



  private buildHashGameConfig(

    game: NonNullable<ReturnType<typeof findMikooGame>>,

    containerUrl: string,

    currencyIcon: string,

    balance: number,

    identity?: { userId: number | string; nickname: string; avatarUrl: string },

  ) {

    return JSON.stringify({

      gameType: game.gameType ?? 0,

      goldUrl: currencyIcon,

      containerUrl,

      isProd: true,

      balance,

      userMoney: balance,

      coin: balance,

      userId: Number(identity?.userId) || 0,

      openId: String(identity?.userId ?? 0),

      nickname: identity?.nickname || 'Player',

      nickName: identity?.nickname || 'Player',

      name: identity?.nickname || 'Player',

      avatar: identity?.avatarUrl || '',

      headImg: identity?.avatarUrl || '',

    });

  }



  async startSession(userId: string, gameId: string, roomId?: string | null) {

    const game = findMikooGame(gameId);

    if (!game) throw new BadRequestException('Unknown slot game');



    const { publicId, user } = await this.resolvePublicUserId(userId);

    const cfg = await this.bsConfig();

    const language = await this.resolveLanguage(userId);

    const voiceRoomId = this.isUuid(roomId) ? roomId!.trim() : null;
    const numericRoomId = await this.resolveNumericRoomId(voiceRoomId);

    const nonce = randomBytes(8).toString('hex');

    const codePayload = `${publicId}:${game.id}:${Date.now()}:${nonce}`;

    const code = `${this.signCode(codePayload, cfg.secret)}${nonce}`;

    const origin = process.env.PUBLIC_API_ORIGIN || 'https://api.adnova.bbs.tr';

    const currencyIcon = `${origin}/assets/pack/ic_pass_card_coin.png`;

    const containerUrl = this.buildContainerUrl(cfg.wsBase, game);
    const routeUrl = `${origin}/games/route/`;

    let wallet = await this.wallets.findOne({ where: { userId } });

    if (!wallet) {

      wallet = await this.wallets.save(this.wallets.create({ userId }));

    }

    const balance = Number(wallet.coins || 0);
    const displayName = this.sanitizeDisplayName(user);

    const hashGameConfig = this.buildHashGameConfig(game, containerUrl, currencyIcon, balance, {
      userId: publicId,
      nickname: displayName,
      avatarUrl: user.avatarUrl || '',
    });



    const session = this.sessions.create({

      userId,

      gameId: game.id,

      roomId: voiceRoomId,

      code,

      status: 'active',

      lastHeartbeatAt: new Date(),

    });

    await this.sessions.save(session);



    this.logger.log(
      `GAME_PROBE SESSION_START user=${userId} game=${game.id} sid=${session.id} bal=${balance} room=${voiceRoomId || '-'} bridge=${game.bridge}`,
    );

    if (voiceRoomId) {

      this.realtime.emitToRoom(voiceRoomId, 'room:slot_active', {
        roomId: voiceRoomId,
        userId,
        displayName,
        avatarUrl: user.avatarUrl || '',
        gameId: game.id,
        gameTitle: game.title,
      });

    }



    return {

      sessionId: session.id,

      gameId: game.id,

      bridge: game.bridge,

      code,

      userId: publicId,

      roomId: numericRoomId,

      appId: cfg.appId,

      gsp: cfg.gsp,

      appChannel: cfg.appChannel,

      language,

      gameMode: game.id === 'fishing' ? '3' : '2',

      currencyIcon,

      gameType: game.gameType ?? 0,

      containerUrl,

      routeUrl,

      hashGameConfig,

      balance,

      displayName,

      avatarUrl: user.avatarUrl || '',

    } satisfies SlotSessionDto;

  }



  /** Mikoo-compatible alias used by extracted clients. */

  async getCode(userId: string, gameId: string, roomId?: string | null) {

    const session = await this.startSession(userId, gameId, roomId);

    return { code: session.code, sessionId: session.sessionId };

  }



  async heartbeat(sessionId: string, userId: string) {

    const session = await this.sessions.findOne({ where: { id: sessionId, userId } });

    if (!session || session.status !== 'active') throw new NotFoundException('Session not found');

    session.lastHeartbeatAt = new Date();

    await this.sessions.save(session);

    return { ok: true };

  }



  async endSession(sessionId: string, userId: string, _stats?: { betCoins?: number; winCoins?: number; spins?: number }) {
    const session = await this.sessions.findOne({ where: { id: sessionId, userId } });
    if (!session) return { ok: true };
    session.status = 'ended';
    session.endedAt = new Date();
    // Ignore client-reported stats — ledger comes from server WS only.
    await this.sessions.save(session);

    if (session.roomId) {
      this.realtime.emitToRoom(session.roomId, 'room:slot_ended', {
        roomId: session.roomId,
        userId,
        gameId: session.gameId,
        totalWinCoins: session.totalWinCoins,
      });
    }
    return { ok: true };
  }



  async recordWin(sessionId: string, userId: string, _winCoins: number) {
    // SECURITY: client-reported wins must NEVER credit wallets.
    // Real settlements happen only inside Mikoo/BaiShun WebSocket handlers.
    void sessionId;
    void userId;
    throw new ForbiddenException('Client win reports are disabled');
  }

  async activeInRoom(roomId: string) {

    const since = new Date(Date.now() - 90_000);

    const rows = await this.sessions

      .createQueryBuilder('s')

      .where('s.roomId = :roomId', { roomId })

      .andWhere('s.status = :status', { status: 'active' })

      .andWhere('s.lastHeartbeatAt >= :since', { since })

      .orderBy('s.lastHeartbeatAt', 'DESC')

      .getMany();



    const out: Array<{

      sessionId: string;

      userId: string;

      displayName: string;

      avatarUrl: string;

      gameId: string;

      totalWinCoins: number;

      spinCount: number;

      since: Date;

    }> = [];

    for (const row of rows) {

      const user = await this.users.findOne({ where: { id: row.userId } });

      out.push({

        sessionId: row.id,

        userId: row.userId,

        displayName: this.sanitizeDisplayName(user),

        avatarUrl: user?.avatarUrl || '',

        gameId: row.gameId,

        totalWinCoins: row.totalWinCoins,

        spinCount: row.spinCount,

        since: row.createdAt,

      });

    }

    return out;

  }



  async roomLeaderboard(roomId: string, limit = 20) {

    const rows = await this.sessions

      .createQueryBuilder('s')

      .select('s.userId', 'userId')

      .addSelect('SUM(s.totalWinCoins)', 'wins')

      .addSelect('SUM(s.spinCount)', 'spins')

      .where('s.roomId = :roomId', { roomId })

      .groupBy('s.userId')

      .orderBy('wins', 'DESC')

      .limit(Math.min(50, Math.max(1, limit)))

      .getRawMany<{ userId: string; wins: string; spins: string }>();



    const out: Array<{

      userId: string;

      displayName: string;

      avatarUrl: string;

      totalWinCoins: number;

      spinCount: number;

    }> = [];

    for (const row of rows) {

      const user = await this.users.findOne({ where: { id: row.userId } });

      out.push({

        userId: row.userId,

        displayName: this.sanitizeDisplayName(user),

        avatarUrl: user?.avatarUrl || '',

        totalWinCoins: Number(row.wins) || 0,

        spinCount: Number(row.spins) || 0,

      });

    }

    return out;

  }



  buildNativeConfig(session: SlotSessionDto) {

    return {

      appChannel: session.appChannel,

      appId: session.appId,

      userId: session.userId,

      code: session.code,

      roomId: session.roomId || '',

      gameMode: session.gameMode,

      language: session.language,

      gsp: session.gsp,

      gameConfig: {

        sceneMode: 0,

        currencyIcon: session.currencyIcon,

      },

    };

  }

}


