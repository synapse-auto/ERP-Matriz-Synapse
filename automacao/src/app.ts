import Fastify from 'fastify';
import type { ConfiguracaoAutomacao } from './config.js';

export function criarAplicacao(configuracao: ConfiguracaoAutomacao) {
  const app = Fastify({ logger: true });

  app.get('/health/liveness', async () => ({
    status: 'UP',
    capacidades: { followUpHabilitado: configuracao.followUpHabilitado },
  }));

  return app;
}
