const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, 'frontend', 'src', 'components', 'shell', 'sidebar.test.tsx');
let data = fs.readFileSync(filePath, 'utf8');

// Replace automacao in the mock
data = data.replace(
  'automacao: "Automa\\u00E7\\u00E3o",', // the old one might have encoding issues or just use literal
  'automacao: "Automa\\u00E7\\u00E3o", administracao: "Administra\\u00E7\\u00E3o",'
);

// If literal
data = data.replace(
  /automacao: "(.*?)",/,
  'automacao: "Automação",\n        administracao: "Administração",'
);

// Add the test
const testCode = `
  it("esconde Administracao para ATENDENTE e mostra para GESTOR", async () => {
    renderSidebar();
    await screen.findByText("Agenda de Contatos");
    expect(screen.queryByText("Administração")).not.toBeInTheDocument();

    authMock.papel = "GESTOR";
    renderSidebar();
    expect(await screen.findByText("Administração")).toBeInTheDocument();
  });
});
`;

data = data.replace(/}\);\s*$/, testCode);

fs.writeFileSync(filePath, data);
console.log("sidebar.test.tsx atualizado");
