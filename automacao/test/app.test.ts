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
      capacidades: { automacaoHabilitada: false, followUpHabilitado: false },
    });
  } finally {
    await app.close();
  }
});

for (const geral of ['false', 'true']) {
  for (const fluxo of ['false', 'true']) {
    test(`HTTP permanece disponivel e mostra estado efetivo: ${geral}/${fluxo}`, async () => {
      const app = criarAplicacao(carregarConfiguracao({
        AUTOMACAO_HABILITADA: geral,
        AUTOMACAO_FOLLOWUP_HABILITADO: fluxo,
      }));
      try {
        const resposta = await app.inject({ method: 'GET', url: '/health/liveness' });
        assert.equal(resposta.statusCode, 200);
        assert.deepEqual(resposta.json(), {
          status: 'UP',
          capacidades: {
            automacaoHabilitada: geral === 'true',
            followUpHabilitado: geral === 'true' && fluxo === 'true',
          },
        });
      } finally {
        await app.close();
      }
    });
  }
}
