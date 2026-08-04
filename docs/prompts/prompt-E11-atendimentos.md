# Prompt E11 — Frontend: tela de Atendimentos

> Pré-requisito: E10 commitada (`fa862c3`). **Sessão limpa.**
> **Esta é a tela que o cliente usa 8 horas por dia.** É o produto.

---

**Etapa E11 — Lista de conversas, conversa aberta e composer, contra a API real.**

## Regras que valem aqui

- **Zero dado mockado.** Endpoint que não existe ⇒ estado vazio de verdade, não conteúdo inventado.
- Nenhuma cor ou string literal — tokens e catálogo de textos da E10.
- `RNF-CRM-01` se aplica: nada nesta tela pode travar o envio ou o recebimento.

## 1. Lista de conversas

Três agrupamentos, por papel (`RF-CRM-20/21`):

| Papel | Vê |
|---|---|
| Atendente | **Ativos** (dele), **Pendentes** (dele, sem resposta), **Potenciais** (em IA, sem dono) |
| Gestor / Subgestor | **Todos** e **Pendentes** de todos |

O servidor já impõe isso — o frontend **não** filtra por papel, só pede a visão certa. Se você se pegar escrevendo `if (papel === ...)` para esconder lead, pare: a informação não deveria ter chegado.

Card: etapa, foto, nome, status básico, atendente responsável, badge de não lidas, prévia da última mensagem.

Filtros por etapa, status, tag e atendente, reusando o filtro modular.

## 2. Conversa

- Todos os tipos de mídia: texto, áudio, imagem, documento
- Anexo rico: card com nome, metadados e download; imagem com miniatura e legenda (`RF-CRM-68`)
- Cabeçalho: avatar, nome, contato, empresa, "Atendido por X", botões **Transferir** e **Finalizar**, busca na conversa, atalho de tags (`RF-CRM-65`)
- Paginação por cursor no histórico, não offset — mensagens novas chegam durante a rolagem

## 3. Composer — três pontos que exigem atenção

**Estado real, não otimismo.** A mensagem aparece imediatamente com o estado que ela tem: `PENDENTE` = relógio, `ENVIADO` = ✓, `ENTREGUE` = ✓✓, `LIDO` = ✓✓ azul, `FALHOU` = ⚠ com ação de reenviar.

`FALHOU` precisa ser visível e acionável enquanto o cliente ainda está na conversa. Um ✓ mentiroso é pior que um erro honesto.

**Janela de 24h da Meta.** Fora dela, texto livre é rejeitado — e o backend rejeita **antes** de chamar o provedor (E05). A UI precisa avisar **antes de o atendente digitar**, não depois de mandar:

- Composer indica quando a janela está fechada
- Oferece os templates aprovados disponíveis
- Não deixa o atendente escrever 3 parágrafos para descobrir que não pode enviar

Isso é o tipo de detalhe que faz a diferença entre uma ferramenta que ajuda e uma que irrita.

**Mensagens rápidas por palavra-chave** (`RF-CRM-12`): o atendente digita a palavra-chave e a mensagem é inserida. Mais o botão de anexo, emoji, áudio e o relógio de agendar (`RF-CRM-69`).

## 4. Tempo real

- Assinar o tópico do atendimento aberto; **desassinar ao trocar de conversa**
- Tratar a revogação (`/user/queue/revogacoes`): o servidor pode revogar a assinatura numa transferência — a UI precisa reagir, não ficar mostrando uma conversa que não é mais dela
- **Reconexão sem perda:** guardar o instante da última mensagem, buscar o que faltou por HTTP ao reconectar, depois retomar o WebSocket
- Backoff exponencial com teto, e indicador visível de "reconectando"

Derrubar a rede por 10 s durante uma conversa ativa não pode perder nem duplicar mensagem.

## 5. Desempenho

A lista pode ter centenas de conversas e a conversa, milhares de mensagens.

- Virtualização na lista de mensagens
- Não recarregar a lista inteira a cada mensagem nova — atualização incremental no cache do TanStack Query
- Server Components onde não há interatividade

## 6. Testes

- Atendente vê só as três visões dele; gestor vê todas
- Envio mostra `PENDENTE` imediatamente e transita ao confirmar
- `FALHOU` aparece e o reenvio funciona
- Janela fechada bloqueia texto livre **antes** do envio
- Trocar de conversa desassina o tópico anterior
- Revogação em transferência remove a conversa da tela
- Queda de rede de 10 s: sem perda, sem duplicata
- Mensagem rápida por palavra-chave insere o texto certo

## Definição de pronto

- [ ] Um atendente trabalha um lead do início ao fim sem sair da tela
- [ ] Todos os tipos de mídia enviando e recebendo
- [ ] Ciclo de entrega completo e visível, com `FALHOU` acionável
- [ ] Janela de 24h comunicada antes da digitação
- [ ] Reconexão sem perda
- [ ] Zero dado mockado, zero cor ou texto literal
- [ ] `npm run build` e `lint` limpos

Commit: `feat: tela de atendimentos`.

Ao terminar, me diga quanto tempo leva do clique até a mensagem aparecer na tela do outro lado, medido com backend e frontend reais — é o número que a subgestora vai sentir na homologação.
