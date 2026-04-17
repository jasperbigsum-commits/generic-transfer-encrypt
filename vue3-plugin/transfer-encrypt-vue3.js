import { computed, inject, provide, ref, unref } from 'vue';

import {
  TransferEncryptClient,
  createTransferEncryptClient,
  createTransferEncryptVueAdapter
} from './transfer-encrypt-vue3-core.js';

export const TransferEncryptClientKey = Symbol('TransferEncryptClient');
export const TransferEncryptAdapterKey = Symbol('TransferEncryptAdapter');

function resolveClient(input) {
  if (input instanceof TransferEncryptClient) {
    return input;
  }
  return createTransferEncryptClient(input || {});
}

function resolveAdapter(input) {
  if (input && input.client instanceof TransferEncryptClient && typeof input.request === 'function') {
    return input;
  }
  if (input instanceof TransferEncryptClient) {
    return createTransferEncryptVueAdapter({ client: input });
  }
  return createTransferEncryptVueAdapter(input || {});
}

export function createTransferEncryptPlugin(options = {}) {
  const adapter = resolveAdapter(options.adapter || options);
  return {
    install(app) {
      app.provide(TransferEncryptClientKey, adapter.client);
      app.provide(TransferEncryptAdapterKey, adapter);
      app.config.globalProperties.$transferEncrypt = adapter.client;
      app.config.globalProperties.$transferEncryptAdapter = adapter;
    },
    client: adapter.client,
    adapter
  };
}

export function provideTransferEncrypt(input) {
  const client = resolveClient(input);
  const adapter = createTransferEncryptVueAdapter({ client });
  provide(TransferEncryptClientKey, client);
  provide(TransferEncryptAdapterKey, adapter);
  return adapter;
}

export function useTransferEncrypt() {
  const client = inject(TransferEncryptClientKey, null);
  if (!client) {
    throw new Error('未找到 TransferEncryptClient，请先在 app.use(...) 或 provideTransferEncrypt(...) 中注入');
  }
  return client;
}

export function useTransferEncryptAdapter() {
  const adapter = inject(TransferEncryptAdapterKey, null);
  if (!adapter) {
    throw new Error('未找到 TransferEncryptAdapter，请先在 app.use(...) 或 provideTransferEncrypt(...) 中注入');
  }
  return adapter;
}

export function useTransferEncryptRequest(source) {
  const adapter = useTransferEncryptAdapter();
  const loading = ref(false);
  const data = ref(null);
  const error = ref(null);

  async function execute(overrideOptions) {
    loading.value = true;
    error.value = null;
    try {
      const resolvedSource = typeof source === 'function'
        ? source(overrideOptions, adapter)
        : (overrideOptions || source);
      const requestOptions = unref(resolvedSource) || {};
      const result = await adapter.request(requestOptions);
      data.value = result;
      return result;
    } catch (requestError) {
      error.value = requestError;
      throw requestError;
    } finally {
      loading.value = false;
    }
  }

  return {
    adapter,
    client: adapter.client,
    loading: computed(() => loading.value),
    data,
    error,
    execute
  };
}

export {
  TransferEncryptClient,
  createTransferEncryptClient,
  createTransferEncryptVueAdapter
};
