# Prompt E05 — Canal WhatsApp (ACL) e Outbox

> Pré-requisito: E04 commitada. **Sessão limpa.**
> **Escopo alterado:** a outbox foi antecipada da E07 para cá. Motivo no §1.

---

**Etapa E05 — Integração com o canal externo, sem contaminar o domínio.**

## 1. Por que a outbox veio para esta etapa

Você observou na E04 que `AFTER_COMMIT` não tem durabilidade nem retry: se o processo morre entre o commit e o listener, a reação não acontece e ninguém fica sabendo. Para timeline isso é aceitável — perder uma linha de diagnóstico não perde dinheiro.

Para "enviar ao WhatsApp" é uma mensagem que o cliente nunca recebe, com o CRM achando que enviou. E o `CLAUDE.md` já proíbe publicar em fila fora da outbox — um listener `AFTER_COMMIT` disparando envio externo é, tecnicamente, publicar fora da outbox.

Você está certo. **A outbox entra aqui.** A tabela `outbox_evento` existe desde a V9 e nunca teve escritor.

## 2. Outbox

- `EnviarMensagemUseCase` grava a linha em `outbox_evento` **na mesma transação** do registro da mensagem
- Publisher separado lê pendentes (`idx_outbox_pendente` já existe), publica e marca `publicado_em`
- Retry com backoff e limite de tentativas; após o limite, a linha vai para inspeção com alarme, **nunca é descartada em silêncio**
- Publisher roda no contexto de **serviço** do RLS, não no do usuário

Teste obrigatório: matar o broker no meio de um envio e religar resulta na mensagem publicada. Nada se perde.

## 3. `status_entrega` — corrigir o otimismo

Hoje `EnviarMensagemUseCase` grava `ENVIADO` antes de qualquer provedor ter aceitado. É dado otimista: se o envio falhar, o atendente vê ✓ numa mensagem que nunca saiu.

Migration nova acrescentando `PENDENTE` ao enum `status_entrega`. Ciclo real:

```
PENDENTE → (provedor aceitou) → ENVIADO → ENTREGUE → LIDO
         → (falhou) → FALHOU
```

Acrescente `FALHOU` também — uma mensagem que não saiu precisa ser distinguível de uma que saiu e não foi lida, tanto na UI quanto no diagnóstico.

## 4. Anti-Corruption Layer

**Provedor da Estrutural Vidros: Meta Cloud API (oficial).** Mas o desenho é para trocar de provedor sem tocar em domínio — outro filho pode usar Z-API, Evolution ou um BSP.

- Porta `CanalGateway` no domínio, resolvida por `Map<String, CanalGateway>` e escolhida por **configuração da instância** (`synapse.canal.whatsapp.provedor`), nunca por `if`
- `MetaCloudApiAdapter` traduz o payload cru para `MensagemRecebida` do domínio. **Nenhum tipo do provedor cruza para `domain`** — quando o formato mudar (e vai), a mudança fica confinada a uma classe
- Circuit breaker (Resilience4j) em toda chamada de saída. Ao abrir, a mensagem permanece na outbox e o sistema entra em modo degradado explícito — não propaga exceção até a tela

**Teste que prova a portabilidade:** um `CanalGateway` falso registrado por configuração faz o fluxo inteiro rodar sem tocar em `domain` nem nos casos de uso. Se algum caso de uso precisar saber qual provedor está ativo, a abstração vazou.

### 4b. Regras da Meta Cloud API que o domínio precisa conhecer

Duas restrições da API oficial não são detalhe de infraestrutura — mudam o que o produto pode fazer, e o `EnviarMensagemUseCase` precisa saber:

- **Janela de 24h.** Fora dela, só mensagem de **template pré-aprovado**. Texto livre é rejeitado.
- **Template aprovado** tem nome, idioma e parâmetros posicionais — não é string arbitrária.

Isso impacta diretamente follow-up, campanhas e fidelização: uma reativação de "90 dias sem contato" é sempre fora da janela, logo sempre template.

Modele agora, mesmo sem usar tudo nesta etapa:

- O domínio distingue `MensagemLivre` de `MensagemTemplate`
- A porta expõe se a janela está aberta para aquele lead (derivável de `ultima_interacao_em`, que a E04 já mantém)
- Tentar enviar texto livre fora da janela falha com erro claro **antes** de chegar ao provedor — não com um 400 traduzido da Meta

Provedores não oficiais não têm essa regra. Portanto ela vive no **adaptador**, não no domínio: o domínio pergunta "posso mandar texto livre para este lead?" e o adaptador responde. Um filho com Z-API responde sempre sim.

## 5. Webhook de entrada

- Validação de assinatura do provedor **antes** de qualquer processamento
- **Idempotência por id externo da mensagem.** Provedores reentregam; sem isso, uma reentrega vira mensagem duplicada na conversa. (A proteção de `GREATEST` que você pôs no `ultima_interacao_em` cobre o timestamp, mas não a duplicata.)
- Responder rápido e processar assíncrono — webhook lento faz o provedor reenviar, o que piora tudo
- O webhook roda **sem usuário autenticado**: contexto de serviço no RLS

## 6. Credencial e troca de número

- `canal_credencial` já existe, versionada, com índice único parcial garantindo uma ativa por canal
- Fluxo de troca: cadastra nova como inativa → valida conexão com o provedor → marca a antiga com `vigente_ate` → ativa a nova → reaponta webhook
- Histórico continua apontando para a credencial antiga; ela não é deletada
- `token_ref` é **referência** ao secret manager, nunca o token

Teste: trocar o número não quebra o histórico nem perde mensagem em trânsito.

## 7. Fechar a brecha do RLS fora de transação

Achado seu na E04: `JdbcTemplate` sem transação pega conexão crua, sem o `SET LOCAL ROLE` do `doBegin`, e roda como dono — enxergando tudo.

**Faça a versão que falha alto:** o adaptador JDBC verifica se há transação ativa (`TransactionSynchronizationManager.isActualTransactionActive()`) e lança se não houver. Meia hora, e a proteção deixa de depender de disciplina.

Regra ArchUnit é a alternativa mais fraca — só pega o que ela sabe procurar. Uma exceção em runtime pega qualquer caminho, inclusive os que ninguém previu. O projeto já foi mordido três vezes por proteção que dependia de alguém lembrar.

Teste de mutação: chame o adaptador fora de transação e confirme que lança.

## 8. Testes

- Envio ponta a ponta com provedor simulado
- Broker derrubado no meio do envio → mensagem publicada ao religar
- Webhook duplicado não cria mensagem duplicada
- Assinatura inválida rejeitada
- Circuit breaker aberto mantém a mensagem na outbox
- Troca de número preserva histórico
- Adaptador JDBC fora de transação lança
- Payload do provedor não aparece em nenhum tipo de `domain`

## Definição de pronto

> **Se as credenciais da Meta ainda não estiverem prontas:** faça tudo contra um provedor simulado e marque só o item de envio real como pendente. Nada mais nesta etapa depende de credencial — outbox, ACL, idempotência, ciclo de entrega e a trava de transação são todos testáveis sem provedor.

- [ ] Mensagem enviada pelo CRM chega no WhatsApp real *(depende de credencial)*
- [ ] Mensagem recebida cria/atualiza atendimento
- [ ] Troca de provedor por configuração, provada com gateway falso
- [ ] Texto livre fora da janela de 24h falha antes de chegar ao provedor
- [ ] Outbox com retry, alarme e sem descarte silencioso
- [ ] `PENDENTE` e `FALHOU` no ciclo de entrega
- [ ] Webhook idempotente e com assinatura validada
- [ ] Adaptador JDBC exige transação ativa
- [ ] CI verde

Commit: `feat: canal whatsapp com outbox e acl`.

Ao terminar, me diga quanto tempo o envio leva do clique até o provedor aceitar — é o número que define se a E06 precisa de optimistic UI ou se dá para esperar a confirmação.
