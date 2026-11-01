# Minha Agenda

Minha Agenda reorganiza a tela existente de eventos pessoais. O calendário mensal continua sendo
a tela inicial do Folhinha Útil; o acesso à agenda e o botão de cadastro têm destaque em âmbar.
O botão Voltar e a navegação do Android retornam ao calendário.

## Organização

- **Próximos eventos**: inclui eventos de hoje, futuros e recorrências anuais, ordenados pela próxima
  ocorrência calculada pelas regras já existentes.
- **Eventos encerrados**: mantém os registros sem próxima ocorrência, com a data original, edição
  e exclusão. São exibidos em ordem de data decrescente, separados dos próximos eventos.
- Cada cartão apresenta título, data original, identificação Anual quando aplicável e a próxima
  ocorrência com contagem regressiva, ou Evento encerrado.
- O cadastro fica disponível no topo da lista. O cabeçalho com retorno permanece visível ao rolar.
- O estado vazio apresenta uma mensagem discreta e mantém a ação de cadastro.
- A lista utiliza LazyColumn e chaves por identificador para suportar listas maiores.

## Reutilização e dados

Foram mantidas a mesma PersonalEventsActivity e PersonalEventsScreen, sem uma tela paralela.
O formulário, a confirmação de exclusão, o DatePicker, a geração de identificadores, os limites
de título e as regras de recorrência são os existentes.

PersonalEventsRepository e o esquema JSON versão 1 do DataStore não foram alterados. Não há migração,
novo banco ou duplicação de registros. Eventos encerrados não são removidos automaticamente.
Também foram preservados CalendarRepository, a seleção de agendas Google/Android, o widget,
o catálogo de feriados e os cálculos lunares. Não foram adicionadas dependências, notificações,
backend ou sincronização na nuvem.

## Validação

Os testes de interface reutilizam PersonalDatePreviewActivity, exclusiva de debug, com repositório
em arquivo de cache isolado e sem atualização dos widgets a partir dos eventos sintéticos.
Os casos cobrem ordenação/separação, estado vazio, cadastro, edição, cancelamento e confirmação
de exclusão, reabertura, alternância da recorrência de um evento passado e retorno ao calendário.
A navegação com as atividades reais é somente de leitura; os testes de escrita usam a fixture.
Os testes existentes do DatePicker continuam disponíveis.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e section agenda com.folhinhautil.app.test/com.folhinhautil.app.widget.WidgetLayoutInstrumentation
adb shell am instrument -w -r -e section date com.folhinhautil.app.test/com.folhinhautil.app.widget.WidgetLayoutInstrumentation
```

## Resultados de 01/11/2026

| Verificação | Resultado |
| --- | --- |
| Testes JVM, execução final | 55 aprovados; zero falhas, erros ou ignorados |
| Lint debug | Zero erros; 17 avisos de manutenção e compatibilidade já existentes |
| Build | APK debug e APK auxiliar gerados |
| Minha Agenda no Samsung | 11 cenários aprovados, incluindo a rodada do APK final |
| DatePicker existente | 10 verificações aprovadas |
| Persistência e estado independente do widget | Dois métodos instrumentados aprovados |
| Layout do widget | 1.165 renderizações aprovadas |
| Interface lunar existente | 108 verificações aprovadas |
| Dados reais e seleção de calendários | Hashes SHA-256 idênticos antes/depois |

A validação foi realizada no Samsung SM-S918B, Android 16, com interface em português brasileiro.
A navegação entre calendário e Minha Agenda passou sem escrita em eventos reais. Os testes de CRUD
usaram o armazenamento isolado da fixture. As capturas inspecionadas contêm apenas dados sintéticos.
O código de produção do widget e os repositórios de calendários e eventos pessoais foram conferidos
por hash e preservados. Os diálogos de cadastro/exclusão e o DatePicker foram reutilizados sem alteração.

Os testes de interface foram executados por seções, diretamente por ADB. Outros modelos, tablets,
idiomas e uma distribuição assinada de release não foram validados nesta entrega. A lista pode rolar;
um cartão parcialmente visível na borda da tela continua acessível pela rolagem.

O APK de debug fica em app/build/outputs/apk/debug/app-debug.apk e foi instalado com adb install -r,
sem limpeza dos dados. Não houve mudança de relógio/fuso, commit ou push.
