function isPlainObject(value) {
  return Object.prototype.toString.call(value) === '[object Object]';
}

function isFormData(value) {
  return typeof FormData !== 'undefined' && value instanceof FormData;
}

function isFileLike(value) {
  return (typeof File !== 'undefined' && value instanceof File)
    || (typeof Blob !== 'undefined' && value instanceof Blob);
}

function utf8Bytes(text) {
  return new TextEncoder().encode(text);
}

function utf8Decode(bytes) {
  return new TextDecoder().decode(bytes);
}

function asciiToHex(text) {
  return Array.prototype.map.call(text, (char) =>
    char.charCodeAt(0).toString(16).padStart(2, '0')).join('');
}

function encodeBase64Url(bytes) {
  if (typeof globalThis.btoa === 'function') {
    let binary = '';
    for (let index = 0; index < bytes.length; index += 1) {
      binary += String.fromCharCode(bytes[index]);
    }
    return globalThis.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  }
  return Buffer.from(bytes).toString('base64url');
}

function decodeBase64Url(text) {
  if (typeof globalThis.atob === 'function') {
    let normalized = String(text).replace(/-/g, '+').replace(/_/g, '/');
    while (normalized.length % 4 !== 0) {
      normalized += '=';
    }
    const binary = globalThis.atob(normalized);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
  }
  return Uint8Array.from(Buffer.from(String(text), 'base64url'));
}

function encodeTransferPayload(envelope) {
  return encodeBase64Url(utf8Bytes(JSON.stringify(envelope)));
}

function decodeTransferPayload(payload) {
  return JSON.parse(utf8Decode(decodeBase64Url(payload)));
}

function buildTransportWrapper(envelope, originalContentType) {
  return {
    transferPayload: encodeTransferPayload(envelope),
    originalContentType
  };
}

function toHex32(value) {
  return ('00000000' + (value >>> 0).toString(16)).slice(-8);
}

function md5ArrayBuffer(buffer) {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  const words = [];
  for (let index = 0; index < bytes.length; index += 1) {
    words[index >> 2] |= bytes[index] << ((index % 4) * 8);
  }
  words[bytes.length >> 2] |= 0x80 << ((bytes.length % 4) * 8);
  words[(((bytes.length + 8) >>> 6) << 4) + 14] = bytes.length * 8;

  let a = 0x67452301;
  let b = 0xefcdab89;
  let c = 0x98badcfe;
  let d = 0x10325476;

  function ff(x, y, z) { return (x & y) | (~x & z); }
  function gg(x, y, z) { return (x & z) | (y & ~z); }
  function hh(x, y, z) { return x ^ y ^ z; }
  function ii(x, y, z) { return y ^ (x | ~z); }
  function cmn(q, x, y, z, m, s, t) {
    return ((((x + q + m + t) | 0) << s)
      | (((x + q + m + t) | 0) >>> (32 - s))) + y | 0;
  }
  function round(func, x, y, z, m, s, t) {
    return cmn(func(y, z, x), x, y, z, m, s, t);
  }

  for (let index = 0; index < words.length; index += 16) {
    const oa = a;
    const ob = b;
    const oc = c;
    const od = d;

    a = round(ff, a, b, c, d, words[index + 0], 7, -680876936);
    d = round(ff, d, a, b, c, words[index + 1], 12, -389564586);
    c = round(ff, c, d, a, b, words[index + 2], 17, 606105819);
    b = round(ff, b, c, d, a, words[index + 3], 22, -1044525330);
    a = round(ff, a, b, c, d, words[index + 4], 7, -176418897);
    d = round(ff, d, a, b, c, words[index + 5], 12, 1200080426);
    c = round(ff, c, d, a, b, words[index + 6], 17, -1473231341);
    b = round(ff, b, c, d, a, words[index + 7], 22, -45705983);
    a = round(ff, a, b, c, d, words[index + 8], 7, 1770035416);
    d = round(ff, d, a, b, c, words[index + 9], 12, -1958414417);
    c = round(ff, c, d, a, b, words[index + 10], 17, -42063);
    b = round(ff, b, c, d, a, words[index + 11], 22, -1990404162);
    a = round(ff, a, b, c, d, words[index + 12], 7, 1804603682);
    d = round(ff, d, a, b, c, words[index + 13], 12, -40341101);
    c = round(ff, c, d, a, b, words[index + 14], 17, -1502002290);
    b = round(ff, b, c, d, a, words[index + 15], 22, 1236535329);

    a = round(gg, a, b, c, d, words[index + 1], 5, -165796510);
    d = round(gg, d, a, b, c, words[index + 6], 9, -1069501632);
    c = round(gg, c, d, a, b, words[index + 11], 14, 643717713);
    b = round(gg, b, c, d, a, words[index + 0], 20, -373897302);
    a = round(gg, a, b, c, d, words[index + 5], 5, -701558691);
    d = round(gg, d, a, b, c, words[index + 10], 9, 38016083);
    c = round(gg, c, d, a, b, words[index + 15], 14, -660478335);
    b = round(gg, b, c, d, a, words[index + 4], 20, -405537848);
    a = round(gg, a, b, c, d, words[index + 9], 5, 568446438);
    d = round(gg, d, a, b, c, words[index + 14], 9, -1019803690);
    c = round(gg, c, d, a, b, words[index + 3], 14, -187363961);
    b = round(gg, b, c, d, a, words[index + 8], 20, 1163531501);
    a = round(gg, a, b, c, d, words[index + 13], 5, -1444681467);
    d = round(gg, d, a, b, c, words[index + 2], 9, -51403784);
    c = round(gg, c, d, a, b, words[index + 7], 14, 1735328473);
    b = round(gg, b, c, d, a, words[index + 12], 20, -1926607734);

    a = round(hh, a, b, c, d, words[index + 5], 4, -378558);
    d = round(hh, d, a, b, c, words[index + 8], 11, -2022574463);
    c = round(hh, c, d, a, b, words[index + 11], 16, 1839030562);
    b = round(hh, b, c, d, a, words[index + 14], 23, -35309556);
    a = round(hh, a, b, c, d, words[index + 1], 4, -1530992060);
    d = round(hh, d, a, b, c, words[index + 4], 11, 1272893353);
    c = round(hh, c, d, a, b, words[index + 7], 16, -155497632);
    b = round(hh, b, c, d, a, words[index + 10], 23, -1094730640);
    a = round(hh, a, b, c, d, words[index + 13], 4, 681279174);
    d = round(hh, d, a, b, c, words[index + 0], 11, -358537222);
    c = round(hh, c, d, a, b, words[index + 3], 16, -722521979);
    b = round(hh, b, c, d, a, words[index + 6], 23, 76029189);
    a = round(hh, a, b, c, d, words[index + 9], 4, -640364487);
    d = round(hh, d, a, b, c, words[index + 12], 11, -421815835);
    c = round(hh, c, d, a, b, words[index + 15], 16, 530742520);
    b = round(hh, b, c, d, a, words[index + 2], 23, -995338651);

    a = round(ii, a, b, c, d, words[index + 0], 6, -198630844);
    d = round(ii, d, a, b, c, words[index + 7], 10, 1126891415);
    c = round(ii, c, d, a, b, words[index + 14], 15, -1416354905);
    b = round(ii, b, c, d, a, words[index + 5], 21, -57434055);
    a = round(ii, a, b, c, d, words[index + 12], 6, 1700485571);
    d = round(ii, d, a, b, c, words[index + 3], 10, -1894986606);
    c = round(ii, c, d, a, b, words[index + 10], 15, -1051523);
    b = round(ii, b, c, d, a, words[index + 1], 21, -2054922799);
    a = round(ii, a, b, c, d, words[index + 8], 6, 1873313359);
    d = round(ii, d, a, b, c, words[index + 15], 10, -30611744);
    c = round(ii, c, d, a, b, words[index + 6], 15, -1560198380);
    b = round(ii, b, c, d, a, words[index + 13], 21, 1309151649);
    a = round(ii, a, b, c, d, words[index + 4], 6, -145523070);
    d = round(ii, d, a, b, c, words[index + 11], 10, -1120210379);
    c = round(ii, c, d, a, b, words[index + 2], 15, 718787259);
    b = round(ii, b, c, d, a, words[index + 9], 21, -343485551);

    a = (a + oa) | 0;
    b = (b + ob) | 0;
    c = (c + oc) | 0;
    d = (d + od) | 0;
  }

  return toHex32(a) + toHex32(b) + toHex32(c) + toHex32(d);
}

function md5String(text) {
  return md5ArrayBuffer(utf8Bytes(text));
}

function normalizeParams(input) {
  if (!input) {
    return '';
  }
  if (input instanceof URLSearchParams) {
    return input.toString();
  }
  if (typeof input === 'string') {
    return input;
  }
  const params = new URLSearchParams();
  Object.keys(input).forEach((key) => {
    const value = input[key];
    if (Array.isArray(value)) {
      value.forEach((item) => {
        params.append(key, item == null ? '' : String(item));
      });
      return;
    }
    if (value != null) {
      params.append(key, String(value));
    }
  });
  return params.toString();
}

function appendQuery(url, query) {
  if (!query) {
    return url;
  }
  return url + (url.indexOf('?') >= 0 ? '&' : '?') + query;
}

function toObject(jsonText) {
  try {
    return JSON.parse(jsonText);
  } catch (error) {
    return null;
  }
}

function lowerHeaders(headers) {
  const map = {};
  headers.forEach((value, key) => {
    map[key.toLowerCase()] = value;
  });
  return map;
}

function resolveSmCrypto(instance) {
  const library = instance && instance.smCrypto
    ? instance.smCrypto
    : globalThis.TransferSmCrypto || globalThis.smCrypto;
  if (!library || !library.sm2 || !library.sm4) {
    throw new Error('缺少本地 SM2/SM4 实现，请通过 options.smCrypto 或全局 TransferSmCrypto 注入');
  }
  return library;
}

function normalizePublicKey(publicKey) {
  const raw = String(publicKey || '').replace(/\s+/g, '');
  if (!raw) {
    throw new Error('publicKey 未配置');
  }

  if (/^[0-9a-fA-F]{128}$/.test(raw)) {
    return '04' + raw;
  }

  if (/^04[0-9a-fA-F]{128}$/.test(raw)) {
    return raw;
  }

  if (/^(30|3059|3081|3082)[0-9a-fA-F]+$/.test(raw)) {
    throw new Error('publicKey 格式不兼容：当前是 DER/X509 十六进制，请改为浏览器可用的未压缩公钥点格式 04 + X + Y');
  }

  if (/-----BEGIN PUBLIC KEY-----/.test(String(publicKey || ''))) {
    throw new Error('publicKey 格式不兼容：当前是 PEM，请改为浏览器可用的未压缩公钥点格式 04 + X + Y');
  }

  throw new Error('publicKey 格式不兼容：期望 128 位裸公钥点或 130 位未压缩公钥点（04 + X + Y）');
}

function resolveCrypto(options) {
  const cryptoImpl = options && options.cryptoImpl ? options.cryptoImpl : globalThis.crypto;
  if (!cryptoImpl || typeof cryptoImpl.getRandomValues !== 'function') {
    throw new Error('缺少 crypto.getRandomValues 实现，请通过 options.cryptoImpl 注入');
  }
  return cryptoImpl;
}

async function ensureMultipartMd5(formData) {
  const groupedFiles = {};
  const entries = Array.from(formData.entries());
  for (let index = 0; index < entries.length; index += 1) {
    const [name, value] = entries[index];
    if (!isFileLike(value)) {
      continue;
    }
    if (!groupedFiles[name]) {
      groupedFiles[name] = [];
    }
    groupedFiles[name].push(value);
  }

  const fieldNames = Object.keys(groupedFiles);
  for (let index = 0; index < fieldNames.length; index += 1) {
    const fieldName = fieldNames[index];
    const files = groupedFiles[fieldName];
    if (files.length === 1) {
      formData.append('__md5_' + fieldName, md5ArrayBuffer(await files[0].arrayBuffer()));
      continue;
    }
    for (let fileIndex = 0; fileIndex < files.length; fileIndex += 1) {
      formData.append('__md5_' + fieldName + '__' + fileIndex,
        md5ArrayBuffer(await files[fileIndex].arrayBuffer()));
    }
  }
}

function randomSm4Key(cryptoImpl) {
  const chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
  const bytes = new Uint8Array(16);
  cryptoImpl.getRandomValues(bytes);
  return Array.prototype.map.call(bytes, (value) => chars[value % chars.length]).join('');
}

export class TransferEncryptClient {
  constructor(options = {}) {
    this.baseUrl = options.baseUrl || '';
    this.publicKey = normalizePublicKey(options.publicKey);
    this.fetchImpl = options.fetchImpl || globalThis.fetch?.bind(globalThis);
    this.smCrypto = options.smCrypto;
    this.cryptoImpl = options.cryptoImpl;
  }

  encryptPayload(plaintext, originalContentType, sm4Key) {
    const library = resolveSmCrypto(this);
    return {
      encryptedKey: library.sm2.doEncrypt(sm4Key, this.publicKey, 1),
      encryptedData: library.sm4.encrypt(plaintext, asciiToHex(sm4Key), { mode: 'ecb', padding: 'pkcs#7' }),
      contentMd5: md5String(plaintext),
      timestamp: Date.now()
    };
  }

  decryptEnvelope(envelope, sm4Key) {
    const library = resolveSmCrypto(this);
    const plaintext = library.sm4.decrypt(envelope.encryptedData, asciiToHex(sm4Key), { mode: 'ecb', padding: 'pkcs#7' });
    if (md5String(plaintext) !== (envelope.contentMd5 || '').toLowerCase()) {
      throw new Error('响应 MD5 校验失败');
    }
    return plaintext;
  }

  async request(options = {}) {
    if (!this.publicKey) {
      throw new Error('publicKey 未配置');
    }
    if (typeof this.fetchImpl !== 'function') {
      throw new Error('fetch 未配置，请通过 options.fetchImpl 注入');
    }

    let url = this.baseUrl + options.url;
    const method = (options.method || 'GET').toUpperCase();
    const headers = new Headers(options.headers || {});
    let body = options.body;
    let sm4Key = null;

    if (isFormData(body)) {
      await ensureMultipartMd5(body);
    } else if (options.params && (method === 'GET' || method === 'DELETE')) {
      sm4Key = randomSm4Key(resolveCrypto(this));
      url = appendQuery(url, normalizeParams(buildTransportWrapper(
        this.encryptPayload(
          normalizeParams(options.params),
          'application/x-www-form-urlencoded',
          sm4Key
        ),
        'application/x-www-form-urlencoded'
      )));
      body = null;
    } else if (options.form) {
      sm4Key = randomSm4Key(resolveCrypto(this));
      headers.set('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
      body = normalizeParams(buildTransportWrapper(this.encryptPayload(
          normalizeParams(options.form),
          'application/x-www-form-urlencoded',
          sm4Key
        ), 'application/x-www-form-urlencoded'));
    } else if (options.json !== undefined) {
      sm4Key = randomSm4Key(resolveCrypto(this));
      headers.set('Content-Type', 'application/json;charset=UTF-8');
      body = JSON.stringify(buildTransportWrapper(
        this.encryptPayload(JSON.stringify(options.json), 'application/json', sm4Key),
        'application/json'
      ));
    } else if (isPlainObject(body)) {
      sm4Key = randomSm4Key(resolveCrypto(this));
      headers.set('Content-Type', 'application/json;charset=UTF-8');
      body = JSON.stringify(buildTransportWrapper(
        this.encryptPayload(JSON.stringify(body), 'application/json', sm4Key),
        'application/json'
      ));
    }

    const response = await this.fetchImpl(url, {
      method,
      headers,
      body
    });

    const headerMap = lowerHeaders(response.headers);
    const encrypted = headerMap['x-transfer-encrypted'] === 'true';
    const contentType = headerMap['content-type'] || '';

    if (encrypted || contentType.indexOf('application/json') >= 0) {
      const text = await response.text();
      let envelope = toObject(text);
      let wrapper = envelope;
      if (wrapper && wrapper.transferPayload) {
        envelope = decodeTransferPayload(wrapper.transferPayload);
      }
      if (envelope && envelope.encryptedData && sm4Key) {
        const plaintext = this.decryptEnvelope(envelope, sm4Key);
        if (String(wrapper && wrapper.originalContentType || '').indexOf('application/json') >= 0) {
          return JSON.parse(plaintext);
        }
        return plaintext;
      }
      if (contentType.indexOf('application/json') >= 0 && text) {
        return JSON.parse(text);
      }
      return text;
    }

    if (options.responseType === 'arrayBuffer' || options.responseType === 'blob') {
      const arrayBuffer = await response.arrayBuffer();
      if (headerMap['x-transfer-content-md5']
        && md5ArrayBuffer(arrayBuffer) !== headerMap['x-transfer-content-md5'].toLowerCase()) {
        throw new Error('文件 MD5 校验失败');
      }
      return options.responseType === 'blob' ? new Blob([arrayBuffer]) : arrayBuffer;
    }

    return response.text();
  }
}

export function createTransferEncryptClient(options) {
  return new TransferEncryptClient(options);
}

export function createTransferEncryptVueAdapter(options = {}) {
  const client = options.client instanceof TransferEncryptClient
    ? options.client
    : new TransferEncryptClient(options);

  return {
    client,
    request(requestOptions) {
      return client.request(requestOptions || {});
    },
    json(url, data, options = {}) {
      return client.request({
        ...options,
        url,
        method: options.method || 'POST',
        json: data
      });
    },
    form(url, data, options = {}) {
      return client.request({
        ...options,
        url,
        method: options.method || 'POST',
        form: data
      });
    },
    get(url, params, options = {}) {
      return client.request({
        ...options,
        url,
        method: 'GET',
        params
      });
    },
    upload(file, options = {}) {
      if (!file) {
        throw new Error('未选择待上传文件');
      }
      return this.uploadMany([file], options);
    },
    uploadMany(files, options = {}) {
      if (!files || !files.length) {
        throw new Error('未选择待上传文件');
      }
      const formData = new FormData();
      const fieldName = options.fieldName || 'file';
      for (let index = 0; index < files.length; index += 1) {
        formData.append(fieldName, files[index]);
      }
      const extraData = options.data || {};
      Object.keys(extraData).forEach((key) => {
        if (extraData[key] != null) {
          formData.append(key, String(extraData[key]));
        }
      });
      return client.request({
        url: options.url,
        method: options.method || 'POST',
        body: formData,
        headers: options.headers,
        responseType: options.responseType
      });
    }
  };
}

export {
  appendQuery,
  ensureMultipartMd5,
  lowerHeaders,
  md5ArrayBuffer,
  md5String,
  decodeTransferPayload,
  encodeTransferPayload,
  normalizePublicKey,
  normalizeParams,
  randomSm4Key,
  resolveCrypto,
  resolveSmCrypto,
  toObject
};
