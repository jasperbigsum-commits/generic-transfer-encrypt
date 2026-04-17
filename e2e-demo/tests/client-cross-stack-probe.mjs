import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath, pathToFileURL } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..', '..');
const baseUrl = process.argv[2] || 'http://localhost:8081';

function log(message) {
  process.stdout.write(`${message}\n`);
}

function createVanillaRuntime() {
  const md5Script = fs.readFileSync(
    path.join(repoRoot, 'vanilla-js-plugin', 'vendor', 'md5.js'),
    'utf8'
  );
  const transferScript = fs.readFileSync(
    path.join(repoRoot, 'vanilla-js-plugin', 'transfer-encrypt.js'),
    'utf8'
  );
  const smCryptoScript = fs.readFileSync(
    path.join(repoRoot, 'vanilla-js-plugin', 'vendor', 'sm-crypto.min.js'),
    'utf8'
  );

  const sandbox = {
    console,
    TextEncoder,
    TextDecoder,
    URLSearchParams,
    Headers,
    FormData,
    Blob,
    File,
    fetch,
    crypto,
    navigator: { userAgent: 'node-e2e-probe' },
    window: null,
    self: null
  };
  sandbox.window = sandbox;
  sandbox.self = sandbox;

  vm.runInNewContext(md5Script, sandbox, { filename: 'md5.js' });
  vm.runInNewContext(smCryptoScript, sandbox, { filename: 'sm-crypto.min.js' });
  vm.runInNewContext(transferScript, sandbox, { filename: 'transfer-encrypt.js' });
  sandbox.TransferEncryptRegisterSmCrypto(sandbox.smCrypto);
  return sandbox;
}

async function fetchText(url) {
  const response = await fetch(url);
  assert.equal(response.status, 200, `GET ${url} should return 200`);
  return response.text();
}

async function runStaticSmokeChecks() {
  log('Running static smoke checks...');
  const homepage = await fetchText(`${baseUrl}/`);
  const md5Script = await fetchText(`${baseUrl}/demo-assets/vanilla/vendor/md5.js`);
  const transferScript = await fetchText(`${baseUrl}/demo-assets/vanilla/transfer-encrypt.js`);
  const smCryptoScript = await fetchText(`${baseUrl}/demo-assets/vanilla/vendor/sm-crypto.min.js`);
  const layuiScript = await fetchText(`${baseUrl}/demo-assets/vanilla/vendor/layui/dist/layui.js`);
  const layuiCss = await fetchText(`${baseUrl}/demo-assets/vanilla/vendor/layui/dist/css/layui.css`);

  assert.match(homepage, /Transfer Encrypt End-to-End Demo/);
  assert.match(md5Script, /SparkMD5/);
  assert.match(transferScript, /TransferEncryptClient/);
  assert.match(smCryptoScript, /generateKeyPairHex/);
  assert.match(layuiScript, /layui/);
  assert.match(layuiCss, /layui/);
}

async function runPublicKeyChecks() {
  log('Running public key checks...');
  const response = await fetch(`${baseUrl}/demo/public-key`);
  assert.equal(response.status, 200, 'demo public key endpoint should return 200');
  const payload = await response.json();
  assert.match(payload.publicKey, /^04[0-9a-fA-F]{128}$/);
  return payload;
}

async function runVanillaRuntimeChecks(payload) {
  log('Running vanilla-js runtime checks...');
  const runtime = createVanillaRuntime();
  assert.equal(typeof runtime.TransferEncryptClient, 'function');
  assert.equal(typeof runtime.TransferEncryptCreateLayuiAdapter, 'function');

  const client = new runtime.TransferEncryptClient({
    baseUrl: payload.baseUrl,
    publicKey: payload.publicKey,
    smCrypto: runtime.smCrypto,
    fetchImpl: fetch
  });

  const sm4Key = '1234567890abcdef';
  const plaintext = JSON.stringify({ name: 'probe-json', from: 'node-probe' });
  const envelope = client.encryptPayload(plaintext, 'application/json', sm4Key);
  const decrypted = client.decryptEnvelope({
    encryptedData: envelope.encryptedData,
    contentMd5: envelope.contentMd5
  }, sm4Key);

  assert.equal(decrypted, plaintext);
}

async function runVue3RuntimeChecks(payload) {
  log('Running vue3 core checks...');
  const smCryptoScript = fs.readFileSync(
    path.join(repoRoot, 'vanilla-js-plugin', 'vendor', 'sm-crypto.min.js'),
    'utf8'
  );
  const smSandbox = {
    console,
    TextEncoder,
    TextDecoder,
    URLSearchParams,
    Headers,
    FormData,
    Blob,
    File,
    fetch,
    crypto,
    navigator: { userAgent: 'node-e2e-probe' },
    window: null,
    self: null
  };
  smSandbox.window = smSandbox;
  smSandbox.self = smSandbox;
  vm.runInNewContext(smCryptoScript, smSandbox, { filename: 'sm-crypto.min.js' });

  const moduleUrl = pathToFileURL(
    path.join(repoRoot, 'vue3-plugin', 'transfer-encrypt-vue3-core.js')
  ).href;
  const { createTransferEncryptClient } = await import(moduleUrl);
  const client = createTransferEncryptClient({
    baseUrl: payload.baseUrl,
    publicKey: payload.publicKey,
    smCrypto: smSandbox.smCrypto,
    fetchImpl: fetch,
    cryptoImpl: crypto
  });

  const envelope = client.encryptPayload(
    JSON.stringify({ name: 'vue3-probe' }),
    'application/json',
    '1234567890abcdef'
  );
  const decrypted = client.decryptEnvelope({
    encryptedData: envelope.encryptedData,
    contentMd5: envelope.contentMd5
  }, '1234567890abcdef');

  assert.equal(decrypted, JSON.stringify({ name: 'vue3-probe' }));
}

async function main() {
  log(`Using base URL: ${baseUrl}`);
  await runStaticSmokeChecks();
  const payload = await runPublicKeyChecks();
  await runVanillaRuntimeChecks(payload);
  await runVue3RuntimeChecks(payload);
  log('Cross-stack smoke probe passed.');
}

main().catch((error) => {
  console.error(error && error.stack ? error.stack : error);
  process.exit(1);
});
