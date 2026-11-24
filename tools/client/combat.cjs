// Combat behaviour probes for the isolated localhost server. Never connects to a public/production server.
// Each scenario imitates either a cheat (fixed-rate clicker, multi-target aura, auto totem, crystal aura) or a person
// doing the same thing slowly. The runner checks which Sentinel checks fired; this client only behaves.
//
// usage: node combat.cjs <port> <scenario> [version]
const mc = require('minecraft-protocol');
const port = Number(process.argv[2]);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('localhost port required');
const scenario = process.argv[3];
const version = process.argv[4] || '26.1';
const data = require('minecraft-data')(version);
const protocol = data.protocol.play.toServer.types;
const usesFlags = protocol.packet_position[1].some(field => field.name === 'flags');
const hasAttackPacket = Boolean(protocol.packet_attack);
const CRYSTAL = data.entitiesByName.end_crystal.id;
const VILLAGER = data.entitiesByName.villager.id;

const SCENARIOS = {
  // Fixed-rate clickers: 25 CPS (some ticks twice) and exactly every second tick (10 CPS).
  clicker25: {seconds: 8, setup: 'one', tick: t => (t % 4 === 0 ? 2 : 1)},
  clicker10: {seconds: 14, setup: 'one', tick: t => (t % 2 === 0 ? 1 : 0)},
  // A person at ~9 CPS with a normal 25 ms timing spread.
  human: {seconds: 16, setup: 'one', tick: humanClicks(110, 25)},
  // Aura: alternate between two targets on opposite sides every tick.
  aura: {seconds: 5, setup: 'two', tick: t => 1, target: t => t % 2},
  // A person fighting two targets: several hits on one, a smooth turn, several hits on the other.
  switcher: {seconds: 12, setup: 'two', tick: t => (t % 4 === 0 ? 1 : 0), target: t => Math.floor(t / 40) % 2, smooth: true},
  autototem: {seconds: 12, setup: 'totem', swapDelayMs: 0},
  manualtotem: {seconds: 12, setup: 'totem', swapDelayMs: 350},
  crystalaura: {seconds: 16, setup: 'crystal', breakDelayMs: 0},
  crystalhuman: {seconds: 16, setup: 'crystal', breakDelayMs: 260},
};
const plan = SCENARIOS[scenario];
if (!plan) throw new Error('unknown scenario ' + scenario + '; one of ' + Object.keys(SCENARIOS).join(', '));

function humanClicks(meanMs, sdMs) {
  let next = 0, now = 0;
  const gauss = () => Math.sqrt(-2 * Math.log(1 - Math.random())) * Math.cos(2 * Math.PI * Math.random());
  return () => {
    now += 50;
    let count = 0;
    while (next <= now) { count++; next += Math.max(30, meanMs + gauss() * sdMs); }
    return count;
  };
}

const username = ('P' + scenario).slice(0, 16);
const client = mc.createClient({host: '127.0.0.1', port, username, version, auth: 'offline'});
let position = null, myId = null, tick = 0, started = 0, finished = false, sequence = 1;
let yaw = 0, pitch = 10;
const entities = new Map();
const report = {scenario, attacks: 0, pops: 0, swaps: 0, crystalsBroken: 0, kicked: null};

function finish(error) {
  if (finished) return;
  finished = true;
  clearInterval(loop);
  client.end();
  if (error) { console.error(error.message || error); process.exitCode = 1; }
  console.log('REPORT ' + JSON.stringify(report));
}
let sentYaw = null, sentPitch = null;
function sendPose() {
  const flags = usesFlags ? {flags: {onGround: true, hasHorizontalCollision: false}} : {onGround: true};
  // Like vanilla: only include the rotation when it changed (a repeated identical look is itself a cheat sign).
  if (yaw !== sentYaw || pitch !== sentPitch) client.write('position_look', {...position, yaw, pitch, ...flags});
  else client.write('position', {...position, ...flags});
  sentYaw = yaw; sentPitch = pitch;
  if (protocol.packet_tick_end) client.write('tick_end', {});
}
function lookAt(x, y, z) {
  const dx = x - position.x, dy = y - (position.y + 1.62), dz = z - position.z;
  yaw = Math.atan2(dz, dx) * 180 / Math.PI - 90;
  pitch = -Math.atan2(dy, Math.hypot(dx, dz)) * 180 / Math.PI;
}
function attack(id) {
  if (hasAttackPacket) client.write('attack', {entityId: id});
  else client.write('use_entity', {target: id, mouse: 1, sneaking: false});
  client.write('arm_animation', {hand: 0});
  report.attacks++;
}
function villagers() {
  return [...entities.entries()].filter(([, e]) => e.type === VILLAGER).sort((a, b) => a[1].x - b[1].x);
}

client.on('error', finish);
client.on('end', () => finish());
client.on('kick_disconnect', p => { report.kicked = JSON.stringify(p.reason); });
client.on('disconnect', p => { report.kicked = JSON.stringify(p.reason); });
client.on('ping', packet => client.write('pong', {id: packet.id}));
client.on('login', packet => { myId = packet.entityId; });
client.on('spawn_entity', packet => {
  entities.set(packet.entityId, {type: packet.type, x: packet.x, y: packet.y, z: packet.z});
  if (packet.type === CRYSTAL && plan.setup === 'crystal' && started) {
    const hit = () => {
      if (finished || !entities.has(packet.entityId)) return;
      attack(packet.entityId);
      report.crystalsBroken++;
    };
    if (plan.breakDelayMs === 0) hit(); else setTimeout(hit, plan.breakDelayMs);
  }
});
client.on('entity_destroy', packet => { for (const id of packet.entityIds) entities.delete(id); });
client.on('update_health', packet => {
  if (packet.health <= 0) client.write('client_command', {actionId: 0}); // respawn
});
client.on('entity_status', packet => {
  if (packet.entityId !== myId || packet.entityStatus !== 35) return;
  report.pops++;
  const swap = () => {
    if (finished) return;
    client.write('block_dig', {status: 6, location: {x: 0, y: 0, z: 0}, face: 0, sequence: 0});
    report.swaps++;
    // Hold the next totem for the next pop.
    setTimeout(() => !finished && client.write('held_item_slot', {slotId: Math.min(8, report.swaps)}), 300);
  };
  if (plan.swapDelayMs === 0) swap(); else setTimeout(swap, plan.swapDelayMs);
});
client.on('position', packet => {
  const previous = position || {x: 0, y: 0, z: 0, yaw: 0, pitch: 0};
  position = {};
  for (const axis of ['x', 'y', 'z', 'yaw', 'pitch'])
    position[axis] = (packet.flags?.[axis] ? previous[axis] : 0) + packet[axis];
  yaw = position.yaw; pitch = position.pitch;
  if (packet.teleportId !== undefined) client.write('teleport_confirm', {teleportId: packet.teleportId});
  sentYaw = null;
  sendPose();
});

// The runner places the player and the targets, then writes "go" to stdin.
process.stdin.on('data', chunk => { if (String(chunk).includes('go') && !started) { started = Date.now(); tick = 0; } });
console.log('READY ' + username);

const loop = setInterval(() => {
  if (finished || !position) return;
  if (started && Date.now() - started > plan.seconds * 1000) return finish();
  if (started && plan.tick) {
    const targets = villagers();
    if (targets.length) {
      const index = plan.target ? Math.min(targets.length - 1, plan.target(tick)) : 0;
      const [id, e] = targets[index];
      if (plan.smooth) {
        const goalYaw = Math.atan2(e.z - position.z, e.x - position.x) * 180 / Math.PI - 90;
        let delta = ((goalYaw - yaw) % 360 + 540) % 360 - 180;
        yaw += Math.max(-15, Math.min(15, delta)); // 300 degrees per second at most
        if (Math.abs(delta) < 20) for (let i = plan.tick(tick); i > 0; i--) attack(id);
      } else {
        for (let i = plan.tick(tick); i > 0; i--) attack(id);
        lookAt(e.x, e.y + 1.0, e.z); // vanilla order: attack, then this tick's rotation
      }
    }
    tick++;
  }
  sendPose();
}, 50);
