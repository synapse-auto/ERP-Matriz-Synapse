import assert from 'node:assert/strict';
import test from 'node:test';
import { carregarConfiguracao } from '../src/config.js';

test('follow-up desabilitado por padrao e servidor restrito ao localhost', () => {
  assert.deepEqual(carregarConfiguracao({}), {
    host: '127.0.0.1',
    port: 3100,
    followUpHabilitado: false,
  });
});

test('feature toggle habilita e desabilita o follow-up por instancia', () => {
  assert.equal(carregarConfiguracao({ AUTOMACAO_FOLLOWUP_HABILITADO: 'true' }).followUpHabilitado, true);
  assert.equal(carregarConfiguracao({ AUTOMACAO_FOLLOWUP_HABILITADO: 'false' }).followUpHabilitado, false);
});

test('valores invalidos falham em vez de ativar capacidade por engano', () => {
  assert.throws(() => carregarConfiguracao({ AUTOMACAO_FOLLOWUP_HABILITADO: '1' }), /true ou false/);
  assert.throws(() => carregarConfiguracao({ AUTOMACAO_PORT: '0' }), /inteiro entre 1 e 65535/);
  assert.throws(() => carregarConfiguracao({ AUTOMACAO_PORT: 'abc' }), /inteiro entre 1 e 65535/);
});
