import assert from 'node:assert/strict';
import test from 'node:test';
import { carregarConfiguracao } from '../src/config.js';

test('follow-up desabilitado por padrao e servidor restrito ao localhost', () => {
  assert.deepEqual(carregarConfiguracao({}), {
    host: '127.0.0.1',
    port: 3100,
    automacaoHabilitada: false,
    followUpHabilitado: false,
  });
});

for (const geral of [undefined, '', 'false', 'true']) {
  for (const fluxo of [undefined, '', 'false', 'true']) {
    test(`follow-up depende das duas chaves: geral=${geral}, fluxo=${fluxo}`, () => {
      const config = carregarConfiguracao({
        AUTOMACAO_HABILITADA: geral,
        AUTOMACAO_FOLLOWUP_HABILITADO: fluxo,
      });
      assert.equal(config.automacaoHabilitada, geral === 'true');
      assert.equal(config.followUpHabilitado, geral === 'true' && fluxo === 'true');
    });
  }
}

for (const nome of ['AUTOMACAO_HABILITADA', 'AUTOMACAO_FOLLOWUP_HABILITADO']) {
  for (const valor of ['1', '0', 'TRUE', 'False', ' true ', 'sim']) {
    test(`rejeita ${nome}=${valor} mesmo com outra chave desligada`, () => {
      assert.throws(() => carregarConfiguracao({ [nome]: valor }), new RegExp(`${nome} deve ser true ou false`));
    });
  }
}

test('valores invalidos falham em vez de ativar capacidade por engano', () => {
  assert.throws(() => carregarConfiguracao({ AUTOMACAO_FOLLOWUP_HABILITADO: '1' }), /true ou false/);
  assert.throws(() => carregarConfiguracao({ AUTOMACAO_PORT: '0' }), /inteiro entre 1 e 65535/);
  assert.throws(() => carregarConfiguracao({ AUTOMACAO_PORT: 'abc' }), /inteiro entre 1 e 65535/);
});
