/**
 * Quick self-check for Packer + swimsuit protobuf encode/decode.
 * Run: npx ts-node -T scripts/check-swimsuit-packer.ts  (from backend) 
 * or: node -e after compile
 */
import { packHeartbeat, packMessage, unpackMessage } from '../src/modules/games/mikoo-gateway/baishun-packer.util';
import {
  SwimsuitRoute,
  decodeEnterGameReq,
  encodeEnterGameRes,
  encodeChannelCfg,
  encodePlayerInfo,
  encodeLastSpinBoard,
  encodeClientMsg,
  decodeClientMsg,
  encodeHiloUserInfoRes,
} from '../src/modules/games/mikoo-gateway/swimsuit-pb.util';
import { encodeMessage, pbInt32, pbString } from '../src/modules/games/mikoo-gateway/mikoo-proto.util';

function assert(cond: boolean, msg: string) {
  if (!cond) throw new Error(msg);
}

const body = encodeEnterGameRes({
  code: 0,
  playerInfo: encodePlayerInfo({
    playerId: 42,
    userName: '42',
    nickName: 'Test',
    headImg: '',
    coin: 1000,
    chip: 100,
    chipIdx: 0,
  }),
  channelCfg: encodeChannelCfg(),
  lastSpin: encodeLastSpinBoard(),
});
const frame = packMessage(SwimsuitRoute.PLAYER_ENTER_GAME, 1, body);
const unpacked = unpackMessage(frame);
assert(!!unpacked && !unpacked!.isHeartbeat, 'packer message');
assert(unpacked!.route === 100, 'route 100');
assert(unpacked!.seq === 1, 'seq 1');

const hb = packHeartbeat(3, 123456);
const hbU = unpackMessage(hb);
assert(!!hbU && hbU!.isHeartbeat, 'heartbeat');
assert(hbU!.seq === 3, 'hb seq');

const req = encodeMessage([
  pbString(4, '99'),
  pbString(8, 'abc'),
  pbInt32(3, 1183),
]);
const d = decodeEnterGameReq(req);
assert(d.userName === '99' && d.code === 'abc' && d.gameId === 1183, 'enter decode');

const cm = encodeClientMsg(504, encodeHiloUserInfoRes({
  userId: '1',
  nickname: 'n',
  avatar: '',
  balance: 50,
}));
const cd = decodeClientMsg(cm);
assert(cd.msgId === 504 && cd.body.length > 0, 'clientmsg');

console.log('swimsuit packer checks OK');
