import { createApp } from 'vue';

import App from './App.vue';
import { createTransferEncryptPlugin } from '../transfer-encrypt-vue3.js';

const smCrypto = window.smCrypto;

const app = createApp(App);

app.use(createTransferEncryptPlugin({
  baseUrl: 'http://localhost:8080',
  publicKey: '服务端SM2公钥',
  smCrypto,
  fetchImpl: window.fetch.bind(window)
}));

app.mount('#app');
