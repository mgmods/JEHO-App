import {
  Injectable,
  NotFoundException,
  BadRequestException,
  ForbiddenException,
  Optional,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { In, Repository } from 'typeorm';
import {
  RoomGame,
  RoomGameStatus,
  RoomGameType,
  RoomGameTurn,
  RoomGameWinner,
} from '../../database/entities/room-game.entity';
import { Room, RoomStatus } from '../../database/entities/room.entity';
import { User } from '../../database/entities/user.entity';
import { RoomModerator } from '../../database/entities/room-moderator.entity';
import {
  RoomAccess,
  RoomAccessGrant,
} from '../../database/entities/room-access.entity';
import { RoomBan } from '../../database/entities/room-ban.entity';
import { RealtimeGateway } from '../realtime/realtime.gateway';
import { StartGameDto, GameMoveDto } from './dto/games.dto';

const WIN_LINES: number[][] = [
  [0, 1, 2],
  [3, 4, 5],
  [6, 7, 8],
  [0, 3, 6],
  [1, 4, 7],
  [2, 5, 8],
  [0, 4, 8],
  [2, 4, 6],
];

const EMPTY_BOARD = (): number[] => [0, 0, 0, 0, 0, 0, 0, 0, 0];

@Injectable()
export class GamesService {
  constructor(
    @InjectRepository(RoomGame)
    private readonly gamesRepo: Repository<RoomGame>,
    @InjectRepository(Room)
    private readonly roomsRepo: Repository<Room>,
    @InjectRepository(User)
    private readonly usersRepo: Repository<User>,
    @InjectRepository(RoomModerator)
    private readonly moderatorsRepo: Repository<RoomModerator>,
    @InjectRepository(RoomAccess)
    private readonly roomAccessRepo: Repository<RoomAccess>,
    @InjectRepository(RoomBan)
    private readonly roomBansRepo: Repository<RoomBan>,
    @Optional() private readonly realtime?: RealtimeGateway,
  ) {}

  async start(_roomId: string, _userId: string, _dto: StartGameDto) {
    // XO / tic-tac-toe retired from product.
    throw new BadRequestException('لعبة XO لم تعد متاحة');
  }

  async join(roomId: string, gameId: string, userId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertRoomParticipant(room, userId);
    const saved = await this.gamesRepo.manager.transaction(async (manager) => {
      const game = await manager.findOne(RoomGame, {
        where: { id: gameId, roomId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!game) throw new NotFoundException('Game not found');
      if (game.status !== RoomGameStatus.WAITING) {
        throw new BadRequestException('Game is not waiting for a player');
      }
      if (game.playerXId === userId) {
        throw new BadRequestException('You are already player X');
      }
      if (game.playerOId && game.playerOId !== userId) {
        throw new ForbiddenException('This challenge is for another player');
      }
      if (!game.playerOId) game.playerOId = userId;
      game.status = RoomGameStatus.PLAYING;
      game.turn = 'X';
      return manager.save(RoomGame, game);
    });
    await this.emitUpdate(saved, 'accepted');
    return this.toPublic(saved);
  }

  /** Opponent rejects a direct challenge */
  async reject(roomId: string, gameId: string, userId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertRoomParticipant(room, userId);
    const game = await this.findGameInRoom(roomId, gameId);
    if (game.status !== RoomGameStatus.WAITING) {
      throw new BadRequestException('Game is not waiting');
    }
    if (game.playerOId !== userId) {
      throw new ForbiddenException('Only the invited player can reject');
    }
    game.status = RoomGameStatus.CANCELLED;
    const saved = await this.gamesRepo.save(game);
    await this.emitUpdate(saved, 'rejected');
    return this.toPublic(saved);
  }

  async move(roomId: string, gameId: string, userId: string, dto: GameMoveDto) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertRoomParticipant(room, userId);
    const saved = await this.gamesRepo.manager.transaction(async (manager) => {
      const game = await manager.findOne(RoomGame, {
        where: { id: gameId, roomId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!game) throw new NotFoundException('Game not found');
      if (game.status !== RoomGameStatus.PLAYING) {
        throw new BadRequestException('Game is not in progress');
      }
      const mark = this.playerMark(game, userId);
      if (!mark) throw new ForbiddenException('You are not a player in this game');
      if (game.turn !== mark) throw new BadRequestException('Not your turn');
      const cell = dto.cell;
      const board = [...(game.board || EMPTY_BOARD())];
      if (cell < 0 || cell > 8) {
        throw new BadRequestException('Cell must be between 0 and 8');
      }
      if (board[cell] !== 0) {
        throw new BadRequestException('Cell is already taken');
      }
      board[cell] = mark === 'X' ? 1 : 2;
      game.board = board;
      const winner = this.detectWinner(board);
      if (winner) {
        game.winner = winner;
        game.status = RoomGameStatus.FINISHED;
      } else {
        game.turn = mark === 'X' ? 'O' : 'X';
      }
      return manager.save(RoomGame, game);
    });
    await this.emitUpdate(saved, 'move');
    return this.toPublic(saved);
  }

  async get(roomId: string, gameId: string, userId: string) {
    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    await this.assertRoomParticipant(room, userId);
    return this.toPublic(await this.findGameInRoom(roomId, gameId));
  }

  async cancel(roomId: string, gameId: string, userId: string) {
    const game = await this.findGameInRoom(roomId, gameId);

    if (
      game.status === RoomGameStatus.FINISHED ||
      game.status === RoomGameStatus.CANCELLED
    ) {
      throw new BadRequestException('Game is already ended');
    }

    const room = await this.roomsRepo.findOne({ where: { id: roomId } });
    const isPlayer = game.playerXId === userId || game.playerOId === userId;
    if (!room) throw new NotFoundException('Room not found');
    await this.assertRoomParticipant(room, userId);
    const isHost =
      room.hostId === userId ||
      room.activeHostId === userId ||
      room.cohostId === userId;
    const moderator = await this.moderatorsRepo.findOne({ where: { roomId, userId } });
    const canControlGames = !!moderator?.canControlGames;
    if (!isPlayer && !isHost && !canControlGames) {
      throw new ForbiddenException('Only players or authorized room staff can cancel');
    }

    game.status = RoomGameStatus.CANCELLED;
    const saved = await this.gamesRepo.save(game);
    await this.emitUpdate(saved, 'cancelled');
    return this.toPublic(saved);
  }

  private async findGameInRoom(roomId: string, gameId: string): Promise<RoomGame> {
    const game = await this.gamesRepo.findOne({ where: { id: gameId, roomId } });
    if (!game) throw new NotFoundException('Game not found');
    return game;
  }

  private async assertRoomParticipant(room: Room, userId: string) {
    if (room.status === RoomStatus.CLOSED) {
      throw new ForbiddenException('Room is closed');
    }
    const ban = await this.roomBansRepo.findOne({
      where: { roomId: room.id, userId },
    });
    if (ban && (!ban.expiresAt || ban.expiresAt > new Date())) {
      throw new ForbiddenException('User is banned from this room');
    }
    if (
      room.hostId === userId ||
      room.activeHostId === userId ||
      room.cohostId === userId
    ) {
      return;
    }
    const access = await this.roomAccessRepo.findOne({
      where: { roomId: room.id, userId },
    });
    if (
      !access ||
      (access.grantType === RoomAccessGrant.SESSION &&
        !!access.expiresAt &&
        access.expiresAt <= new Date())
    ) {
      throw new ForbiddenException('Join the room before using room games');
    }
  }

  private playerMark(game: RoomGame, userId: string): RoomGameTurn | null {
    if (game.playerXId === userId) return 'X';
    if (game.playerOId === userId) return 'O';
    return null;
  }

  private detectWinner(board: number[]): RoomGameWinner {
    for (const [a, b, c] of WIN_LINES) {
      const v = board[a];
      if (v !== 0 && v === board[b] && v === board[c]) {
        return v === 1 ? 'X' : 'O';
      }
    }
    if (board.every((cell) => cell !== 0)) {
      return 'draw';
    }
    return null;
  }

  private async toPublic(game: RoomGame) {
    const ids = [game.playerXId, game.playerOId].filter(Boolean) as string[];
    const users = ids.length
      ? await this.usersRepo.find({ where: { id: In(ids) } })
      : [];
    const nameOf = (id: string | null) => {
      if (!id) return null;
      const u = users.find((x) => x.id === id);
      return u?.displayName || u?.username || null;
    };
    return {
      id: game.id,
      roomId: game.roomId,
      type: game.type,
      status: game.status,
      playerXId: game.playerXId,
      playerOId: game.playerOId,
      playerXName: nameOf(game.playerXId),
      playerOName: nameOf(game.playerOId),
      board: game.board,
      turn: game.turn,
      winner: game.winner,
      createdAt: game.createdAt,
      updatedAt: game.updatedAt,
    };
  }

  /** Emit via room:event so Android RoomListener receives it in realtime. */
  private async emitUpdate(game: RoomGame, action: string) {
    const publicGame = await this.toPublic(game);
    const payload = { ...publicGame, action };
    try {
      this.realtime?.emitToRoom(game.roomId, 'room:event', {
        roomId: game.roomId,
        event: 'game:update',
        payload,
        at: new Date().toISOString(),
      });
      // Personal ping for invitee (accept/reject UI even if room socket lag)
      if (action === 'invite' && game.playerOId) {
        this.realtime?.emitToUser(game.playerOId, 'game:invite', payload);
      }
      if (game.playerXId) {
        this.realtime?.emitToUser(game.playerXId, 'game:update', payload);
      }
      if (game.playerOId) {
        this.realtime?.emitToUser(game.playerOId, 'game:update', payload);
      }
    } catch {
      // Realtime optional
    }
  }
}
