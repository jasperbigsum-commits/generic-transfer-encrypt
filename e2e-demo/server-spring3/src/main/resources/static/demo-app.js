(function () {
  'use strict';

  var resultElement = document.getElementById('result');
  var metaElement = document.getElementById('meta');
  var actionButtons = document.querySelectorAll('button');
  var client = null;
  var layuiAdapter = null;
  var layuiTable = null;
  var layuiForm = null;

  function setMeta(text, isError) {
    metaElement.textContent = text;
    metaElement.className = isError ? 'meta error' : 'meta';
  }

  function setResult(value) {
    if (typeof value === 'string') {
      resultElement.textContent = value;
      return;
    }
    resultElement.textContent = JSON.stringify(value, null, 2);
  }

  function setBusy(busy) {
    Array.prototype.forEach.call(actionButtons, function (button) {
      button.disabled = busy;
    });
  }

  function setResultAndMeta(action, payload) {
    setResult(payload);
    setMeta('执行成功: ' + action);
  }

  function setError(action, error) {
    setResult({
      message: error.message,
      stack: error.stack || null
    });
    setMeta('执行失败: ' + action, true);
  }

  async function init() {
    setBusy(true);
    try {
      if (typeof TransferEncryptRegisterSmCrypto !== 'function') {
        throw new Error('transfer-encrypt.js 未正确加载');
      }
      TransferEncryptRegisterSmCrypto(window.smCrypto);
      var response = await fetch('/demo/public-key');
      var payload = await response.json();
      client = new TransferEncryptClient({
        baseUrl: payload.baseUrl,
        publicKey: payload.publicKey,
        smCrypto: window.smCrypto
      });
      layui.use(['table', 'form'], function () {
        layuiAdapter = TransferEncryptCreateLayuiAdapter({
          layui: layui,
          baseUrl: payload.baseUrl,
          publicKey: payload.publicKey,
          smCrypto: window.smCrypto
        });
        layuiTable = layui.table;
        layuiForm = layui.form;
        layuiAdapter.installAjaxBridge();
        layuiAdapter.installTableBridge();
        installDemoFormBridge();
        installTableSearchFormBridge();
        renderDemoTable();
      });
      setMeta('初始化成功，已拿到服务端公钥。');
      setResult(payload);
    } catch (error) {
      setMeta(error.message || '初始化失败', true);
      setResult(String(error && error.stack ? error.stack : error));
    } finally {
      setBusy(false);
    }
  }

  async function handleJson() {
    var value = document.getElementById('jsonName').value;
    return client.request({
      url: '/api/json',
      method: 'POST',
      json: {
        name: value,
        from: 'e2e-demo'
      }
    });
  }

  async function handleQuery() {
    var value = document.getElementById('queryName').value;
    return client.request({
      url: '/api/query',
      method: 'GET',
      params: {
        name: value,
        channel: 'query-demo'
      }
    });
  }

  function handleAjaxBridge() {
    if (!layuiAdapter || !layui || !layui.$ || typeof layui.$.ajax !== 'function') {
      return Promise.reject(new Error('Layui ajax bridge 尚未初始化'));
    }
    var value = document.getElementById('ajaxName').value;
    return new Promise(function (resolve, reject) {
      layui.$.ajax({
        url: '/api/form',
        type: 'POST',
        data: {
          name: value,
          channel: 'layui-ajax-bridge'
        },
        transferEncrypt: true,
        loading: false,
        success: function (response) {
          resolve(response);
        },
        error: function (xhr, status, error) {
          void xhr;
          reject(error || new Error(status || 'ajax bridge request failed'));
        }
      });
    });
  }

  async function handleUpload() {
    var input = document.getElementById('uploadFile');
    if (!input.files.length) {
      throw new Error('请先选择单文件');
    }
    var formData = new FormData();
    formData.append('file', input.files[0]);
    formData.append('bizType', 'single-upload');
    return client.request({
      url: '/api/upload',
      method: 'POST',
      body: formData
    });
  }

  async function handleUploadMulti() {
    var input = document.getElementById('uploadFiles');
    if (!input.files.length) {
      throw new Error('请先选择多个文件');
    }
    var formData = new FormData();
    for (var index = 0; index < input.files.length; index += 1) {
      formData.append('files', input.files[index]);
    }
    return client.request({
      url: '/api/upload/multi',
      method: 'POST',
      body: formData
    });
  }

  async function handleDownload() {
    var arrayBuffer = await client.request({
      url: '/api/download',
      method: 'GET',
      responseType: 'arrayBuffer'
    });
    var blob = new Blob([arrayBuffer], { type: 'application/octet-stream' });
    var url = URL.createObjectURL(blob);
    var link = document.createElement('a');
    link.href = url;
    link.download = 'demo-download.txt';
    link.click();
    URL.revokeObjectURL(url);
    return {
      status: 'downloaded',
      bytes: arrayBuffer.byteLength
    };
  }

  async function handleFeign() {
    var value = document.getElementById('feignName').value;
    return client.request({
      url: '/api/feign/json',
      method: 'POST',
      json: {
        name: value,
        from: 'browser-via-feign'
      }
    });
  }

  var handlers = {
    json: handleJson,
    query: handleQuery,
    ajaxBridge: handleAjaxBridge,
    upload: handleUpload,
    uploadMulti: handleUploadMulti,
    download: handleDownload,
    feign: handleFeign
  };

  function renderDemoTable() {
    if (!layuiTable || !layuiAdapter) {
      return;
    }
    layuiTable.render({
      elem: '#encrypt-table',
      id: 'encrypt-table',
      url: '/api/table',
      method: 'GET',
      where: {
        keyword: document.getElementById('tableKeyword').value
      },
      page: false,
      cols: [[
        { field: 'id', title: 'ID', width: 120 },
        { field: 'name', title: '姓名', minWidth: 180 }
      ]]
    });
  }

  function reloadDemoTable(keyword) {
    if (!layuiTable) {
      return;
    }
    layuiTable.reload('encrypt-table', {
      url: '/api/table',
      method: 'GET',
      where: {
        keyword: keyword
      },
      page: false
    });
  }

  function installTableSearchFormBridge() {
    if (!layuiForm) {
      return;
    }
    layuiForm.on('submit(table-search-submit)', function (data) {
      var keyword = data && data.field ? data.field.keyword : '';
      try {
        reloadDemoTable(keyword);
        setResultAndMeta('table-search-submit', {
          status: 'reloaded',
          source: 'layui.form.on + layui.table.reload',
          keyword: keyword
        });
      } catch (error) {
        setError('table-search-submit', error);
      }
      return false;
    });
  }

  function installDemoFormBridge() {
    if (!layuiForm || !layuiAdapter) {
      return;
    }
    layuiAdapter.installFormBridge();
    layuiForm.on('submit(form-submit)', function () {
      setBusy(true);
      setMeta('执行中: form-submit');
      return {
        url: '/api/form',
        form: {
          name: document.getElementById('formName').value,
          channel: 'layui-form-bridge'
        },
        onSuccess: function (response) {
          setResultAndMeta('form-submit', response);
        },
        onError: function (error) {
          setError('form-submit', error);
        },
        complete: function () {
          setBusy(false);
        }
      };
    });
  }

  Array.prototype.forEach.call(actionButtons, function (button) {
    button.addEventListener('click', async function () {
      var action = button.getAttribute('data-action');
      var handler = handlers[action];
      if (!handler) {
        return;
      }
      setBusy(true);
      setMeta('执行中: ' + action);
      try {
        var result = await handler();
        setResultAndMeta(action, result);
      } catch (error) {
        setError(action, error);
      } finally {
        setBusy(false);
      }
    });
  });

  document.getElementById('reloadTable').addEventListener('click', function () {
    try {
      reloadDemoTable(document.getElementById('tableKeyword').value);
      setResultAndMeta('reloadTable', {
        status: 'reloaded',
        source: 'plain button + layui.table.reload',
        keyword: document.getElementById('tableKeyword').value
      });
    } catch (error) {
      setError('reloadTable', error);
    }
  });

  document.getElementById('loadProfile').addEventListener('click', async function () {
    if (!layuiAdapter) {
      setMeta('Layui adapter 尚未初始化', true);
      return;
    }
    try {
      var response = await layuiAdapter.renderForm('profile-form', {
        url: '/api/profile',
        method: 'GET',
        params: {
          userId: document.getElementById('profileUserId').value
        },
        type: 'select'
      });
      setResultAndMeta('loadProfile', response);
    } catch (error) {
      setError('loadProfile', error);
    }
  });

  init();
}());
