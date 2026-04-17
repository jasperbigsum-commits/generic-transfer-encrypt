<template>
  <main class="page">
    <section class="panel">
      <h1>Vue 3 Transfer Encrypt Demo</h1>
      <p class="desc">点击按钮后，请求会按仓库统一协议自动封装。</p>
      <div class="actions">
        <button :disabled="loading" @click="loadData">
          {{ loading ? '请求中...' : '发送加密 GET 请求' }}
        </button>
      </div>
      <pre class="result">{{ pretty }}</pre>
      <p v-if="error" class="error">{{ error.message }}</p>
    </section>
  </main>
</template>

<script setup>
import { computed } from 'vue';

import { useTransferEncryptRequest } from '../transfer-encrypt-vue3.js';

const { loading, data, error, execute } = useTransferEncryptRequest(() => ({
  url: '/api/query',
  method: 'GET',
  params: { name: 'vue3-demo' }
}));

const pretty = computed(() => JSON.stringify(data.value, null, 2));

async function loadData() {
  await execute();
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 32px;
  background:
    radial-gradient(circle at top right, rgba(11, 114, 133, 0.18), transparent 35%),
    linear-gradient(135deg, #f8fafc, #e6f4f1);
}

.panel {
  width: min(720px, 100%);
  padding: 32px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.12);
}

h1 {
  margin: 0 0 12px;
  color: #0f172a;
}

.desc {
  margin: 0 0 20px;
  color: #334155;
}

.actions button {
  padding: 12px 18px;
  border: none;
  border-radius: 999px;
  background: #0b7285;
  color: white;
  cursor: pointer;
}

.actions button:disabled {
  opacity: 0.7;
  cursor: wait;
}

.result {
  margin-top: 20px;
  min-height: 140px;
  padding: 16px;
  border-radius: 16px;
  background: #0f172a;
  color: #dbeafe;
  overflow: auto;
}

.error {
  color: #b91c1c;
}
</style>
