import {
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { Room, RoomStatus } from '../../database/entities/room.entity';
import {
  RoomAccess,
  RoomAccessGrant,
} from '../../database/entities/room-access.entity';
import { RoomBan } from '../../database/entities/room-ban.entity';
import { User } from '../../database/entities/user.entity';

@Injectable()
export class RoomGameAccessService {
  constructor(
    @InjectRepository(Room) private readonly rooms: Repository<Room>,
    @InjectRepository(RoomAccess)
    private readonly access: Repository<RoomAccess>,
    @InjectRepository(RoomBan) private readonly bans: Repository<RoomBan>,
    @InjectRepository(User) private readonly users: Repository<User>,
  ) {}

  async assertParticipant(roomId: string | undefined, userId: string) {
    if (!roomId) return;
    const room = await this.rooms.findOne({ where: { id: roomId } });
    if (!room) throw new NotFoundException('Room not found');
    if (room.status === RoomStatus.CLOSED) {
      throw new ForbiddenException('Room is closed');
    }
    const ban = await this.bans.findOne({ where: { roomId, userId } });
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
    const grant = await this.access.findOne({ where: { roomId, userId } });
    const active =
      !!grant &&
      (grant.grantType === RoomAccessGrant.PERMANENT ||
        !grant.expiresAt ||
        grant.expiresAt > new Date());
    if (!active) {
      throw new ForbiddenException('Join the room before using room games');
    }
  }

  async playerIdentity(userId: string) {
    const user = await this.users.findOne({ where: { id: userId } });
    return {
      userId,
      displayName: user?.displayName || user?.username || 'لاعب',
      publicId: user?.publicId || null,
    };
  }
}
