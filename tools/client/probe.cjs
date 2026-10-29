// Offline localhost test clients. Never connects to a public/production server.
const mc = require('minecraft-protocol');
const assert = require('node:assert/strict');
const port = Number(process.argv[2]);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('localhost port required');

async function probe(version, blacklist) {
  await new Promise((resolve, reject) => {
    const name = (blacklist ? 'Blocked' : 'Clean') + version.replaceAll('.', '').slice(0, 8);
    const client = mc.createClient({host: '127.0.0.1', port, username: name, version, auth: 'offline'});
    let finished = false, joined = false, timer;
    const deadline = setTimeout(() => finish(new Error(`Client ${version} timed out`)), 20000);
    function finish(error) {
      if (finished) return;
      finished = true; clearTimeout(deadline); clearTimeout(timer);
      client.end();
      if (error) reject(error); else resolve();
    }
    client.on('error', error => finish(error));
    client.on('end', reason => { if (!finished) finish(new Error(`Unexpected disconnect: ${reason}`)); });
    const kicked = packet => {
      if (!blacklist) return finish(new Error('Clean client kicked: ' + JSON.stringify(packet)));
      const reason = JSON.stringify(packet);
      if (!reason.includes('baritone')) return finish(new Error('Unexpected kick reason: ' + reason));
      finish();
    };
    client.on('kick_disconnect', kicked);
    client.on('disconnect', kicked);
    client.on('ping', packet => client.write('pong', {id: packet.id}));
    client.on('position', packet => {
      if (packet.teleportId !== undefined) client.write('teleport_confirm', {teleportId: packet.teleportId});
    });
    client.on('packet', (packet, meta) => {
      if (joined || meta.state !== 'play' || meta.name !== 'login') return;
      joined = true;
      timer = setTimeout(() => {
        if (blacklist) client.write('custom_payload', {channel: 'minecraft:register', data: Buffer.from('baritone:channel')});
        else finish();
      }, 2500);
    });
  });
  console.log(`PASS: ${version} ${blacklist ? 'blacklisted channel kicked' : 'clean login and idle'}`);
}
(async () => {
  for (const version of (process.argv[3] ? process.argv[3].split(',') : ['1.21.11', '26.1'])) {
    assert(mc.supportedVersions.includes(version));
    await probe(version, false);
    await probe(version, true);
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
