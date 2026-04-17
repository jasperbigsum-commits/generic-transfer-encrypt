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

  function asciiToHex(text) {
    return Array.prototype.map.call(text, function (char) {
      return char.charCodeAt(0).toString(16).padStart(2, '0');
    }).join('');
  }

  function utf8Encode(text) {
    return utf8Bytes(text);
  }

  function utf8Decode(bytes) {
    return new TextDecoder().decode(bytes);
  }

  function encodeBase64Url(bytes) {
    var binary = '';
    for (var index = 0; index < bytes.length; index += 1) {
      binary += String.fromCharCode(bytes[index]);
    }
    return global.btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
  }

  function decodeBase64Url(text) {
    var normalized = String(text).replace(/-/g, '+').replace(/_/g, '/');
    while (normalized.length % 4 !== 0) {
      normalized += '=';
    }
    var binary = global.atob(normalized);
    var bytes = new Uint8Array(binary.length);
    for (var index = 0; index < binary.length; index += 1) {
      bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
  }

  function resolveSparkMd5() {
    var spark = global.SparkMD5;
    if (!spark || !spark.ArrayBuffer || typeof spark.hash !== 'function') {
      throw new Error('缺少 MD5 实现，请先加载 ./vendor/md5.js');
    }
    return spark;
  }

  function md5ArrayBuffer(buffer) {
    var bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
    return resolveSparkMd5().ArrayBuffer.hash(bytes.buffer.slice(
      bytes.byteOffset,
      bytes.byteOffset + bytes.byteLength
    ));
  }

  function md5String(text) {
    return resolveSparkMd5().hash(String(text));
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

  function encodeTransferPayload(envelope) {
    return encodeBase64Url(utf8Encode(JSON.stringify(envelope)));
  }

  function decodeTransferPayload(payload) {
    return toObject(utf8Decode(decodeBase64Url(payload)));
  }

  function buildTransportWrapper(envelope, originalContentType) {
    return {
      transferPayload: encodeTransferPayload(envelope),
      originalContentType: originalContentType
    };
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

  function normalizePublicKey(publicKey) {
    var raw = String(publicKey || '').replace(/\s+/g, '');
    if (!raw) {
      throw new Error('publicKey 未配置');
    }

    // 裸公钥点缺少未压缩前缀时，自动补 04。
    if (/^[0-9a-fA-F]{128}$/.test(raw)) {
      return '04' + raw;
    }

    // 已经是浏览器 sm-crypto 常用的未压缩公钥点。
    if (/^04[0-9a-fA-F]{128}$/.test(raw)) {
      return raw;
    }

    // 常见 DER/X509 十六进制前缀，直接给出可理解错误。
    if (/^(30|3059|3081|3082)[0-9a-fA-F]+$/.test(raw)) {
      throw new Error('publicKey 格式不兼容：当前是 DER/X509 十六进制，请改为浏览器可用的未压缩公钥点格式 04 + X + Y');
    }

    if (/-----BEGIN PUBLIC KEY-----/.test(String(publicKey || ''))) {
      throw new Error('publicKey 格式不兼容：当前是 PEM，请改为浏览器可用的未压缩公钥点格式 04 + X + Y');
    }

    throw new Error('publicKey 格式不兼容：期望 128 位裸公钥点或 130 位未压缩公钥点（04 + X + Y）');
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

  function hasOwn(object, key) {
    return Object.prototype.hasOwnProperty.call(object, key);
  }

  function mergeHeaders(headers, extraHeaders) {
    var merged = {};
    var key;
    headers = headers || {};
    extraHeaders = extraHeaders || {};
    for (key in headers) {
      if (hasOwn(headers, key)) {
        merged[key] = headers[key];
      }
    }
    for (key in extraHeaders) {
      if (hasOwn(extraHeaders, key)) {
        merged[key] = extraHeaders[key];
      }
    }
    return merged;
  }

  function lowerHeaderValue(headers, name) {
    if (!headers) {
      return null;
    }
    var names = Object.keys(headers);
    for (var index = 0; index < names.length; index += 1) {
      if (names[index].toLowerCase() === name) {
        return headers[names[index]];
      }
    }
    return null;
  }

  function omitTransferHeaders(headers) {
    var source = headers || {};
    var result = {};
    Object.keys(source).forEach(function (key) {
      var lowerKey = key.toLowerCase();
      if (lowerKey === 'x-transfer-encrypt-request' || lowerKey === 'x-requested-with') {
        return;
      }
      result[key] = source[key];
    });
    return result;
  }

  function createLayuiAjaxResponseStub(headers, body) {
    var lower = {};
    Object.keys(headers || {}).forEach(function (key) {
      lower[key.toLowerCase()] = headers[key];
    });
    return {
      readyState: 4,
      status: 200,
      responseJSON: body,
      responseText: typeof body === 'string' ? body : JSON.stringify(body),
      getResponseHeader: function (name) {
        return lower[String(name).toLowerCase()] || null;
      },
      getAllResponseHeaders: function () {
        return Object.keys(lower).map(function (key) {
          return key + ': ' + lower[key];
        }).join('\r\n');
      },
      abort: function () {}
    };
  }

  function cloneConfig(config) {
    var cloned = {};
    Object.keys(config || {}).forEach(function (key) {
      cloned[key] = config[key];
    });
    return cloned;
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
    this.publicKey = normalizePublicKey(options && options.publicKey);
    this.fetchImpl = (options && options.fetchImpl) || global.fetch.bind(global);
    this.smCrypto = options && options.smCrypto;
  }

  TransferEncryptClient.prototype.encryptPayload = function (plaintext, originalContentType, sm4Key) {
    var library = resolveSmCrypto(this);
    return {
      encryptedKey: library.sm2.doEncrypt(sm4Key, this.publicKey, 1),
      encryptedData: library.sm4.encrypt(plaintext, asciiToHex(sm4Key), { mode: 'ecb', padding: 'pkcs#7' }),
      contentMd5: md5String(plaintext),
      timestamp: Date.now()
    };
  };

  TransferEncryptClient.prototype.decryptEnvelope = function (envelope, sm4Key) {
    var library = resolveSmCrypto(this);
    var plaintext = library.sm4.decrypt(envelope.encryptedData, asciiToHex(sm4Key), { mode: 'ecb', padding: 'pkcs#7' });
    if (md5String(plaintext) !== (envelope.contentMd5 || '').toLowerCase()) {
      throw new Error('响应 MD5 校验失败');
    }
    return plaintext;
  };

  TransferEncryptClient.prototype.request = async function (options) {
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
      url = appendQuery(url, normalizeParams(buildTransportWrapper(
        this.encryptPayload(normalizeParams(options.params), 'application/x-www-form-urlencoded', sm4Key),
        'application/x-www-form-urlencoded'
      )));
    } else if (options.form) {
      sm4Key = randomSm4Key();
      headers.set('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
      body = normalizeParams(buildTransportWrapper(
        this.encryptPayload(normalizeParams(options.form), 'application/x-www-form-urlencoded', sm4Key),
        'application/x-www-form-urlencoded'
      ));
    } else if (options.json !== undefined) {
      sm4Key = randomSm4Key();
      headers.set('Content-Type', 'application/json;charset=UTF-8');
      body = JSON.stringify(buildTransportWrapper(
        this.encryptPayload(JSON.stringify(options.json), 'application/json', sm4Key),
        'application/json'
      ));
    } else if (isPlainObject(body)) {
      sm4Key = randomSm4Key();
      headers.set('Content-Type', 'application/json;charset=UTF-8');
      body = JSON.stringify(buildTransportWrapper(
        this.encryptPayload(JSON.stringify(body), 'application/json', sm4Key),
        'application/json'
      ));
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
      var wrapper = toObject(text);
      var envelope = wrapper;
      if (wrapper && wrapper.transferPayload) {
        envelope = decodeTransferPayload(wrapper.transferPayload);
      }
      if (envelope && envelope.encryptedData && sm4Key) {
        var plaintext = this.decryptEnvelope(envelope, sm4Key);
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

  TransferEncryptLayuiAdapter.prototype.resolveRequestOptions = function (config) {
    var requestConfig = config || {};
    var method = (requestConfig.method || requestConfig.type || 'GET').toUpperCase();
    var headers = omitTransferHeaders(requestConfig.headers);
    var requestOptions = {
      url: requestConfig.url,
      method: method,
      headers: headers
    };

    if (requestConfig.body) {
      requestOptions.body = requestConfig.body;
      return requestOptions;
    }

    if (method === 'GET' || method === 'DELETE') {
      requestOptions.params = requestConfig.params || requestConfig.data || requestConfig.where;
      return requestOptions;
    }

    if (requestConfig.json !== undefined) {
      requestOptions.json = requestConfig.json;
      return requestOptions;
    }

    if (requestConfig.form !== undefined) {
      requestOptions.form = requestConfig.form;
      return requestOptions;
    }

    if (requestConfig.encryptBodyType === 'json') {
      requestOptions.json = requestConfig.data || requestConfig.where;
      return requestOptions;
    }

    if (requestConfig.encryptBodyType === 'form') {
      requestOptions.form = requestConfig.data || requestConfig.where;
      return requestOptions;
    }

    var contentType = String(requestConfig.contentType || lowerHeaderValue(headers, 'content-type') || '').toLowerCase();
    if (contentType.indexOf('application/json') >= 0) {
      requestOptions.json = requestConfig.data || requestConfig.where;
      return requestOptions;
    }

    requestOptions.form = requestConfig.data || requestConfig.where;
    return requestOptions;
  };

  TransferEncryptLayuiAdapter.prototype.handleMarkedLayuiAjax = function (ajaxOptions) {
    var adapter = this;
    var requestOptions = adapter.resolveRequestOptions(ajaxOptions);
    var responseHeaders = { 'content-type': 'application/json;charset=UTF-8' };
    var xhr = createLayuiAjaxResponseStub(responseHeaders, null);

    adapter.client.request(requestOptions).then(function (responseBody) {
      xhr.responseJSON = responseBody;
      xhr.responseText = typeof responseBody === 'string' ? responseBody : JSON.stringify(responseBody);
      if (typeof ajaxOptions.success === 'function') {
        ajaxOptions.success(responseBody, 'success', xhr);
      }
      if (typeof ajaxOptions.complete === 'function') {
        ajaxOptions.complete(xhr, 'success');
      }
    }).catch(function (error) {
      xhr.status = 500;
      xhr.responseJSON = { message: error && error.message ? error.message : '请求失败' };
      xhr.responseText = JSON.stringify(xhr.responseJSON);
      if (typeof ajaxOptions.error === 'function') {
        ajaxOptions.error(xhr, 'error', error);
      } else {
        defaultErrorHandler(error, adapter.layui);
      }
      if (typeof ajaxOptions.complete === 'function') {
        ajaxOptions.complete(xhr, 'error');
      }
    });

    return xhr;
  };

  TransferEncryptLayuiAdapter.prototype.installTableBridge = function () {
    var layui = this.layui;
    if (!layui || !layui.table) {
      throw new Error('layui.table 不可用，无法安装 table 自动加密桥接');
    }
    if (!layui.$ || typeof layui.$.ajax !== 'function') {
      throw new Error('layui.$.ajax 不可用，无法桥接 table 的 URL 请求');
    }

    var adapter = this;
    if (!layui.$.ajax.__transferEncryptWrapped) {
      var originalAjax = layui.$.ajax;
      layui.$.ajax = function (options) {
        var ajaxOptions = options || {};
        var marker = lowerHeaderValue(ajaxOptions.headers, 'x-transfer-encrypt-request');
        if (marker !== 'true' && ajaxOptions.transferEncrypt !== true) {
          return originalAjax.apply(this, arguments);
        }
        return layui.$.ajax.__transferEncryptAdapter.handleMarkedLayuiAjax(ajaxOptions);
      };
      layui.$.ajax.__transferEncryptWrapped = true;
      layui.$.ajax.__transferEncryptOriginal = originalAjax;
    }
    layui.$.ajax.__transferEncryptAdapter = adapter;

    function markConfig(config) {
      var tableConfig = cloneConfig(config || {});
      if (!tableConfig.url || tableConfig.transferEncrypt === false) {
        return tableConfig;
      }
      tableConfig.headers = mergeHeaders(tableConfig.headers, {
        'X-Transfer-Encrypt-Request': 'true'
      });
      if (!tableConfig.method && !tableConfig.type) {
        tableConfig.method = 'GET';
      }
      return tableConfig;
    }

    if (layui.table.render && !layui.table.render.__transferEncryptWrapped) {
      var originalRender = layui.table.render;
      layui.table.render = function (config) {
        return originalRender.call(this, markConfig(config));
      };
      layui.table.render.__transferEncryptWrapped = true;
      layui.table.render.__transferEncryptOriginal = originalRender;
    }

    if (layui.table.reload && !layui.table.reload.__transferEncryptWrapped) {
      var originalReload = layui.table.reload;
      layui.table.reload = function (id, config, deep) {
        return originalReload.call(this, id, markConfig(config), deep);
      };
      layui.table.reload.__transferEncryptWrapped = true;
      layui.table.reload.__transferEncryptOriginal = originalReload;
    }

    if (layui.table.reloadData && !layui.table.reloadData.__transferEncryptWrapped) {
      var originalReloadData = layui.table.reloadData;
      layui.table.reloadData = function (id, config, deep) {
        return originalReloadData.call(this, id, markConfig(config), deep);
      };
      layui.table.reloadData.__transferEncryptWrapped = true;
      layui.table.reloadData.__transferEncryptOriginal = originalReloadData;
    }

    return layui.table;
  };

  TransferEncryptLayuiAdapter.prototype.renderForm = async function (filter, config) {
    var layui = this.layui;
    if (!layui || !layui.form || !layui.form.val) {
      throw new Error('layui.form 不可用，无法自动渲染表单');
    }

    var requestConfig = cloneConfig(config || {});
    requestConfig.url = requestConfig.url;
    var response = await this.request(this.resolveRequestOptions(requestConfig));
    var formData = typeof requestConfig.mapResponse === 'function'
      ? requestConfig.mapResponse(response)
      : response;

    layui.form.val(filter, formData || {});
    if (layui.form.render) {
      layui.form.render(requestConfig.type);
    }
    if (typeof requestConfig.onSuccess === 'function') {
      requestConfig.onSuccess(formData, response);
    }
    return response;
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
