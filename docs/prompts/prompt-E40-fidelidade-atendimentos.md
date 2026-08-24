# Prompt E40 — fidelidade da aba Atendimentos ao protótipo

> Leia `AGENTS.md`, `frontend/AGENTS.md` e `design/TOKENS.md`.
> **Não faça commit nem push sem autorização explícita do Marcondes.**
> Ao encerrar, informe os testes executados, o diff e o que ficou sem verificação.

---

## Contexto

A E34 refinou a **Agenda** e o **composer** (`c9cf138`). Esta etapa é outra superfície: a **lista de
Atendimentos**, o **cabeçalho da conversa**, os **balões** e o **painel de detalhes do lead**.

A comparação foi feita entre o protótipo aprovado e a tela em produção. A referência visual é o
protótipo em `C:\Users\marcondes\Downloads\CRM_EstruturalVidros_App (2).html` e as capturas
fornecidas. **Não execute nem importe esse HTML** — ele tem template engine próprio. Extraia
hierarquia, densidade e estados; a implementação continua em React, Tailwind, `lucide-react`,
catálogo de textos e tokens do tema.

## Bloco 0 — Separar dado ausente de interface ausente

**Faça isto primeiro e relate antes de mexer em JSX.**

O ambiente comparado ainda não tinha o seed de demonstração aplicado. Vários elementos do protótipo
podem já existir na interface e estarem invisíveis por falta de dado:

- empresa/segmento do lead
- e-mail e localidade
- etapa do atendimento
- resumo por IA

Para **cada item** dos blocos abaixo, responda: **o campo já existe no backend e no componente, e só
falta dado?** Ou **a interface não o exibe de jeito nenhum?**

> **Não construa o que já existe.** Item que só precisa de dado vai para o relatório como "aguarda
> seed", não vira código novo. Este projeto já pagou caro por agente que reimplementou coisa
> existente.

---

## Bloco 1 — O card da lista de atendimentos

Hoje o card tem: avatar, nome, horário, prévia da mensagem, iniciais do responsável e contador de
não lidas.

O protótipo tem, além disso:

- **Segunda linha com empresa ou segmento** do lead ("Vidraçaria Cristal Clara", "Construtora
  Horizonte", "Cliente final"), em texto suave, acima da prévia
- **Selo de etapa** colorido no rodapé do card ("Orçamento", "Negociação", "Novo Contato",
  "Aguardando Medidas", "Fechado", "Pós-venda"), cada etapa com sua cor
- **Ícone do canal** (WhatsApp) à esquerda do selo

As cores dos selos saem de tokens do tema, uma por etapa. **Nenhum literal hexadecimal no JSX**, e
etapa sem cor definida cai num neutro em vez de quebrar.

## Bloco 2 — Abas e cabeçalho da lista

- As abas **Todos / Ativos / Pendentes / Potenciais** precisam de indicação clara da ativa —
  sublinhado na cor primária, como no protótipo. Hoje a diferença entre ativa e inativa é fraca.
- O contador de cada aba fica ao lado do rótulo, em pílula.
- O cabeçalho da lista tem **dois** controles à direita no protótipo; hoje tem um. Identifique o
  segundo e diga no relatório o que ele faz **antes** de implementar — se for função que não existe,
  **não invente**: relate e pare neste item.

## Bloco 3 — Cabeçalho da conversa

- **Selo do canal** ao lado do nome ("WhatsApp", com o ícone e o verde do canal).
- A linha de contexto mostra telefone · empresa · "Atendido por *nome*".
- O protótipo tem um menu **⋮** à direita dos ícones. Mesma regra do Bloco 2: descubra o que ele
  agrupa antes de criar. Se não houver ação existente para pôr ali, **não crie o menu**.

## Bloco 4 — Balões e anexos

Comparando os dois lados:

- Os balões **recebidos** em produção estão pequenos e sem presença. No protótipo têm largura
  mínima, respiro interno maior e sombra sutil — leem como cartão, não como texto solto.
- A linha de sistema ("Atendimento recebido · WhatsApp · *nome*") tem ícone de escudo e é mais
  discreta que o corpo da conversa.
- **Anexo de imagem**: miniatura em cartão, com legenda abaixo e horário.
- **Anexo de documento**: cartão com ícone do tipo, nome do arquivo, uma linha de descrição e
  **botão de download**.

Os dois tipos de anexo já existem no domínio (`TipoMensagem.IMAGEM`, `DOCUMENTO`) e já entram pelo
webhook. Confirme como são renderizados hoje antes de escrever componente novo.

## Bloco 5 — Painel de detalhes do lead

O protótipo tem, e produção não mostra:

- **Etapa do atendimento**: barra segmentada com a posição atual ("3 de 6"), rótulos das pontas e
  selo da etapa corrente. O domínio **já tem** etapa, histórico de transição e resultado — isto é
  exibição, não modelagem.
- **E-mail** e **localidade** nas informações gerais.
- Botão de **câmera** sobre o avatar, para trocar a foto do lead. Se não houver endpoint de upload
  de foto de lead, **não crie**: relate como pendência.
- Etiquetas como chips coloridos, com o "+ Tag" na mesma linha.

Produção também mostra **Mensagens programadas** e **Lembretes**, que o protótipo não tem.
**Mantenha os dois** — são funcionalidade entregue, e o protótipo é anterior a eles.

---

## Regras que valem para todos os blocos

- **Nenhum literal de UI.** Todo texto sai do catálogo (`textos.json`). O E36 já mostrou o que
  acontece quando esse arquivo é editado sem cuidado: `ZodError` e 500 no login.
- **Nenhuma cor hardcoded.** Selos de etapa, verde do WhatsApp, tudo em token.
- **Sem regressão de comportamento.** A E28 consertou o scroll interno e a E39 tirou a moldura; a
  lista rola dentro dela mesma e a janela não ganha barra de rolagem.
- **Degradação preservada.** Com o backend fora, a aba mostra erro tratado e mantém menu e
  cabeçalho — regra de precedência absoluta do projeto.

## Testes

- Card renderiza com e sem empresa, com e sem etapa, com e sem ícone de canal.
- Etapa desconhecida cai no neutro em vez de quebrar.
- Aba ativa distinguível; contador correto por aba.
- Balão recebido, balão enviado, anexo de imagem e anexo de documento, cada um com seu teste.
- Painel do lead com e sem e-mail, com e sem localidade, com e sem etapa.
- Nenhum literal de texto e nenhuma cor literal nos arquivos alterados.
- A lista continua rolando internamente.

## No relatório

1. **A resposta do Bloco 0, item por item**: o que era dado ausente e o que era interface ausente.
2. O que você **não** construiu por não haver ação ou endpoint existente (segundo controle da lista,
   menu ⋮, câmera do avatar).
3. **Os nomes dos testes novos, um por linha.** Não informe o total da suíte.
4. O diff, e o que ficou sem verificação visual.
5. **Não comitou** — confirme que parou para revisão.

---

## Fora desta etapa

Agenda e composer (E34, já feita). Entrar/sair de atendimento e chat interno — são features, não
fidelidade, e têm etapa própria. Qualquer mudança de visibilidade de lead: está bloqueada aguardando
decisão do cliente.
