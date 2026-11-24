// A player that stands still (and respawns) so cheat modules that only target players have someone to hit.
// Localhost test servers only. usage: node target.cjs <port> <name> [version]
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('localhost port required');
const name = process.argv[3] || 'Target';
const version = process.argv[4] || '1.21.11';
const protocol = require('minecraft-data')(version).protocol.play.toServer.types;
const usesFlags = protocol.packet_position[1].some(field => field.name === 'flags');
const client = mc.createClient({host: '127.0.0.1', port, username: name, version, auth: 'offline'});
let position = null, strafe = false, phase = 0;
process.stdin.on('data', d => { const t = String(d); if (t.includes('strafe on')) strafe = true; if (t.includes('strafe off')) strafe = false; });
client.on('error', e => { console.error(e.message); process.exit(1); });
client.on('end', () => process.exit(0));
client.on('ping', p => client.write('pong', {id: p.id}));
client.on('update_health', p => { if (p.health <= 0) setTimeout(() => client.write('client_command', {actionId: 0}), 500); });
client.on('position', packet => {
  const previous = position || {x: 0, y: 0, z: 0, yaw: 0, pitch: 0};
  position = {};
  for (const axis of ['x', 'y', 'z', 'yaw', 'pitch'])
    position[axis] = (packet.flags?.[axis] ? previous[axis] : 0) + packet[axis];
  if (packet.teleportId !== undefined) client.write('teleport_confirm', {teleportId: packet.teleportId});
  client.write('position_look', {...position, ...(usesFlags ? {flags: {onGround: true, hasHorizontalCollision: false}} : {onGround: true})});
});
setInterval(() => {
  if (!position) return;
  if (strafe) { phase += 1; position.x += 0.11 * Math.sign(Math.sin(phase * Math.PI / 30)); } // ~2.2 b/s side to side
  client.write('position', {x: position.x, y: position.y, z: position.z,
    ...(usesFlags ? {flags: {onGround: true, hasHorizontalCollision: false}} : {onGround: true})});
  if (protocol.packet_tick_end) client.write('tick_end', {});
}, 50);
