// Impossible movement probe for the isolated localhost server.
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('localhost port required');
const version = process.argv[3] || '26.1';
const protocol = require('minecraft-data')(version).protocol.play.toServer.types;
const usesFlags = protocol.packet_position[1].some(field => field.name === 'flags');
const client = mc.createClient({host: '127.0.0.1', port, username: 'MovementProbe', version, auth: 'offline'});
let position, started, attack = false, finished = false, corrections = 0;
const deadline = setTimeout(() => finish(new Error('No movement corrections within deadline')), 25000);
function finish(error) {
  if (finished) return;
  finished = true; clearTimeout(deadline); clearInterval(ticks); client.end();
  if (error) { console.error(error.message); process.exitCode = 1; }
  else console.log('PASS: ' + version + ' impossible movement received ' + corrections + ' server corrections');
}
function sendPose(ground, rotation = false) {
  client.write(rotation ? 'position_look' : 'position', {
    ...position,
    ...(usesFlags ? {flags: {onGround: ground, hasHorizontalCollision: false}} : {onGround: ground})
  });
  if (protocol.packet_tick_end) client.write('tick_end', {});
}
client.on('error', finish);
client.on('end', () => { if (!finished) finish(new Error('Disconnected before movement corrections')); });
client.on('ping', packet => client.write('pong', {id: packet.id}));
client.on('position', packet => {
  const previous = position || {x: 0, y: 0, z: 0, yaw: 0, pitch: 0};
  position = {};
  for (const axis of ['x','y','z','yaw','pitch'])
    position[axis] = (packet.flags?.[axis] ? previous[axis] : 0) + packet[axis];
  if (packet.teleportId !== undefined) client.write('teleport_confirm', {teleportId: packet.teleportId});
  // Vanilla also returns the exact teleport pose. An ID alone is not enough
  // for the engine to mark the spawn/setback teleport as accepted.
  sendPose(false, true);
  if (!started) started = Date.now();
  if (attack && ++corrections >= 3) finish();
});
const ticks = setInterval(() => {
  if (finished || !position) return;
  if (Date.now() - started < 2000) {
    sendPose(true);
    return;
  }
  attack = true;
  position.y += 5;
  sendPose(false);
}, 50);
