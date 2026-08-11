import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
  ]),
  {
    files: ["**/*.{js,jsx,ts,tsx}"],
    rules: {
      "no-restricted-syntax": [
        "error",
        {
          selector: "JSXOpeningElement[name.name='select']",
          message: "Use o Select do shadcn em vez de <select> nativo.",
        },
        {
          selector:
            "JSXOpeningElement[name.name='input'] > JSXAttribute[name.name='type'][value.value='date']",
          message: "Use Popover + Calendar do shadcn em vez de input date nativo.",
        },
        {
          selector:
            "JSXOpeningElement[name.name='Input'] > JSXAttribute[name.name='type'][value.value='date']",
          message: "Use Popover + Calendar do shadcn em vez de Input date nativo.",
        },
        {
          selector:
            "JSXOpeningElement[name.name='input'] > JSXAttribute[name.name='type'][value.value='datetime-local']",
          message: "Use Calendar + Select do shadcn em vez de input datetime-local nativo.",
        },
        {
          selector:
            "JSXOpeningElement[name.name='Input'] > JSXAttribute[name.name='type'][value.value='datetime-local']",
          message: "Use Calendar + Select do shadcn em vez de Input datetime-local nativo.",
        },
      ],
    },
  },
]);

export default eslintConfig;
