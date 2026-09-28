# Gestão: Equipe, Permissões e Exceções por usuário

Entrega da área **Gestão** (`/gestao`): integrantes, permissões por perfil e exceções individuais,
com persistência, autorização no backend, revalidação de sessão, auditoria e tela. Referências
visuais: `Gestao - Estrutural Vidros.html` e as capturas `01`–`06` do pacote de 26/09. O protótipo
é referência de **estrutura e interação**, não de segurança: as divergências estão na seção 11.

## 1. Regra de ouro

**O papel continua sendo o teto.** Cada capacidade do catálogo tem como teto exatamente o
`hasAnyRole(...)` que o caso de uso já tinha antes desta etapa. A configuração de Gestão só
**restringe dentro do teto** — nunca amplia. Ampliar (por exemplo, deixar atendente editar
template, hoje exclusivo de SUBGESTOR+) é decisão de produto, não um interruptor, e fica na
seção 13.

Exceção única, pedida explicitamente: **delegação ao SUBGESTOR** (criar/editar/desativar
atendente, senha provisória de atendente, exceções de atendentes). Essas capacidades estão no
teto do SUBGESTOR, mas nascem **negadas**; só um GESTOR ou ADMINISTRADOR as concede.

Consequência: uma instância que nunca abriu a tela de Gestão tem exatamente o acesso operacional
de antes. A V83 não semeia valores; o padrão vem do catálogo em código
(`PoliticaDePermissoesTest.padraoPreservaAcessoOperacional` prova, papel a papel).

## 2. Inventário de capacidades

Legenda de papéis: A = ATENDENTE, S = SUBGESTOR, G = GESTOR, D = ADMINISTRADOR.
"Padrão" = efetivo sem nada salvo. Nível = nível mínimo do módulo que libera a ação.

### 2.1 Incluídas no catálogo

| Id | Módulo | Nível | Ponto de entrada | Caso de uso / método | Teto (antes) | Padrão | Delegável | Sensível |
|---|---|---|---|---|---|---|---|---|
| `atendimentos.ver` | Atendimentos | — | todas as leituras de atendimento | Specification + RLS (RN-CRM-01) | A S G D | **estrutural**: A = Meus (+IA), demais = Todos | — | — |
| `atendimentos.responder` | Atendimentos | Editar | `POST /atendimentos/mensagens`, `/mensagens/template`, `/{id}/mensagens/midia`, `/{id}/mensagens/{m}/encaminhamentos`, `PUT/DELETE .../reacao` | `EnviarMensagemUseCase` (8 humanos), `EnviarMidiaUseCase` (5), `EncaminharMensagemUseCase` (2), `Definir/RemoverReacaoDaMensagemUseCase` | A S G D | todos | sim | não |
| `atendimentos.iniciar_conversa` | Atendimentos | Editar | `POST /atendimentos/novo-contato` | `IniciarNovoContatoUseCase.executar` | A S G D | todos | sim | não |
| `atendimentos.abrir_para_contato` | Atendimentos | Editar | `POST /atendimentos/leads/{id}/novo` | `IniciarNovoContatoUseCase.abrirParaLeadExistente` | A S G D | todos | sim | não |
| `atendimentos.transferir` | Atendimentos | Editar | `POST /atendimentos/{id}/transferir` com destino | `TransferirAtendimentoUseCase.executar` (`#p1 != null`) | A S G D | todos | sim | não |
| `atendimentos.devolver_ia` | Atendimentos | Editar | `POST /atendimentos/{id}/transferir` sem destino | `TransferirAtendimentoUseCase.executar` (`#p1 == null`) | A S G D | todos | sim | não |
| `atendimentos.finalizar` | Atendimentos | Editar | `POST /atendimentos/{id}/finalizar` | `FinalizarAtendimentoUseCase.executar` | A S G D | todos | sim | não |
| `atendimentos.colaborar` | Atendimentos | Editar | `pedir-entrada`, `entrar`, `convidar` | `GerenciarParticipacaoAtendimentoUseCase.solicitar/solicitarPorLead/entrar/convidar` | A S G D | todos | sim | não |
| `atendimentos.finalizar_lote` | Atendimentos | Gerenciar | `GET/POST /atendimentos/finalizar-lote` | `FinalizarAtendimentosVisiveisUseCase` (2) + `FinalizarAtendimentoUseCase.executarEmLote` | A S G D | todos | **não** | sim |
| `contatos.editar` | Contatos | Editar | `PUT /leads/{id}` | `AtualizarLeadUseCase` | A S G D | todos | sim | não |
| `tags.aplicar` | Tags | Editar | `PUT/DELETE /leads/{id}/tags/{tag}` | `Vincular/DesvincularTagDoLeadUseCase` | A S G D | todos | sim | não |
| `tags.criar` | Tags | Gerenciar | `POST /tags` | `GestaoDeTagsUseCases.criar` | S G D | S G D | não | não |
| `tags.editar_excluir` | Tags | Gerenciar | `PUT/DELETE /tags/{id}` | `GestaoDeTagsUseCases.atualizar/remover` | S G D | S G D | não | sim |
| `mensagens_rapidas.usar` | Mensagens rápidas | Ver | `GET /mensagens-rapidas` | `ListarMensagensRapidasUseCase` | A S G D | todos | sim | não |
| `mensagens_rapidas.criar` | Mensagens rápidas | Editar | `POST /mensagens-rapidas` | `CriarMensagemRapidaUseCase` | A S G D | todos | sim | não |
| `mensagens_rapidas.editar_excluir` | Mensagens rápidas | Editar | `PUT/DELETE /mensagens-rapidas/{id}` | `Atualizar/RemoverMensagemRapidaUseCase` | A S G D | todos | sim | não |
| `templates.ver` | Templates | Ver | `GET /whatsapp/templates` | `ListarTemplatesWhatsAppUseCase` | A S G D | todos | sim | não |
| `templates.criar` | Templates | Editar | `POST /whatsapp/templates` | `CriarTemplateWhatsAppUseCase` | A S G D | todos | sim | não |
| `templates.editar` | Templates | Gerenciar | `PUT /whatsapp/templates/{id}` | `EditarTemplateWhatsAppUseCase` | S G D | S G D | não | não |
| `templates.excluir` | Templates | Gerenciar | `DELETE /whatsapp/templates/{id}` | `ExcluirTemplateWhatsAppUseCase` | S G D | S G D | não | sim |
| `resumo_ia.ver` | Resumo por IA | Ver | `GET /atendimentos/{id}/resumo-ia`; campo `resumoIa` de `GET /leads/{id}` e `/{id}/agenda` | `SolicitarResumoIaUseCase.estado`; `LeadController.ficha` | A S G D | todos | sim | não |
| `resumo_ia.solicitar` | Resumo por IA | Editar | `POST /atendimentos/{id}/resumo-ia` | `SolicitarResumoIaUseCase.executar` | A S G D | todos | sim | não |
| `dashboard.ver` | Dashboard (flag `dashboard`) | Ver | `GET /dashboard/visao-geral` | `ObterVisaoGeralDashboardUseCase` | S G D | S G D | não | não |
| `mensagens_programadas.ver` | Programadas | Ver | `GET /mensagens-programadas` | `ListarMensagensProgramadasUseCase` | A S G D | todos | sim | não |
| `mensagens_programadas.criar` | Programadas | Editar | `POST /mensagens-programadas` | `CriarMensagemProgramadaUseCase` | A S G D | todos | sim | não |
| `mensagens_programadas.editar_cancelar` | Programadas | Editar | `PUT /{id}`, `POST /{id}/cancelar` | `Atualizar/CancelarMensagemProgramadaUseCase` | A S G D | todos | sim | não |
| `lembretes.ver` | Lembretes | Ver | `GET /lembretes` | `ListarLembretesUseCase` | A S G D | todos | sim | não |
| `lembretes.criar` | Lembretes | Editar | `POST /lembretes` | `CriarLembreteUseCase` | A S G D | todos | sim | não |
| `lembretes.editar_excluir` | Lembretes | Editar | `PUT/DELETE /lembretes/{id}` | `Atualizar/RemoverLembreteUseCase` | A S G D | todos | sim | não |
| `automacao.ver` | Automação | Ver | `GET /automacao/config`, `/config/resumo-ia`, `/follow-ups`, `/fidelizacao`, `/telemetria` | `ListarConfiguracoesAutomacaoAdmin`, `ObterConfiguracaoResumoIa`, `ListarRegras*Admin`, `ObterStatusAutomacaoTelemetria` | S G D | S G D | não | não |
| `automacao.editar_parametros` | Automação | Gerenciar | `PUT /automacao/config/{chave}`, `PUT /config/resumo-ia` | `AtualizarConfiguracaoAutomacao`, `AtualizarConfiguracaoResumoIa` | S G D | S G D | não | sim |
| `automacao.regras` | Automação | Gerenciar | `POST/PUT/PATCH/DELETE /automacao/follow-ups*`, `/fidelizacao*` (regras) | `SalvarRegraFollowUp/Fidelizacao` (3+3), `AlternarRegra*` | S G D | S G D | não | não |
| `equipe.ver` | Gestão da equipe | Ver | `GET /usuarios`, `GET /equipe/desempenho`, leitura de `/gestao/permissoes/**` | `ListarUsuariosUseCase`, `ObterDesempenhoDaEquipeUseCase`, `AutorizacaoDeGestao.LER` | S G D | S G D | não | não |
| `equipe.disponibilidade_ia` | Gestão da equipe | Editar | `PATCH /usuarios/{id}/disponibilidade-ia` | `AtualizarDisponibilidadeParaIaUseCase` | S G D | S G D | não | não |
| `equipe.criar` | Gestão da equipe | Gerenciar | `POST /usuarios` | `CriarUsuarioUseCase` (S: só ATENDENTE) | G D **+ S delegado** | G D | não | sim |
| `equipe.editar` | Gestão da equipe | Gerenciar | `PUT /usuarios/{id}` | `AtualizarUsuarioUseCase` (S: só ATENDENTE, papel inalterado) | G D **+ S delegado** | G D | não | não |
| `equipe.alterar_papel` | Gestão da equipe | Gerenciar | `PUT /usuarios/{id}` quando o papel muda | `AtualizarUsuarioUseCase` (programático) | G D | G D | não | sim |
| `equipe.senha_provisoria` | Gestão da equipe | Gerenciar | `POST /usuarios/{id}/senha-provisoria` | `DefinirSenhaProvisoriaUseCase` (S: só ATENDENTE) | G D **+ S delegado** | G D | não | sim |
| `equipe.desativar` | Gestão da equipe | Gerenciar | `PATCH /usuarios/{id}/desativar` | `DesativarUsuarioUseCase` (S: só ATENDENTE) | G D **+ S delegado** | G D | não | sim |
| `equipe.excecoes_atendentes` | Gestão da equipe | Gerenciar | `PUT/DELETE /gestao/permissoes/usuarios/{id}/excecoes`, prévia de cópia | `SalvarExcecoes`, `RestaurarPadrao`, `PreverCopia.paraUsuario` | G D **+ S delegado** | G D | não | sim |
| `equipe.perfis` | Gestão da equipe | Gerenciar | `PUT /gestao/permissoes/perfis/{papel}`, prévia de cópia de perfil | `SalvarPerfilDePermissao`, `PreverCopia.paraPerfil` | G D | G D | não | sim |

`CapacidadesReferenciadasTest` reprova o build se um `@PreAuthorize` citar um id fora do catálogo
e se uma capacidade configurável não tiver nenhum ponto de aplicação.

### 2.2 Excluídas, com motivo

| Função existente | Motivo |
|---|---|
| Banco de Arquivos | Fora da primeira entrega (flag `banco_arquivos` desligada). Upload de anexo no chat **não** é Banco de Arquivos: está em `atendimentos.responder`. |
| Campanhas, Relatórios, Horários, sub-abas do Dashboard | Fora de escopo / flags desligadas. Nenhum controle "Em breve". |
| Importação e exportação de leads (`/leads/importacao/**`, `/leads/exportar`) | Excluídas pelo pedido. Continuam com a regra que já tinham. |
| Visibilidade de leads/contatos/timeline/mídias | Estrutural (RN-CRM-01, Specification, RLS). Exibida como "regra fixa", nunca configurável. |
| "Assumir conversa de outro atendente" | Não é ação discreta: é consequência de RN-CRM-06 dentro do recorte. ATENDENTE não alcança lead de colega; configurar isso exigiria mudar a Specification. |
| Alcance "Equipe" | Não existe modelo de times; oferecer "Equipe" seria prometer um recorte que o sistema não faz. |
| Identidade/tema/logo da instância, fidelização (config) e datas festivas | Configurações de aparência estão fora da primeira entrega; fidelização/festivas já são exclusivas de G/D (não configuráveis). |
| Avaliações da equipe (`GET /equipe/avaliacoes`), audit log, feedbacks (lista), Administração | Exclusivas de G/D (ou só D). Não há o que restringir para perfis configuráveis; não são concedíveis. |
| Etapas do funil, campos customizados, canais, chat interno, presença própria | Não pedidos no escopo; continuam como estão. |

### 2.3 Decisões necessárias (não tomadas sozinho)

Ver seção 13 do relatório e a seção 13 deste documento.

## 3. Modelo de permissão

- **Nível por módulo** (Sem acesso / Ver / Editar / Gerenciar) é **preset e limite** ao mesmo
  tempo. Limite: ação com nível mínimo acima do nível do módulo fica bloqueada, mesmo com o
  interruptor ligado. Preset: escolher um nível na tela liga as ações até ele e desliga as acima
  (feito no rascunho; o servidor recebe valores explícitos e só valida).
- Módulos cuja leitura é estrutural (Atendimentos, Contatos, Tags) não oferecem "Sem acesso".
- Níveis acima do máximo útil do papel ficam desabilitados (ex.: ATENDENTE em Tags vai até Editar).
- O servidor recusa combinação incoerente: interruptor ligado com nível abaixo do mínimo
  (`NIVEL_INSUFICIENTE`). A tela desenha o **efetivo** (o que o backend calculou), nunca "ligado
  mas bloqueado".
- **Dependências** (ex.: criar mensagem rápida depende de usar) aparecem como bloqueio com motivo.
- **Exceção** por usuário: herdar (ausente) / permitir / negar, por ação e por nível de módulo.
  Exceção substitui o herdado, mas nunca supera teto, flag ou dependência. Restaurar apaga a linha.
- **Contagem**: cada chave persistida conta como uma exceção, inclusive as de nível. O badge da aba
  conta **usuários** com ao menos uma exceção válida (módulo com flag ligada); o do usuário conta
  as exceções persistidas válidas e marca "rascunho" enquanto há alteração não salva.
- **Mudança de papel** descarta todas as exceções do usuário (nenhum privilégio do papel anterior
  sobrevive) e grava `MUDANCA_DE_PAPEL` no histórico.

## 4. Quem concede o quê

| Ator | Perfis | Exceções | Usuários |
|---|---|---|---|
| ADMINISTRADOR, GESTOR | editam SUBGESTOR e ATENDENTE | de SUBGESTOR e ATENDENTE | criar/editar/mudar papel/senha/desativar ATENDENTE e SUBGESTOR |
| SUBGESTOR sem delegação | lê | lê ATENDENTES e as próprias | alterna disponibilidade IA (como antes) |
| SUBGESTOR delegado | lê | só de ATENDENTE; só ações delegáveis; nunca nível; nunca liga o que ele mesmo não tem | só ATENDENTE, nunca a si; nunca cria/promove SUBGESTOR; nunca muda papel |
| ATENDENTE | — | — | — |

GESTOR e ADMINISTRADOR têm perfil **fixo** (tudo do teto) e não são alvo de exceção nem de edição
pela gestão comum — o que também impede um GESTOR de receber operação exclusiva de ADMINISTRADOR.
O formulário comum não oferece GESTOR nem ADMINISTRADOR (contrato `PapelGerenciavel`).

## 5. Persistência (V83)

| Tabela | Papel |
|---|---|
| `permissao_politica` | revisão global (linha única); sobe em toda mudança de acesso |
| `permissao_perfil` / `_item` | revisão por perfil + itens (`NIVEL`/`ACAO`) salvos |
| `permissao_usuario` / `_excecao` | revisão por usuário + exceções explícitas |
| `permissao_historico` | antes/depois, revisão, operação, autor, alvo, origem de cópia — gravado **na mesma transação** |

- **Concorrência**: salvar exige `revisaoEsperada`; `UPDATE ... WHERE revisao = ?` sem linha
  afetada ⇒ 409 com `revisaoAtual`, sem tocar em nada. Gravação de exceções trava a linha do
  usuário (`FOR SHARE`); mudança de papel/desativação trava `FOR UPDATE`: as duas se serializam.
- **Atomicidade**: todo o payload é validado antes da primeira escrita; qualquer violação ⇒ 422
  com a lista completa e nada persistido.
- **Flags**: salvar com um módulo desligado preserva as linhas dele; religar a flag volta a
  aplicá-las.
- **Triggers**: mudança de `papel`/`ativo` em `usuario` e qualquer escrita nas tabelas de item
  sobem a revisão global — inclusive correções manuais pelo `psql`.
- **Último responsável administrativo**: trigger com `pg_advisory_xact_lock` recusa desativar ou
  rebaixar o último GESTOR/ADMINISTRADOR ativo, inclusive em transações concorrentes. A API nem
  oferece essas operações sobre GESTOR/ADMINISTRADOR (404).
- Sem RLS nas tabelas novas: não há dado de lead; toda escrita passa por caso de uso com
  `@PreAuthorize`; sem `tenant_id` (Silo).

## 6. Aplicação (enforcement)

- `@PreAuthorize("<papel de antes> and @capacidades.permite('id')")` em 82 pontos. O bean
  `capacidades` (`VerificadorDeCapacidadesSpring`) decide com o papel e a situação do **banco**, não
  do JWT; id desconhecido lança (falha alta).
- **Técnico × humano**: caminhos `hasRole('SERVICO')` (Automação, outbox, jobs, programadas via
  `executarComoServico`) nunca passam por capacidade (`CapacidadesReferenciadasTest.servicoSemCapacidade`).
  Sem usuário em `ContextoDeServico`, `permite` devolve `true`: comando já aceito não é revalidado,
  então não há reenvio, duplicação nem descarte de outbox.
- Leitura de lead continua pela Specification/RLS; capacidade não amplia recorte
  (`GestaoPermissoesIT.excecaoNaoAlcancaLeadDeColega`).
- `/internal/v1` e `X-Synapse-Token`: intocados.

## 7. Sessão, cache e tempo real

| Mudança | Mesmo nó | Outro nó | JWT antigo | WebSocket |
|---|---|---|---|---|
| Permissão (perfil/exceção) | próxima chamada | ≤ `SYNAPSE_PERMISSOES_REVALIDACAO` (2s) | continua válido; capacidade recalculada | aviso `ACESSO_ALTERADO` → tela recarrega permissões |
| Papel alterado | próxima chamada: 401 `sessao-desatualizada` | ≤ 2s | refresh devolve token com o papel novo | assinaturas do usuário descartadas; handshake com token antigo recusado; revalidação por TTL compara papel atual |
| Desativação | próxima chamada: 401 | ≤ 2s | refresh recusado (tokens revogados) | idem |

- `ResolvedorDePermissoesEfetivas`: cache em memória por usuário, marcado com revisão global e
  flags. A revisão é relida no máximo a cada intervalo (uma consulta por nó, não por usuário);
  recálculo é uma ida ao banco por usuário e revisão. Leituras do cache usam o **pool do chat**:
  o caminho de toda requisição não pode esperar conexão atrás de relatório pesado no pool geral.
- **Redis fora do ar** não abre nem fecha nada: o cache revalida pela revisão do banco; o aviso
  em tempo real é publicado de forma assíncrona e com fila limitada (salvar não espera Redis —
  `GestaoTempoRealIT.redisForaNaoAbreNemTrava`). Falha de banco propaga (fecha).
- **Não prometido**: arquivos já baixados e URLs assinadas de mídia já emitidas (expiração do
  storage) não são recolhidos.

## 8. API (`/api/v1/gestao/permissoes`)

| Método | Rota | Quem |
|---|---|---|
| GET | `/minhas` | autenticado |
| GET | `/catalogo`, `/perfis`, `/usuarios`, `/usuarios/{id}` | G/D; S com `equipe.ver` (S vê ATENDENTES e a si) |
| PUT | `/perfis/{papel}` `{revisaoEsperada, niveis, acoes, copiadoDe?}` | G/D |
| POST | `/perfis/{papel}/copia/previa` `{origem}` | G/D |
| PUT | `/usuarios/{id}/excecoes` `{revisaoEsperada, niveis, acoes, copiadoDe?}` | G/D; S delegado |
| DELETE | `/usuarios/{id}/excecoes?revisaoEsperada=` | G/D; S delegado |
| POST | `/usuarios/{id}/copia/previa` `{origemUsuarioId}` | G/D; S delegado (origem ATENDENTE) |

Erros RFC 7807: `permissao-invalida` (422, `violacoes[{chave,codigo}]`), `concessao-negada` (403,
`codigo`), `revisao-desatualizada` (409, `revisaoAtual`), `sessao-desatualizada` (401). Cópia é
sempre prévia + salvamento normal (com revisão e histórico `COPIAR`); origem GESTOR/ADMINISTRADOR
é recusada.

## 9. Tela

- Menu: item **Gestão** (`/gestao`) no grupo Gestão; `/equipe` redireciona. Itens Dashboard,
  Automação, Templates, Mensagens rápidas, Programadas e Lembretes somem quando a leitura foi
  revogada (efetivas do backend; sem elas, vale a regra por papel de antes).
- Selo do cabeçalho reflete o papel real (Gestão autorizada / Administrador / Acesso delegado /
  Somente leitura) — não "SOMENTE GESTOR".
- Salvar/Descartar com impacto, confirmação para alteração sensível, proteção ao sair (link e
  fechar aba) e ao trocar perfil/usuário/aba, sem duplo envio, erro sem perder rascunho, 409 com
  "recarregar versão atual" explícito. Nada é publicado antes da resposta do backend.

## 10. Ativação, desativação e rollback

- Não há flag que desligue o enforcement. Revogação salva é sempre aplicada; desligar uma feature
  flag de módulo só oculta o módulo e **preserva** as linhas salvas.
- **Rollback de imagem para antes da V83** ignora as tabelas `permissao_*` (o código antigo não as
  conhece): toda revogação salva deixa de valer. Antes de um rollback, liste o que seria perdido:

```sql
SELECT 'PERFIL' AS escopo, papel::text AS alvo, alvo AS chave, valor FROM permissao_perfil_item
UNION ALL
SELECT 'USUARIO', u.email, e.alvo, e.valor
  FROM permissao_usuario_excecao e JOIN usuario u ON u.id = e.usuario_id
 ORDER BY 1, 2, 3;
```

  e registre a decisão; a V83 não é revertida (as tabelas ficam, voltam a valer no redeploy).
- **Banco sem a V83** (banco novo que sobe pausado antes da V73, docs/41): o cache de permissões
  detecta a ausência das tabelas (`to_regclass`), registra `[PERMISSOES_SEM_V83]` uma vez e usa o
  padrão de cada papel, que é o acesso anterior à Gestão. As telas de Gestão só funcionam depois da
  V83. Quando as tabelas aparecem, a revisão muda (de `-1` para a real) e o cache se refaz sozinho.
- Variável nova, opcional: `SYNAPSE_PERMISSOES_REVALIDACAO` (default `2s`), já declarada com
  default no `dokploy-stack.yml`. Nenhuma ação obrigatória no Dokploy.

## 11. Divergências em relação ao protótipo

| Protótipo | Implementado | Por quê |
|---|---|---|
| Selo "SOMENTE GESTOR" | selo conforme papel real | ADMINISTRADOR e SUBGESTOR delegado também acessam |
| Três papéis | + ADMINISTRADOR (fixo, fora da grade como já era) | papel real do sistema |
| Números 51/49, 8/8, contadores fixos | contagens reais do catálogo e do banco | proibido dado mockado |
| Modal sem senha, com "Gestor" | senha inicial obrigatória; só Subgestor/Atendente (S delegado: só Atendente) | contrato atual; sem credencial implícita |
| Ver conversas com Não/Meus/Equipe/Todos | Meus/Todos exibidos como regra fixa | RN-CRM-01; não existe "Equipe" |
| "Assumir conversa de outro atendente" | ausente | ver 2.2 |
| Banco de Arquivos, Campanhas, Relatórios no menu | fora | escopo |
| Cópia de gestor para atendente | recusada; cópias sempre recortadas pelo teto com prévia | não transmitir limite superior |
| Exceção ligando "assumir colega"/"criar template" do atendente | impossível: "assumir" não existe; ações fora do teto são recusadas | teto |
| Toggle ligado com nível abaixo do mínimo | bloqueado e explicado | coerência |
| Botão reativar usuário | ausente | não existe endpoint de reativação |
| Select nativo "Copiar de outro usuário…" | `Seletor` acessível do projeto | consistência e teclado |

## 12. Desempenho medido

Ver o relatório da entrega (`[GESTAO-MEDICAO]` do `GestaoPermissoesIT.desempenhoDoCache`): em
requisições quentes, zero recálculos; após alteração, exatamente um recálculo para o usuário
afetado. Cada operação de gestão faz uma consulta de contexto por usuário afetado que chamar a
API (lote) e uma leitura de revisão por nó por intervalo.

## 13. Pendências de decisão

1. Ampliar alguma ação além do teto atual (ex.: atendente editar template)?
2. Oferecer "Sem acesso" em Contatos/Tags exigiria cortar leituras hoje estruturais — manter?
3. Reativação de usuário desativado: criar endpoint?
4. Nível de módulo delegável ao SUBGESTOR (hoje não é)?
5. Alcance configurável para SUBGESTOR (Meus em vez de Todos) exigiria mudar Specification e RLS.

## 14. Permissões na interface

A tela pergunta ao backend, nunca ao papel. `useCapacidades()` (`frontend/src/lib/gestao/use-capacidades.ts`)
lê `/gestao/permissoes/minhas`, que já traz o efetivo de perfil + exceção + teto do papel + flags, e
expõe `pode(id)` e `alcancaTodos` (recorte estrutural de `atendimentos.ver`).

- **Estado seguro.** Sem resposta (carregando ou erro), `pode` é `false`: nenhuma ação privilegiada
  aparece para sumir depois. Com uma resposta já obtida, uma revalidação que falhe mantém a última.
- **Controle negado sai da tela.** Botão, item de menu, item do "⋯", atalho de teclado (`/` das
  respostas rápidas) e diálogo aberto fecham junto (os diálogos recebem `aberto && pode`). Campos de
  leitura permanecem: revogar criar template não esconde a lista.
- **Rota.** `ExigeCapacidade` protege a URL direta das páginas cuja leitura é configurável
  (templates, mensagens rápidas, programadas, lembretes, dashboard, automação): verificando →
  erro com "tentar novamente" → "Sem acesso" com volta para Atendimentos. O menu esconde os mesmos
  itens e, com permissão desconhecida, não cai mais na regra por papel.
- **Sem F5.** `ACESSO_ALTERADO` invalida `["permissoes"]`; cada aba tem a própria assinatura STOMP e
  revalida sozinha. O backend continua sendo a fonte final (POST direto com sessão antiga → 403).
- **Testes.** `vitest.setup.ts` controla `useCapacidades` (`src/test/capacidades-de-teste.ts`,
  padrão "tudo permitido"); testes de permissão declaram o cenário ou usam o hook real
  (`usarCapacidadesReais()`), e o E2E `permissoes-na-interface.spec.ts` roda contra o backend real.

### 14.1 Matriz auditada (capacidade → controle → ponto de entrada)

"Antes" é o que decidia o controle até esta correção.

| Capacidade | Controles na UI | Endpoint / caso de uso | Antes | Agora |
|---|---|---|---|---|
| `templates.ver` | menu; rota `/templates-whatsapp`; item "Templates" do clipe; "Nova mensagem" com janela fechada | `GET /whatsapp/templates` | menu ok; resto sem checagem | capacidade |
| `templates.criar` | "Novo template" (página); link "Criar template" (modal do composer) | `POST /whatsapp/templates` | `podeCriarTemplates(papel)` (todos) | capacidade |
| `templates.editar` / `templates.excluir` | lápis / lixeira (página e modal) | `PUT`/`DELETE /whatsapp/templates/{id}` | `podeGerenciarTemplates(papel)` | capacidade, cada uma separada |
| `mensagens_rapidas.usar` | menu; rota; item do clipe; sugestões do atalho `/` | `GET /mensagens-rapidas` | menu ok; atalho só sumia com erro 403 | capacidade |
| `mensagens_rapidas.criar` / `editar_excluir` | "Nova mensagem rápida"; lápis/lixeira | `POST`/`PUT`/`DELETE /mensagens-rapidas` | sem checagem | capacidade |
| `mensagens_programadas.ver` | menu; rota; seção do painel da conversa | `GET /mensagens-programadas` | menu ok | capacidade |
| `mensagens_programadas.criar` | "Programar mensagem"; relógio do composer; "Adicionar" no painel; botão na ficha da agenda | `POST /mensagens-programadas` | sem checagem | capacidade |
| `mensagens_programadas.editar_cancelar` | editar/cancelar (página e painel) | `PUT /{id}`, `POST /{id}/cancelar` | sem checagem | capacidade |
| `lembretes.ver` / `criar` / `editar_excluir` | menu; rota; seção do painel; "Novo lembrete", "Adicionar", botão da ficha; concluir, lápis, lixeira | `/lembretes` | menu ok; ações sem checagem | capacidade |
| `tags.criar` / `tags.editar_excluir` | "Nova tag"; lápis/lixeira em `/tags` | `POST`/`PUT`/`DELETE /tags` | **sem checagem (atendente via os botões)** | capacidade |
| `tags.aplicar` | seletor de tags do cabeçalho/painel; "⋯ Tags"; tags da ficha da agenda | `PUT`/`DELETE /leads/{id}/tags/{tag}` | sem checagem | capacidade (chips seguem visíveis) |
| `contatos.editar` | nome, código e notas no painel; formulário da ficha da agenda | `PUT /leads/{id}` | sem checagem | capacidade (vira leitura) |
| `resumo_ia.ver` / `resumo_ia.solicitar` | seção de resumo (painel e ficha); "Gerar/Regerar" | `GET`/`POST /atendimentos/{id}/resumo-ia` | sem checagem | capacidade |
| `atendimentos.responder` | composer; responder/encaminhar/reagir no menu da mensagem | `POST /atendimentos/mensagens` etc. | estado da conversa | capacidade **e** estado da conversa; negado → aviso no lugar do composer, reações só leitura |
| `atendimentos.iniciar_conversa` | botão de novo atendimento da lista; "Iniciar conversa" do cartão de contato | `POST /atendimentos/novo-contato` | sem checagem | capacidade |
| `atendimentos.abrir_para_contato` | "Reativar atendimento" (finalizado); "Abrir atendimento" na agenda e na ficha | `POST /atendimentos/leads/{id}/novo` | sem checagem | capacidade |
| `atendimentos.transferir` / `devolver_ia` | "Transferir"; opções do diálogo (assumir/colegas × devolver) | `POST /atendimentos/{id}/transferir` | sem checagem | capacidade, por opção |
| `atendimentos.finalizar` | "Finalizar" | `POST /atendimentos/{id}/finalizar` | sem checagem | capacidade |
| `atendimentos.finalizar_lote` | "Finalizar Todos" (⋯ da lista) e a contagem | `GET`/`POST /atendimentos/finalizar-lote` | sem checagem (a contagem dava 403) | capacidade |
| `atendimentos.colaborar` | pedir entrada, entrar, convidar, aceitar/recusar; "pedir entrada" da agenda | `pedir-entrada`, `entrar`, `convidar` | `papel !== "ATENDENTE"` | capacidade; "entrar direto" usa `alcancaTodos` |
| `dashboard.ver` | menu; rota `/dashboard` | `GET /dashboard/visao-geral` | papel + menu | capacidade |
| `automacao.ver` | menu; rota `/automacao` | `GET /automacao/**` | papel (dentro da página) | capacidade (rota) |
| `automacao.editar_parametros` | switches de recursos de IA; parâmetros avançados + salvar | `PUT /automacao/config/**` | sem checagem | capacidade (fieldset + switches) |
| `automacao.regras` | novo, alternar, excluir e editar regras de follow-up/fidelização | `/automacao/follow-ups*`, `/fidelizacao*` | sem checagem | capacidade |
| `equipe.ver` / `equipe.disponibilidade_ia` | card de disponibilidade na automação; switch | `GET /usuarios`; `PATCH .../disponibilidade-ia` | sem checagem | capacidade (switch inoperante, pois também mostra o estado) |
| `equipe.*`, `equipe.perfis`, `equipe.excecoes_atendentes` | aba Equipe e demais abas da Gestão | `/usuarios/**`, `/gestao/permissoes/**` | já por capacidade (docs/47 §9) | sem mudança |
| `atendimentos.ver` | abas Meus/Todos, filtro de atendente | Specification + RLS | papel (estrutural) | sem mudança (RN-CRM-01) |

**Fora da matriz, de propósito:**
- configuração de fidelização e datas festivas (G/D, fora do catálogo) e importação/exportação
  (`gerenciaImportacaoDeLeads`, §2.2) seguem por papel;
- colunas "atendente" e agrupamento por autor em lembretes, programadas e mensagens rápidas são
  só apresentação;
- a alçada da aba Equipe (quem administra quem) espelha a do backend, que continua decidindo.
