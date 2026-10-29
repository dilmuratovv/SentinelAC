// Impossible movement probe for the isolated localhost server.
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('localhost port required');
const version = process.argv[3] || '26.1';
const protocol = require('minecraft-data')(version).protocol.play.toServer.types;
const usesFlags = protocol.packet_position_look[1].some(field => field.name === 'flags');
const client = mc.createClient({host: '127.0.0.1', port, username: 'MovementProbe', version, auth: 'offline'});
let position, attack = false, finished = false, ticks;
const deadline = setTimeout(() => finish(new Error('No movement correction within deadline')), 18000);
function finish(error) {
  if (finished) return;
  finished = true; clearTimeout(deadline); clearInterval(ticks); client.end();
  if (error) { console.error(error.message); process.exitCode = 1; }
  else console.log('PASS: ' + version + ' impossible movement received a server correction');
}
client.on('error', finish);
client.on('end', () => { if (!finished) finish(new Error('Disconnected before movement correction')); });
client.on('ping', packet => client.write('pong', {id: packet.id}));
client.on('position', packet => {
  if (packet.teleportId !== undefined) client.write('teleport_confirm', {teleportId: packet.teleportId});
  position = {x: packet.x, y: packet.y, z: packet.z};
  // The harness also requires a Simulation finding, since vanilla can teleport.
  if (attack) finish();
});
setTimeout(() => {
  if (finished) return;
  if (!position) return finish(new Error('Spawn position unavailable'));
  attack = true;
  ticks = setInterval(() => {
    position.y += 5;
    client.write('position_look', {...position, yaw: 0, pitch: 0,
      ...(usesFlags ? {flags: {onGround: false, hasHorizontalCollision: false}} : {onGround: false})});
    if (protocol.packet_tick_end) client.write('tick_end', {});
  }, 50);
}, 5000);
