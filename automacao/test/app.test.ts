import assert from 'node:assert/strict';
import test from 'node:test';
import { criarAplicacao } from '../src/app.js';
import { carregarConfiguracao } from '../src/config.js';

test('healthcheck responde sem banco, Redis ou canal de mensagens', async () => {
  const app = criarAplicacao(carregarConfiguracao({}));
  try {
    const resposta = await app.inject({ method: 'GET', url: '/health/liveness' });
    assert.equal(resposta.statusCode, 200);
    assert.deepEqual(resposta.json(), {
      status: 'UP',
      capacidades: { followUpHabilitado: false },
    });
  } finally {
    await app.close();
  }
});
