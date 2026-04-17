import assert from 'node:assert/strict';

import {
  TransferEncryptClient,
  createTransferEncryptClient,
  createTransferEncryptVueAdapter,
  md5ArrayBuffer
} from '../transfer-encrypt-vue3-core.js';

const TEST_PUBLIC_KEY = '1'.repeat(128);

function decodeTransferPayload(payload) {
  return JSON.parse(Buffer.from(String(payload), 'base64url').toString('utf8'));
}

class BlobPolyfill {
  constructor(parts = [], options = {}) {
    const buffers = parts.map((part) => {
      if (part instanceof Uint8Array) {
        return Buffer.from(part);
      }
      if (Buffer.isBuffer(part)) {
        return part;
      }
      return Buffer.from(String(part));
    });
    this._buffer = Buffer.concat(buffers);
    this.type = options.type || '';
    this.size = this._buffer.length;
  }

  async arrayBuffer() {
    return this._buffer.buffer.slice(
      this._buffer.byteOffset,
      this._buffer.byteOffset + this._buffer.byteLength
    );
  }
}

class FilePolyfill extends BlobPolyfill {
  constructor(parts, name, options) {
    super(parts, options);
    this.name = name;
  }
}

class FormDataPolyfill {
  constructor() {
    this._entries = [];
  }

  append(name, value) {
    this._entries.push([name, value]);
  }

  entries() {
    return this._entries[Symbol.iterator]();
  }

  get(name) {
    const item = this._entries.find((entry) => entry[0] === name);
    return item ? item[1] : null;
  }

  [Symbol.iterator]() {
    return this.entries();
  }
}

class HeadersPolyfill {
  constructor(source = {}) {
    this._map = {};
    Object.keys(source).forEach((key) => {
      this.set(key, source[key]);
    });
  }

  set(key, value) {
    this._map[String(key).toLowerCase()] = String(value);
  }

  forEach(callback) {
    Object.keys(this._map).forEach((key) => {
      callback(this._map[key], key);
    });
  }
}

function sm4Encrypt(text, key) {
  return Buffer.from(JSON.stringify({ text, key }), 'utf8').toString('base64');
}

function sm4Decrypt(cipherText) {
  return JSON.parse(Buffer.from(cipherText, 'base64').toString('utf8')).text;
}

function createSmCrypto() {
  return {
    sm2: {
      doEncrypt(text, publicKey, cipherMode) {
        return `sm2:${publicKey}:${cipherMode}:${text}`;
      }
    },
    sm4: {
      encrypt(text, key) {
        return sm4Encrypt(text, key);
      },
      decrypt(cipherText) {
        return sm4Decrypt(cipherText);
      }
    }
  };
}

globalThis.FormData = FormDataPolyfill;
globalThis.Blob = BlobPolyfill;
globalThis.File = FilePolyfill;
globalThis.Headers = HeadersPolyfill;
globalThis.TextEncoder = TextEncoder;
globalThis.TextDecoder = TextDecoder;
globalThis.btoa = (value) => Buffer.from(value, 'binary').toString('base64');
globalThis.atob = (value) => Buffer.from(value, 'base64').toString('binary');

async function testJsonRequest() {
  let seenBody = null;
  let negotiatedSm4Key = null;
  let client = null;

  client = createTransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY,
    smCrypto: createSmCrypto(),
    cryptoImpl: {
      getRandomValues(target) {
        for (let index = 0; index < target.length; index += 1) {
          target[index] = index + 1;
        }
        return target;
      }
    },
    async fetchImpl(url, options) {
      void url;
      seenBody = JSON.parse(options.body);
      return {
        headers: new HeadersPolyfill({
          'content-type': 'application/json',
          'x-transfer-encrypted': 'true'
        }),
        async text() {
          const payload = client.encryptPayload(
            JSON.stringify({ ok: true }),
            'application/json',
            negotiatedSm4Key
          );
          return JSON.stringify({
            transferPayload: Buffer.from(JSON.stringify({
              encryptedData: payload.encryptedData,
              contentMd5: payload.contentMd5,
              timestamp: Date.now()
            }), 'utf8').toString('base64url')
            ,
            originalContentType: 'application/json'
          });
        }
      };
    }
  });

  const originalEncrypt = client.encryptPayload.bind(client);
  client.encryptPayload = function patchedEncrypt(plaintext, contentType, sm4Key) {
    negotiatedSm4Key = sm4Key;
    return originalEncrypt(plaintext, contentType, sm4Key);
  };

  const result = await client.request({
    url: '/api/json',
    method: 'POST',
    json: { name: 'alice' }
  });

  const envelope = decodeTransferPayload(seenBody.transferPayload);
  assert.equal(seenBody.originalContentType, 'application/json');
  assert.ok(envelope.encryptedKey);
  assert.equal(result.ok, true);
}

async function testQueryRequest() {
  let seenUrl = null;
  const client = new TransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY,
    smCrypto: createSmCrypto(),
    cryptoImpl: {
      getRandomValues(target) {
        for (let index = 0; index < target.length; index += 1) {
          target[index] = index + 7;
        }
        return target;
      }
    },
    async fetchImpl(url) {
      seenUrl = url;
      return {
        headers: new HeadersPolyfill({ 'content-type': 'application/json' }),
        async text() {
          return JSON.stringify({ ok: true });
        }
      };
    }
  });

  const result = await client.request({
    url: '/api/query',
    method: 'GET',
    params: { name: 'bob' }
  });

  assert.equal(result.ok, true);
  assert.match(seenUrl, /transferPayload=/);
  assert.ok(!seenUrl.includes('name=bob'));
}

async function testUploadMultiple() {
  let seenBody = null;
  const adapter = createTransferEncryptVueAdapter({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY,
    smCrypto: createSmCrypto(),
    cryptoImpl: {
      getRandomValues(target) {
        for (let index = 0; index < target.length; index += 1) {
          target[index] = index + 9;
        }
        return target;
      }
    },
    async fetchImpl(url, options) {
      void url;
      seenBody = options.body;
      return {
        headers: new HeadersPolyfill({ 'content-type': 'application/json' }),
        async text() {
          return JSON.stringify({ ok: true });
        }
      };
    }
  });

  await adapter.uploadMany([
    new File(['first'], 'a.txt', { type: 'text/plain' }),
    new File(['second'], 'b.txt', { type: 'text/plain' })
  ], {
    url: '/api/upload',
    fieldName: 'files'
  });

  assert.ok(seenBody.get('__md5_files__0'));
  assert.ok(seenBody.get('__md5_files__1'));
}

async function testBinaryVerify() {
  const client = createTransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY,
    smCrypto: createSmCrypto(),
    cryptoImpl: {
      getRandomValues(target) {
        for (let index = 0; index < target.length; index += 1) {
          target[index] = index + 1;
        }
        return target;
      }
    },
    async fetchImpl() {
      const buffer = Buffer.from('hello-file', 'utf8');
      const arrayBuffer = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
      return {
        headers: new HeadersPolyfill({
          'x-transfer-content-md5': md5ArrayBuffer(arrayBuffer)
        }),
        async text() {
          return buffer.toString('utf8');
        },
        async arrayBuffer() {
          return arrayBuffer;
        }
      };
    }
  });

  const result = await client.request({
    url: '/api/file',
    method: 'GET',
    responseType: 'arrayBuffer'
  });

  assert.ok(result instanceof ArrayBuffer);
}

async function testPublicKeyAutoPrefix04() {
  let seenBody = null;
  const client = createTransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: '2'.repeat(128),
    smCrypto: createSmCrypto(),
    cryptoImpl: {
      getRandomValues(target) {
        for (let index = 0; index < target.length; index += 1) {
          target[index] = index + 2;
        }
        return target;
      }
    },
    async fetchImpl(url, options) {
      void url;
      seenBody = JSON.parse(options.body);
      return {
        headers: new HeadersPolyfill({ 'content-type': 'application/json' }),
        async text() {
          return JSON.stringify({ ok: true });
        }
      };
    }
  });

  await client.request({
    url: '/api/json',
    method: 'POST',
    json: { name: 'alice' }
  });

  const envelope = decodeTransferPayload(seenBody.transferPayload);
  assert.ok(envelope.encryptedKey.startsWith('sm2:04' + '2'.repeat(128) + ':1:'));
}

function testRejectDerKey() {
  assert.throws(() => {
    createTransferEncryptClient({
      baseUrl: 'http://localhost:8080',
      publicKey: '3059301306072a8648ce3d020106082a811ccf5501822d03420004' + '3'.repeat(128),
      smCrypto: createSmCrypto()
    });
  }, /DER\/X509/);
}

async function testSm4UsesHexKeyEncoding() {
  let seenKey = null;
  const smCrypto = createSmCrypto();
  smCrypto.sm4.encrypt = function (text, key) {
    seenKey = key;
    return sm4Encrypt(text, key);
  };

  const client = createTransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY,
    smCrypto,
    cryptoImpl: {
      getRandomValues(target) {
        for (let index = 0; index < target.length; index += 1) {
          target[index] = index + 3;
        }
        return target;
      }
    },
    async fetchImpl() {
      return {
        headers: new HeadersPolyfill({ 'content-type': 'application/json' }),
        async text() {
          return JSON.stringify({ ok: true });
        }
      };
    }
  });

  await client.request({
    url: '/api/json',
    method: 'POST',
    json: { name: 'alice' }
  });

  assert.equal(seenKey.length, 32);
  assert.match(seenKey, /^[0-9a-f]+$/);
}

await testJsonRequest();
await testQueryRequest();
await testUploadMultiple();
await testBinaryVerify();
await testPublicKeyAutoPrefix04();
testRejectDerKey();
await testSm4UsesHexKeyEncoding();
