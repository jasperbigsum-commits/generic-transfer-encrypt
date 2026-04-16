(function (global) {
  'use strict';

  /**
   * 原生前端传输层加密 SDK。
   *
   * 设计目标：
   * 1. 不依赖 npm / 构建工具
   * 2. 支持 JSON / form / query
   * 3. 对文件上传自动补充 MD5
   * 4. 可直接与 layui 协同使用
   */

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

  function toHex32(value) {
    return ('00000000' + (value >>> 0).toString(16)).slice(-8);
  }

  function md5ArrayBuffer(buffer) {
    var bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
    var words = [];
    var i;
    for (i = 0; i < bytes.length; i += 1) {
      words[i >> 2] |= bytes[i] << ((i % 4) * 8);
    }
    words[bytes.length >> 2] |= 0x80 << ((bytes.length % 4) * 8);
    words[(((bytes.length + 8) >>> 6) << 4) + 14] = bytes.length * 8;

    var a = 0x67452301;
    var b = 0xefcdab89;
    var c = 0x98badcfe;
    var d = 0x10325476;

    function ff(x, y, z) { return (x & y) | (~x & z); }
    function gg(x, y, z) { return (x & z) | (y & ~z); }
    function hh(x, y, z) { return x ^ y ^ z; }
    function ii(x, y, z) { return y ^ (x | ~z); }
    function rol(x, n) { return (x << n) | (x >>> (32 - n)); }
    function cmn(q, x, y, z, m, s, t) {
      return (((x + q + m + t) | 0) << s | ((x + q + m + t) | 0) >>> (32 - s)) + y | 0;
    }
    function round(func, x, y, z, m, s, t) {
      return cmn(func(y, z, x), x, y, z, m, s, t);
    }

    for (i = 0; i < words.length; i += 16) {
      var oa = a;
      var ob = b;
      var oc = c;
      var od = d;

      a = round(ff, a, b, c, d, words[i + 0], 7, -680876936);
      d = round(ff, d, a, b, c, words[i + 1], 12, -389564586);
      c = round(ff, c, d, a, b, words[i + 2], 17, 606105819);
      b = round(ff, b, c, d, a, words[i + 3], 22, -1044525330);
      a = round(ff, a, b, c, d, words[i + 4], 7, -176418897);
      d = round(ff, d, a, b, c, words[i + 5], 12, 1200080426);
      c = round(ff, c, d, a, b, words[i + 6], 17, -1473231341);
      b = round(ff, b, c, d, a, words[i + 7], 22, -45705983);
      a = round(ff, a, b, c, d, words[i + 8], 7, 1770035416);
      d = round(ff, d, a, b, c, words[i + 9], 12, -1958414417);
      c = round(ff, c, d, a, b, words[i + 10], 17, -42063);
      b = round(ff, b, c, d, a, words[i + 11], 22, -1990404162);
      a = round(ff, a, b, c, d, words[i + 12], 7, 1804603682);
      d = round(ff, d, a, b, c, words[i + 13], 12, -40341101);
      c = round(ff, c, d, a, b, words[i + 14], 17, -1502002290);
      b = round(ff, b, c, d, a, words[i + 15], 22, 1236535329);

      a = round(gg, a, b, c, d, words[i + 1], 5, -165796510);
      d = round(gg, d, a, b, c, words[i + 6], 9, -1069501632);
      c = round(gg, c, d, a, b, words[i + 11], 14, 643717713);
      b = round(gg, b, c, d, a, words[i + 0], 20, -373897302);
      a = round(gg, a, b, c, d, words[i + 5], 5, -701558691);
      d = round(gg, d, a, b, c, words[i + 10], 9, 38016083);
      c = round(gg, c, d, a, b, words[i + 15], 14, -660478335);
      b = round(gg, b, c, d, a, words[i + 4], 20, -405537848);
      a = round(gg, a, b, c, d, words[i + 9], 5, 568446438);
      d = round(gg, d, a, b, c, words[i + 14], 9, -1019803690);
      c = round(gg, c, d, a, b, words[i + 3], 14, -187363961);
      b = round(gg, b, c, d, a, words[i + 8], 20, 1163531501);
      a = round(gg, a, b, c, d, words[i + 13], 5, -1444681467);
      d = round(gg, d, a, b, c, words[i + 2], 9, -51403784);
      c = round(gg, c, d, a, b, words[i + 7], 14, 1735328473);
      b = round(gg, b, c, d, a, words[i + 12], 20, -1926607734);

      a = round(hh, a, b, c, d, words[i + 5], 4, -378558);
      d = round(hh, d, a, b, c, words[i + 8], 11, -2022574463);
      c = round(hh, c, d, a, b, words[i + 11], 16, 1839030562);
      b = round(hh, b, c, d, a, words[i + 14], 23, -35309556);
      a = round(hh, a, b, c, d, words[i + 1], 4, -1530992060);
      d = round(hh, d, a, b, c, words[i + 4], 11, 1272893353);
      c = round(hh, c, d, a, b, words[i + 7], 16, -155497632);
      b = round(hh, b, c, d, a, words[i + 10], 23, -1094730640);
      a = round(hh, a, b, c, d, words[i + 13], 4, 681279174);
      d = round(hh, d, a, b, c, words[i + 0], 11, -358537222);
      c = round(hh, c, d, a, b, words[i + 3], 16, -722521979);
      b = round(hh, b, c, d, a, words[i + 6], 23, 76029189);
      a = round(hh, a, b, c, d, words[i + 9], 4, -640364487);
      d = round(hh, d, a, b, c, words[i + 12], 11, -421815835);
      c = round(hh, c, d, a, b, words[i + 15], 16, 530742520);
      b = round(hh, b, c, d, a, words[i + 2], 23, -995338651);

      a = round(ii, a, b, c, d, words[i + 0], 6, -198630844);
      d = round(ii, d, a, b, c, words[i + 7], 10, 1126891415);
      c = round(ii, c, d, a, b, words[i + 14], 15, -1416354905);
      b = round(ii, b, c, d, a, words[i + 5], 21, -57434055);
      a = round(ii, a, b, c, d, words[i + 12], 6, 1700485571);
      d = round(ii, d, a, b, c, words[i + 3], 10, -1894986606);
      c = round(ii, c, d, a, b, words[i + 10], 15, -1051523);
      b = round(ii, b, c, d, a, words[i + 1], 21, -2054922799);
      a = round(ii, a, b, c, d, words[i + 8], 6, 1873313359);
      d = round(ii, d, a, b, c, words[i + 15], 10, -30611744);
      c = round(ii, c, d, a, b, words[i + 6], 15, -1560198380);
      b = round(ii, b, c, d, a, words[i + 13], 21, 1309151649);
      a = round(ii, a, b, c, d, words[i + 4], 6, -145523070);
      d = round(ii, d, a, b, c, words[i + 11], 10, -1120210379);
      c = round(ii, c, d, a, b, words[i + 2], 15, 718787259);
      b = round(ii, b, c, d, a, words[i + 9], 21, -343485551);

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
    var params = new URLSearchParams();
    Object.keys(input).forEach(function (key) {
      var value = input[key];
      if (Array.isArray(value)) {
        value.forEach(function (item) {
          params.append(key, item == null ? '' : String(item));
        });
      } else if (value != null) {
        params.append(key, String(value));
      }
    });
    return params.toString();
  }

  function randomSm4Key() {
    var chars = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
    var bytes = new Uint8Array(16);
    global.crypto.getRandomValues(bytes);
    return Array.prototype.map.call(bytes, function (value) {
      return chars[value % chars.length];
    }).join('');
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
    var map = {};
    headers.forEach(function (value, key) {
      map[key.toLowerCase()] = value;
    });
    return map;
  }

  function resolveSmCrypto(instance) {
    var library = instance && instance.smCrypto
      ? instance.smCrypto
      : (global.TransferSmCrypto || global.smCrypto);
    if (!library || !library.sm2 || !library.sm4) {
      throw new Error('缺少本地 SM2/SM4 实现，请通过 options.smCrypto 或 window.TransferSmCrypto 注入');
    }
    return library;
  }

  async function ensureMultipartMd5(formData) {
    var groupedFiles = {};
    var entries = Array.from(formData.entries());
    var i;
    for (i = 0; i < entries.length; i += 1) {
      var entry = entries[i];
      var name = entry[0];
      var value = entry[1];
      if (isFileLike(value)) {
        if (!groupedFiles[name]) {
          groupedFiles[name] = [];
        }
        groupedFiles[name].push(value);
      }
    }

    var fieldNames = Object.keys(groupedFiles);
    for (i = 0; i < fieldNames.length; i += 1) {
      var fieldName = fieldNames[i];
      var files = groupedFiles[fieldName];
      if (files.length === 1) {
        // 单文件保持兼容旧协议：__md5_<fieldName>
        formData.append('__md5_' + fieldName, md5ArrayBuffer(await files[0].arrayBuffer()));
        continue;
      }
      // 多文件采用索引参数，和后端的 MultipartFile[] 校验规则一一对应。
      for (var fileIndex = 0; fileIndex < files.length; fileIndex += 1) {
        formData.append('__md5_' + fieldName + '__' + fileIndex,
          md5ArrayBuffer(await files[fileIndex].arrayBuffer()));
      }
    }
  }

  function resolveLayui(layuiLike) {
    return layuiLike || global.layui || null;
  }

  function defaultErrorHandler(error, layuiLike) {
    var layui = resolveLayui(layuiLike);
    if (layui && layui.layer && layui.layer.msg) {
      layui.layer.msg(error && error.message ? error.message : '请求失败', { icon: 2 });
      return;
    }
    throw error;
  }

  function TransferEncryptClient(options) {
    this.baseUrl = (options && options.baseUrl) || '';
    this.publicKey = options && options.publicKey;
    this.fetchImpl = (options && options.fetchImpl) || global.fetch.bind(global);
    this.smCrypto = options && options.smCrypto;
  }

  TransferEncryptClient.prototype.encryptPayload = function (plaintext, originalContentType, sm4Key) {
    var library = resolveSmCrypto(this);
    return {
      algorithm: 'SM2_SM4',
      encryptedKey: library.sm2.doEncrypt(sm4Key, this.publicKey, 1),
      encryptedData: library.sm4.encrypt(plaintext, sm4Key, { mode: 'ecb', padding: 'pkcs#7' }),
      contentMd5: md5String(plaintext),
      originalContentType: originalContentType,
      timestamp: Date.now()
    };
  };

  TransferEncryptClient.prototype.decryptEnvelope = function (envelope, sm4Key) {
    var library = resolveSmCrypto(this);
    var plaintext = library.sm4.decrypt(envelope.encryptedData, sm4Key, { mode: 'ecb', padding: 'pkcs#7' });
    if (md5String(plaintext) !== (envelope.contentMd5 || '').toLowerCase()) {
      throw new Error('响应 MD5 校验失败');
    }
    return plaintext;
  };

  TransferEncryptClient.prototype.request = async function (options) {
    if (!this.publicKey) {
      throw new Error('publicKey 未配置');
    }

    var url = this.baseUrl + options.url;
    var method = (options.method || 'GET').toUpperCase();
    var headers = new Headers(options.headers || {});
    var body = options.body;
    var sm4Key = null;

    if (isFormData(body)) {
      await ensureMultipartMd5(body);
    } else if (options.params && (method === 'GET' || method === 'DELETE')) {
      sm4Key = randomSm4Key();
      body = null;
      url = appendQuery(url, normalizeParams(this.encryptPayload(normalizeParams(options.params),
        'application/x-www-form-urlencoded', sm4Key)));
    } else if (options.form) {
      sm4Key = randomSm4Key();
      headers.set('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
      body = normalizeParams(this.encryptPayload(normalizeParams(options.form),
        'application/x-www-form-urlencoded', sm4Key));
    } else if (options.json !== undefined) {
      sm4Key = randomSm4Key();
      headers.set('Content-Type', 'application/json;charset=UTF-8');
      body = JSON.stringify(this.encryptPayload(JSON.stringify(options.json), 'application/json', sm4Key));
    } else if (isPlainObject(body)) {
      sm4Key = randomSm4Key();
      headers.set('Content-Type', 'application/json;charset=UTF-8');
      body = JSON.stringify(this.encryptPayload(JSON.stringify(body), 'application/json', sm4Key));
    }

    var response = await this.fetchImpl(url, {
      method: method,
      headers: headers,
      body: body
    });

    var headerMap = lowerHeaders(response.headers);
    var encrypted = headerMap['x-transfer-encrypted'] === 'true';
    var contentType = headerMap['content-type'] || '';

    if (encrypted || contentType.indexOf('application/json') >= 0) {
      var text = await response.text();
      var envelope = toObject(text);
      if (envelope && envelope.encryptedData && sm4Key) {
        var plaintext = this.decryptEnvelope(envelope, sm4Key);
        if ((envelope.originalContentType || '').indexOf('application/json') >= 0) {
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
      var arrayBuffer = await response.arrayBuffer();
      if (headerMap['x-transfer-content-md5'] && md5ArrayBuffer(arrayBuffer) !== headerMap['x-transfer-content-md5'].toLowerCase()) {
        throw new Error('文件 MD5 校验失败');
      }
      return options.responseType === 'blob' ? new Blob([arrayBuffer]) : arrayBuffer;
    }

    return response.text();
  };

  function TransferEncryptLayuiAdapter(options) {
    // layui 适配器只负责 UI 交互层，底层协议仍复用 TransferEncryptClient。
    this.layui = resolveLayui(options && options.layui);
    this.client = new TransferEncryptClient(options || {});
    this.layer = this.layui && this.layui.layer ? this.layui.layer : null;
  }

  TransferEncryptLayuiAdapter.prototype.request = async function (options) {
    var layer = this.layer;
    var loadingIndex = null;
    if (layer && options && options.loading !== false && layer.load) {
      loadingIndex = layer.load(1, { shade: 0.15 });
    }
    try {
      var result = await this.client.request(options || {});
      if (layer && options && options.successMessage && layer.msg) {
        layer.msg(options.successMessage, { icon: 1 });
      }
      return result;
    } catch (error) {
      if (options && typeof options.onError === 'function') {
        options.onError(error);
      } else {
        defaultErrorHandler(error, this.layui);
      }
      throw error;
    } finally {
      if (layer && loadingIndex !== null && layer.close) {
        layer.close(loadingIndex);
      }
    }
  };

  TransferEncryptLayuiAdapter.prototype.json = function (url, data, options) {
    var requestOptions = options || {};
    requestOptions.url = url;
    requestOptions.method = requestOptions.method || 'POST';
    requestOptions.json = data;
    return this.request(requestOptions);
  };

  TransferEncryptLayuiAdapter.prototype.form = function (url, data, options) {
    var requestOptions = options || {};
    requestOptions.url = url;
    requestOptions.method = requestOptions.method || 'POST';
    requestOptions.form = data;
    return this.request(requestOptions);
  };

  TransferEncryptLayuiAdapter.prototype.get = function (url, params, options) {
    var requestOptions = options || {};
    requestOptions.url = url;
    requestOptions.method = 'GET';
    requestOptions.params = params;
    return this.request(requestOptions);
  };

  TransferEncryptLayuiAdapter.prototype.upload = function (file, options) {
    if (!file) {
      throw new Error('未选择待上传文件');
    }
    return this.uploadMany([file], options);
  };

  TransferEncryptLayuiAdapter.prototype.uploadMany = function (files, options) {
    if (!files || !files.length) {
      throw new Error('未选择待上传文件');
    }
    var uploadOptions = options || {};
    var formData = new FormData();
    var fieldName = uploadOptions.fieldName || 'file';
    for (var index = 0; index < files.length; index += 1) {
      // 同名字段多次 append，后端即可直接按 MultipartFile[] / List<MultipartFile> 绑定。
      formData.append(fieldName, files[index]);
    }

    var extraData = uploadOptions.data || {};
    Object.keys(extraData).forEach(function (key) {
      if (extraData[key] != null) {
        formData.append(key, String(extraData[key]));
      }
    });

    return this.request({
      url: uploadOptions.url,
      method: uploadOptions.method || 'POST',
      body: formData,
      headers: uploadOptions.headers,
      successMessage: uploadOptions.successMessage,
      loading: uploadOptions.loading,
      onError: uploadOptions.onError
    });
  };

  TransferEncryptLayuiAdapter.prototype.submitHandler = function (config) {
    var adapter = this;
    return async function (data) {
      var payload = data && data.field ? data.field : data;
      var response = await adapter.request({
        url: config.url,
        method: config.method || 'POST',
        json: config.json ? payload : undefined,
        form: config.form ? payload : undefined,
        successMessage: config.successMessage,
        loading: config.loading,
        onError: config.onError
      });
      if (typeof config.onSuccess === 'function') {
        config.onSuccess(response, data);
      }
      return false;
    };
  };

  TransferEncryptLayuiAdapter.prototype.bindFormSubmit = function (filter, config) {
    var layui = this.layui;
    if (!layui || !layui.form || !layui.form.on) {
      throw new Error('layui.form 不可用，无法绑定表单提交');
    }
    layui.form.on('submit(' + filter + ')', this.submitHandler(config));
  };

  global.TransferEncryptClient = TransferEncryptClient;
  global.TransferEncryptLayuiAdapter = TransferEncryptLayuiAdapter;
  global.TransferEncryptCreateLayuiAdapter = function (options) {
    return new TransferEncryptLayuiAdapter(options || {});
  };
  global.TransferEncryptRegisterSmCrypto = function (library) {
    global.TransferSmCrypto = library;
  };
}(window));
