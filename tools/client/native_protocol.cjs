// Resolve an exact wire-protocol match, including patch versions sharing a codec.
const mc = require('minecraft-protocol');
const data = require('minecraft-data');
const port = Number(process.argv[2]);
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error('localhost port required');
(async () => {
  const status = await mc.ping({host: '127.0.0.1', port, closeTimeout: 8000, noPongTimeout: 1000});
  const protocol = status.version.protocol;
  const client = mc.supportedVersions.findLast(version => data(version).version.version === protocol) || null;
  console.log(JSON.stringify({protocol, client}));
})().catch(error => { console.error(error.message); process.exitCode = 1; });
