# E220 — capturas da interface de campanhas

Geradas contra a **stack real**: backend Spring (perfil dev, migrations V1–V91, seed), Postgres, frontend Next
e um stub local da Graph API da Meta no lugar do provedor. Campanhas, destinatários, entregas, leituras e
respostas vieram do motor e dos webhooks, não de dados escritos na tela. Contatos e telefones são fictícios.

- **Escuro:** o produto não tem alternância de modo escuro e o tema do tenant (`:root{...}`) sobrepõe a
  paleta `.dark`. As capturas escuras sobrescrevem os tokens semânticos (fundo, texto, borda, primária e as
  cores de status) para provar que as telas só dependem de tokens. Não é um tema que o produto entrega hoje.
- **Perfil gestor** (itens 12 e 13): mesmas telas, sem a permissão de escrita.
- **Acessibilidade:** axe-core (WCAG 2.0/2.1/2.2 A e AA) sem violações em lista, passos 1–4, detalhe,
  detalhe pausado e configurações.

## Claro, desktop (1440 px)

**Lista de campanhas**

![Lista de campanhas — Claro, desktop (1440 px)](./claro-desktop/01-lista.png)

**Assistente, passo 1: template, variáveis e prévia em bolha**

![Assistente, passo 1: template, variáveis e prévia em bolha — Claro, desktop (1440 px)](./claro-desktop/02-passo1-template.png)

**Assistente, passo 2: público, contagens e excluídos por motivo**

![Assistente, passo 2: público, contagens e excluídos por motivo — Claro, desktop (1440 px)](./claro-desktop/03-passo2-publico.png)

**Assistente, passo 3: ritmo, janela, dias e mini-calendário**

![Assistente, passo 3: ritmo, janela, dias e mini-calendário — Claro, desktop (1440 px)](./claro-desktop/04-passo3-ritmo-e-agenda.png)

**Assistente, passo 4: revisão, teste e confirmação digitada**

![Assistente, passo 4: revisão, teste e confirmação digitada — Claro, desktop (1440 px)](./claro-desktop/05-passo4-revisao.png)

**Detalhe de campanha em andamento**

![Detalhe de campanha em andamento — Claro, desktop (1440 px)](./claro-desktop/06-detalhe-em-andamento.png)

**Detalhe: destinatários**

![Detalhe: destinatários — Claro, desktop (1440 px)](./claro-desktop/07-detalhe-destinatarios.png)

**Detalhe: conferência manual (vazia)**

![Detalhe: conferência manual (vazia) — Claro, desktop (1440 px)](./claro-desktop/08-detalhe-conferencia.png)

**Detalhe: pausada automaticamente (alerta com motivo e o que fazer)**

![Detalhe: pausada automaticamente (alerta com motivo e o que fazer) — Claro, desktop (1440 px)](./claro-desktop/09-detalhe-pausada-automaticamente.png)

**Detalhe de campanha concluída**

![Detalhe de campanha concluída — Claro, desktop (1440 px)](./claro-desktop/10-detalhe-concluida.png)

**Configurações da instância**

![Configurações da instância — Claro, desktop (1440 px)](./claro-desktop/11-configuracoes-da-instancia.png)

**Perfil gestor: lista, Nova campanha desabilitada com a explicação**

![Perfil gestor: lista, Nova campanha desabilitada com a explicação — Claro, desktop (1440 px)](./claro-desktop/12-gestor-lista-sem-nova-campanha.png)

**Perfil gestor: detalhe, ações do administrador desabilitadas**

![Perfil gestor: detalhe, ações do administrador desabilitadas — Claro, desktop (1440 px)](./claro-desktop/13-gestor-detalhe-acoes-desabilitadas.png)

## Claro, celular (390 px)

**Lista de campanhas**

![Lista de campanhas — Claro, celular (390 px)](./claro-celular/01-lista.png)

**Assistente, passo 1: template, variáveis e prévia em bolha**

![Assistente, passo 1: template, variáveis e prévia em bolha — Claro, celular (390 px)](./claro-celular/02-passo1-template.png)

**Assistente, passo 2: público, contagens e excluídos por motivo**

![Assistente, passo 2: público, contagens e excluídos por motivo — Claro, celular (390 px)](./claro-celular/03-passo2-publico.png)

**Assistente, passo 3: ritmo, janela, dias e mini-calendário**

![Assistente, passo 3: ritmo, janela, dias e mini-calendário — Claro, celular (390 px)](./claro-celular/04-passo3-ritmo-e-agenda.png)

**Assistente, passo 4: revisão, teste e confirmação digitada**

![Assistente, passo 4: revisão, teste e confirmação digitada — Claro, celular (390 px)](./claro-celular/05-passo4-revisao.png)

**Detalhe de campanha em andamento**

![Detalhe de campanha em andamento — Claro, celular (390 px)](./claro-celular/06-detalhe-em-andamento.png)

**Detalhe: destinatários**

![Detalhe: destinatários — Claro, celular (390 px)](./claro-celular/07-detalhe-destinatarios.png)

**Detalhe: conferência manual (vazia)**

![Detalhe: conferência manual (vazia) — Claro, celular (390 px)](./claro-celular/08-detalhe-conferencia.png)

**Detalhe: pausada automaticamente (alerta com motivo e o que fazer)**

![Detalhe: pausada automaticamente (alerta com motivo e o que fazer) — Claro, celular (390 px)](./claro-celular/09-detalhe-pausada-automaticamente.png)

**Detalhe de campanha concluída**

![Detalhe de campanha concluída — Claro, celular (390 px)](./claro-celular/10-detalhe-concluida.png)

**Configurações da instância**

![Configurações da instância — Claro, celular (390 px)](./claro-celular/11-configuracoes-da-instancia.png)

**Perfil gestor: lista, Nova campanha desabilitada com a explicação**

![Perfil gestor: lista, Nova campanha desabilitada com a explicação — Claro, celular (390 px)](./claro-celular/12-gestor-lista-sem-nova-campanha.png)

**Perfil gestor: detalhe, ações do administrador desabilitadas**

![Perfil gestor: detalhe, ações do administrador desabilitadas — Claro, celular (390 px)](./claro-celular/13-gestor-detalhe-acoes-desabilitadas.png)

## Escuro, desktop (1440 px)

**Lista de campanhas**

![Lista de campanhas — Escuro, desktop (1440 px)](./escuro-desktop/01-lista.png)

**Assistente, passo 1: template, variáveis e prévia em bolha**

![Assistente, passo 1: template, variáveis e prévia em bolha — Escuro, desktop (1440 px)](./escuro-desktop/02-passo1-template.png)

**Assistente, passo 2: público, contagens e excluídos por motivo**

![Assistente, passo 2: público, contagens e excluídos por motivo — Escuro, desktop (1440 px)](./escuro-desktop/03-passo2-publico.png)

**Assistente, passo 3: ritmo, janela, dias e mini-calendário**

![Assistente, passo 3: ritmo, janela, dias e mini-calendário — Escuro, desktop (1440 px)](./escuro-desktop/04-passo3-ritmo-e-agenda.png)

**Assistente, passo 4: revisão, teste e confirmação digitada**

![Assistente, passo 4: revisão, teste e confirmação digitada — Escuro, desktop (1440 px)](./escuro-desktop/05-passo4-revisao.png)

**Detalhe de campanha em andamento**

![Detalhe de campanha em andamento — Escuro, desktop (1440 px)](./escuro-desktop/06-detalhe-em-andamento.png)

**Detalhe: destinatários**

![Detalhe: destinatários — Escuro, desktop (1440 px)](./escuro-desktop/07-detalhe-destinatarios.png)

**Detalhe: conferência manual (vazia)**

![Detalhe: conferência manual (vazia) — Escuro, desktop (1440 px)](./escuro-desktop/08-detalhe-conferencia.png)

**Detalhe: pausada automaticamente (alerta com motivo e o que fazer)**

![Detalhe: pausada automaticamente (alerta com motivo e o que fazer) — Escuro, desktop (1440 px)](./escuro-desktop/09-detalhe-pausada-automaticamente.png)

**Detalhe de campanha concluída**

![Detalhe de campanha concluída — Escuro, desktop (1440 px)](./escuro-desktop/10-detalhe-concluida.png)

**Configurações da instância**

![Configurações da instância — Escuro, desktop (1440 px)](./escuro-desktop/11-configuracoes-da-instancia.png)

**Perfil gestor: lista, Nova campanha desabilitada com a explicação**

![Perfil gestor: lista, Nova campanha desabilitada com a explicação — Escuro, desktop (1440 px)](./escuro-desktop/12-gestor-lista-sem-nova-campanha.png)

**Perfil gestor: detalhe, ações do administrador desabilitadas**

![Perfil gestor: detalhe, ações do administrador desabilitadas — Escuro, desktop (1440 px)](./escuro-desktop/13-gestor-detalhe-acoes-desabilitadas.png)

## Escuro, celular (390 px)

**Lista de campanhas**

![Lista de campanhas — Escuro, celular (390 px)](./escuro-celular/01-lista.png)

**Assistente, passo 1: template, variáveis e prévia em bolha**

![Assistente, passo 1: template, variáveis e prévia em bolha — Escuro, celular (390 px)](./escuro-celular/02-passo1-template.png)

**Assistente, passo 2: público, contagens e excluídos por motivo**

![Assistente, passo 2: público, contagens e excluídos por motivo — Escuro, celular (390 px)](./escuro-celular/03-passo2-publico.png)

**Assistente, passo 3: ritmo, janela, dias e mini-calendário**

![Assistente, passo 3: ritmo, janela, dias e mini-calendário — Escuro, celular (390 px)](./escuro-celular/04-passo3-ritmo-e-agenda.png)

**Assistente, passo 4: revisão, teste e confirmação digitada**

![Assistente, passo 4: revisão, teste e confirmação digitada — Escuro, celular (390 px)](./escuro-celular/05-passo4-revisao.png)

**Detalhe de campanha em andamento**

![Detalhe de campanha em andamento — Escuro, celular (390 px)](./escuro-celular/06-detalhe-em-andamento.png)

**Detalhe: destinatários**

![Detalhe: destinatários — Escuro, celular (390 px)](./escuro-celular/07-detalhe-destinatarios.png)

**Detalhe: conferência manual (vazia)**

![Detalhe: conferência manual (vazia) — Escuro, celular (390 px)](./escuro-celular/08-detalhe-conferencia.png)

**Detalhe: pausada automaticamente (alerta com motivo e o que fazer)**

![Detalhe: pausada automaticamente (alerta com motivo e o que fazer) — Escuro, celular (390 px)](./escuro-celular/09-detalhe-pausada-automaticamente.png)

**Detalhe de campanha concluída**

![Detalhe de campanha concluída — Escuro, celular (390 px)](./escuro-celular/10-detalhe-concluida.png)

**Configurações da instância**

![Configurações da instância — Escuro, celular (390 px)](./escuro-celular/11-configuracoes-da-instancia.png)

**Perfil gestor: lista, Nova campanha desabilitada com a explicação**

![Perfil gestor: lista, Nova campanha desabilitada com a explicação — Escuro, celular (390 px)](./escuro-celular/12-gestor-lista-sem-nova-campanha.png)

**Perfil gestor: detalhe, ações do administrador desabilitadas**

![Perfil gestor: detalhe, ações do administrador desabilitadas — Escuro, celular (390 px)](./escuro-celular/13-gestor-detalhe-acoes-desabilitadas.png)

