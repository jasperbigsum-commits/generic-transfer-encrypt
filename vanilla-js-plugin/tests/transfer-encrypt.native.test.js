const assert = require('assert');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

class BlobPolyfill {
  constructor(parts, options) {
    const buffers = (parts || []).map((part) => {
      if (Buffer.isBuffer(part)) {
        return part;
      }
      if (part instanceof Uint8Array) {
        return Buffer.from(part);
      }
      return Buffer.from(String(part));
    });
    this._buffer = Buffer.concat(buffers);
    this.type = options && options.type ? options.type : '';
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

  getAll(name) {
    return this._entries.filter((entry) => entry[0] === name).map((entry) => entry[1]);
  }

  [Symbol.iterator]() {
    return this.entries();
  }
}

class HeadersPolyfill {
  constructor(source) {
    this._map = {};
    if (source) {
      Object.keys(source).forEach((key) => {
        this.set(key, source[key]);
      });
    }
  }

  set(key, value) {
    this._map[String(key).toLowerCase()] = String(value);
  }

  get(key) {
    return this._map[String(key).toLowerCase()] || null;
  }

  forEach(callback) {
    Object.keys(this._map).forEach((key) => {
      callback(this._map[key], key);
    });
  }
}

function sm4Encrypt(text, key) {
  return Buffer.from(JSON.stringify({ key, text }), 'utf8').toString('base64');
}

function sm4Decrypt(cipherText) {
  return JSON.parse(Buffer.from(cipherText, 'base64').toString('utf8')).text;
}

const TEST_PUBLIC_KEY = '1'.repeat(128);

function decodeTransferPayload(payload) {
  const normalized = String(payload).replace(/-/g, '+').replace(/_/g, '/');
  const padded = normalized + '='.repeat((4 - normalized.length % 4) % 4);
  return JSON.parse(Buffer.from(padded, 'base64').toString('utf8'));
}

function createBinaryResponse(content, md5Override) {
  const buffer = Buffer.from(content, 'utf8');
  return {
    headers: new HeadersPolyfill({
      'X-Transfer-Content-MD5': md5Override || crypto.createHash('md5').update(buffer).digest('hex')
    }),
    async text() {
      return buffer.toString('utf8');
    },
    async arrayBuffer() {
      return buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
    }
  };
}

function createRuntime(fetchImpl) {
  const md5Script = fs.readFileSync(path.join(__dirname, '..', 'vendor', 'md5.js'), 'utf8');
  const script = fs.readFileSync(path.join(__dirname, '..', 'transfer-encrypt.js'), 'utf8');
  const sandbox = {
    console,
    TextEncoder,
    TextDecoder,
    URLSearchParams,
    btoa(value) {
      return Buffer.from(value, 'binary').toString('base64');
    },
    atob(value) {
      return Buffer.from(value, 'base64').toString('binary');
    },
    FormData: FormDataPolyfill,
    Blob: BlobPolyfill,
    File: FilePolyfill,
    Headers: HeadersPolyfill,
    fetch: fetchImpl,
    crypto: {
      getRandomValues(target) {
        for (let index = 0; index < target.length; index += 1) {
          target[index] = index + 1;
        }
        return target;
      }
    },
    smCrypto: {
      sm2: {
        doEncrypt(text, publicKey, cipherMode) {
          return 'sm2:' + publicKey + ':' + cipherMode + ':' + text;
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
    }
  };
  sandbox.window = sandbox;
  vm.runInNewContext(md5Script, sandbox, { filename: 'vendor/md5.js' });
  vm.runInNewContext(script, sandbox, { filename: 'transfer-encrypt.js' });
  sandbox.TransferEncryptRegisterSmCrypto(sandbox.smCrypto);
  return sandbox;
}

function waitForAsyncBridge() {
  return new Promise((resolve) => {
    setTimeout(resolve, 0);
  });
}

async function testJsonRequest() {
  let seenBody = null;
  let negotiatedSm4Key = null;
  let client = null;
  const runtime = createRuntime(async (url, options) => {
    seenBody = JSON.parse(options.body);
    return {
      headers: new HeadersPolyfill({
        'Content-Type': 'application/json',
        'X-Transfer-Encrypted': 'true'
      }),
      async text() {
        const responsePayload = client.encryptPayload(JSON.stringify({ ok: true }), 'application/json', negotiatedSm4Key);
        return JSON.stringify({
          transferPayload: Buffer.from(JSON.stringify({
            encryptedData: responsePayload.encryptedData,
            contentMd5: responsePayload.contentMd5,
            timestamp: Date.now()
          }), 'utf8').toString('base64url'),
          originalContentType: 'application/json'
        });
      }
    };
  });
  client = new runtime.TransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });
  const originalEncrypt = client.encryptPayload.bind(client);
  client.encryptPayload = function (plaintext, contentType, sm4Key) {
    const result = originalEncrypt(plaintext, contentType, sm4Key);
    negotiatedSm4Key = sm4Key;
    return result;
  };
  const result = await client.request({
    url: '/api/json',
    method: 'POST',
    json: { name: 'alice' }
  });
  const envelope = decodeTransferPayload(seenBody.transferPayload);
  assert.strictEqual(seenBody.originalContentType, 'application/json');
  assert.ok(envelope.encryptedKey);
  assert.strictEqual(result.ok, true);
}

async function testQueryRequest() {
  let seenUrl = null;
  let negotiatedSm4Key = null;
  let client = null;
  const runtime = createRuntime(async (url) => {
    seenUrl = url;
    return {
      headers: new HeadersPolyfill({
        'Content-Type': 'application/json',
        'X-Transfer-Encrypted': 'true'
      }),
      async text() {
        const responsePayload = client.encryptPayload(JSON.stringify({ ok: true }), 'application/json', negotiatedSm4Key);
        return JSON.stringify({
          transferPayload: Buffer.from(JSON.stringify({
            encryptedData: responsePayload.encryptedData,
            contentMd5: responsePayload.contentMd5,
            timestamp: Date.now()
          }), 'utf8').toString('base64url'),
          originalContentType: 'application/json'
        });
      }
    };
  });
  client = new runtime.TransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });
  const originalEncrypt = client.encryptPayload.bind(client);
  client.encryptPayload = function (plaintext, contentType, sm4Key) {
    const result = originalEncrypt(plaintext, contentType, sm4Key);
    negotiatedSm4Key = sm4Key;
    return result;
  };
  await client.request({
    url: '/api/query',
    method: 'GET',
    params: { name: 'bob' }
  });
  assert.ok(seenUrl.indexOf('transferPayload=') > -1);
  assert.ok(seenUrl.indexOf('name=bob') === -1);
}

async function testFormRequest() {
  let seenBody = null;
  let negotiatedSm4Key = null;
  let client = null;
  const runtime = createRuntime(async (url, options) => {
    seenBody = options.body;
    return {
      headers: new HeadersPolyfill({
        'Content-Type': 'application/json',
        'X-Transfer-Encrypted': 'true'
      }),
      async text() {
        const responsePayload = client.encryptPayload(JSON.stringify({ ok: true }), 'application/json', negotiatedSm4Key);
        return JSON.stringify({
          transferPayload: Buffer.from(JSON.stringify({
            encryptedData: responsePayload.encryptedData,
            contentMd5: responsePayload.contentMd5,
            timestamp: Date.now()
          }), 'utf8').toString('base64url'),
          originalContentType: 'application/json'
        });
      }
    };
  });
  client = new runtime.TransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });
  const originalEncrypt = client.encryptPayload.bind(client);
  client.encryptPayload = function (plaintext, contentType, sm4Key) {
    const result = originalEncrypt(plaintext, contentType, sm4Key);
    negotiatedSm4Key = sm4Key;
    return result;
  };
  await client.request({
    url: '/api/form',
    method: 'POST',
    form: { name: 'carol' }
  });
  assert.ok(seenBody.indexOf('transferPayload=') > -1);
}

async function testUploadSingle() {
  let seenFormData = null;
  const runtime = createRuntime(async (url, options) => {
    seenFormData = options.body;
    return {
      headers: new HeadersPolyfill({ 'Content-Type': 'application/json' }),
      async text() {
        return JSON.stringify({ ok: true });
      }
    };
  });
  const adapter = runtime.TransferEncryptCreateLayuiAdapter({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });
  const file = new runtime.File(['hello'], 'demo.txt', { type: 'text/plain' });
  const result = await adapter.upload(file, {
    url: '/api/upload',
    fieldName: 'file'
  });
  assert.strictEqual(result.ok, true);
  assert.ok(seenFormData.get('__md5_file'));
}

async function testUploadMultiple() {
  let seenFormData = null;
  const runtime = createRuntime(async (url, options) => {
    seenFormData = options.body;
    return {
      headers: new HeadersPolyfill({ 'Content-Type': 'application/json' }),
      async text() {
        return JSON.stringify({ ok: true });
      }
    };
  });
  const adapter = runtime.TransferEncryptCreateLayuiAdapter({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });
  const files = [
    new runtime.File(['first'], 'first.txt', { type: 'text/plain' }),
    new runtime.File(['second'], 'second.txt', { type: 'text/plain' })
  ];
  await adapter.uploadMany(files, {
    url: '/api/upload/multi',
    fieldName: 'files'
  });
  assert.ok(seenFormData.get('__md5_files__0'));
  assert.ok(seenFormData.get('__md5_files__1'));
}

async function testBinaryMd5Verify() {
  let client = null;
  const runtime = createRuntime(async () => {
    const md5Holder = client.encryptPayload('binary-body', 'text/plain', '1234567890abcdef');
    return createBinaryResponse('binary-body', md5Holder.contentMd5);
  });
  client = new runtime.TransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });
  const result = await client.request({
    url: '/api/file',
    method: 'GET',
    responseType: 'arrayBuffer'
  });
  assert.ok(result instanceof ArrayBuffer);
}

async function testLayuiFormBinding() {
  let submitHandler = null;
  const runtime = createRuntime(async () => ({
    headers: new HeadersPolyfill({ 'Content-Type': 'application/json' }),
    async text() {
      return JSON.stringify({ ok: true });
    }
  }));
  runtime.layui = {
    layer: {
      load() { return 1; },
      close() {},
      msg() {}
    },
    form: {
      on(eventName, handler) {
        assert.strictEqual(eventName, 'submit(encrypt-submit)');
        submitHandler = handler;
      }
    }
  };
  const adapter = runtime.TransferEncryptCreateLayuiAdapter({
    layui: runtime.layui,
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });
  adapter.bindFormSubmit('encrypt-submit', {
    url: '/api/form',
    form: true,
    onSuccess(result) {
      assert.strictEqual(result.ok, true);
    }
  });
  assert.ok(typeof submitHandler === 'function');
  await submitHandler({ field: { name: 'layui' } });
}

async function testLayuiTableRenderBridge() {
  let seenUrl = null;
  let tableSuccessResponse = null;
  const runtime = createRuntime(async (url) => {
    seenUrl = url;
    return {
      headers: new HeadersPolyfill({ 'Content-Type': 'application/json' }),
      async text() {
        return JSON.stringify({
          code: 0,
          msg: '',
          count: 1,
          data: [{ id: 1, name: 'alice' }]
        });
      }
    };
  });

  runtime.layui = {
    $: {
      ajax(options) {
        if (typeof options.success === 'function') {
          options.success({ code: 0, data: [] });
        }
      }
    },
    table: {
      render(config) {
        runtime.layui.$.ajax({
          url: config.url,
          type: config.method,
          data: config.where,
          headers: config.headers,
          success(res) {
            tableSuccessResponse = res;
          }
        });
        return { config: config };
      }
    }
  };

  const adapter = runtime.TransferEncryptCreateLayuiAdapter({
    layui: runtime.layui,
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });

  adapter.installTableBridge();
  runtime.layui.table.render({
    elem: '#demo',
    url: '/api/table',
    method: 'GET',
    where: { name: 'alice' }
  });

  await waitForAsyncBridge();
  await waitForAsyncBridge();

  assert.ok(seenUrl.indexOf('transferPayload=') > -1);
  assert.strictEqual(tableSuccessResponse.count, 1);
  assert.strictEqual(tableSuccessResponse.data[0].name, 'alice');
}

async function testLayuiRenderFormByUrl() {
  let formValue = null;
  let renderType = null;
  let seenUrl = null;
  const runtime = createRuntime(async (url) => {
    seenUrl = url;
    return {
      headers: new HeadersPolyfill({ 'Content-Type': 'application/json' }),
      async text() {
        return JSON.stringify({ name: 'alice', age: 18 });
      }
    };
  });

  runtime.layui = {
    form: {
      val(filter, values) {
        formValue = { filter: filter, values: values };
      },
      render(type) {
        renderType = type;
      }
    }
  };

  const adapter = runtime.TransferEncryptCreateLayuiAdapter({
    layui: runtime.layui,
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });

  await adapter.renderForm('demo-filter', {
    url: '/api/form-data',
    method: 'GET',
    params: { id: 1 },
    type: 'select'
  });

  assert.ok(seenUrl.indexOf('transferPayload=') > -1);
  assert.strictEqual(formValue.filter, 'demo-filter');
  assert.strictEqual(formValue.values.name, 'alice');
  assert.strictEqual(renderType, 'select');
}

async function testPublicKeyAutoPrefix04() {
  let seenBody = null;
  const runtime = createRuntime(async (url, options) => {
    void url;
    seenBody = JSON.parse(options.body);
    return {
      headers: new HeadersPolyfill({ 'Content-Type': 'application/json' }),
      async text() {
        return JSON.stringify({ ok: true });
      }
    };
  });

  const client = new runtime.TransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: '1'.repeat(128)
  });

  await client.request({
    url: '/api/json',
    method: 'POST',
    json: { name: 'alice' }
  });

  const envelope = decodeTransferPayload(seenBody.transferPayload);
  assert.ok(envelope.encryptedKey.indexOf('sm2:04' + '1'.repeat(128) + ':1:') === 0);
}

async function testSm4UsesHexKeyEncoding() {
  let seenKey = null;
  const runtime = createRuntime(async (url, options) => {
    void url;
    void options;
    return {
      headers: new HeadersPolyfill({ 'Content-Type': 'application/json' }),
      async text() {
        return JSON.stringify({ ok: true });
      }
    };
  });

  runtime.smCrypto.sm4.encrypt = function (text, key) {
    void text;
    seenKey = key;
    return sm4Encrypt(text, key);
  };
  runtime.TransferEncryptRegisterSmCrypto(runtime.smCrypto);

  const client = new runtime.TransferEncryptClient({
    baseUrl: 'http://localhost:8080',
    publicKey: TEST_PUBLIC_KEY
  });

  await client.request({
    url: '/api/json',
    method: 'POST',
    json: { name: 'alice' }
  });

  assert.strictEqual(seenKey.length, 32);
  assert.match(seenKey, /^[0-9a-f]+$/);
}

async function testPublicKeyRejectDerHex() {
  const runtime = createRuntime(async () => ({
    headers: new HeadersPolyfill({ 'Content-Type': 'application/json' }),
    async text() {
      return JSON.stringify({ ok: true });
    }
  }));

  assert.throws(function () {
    return new runtime.TransferEncryptClient({
      baseUrl: 'http://localhost:8080',
      publicKey: '3059301306072a8648ce3d020106082a811ccf5501822d03420004' + '1'.repeat(128)
    });
  }, /DER\/X509/);
}

async function main() {
  await testJsonRequest();
  await testQueryRequest();
  await testFormRequest();
  await testUploadSingle();
  await testUploadMultiple();
  await testBinaryMd5Verify();
  await testLayuiFormBinding();
  await testLayuiTableRenderBridge();
  await testLayuiRenderFormByUrl();
  await testPublicKeyAutoPrefix04();
  await testPublicKeyRejectDerHex();
  await testSm4UsesHexKeyEncoding();
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
