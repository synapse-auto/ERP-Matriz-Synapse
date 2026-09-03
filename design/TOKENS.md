# Design Tokens — extraídos do protótipo Claude Design

Fonte: `design/CRM_EstruturalVidros_App.html` (15 telas, decodificadas em `design/componentes/`).

O protótipo usa **estilos inline**, não CSS variables. Este documento é a tradução para tokens — a etapa E10 gera `tema.json` a partir daqui, e **nenhum componente React recebe cor literal**.

---

## 1. Paleta

### Marca e ação

| Token | Valor | Uso no protótipo |
|---|---|---|
| `--cor-primaria` | `#1F74E0` | Botão primário, item ativo da sidebar, badge de não lidas (227 ocorrências — é a cor da marca) |
| `--cor-primaria-hover` | `#1560C4` | Estado hover/pressed |
| `--cor-primaria-suave` | `#EAF2FD` | Fundo de item selecionado |
| `--cor-primaria-borda` | `#CFE1FA` | Borda de elemento em destaque |

### Superfícies

| Token | Valor | Uso |
|---|---|---|
| `--fundo-app` | `#F4F7FB` | Fundo geral |
| `--fundo-superficie` | `#FFFFFF` | Cards, painéis |
| `--fundo-sutil` | `#EEF3F8` | Faixa alternada, hover de linha |
| `--fundo-sidebar` | `#0C2A43` | Sidebar escura (`#123A5C` para blocos internos, ex.: popup de presença) |

### Texto

| Token | Valor | Uso |
|---|---|---|
| `--texto-forte` | `#0F2438` | Títulos |
| `--texto-padrao` | `#33475B` | Corpo |
| `--texto-suave` | `#5A6B7B` | Secundário |
| `--texto-fraco` | `#8A9BAD` | Placeholder, metadados (185 ocorrências) |
| `--texto-sidebar-titulo` | `#5E7E9C` | Rótulos "MENU" / "GESTÃO" |
| `--texto-sidebar-sub` | `#6E92B4` | "CRM · ATENDIMENTO" |
| `--texto-sidebar-item` | `#C3D6E8` | Texto de item de menu inativo |

### Bordas

| Token | Valor |
|---|---|
| `--borda` | `#E7EDF4` |
| `--borda-forte` | `#E1E8F0` |
| `--borda-suave` | `#CBD8E6` |

### Semânticas

| Token | Valor | Significado |
|---|---|---|
| `--cor-sucesso` | `#17835A` | Finalizado, entregue |
| `--cor-atencao` | `#E0A61C` | Pendente, aguardando |
| `--cor-atencao-escura` | `#B07A15` | Texto sobre fundo de atenção |
| `--cor-ia` | `#6D4FD6` | Lead em atendimento pela IA |
| `--cor-info` | `#3E8FD0` | Informativo |
| `--cor-destaque-2` | `#4C55B8` | Categoria secundária |
| `--cor-destaque-3` | `#2F8F86` | Categoria terciária |

### Acrescentados na E10

O protótipo não cobria todos os estados da aplicação real. Quatro tokens entraram:

| Token | Valor | Por quê |
|---|---|---|
| `--cor-erro` | `#C0392B` | Estado `FALHOU` do ciclo de entrega (E05). O protótipo não tem estado de falha |
| `--cor-erro-suave` | fundo suave | Simétrico a `--cor-primaria-suave` |
| `--cor-primaria-texto` | `#FFFFFF` | Texto sobre botão primário |
| `--fundo-sidebar-bloco` | `#1B3248` | Estava em prosa, não formalizado |

### Acrescentados na E17 — item de menu ativo/hover da Sidebar

`Sidebar.html` usa **overlays translúcidos**, não cores sólidas, para os estados de hover e ativo dos itens de menu — algo que os tokens sólidos de "Marca e ação" não expressam. Em vez de aproximar com `--cor-primaria` (que teria contraste errado sobre o fundo escuro `#0C2A43`), estes tokens replicam os valores exatos do protótipo:

| Token | Valor | Uso no protótipo |
|---|---|---|
| `--sidebar-item-texto-hover` | `#F0F6FC` | Cor do texto do item inativo, no hover |
| `--sidebar-item-icone-ativo` | `#7FBBF2` | Cor do ícone quando o item está ativo |
| `--sidebar-item-overlay-hover` | `rgba(255,255,255,.06)` | Fundo do item inativo, no hover |
| `--sidebar-item-overlay-ativo` | `rgba(79,163,240,.17)` | Fundo do item ativo |
| `--sidebar-item-overlay-ativo-hover` | `rgba(79,163,240,.2)` | Fundo do item ativo, no hover |
| `--sidebar-item-acento-ativo` | `#4FA3F0` | Barra vertical de 3px (`box-shadow: inset`) do item ativo |
| `--marca-icone-gradiente-inicio` | `#3B86E6` | Início do gradiente do ícone de marca (topo da sidebar) |
| `--marca-icone-gradiente-fim` | `#1657B8` | Fim do mesmo gradiente |
| `--sidebar-item-texto-perigo` | `#E88B7D` | Texto da ação "Sair" no popup de presença (sobre fundo escuro — diferente de `--cor-erro`, calibrado para fundo claro) |
| `--sidebar-item-overlay-perigo` | `rgba(255,90,70,.12)` | Fundo no hover da mesma ação |

### Acrescentado na E143 — status de leitura sobre o balão primário

O balão de mensagem de saída é sempre `--cor-primaria` (`#1F74E0`, azul). O token semântico `--cor-sucesso` (`#17835A`) foi calibrado para "Finalizado, entregue" sobre superfícies claras; quando aplicado sobre o azul do balão, sua luminância relativa ($L \approx 0.1715$) é praticamente idêntica à do fundo ($L \approx 0.1816$), gerando uma razão de contraste de apenas **1.05:1** (completamente invisível).

Para atender ao pedido de traços verdes de leitura (`CheckCheck` estilo WhatsApp) mantendo contraste e legibilidade, criou-se um token calibrado para superfície escura/azul:

| Token | Valor | Uso / Calibragem |
|---|---|---|
| `--cor-status-lido` | `#86EFAC` | Ícone de mensagem lida (`CheckCheck`) no balão de saída (`bg-primary`). Calibrado com razão de contraste de **3.23:1** contra `#1F74E0` (atende ao critério WCAG 2.1 AA 1.4.11 de 3:1 para elementos gráficos e componentes de interface). |

Também corrigido nesta rodada: `--fundo-sidebar` estava documentado como `#0F2438` (valor de uma versão anterior do protótipo); o `Sidebar.html` atual, aprovado pelo cliente, usa `#0C2A43`. `--fundo-sidebar-bloco` foi de `#1B3248` para `#123A5C` pelo mesmo motivo. `--texto-sidebar-item`, que só existia em `tema.json` sem entrar nesta tabela, ganhou registro formal.

---

## 2. Tipografia

```
--fonte-base: 'Inter', sans-serif
--fonte-mono: 'JetBrains Mono', monospace
```

Escala observada (px): 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21.
As mais usadas são **13, 12 e 14** — interface densa, como pedido em "menos texto, mais ícones".

| Token | Valor |
|---|---|
| `--texto-xs` | 11px |
| `--texto-sm` | 12px |
| `--texto-base` | 13px |
| `--texto-md` | 14px |
| `--texto-lg` | 16px |
| `--texto-xl` | 18px |
| `--texto-2xl` | 20px |

Pesos: **400** (dados de tabela, descrições secundárias, conteúdo do chat), **500** (menu
lateral, nomes de abas, botões), **600/700** (títulos de página, nomes de clientes em destaque,
números de métrica nos cards).
Rótulos de seção usam `letter-spacing: .11em` a `.16em` com caixa alta.

---

## 3. Raios

O protótipo usa muitos valores próximos (16, 11, 9, 12, 10, 20, 8, 14…). **Consolidar em cinco** — a variação de 1px não é intencional, é ruído de mockup:

| Token | Valor | Uso |
|---|---|---|
| `--raio-sm` | 8px | Badge, chip pequeno |
| `--raio-md` | 11px | Botão, input, item de menu |
| `--raio-lg` | 16px | Card, painel |
| `--raio-xl` | 20px | Modal, contêiner grande |
| `--raio-pill` | 999px | Pílula, avatar |

### Login Synapse

| Token | Valor | Uso |
|---|---|---|
| `--raio-login` | 10px | Campos de e-mail/senha e botão da tela de login; medido no protótipo |

---

## 4. Sombras

| Token | Valor |
|---|---|
| `--sombra-sm` | `0 1px 3px rgba(0,0,0,.25)` |
| `--sombra-md` | `0 10px 26px -18px rgba(12,42,67,.4)` |
| `--sombra-lg` | `0 24px 60px -22px rgba(12,42,67,.4)` |
| `--sombra-xl` | `0 40px 120px -34px rgba(12,42,67,.5)` |
| `--sombra-primaria` | `0 8px 18px -8px rgba(31,116,224,.7)` |

---

## 5. Estrutura de navegação (da Sidebar)

```
Estrutural Vidros
CRM · ATENDIMENTO

MENU
  Atendimentos · Dashboard · Agenda de Contatos · Tags
  Mensagens Rápidas · Banco de Arquivos*
  Mensagens Programadas · Lembretes

GESTÃO
  Equipe · Campanhas* · Automação · Horários · Relatórios*

Novidades & Em Breve
Administração (ADM)

Rodapé: STATUS DE PRESENÇA · Trocar conta · Sair
```

`*` fora da primeira entrega (`docs/09`) — a flag fica `false` e o item não é construído.

O protótipo tem também uma tela **Admin** que não estava nos requisitos. Avaliar se é o "mini front-end da Base PAI" do roadmap interno; se for, é fase 2.

---

## 6. Regras de tradução

1. **Nenhuma cor literal em componente React.** Tudo via CSS variable, servida por `GET /api/v1/config/tema`.
2. **Nenhuma string de UI em componente.** Rótulos vêm do catálogo de textos (`GET /api/v1/config/textos`).
3. **O protótipo é referência visual, não código a copiar.** Ele usa um template engine próprio (`sc-for`, `sc-if`) e estilos inline — nada disso vai para o Next.js.
4. **Consolidar valores próximos.** 11px e 12px de raio no mesmo tipo de elemento é ruído; escolha um token.
5. **A identidade do próximo filho é outro `tema.json`.** Se um componente precisar mudar para trocar de cliente, a tradução falhou.
