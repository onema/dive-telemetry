import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Dive Telemetry',
  description: 'Convert dive computer exports into Telemetry overlay CSV format',
  base: '/dive-telemetry/',
  appearance: 'dark',

  themeConfig: {
    logo: '/img/dive-telemetry-logo.svg',

    nav: [
      { text: 'Home', link: '/' },
      { text: 'UI', link: '/ui-usage.md' },
      { text: 'CLI', link: '/cli-usage.md' },
      { text: 'Plugin System', link: '/plugin-system.md' },
      { text: 'GitHub', link: 'https://github.com/onema/dive-telemetry' }
    ],

    sidebar: {
      '/': [
        {
          text: 'Getting Started',
          items: [
            { text: 'Home', link: '/' },
            { text: 'UI Usage', link: '/ui-usage.md' },
            { text: 'CLI Usage', link: '/cli-usage.md' }
          ]
        },
        {
          text: 'Plugin System',
          items: [
            { text: 'Plugin System Overview', link: '/plugin-system.md' },
            { text: 'Creating Plugins', link: '/creating-plugins.md' },
            { text: 'Adding Plugins to UI & CLI', link: '/adding-plugins-to-ui-cli.md' }
          ]
        },
        {
          text: 'Architecture',
          items: [
            { text: 'Parsing & Domain Model', link: '/parsing-and-domain-model.md' },
            { text: 'Converter', link: '/converter.md' },
            { text: 'Error Handling', link: '/error-handling.md' }
          ]
        },
        {
          text: 'Advanced',
          items: [
            { text: 'Adding Dive Computers', link: '/adding-dive-computers.md' },
            { text: 'VBA Macro Gap Analysis', link: '/gap-analysis.md' }
          ]
        }
      ]
    },

    search: {
      provider: 'local'
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/onema/dive-telemetry' }
    ]
  }
})
