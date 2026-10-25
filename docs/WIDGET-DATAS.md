# Widget lateral e seleção visual de datas — 25/10/2026

## Causa e correção

O widget antigo media o cabeçalho e a legenda fora da comparação das duas colunas. A enumeração de Placement permitia outras datas abaixo da grade e priorizava o número de eventos pessoais antes de preferir a lateral. Outubro de 2026 tem nomes nacionais mais extensos que março de 2027; o custo de quebra de linha fazia a combinação com a data facultativa na lateral perder para a opção horizontal.

O cabeçalho com navegação, dias da semana, grade e legenda agora pertencem à coluna esquerda. A direita começa no topo do cabeçalho, com os mesmos três nacionais e um divisor antes do título condicional de outras datas. A altura mínima superior é max(altura completa esquerda, altura completa direita). Placement não admite datas amarelas no rodapé quando a variante é lateral. As legendas se tornam compactas, em duas linhas quando necessário. O título amarelo e seu espaçamento não são reservados quando o grupo está vazio.

A seleção dos feriados e suas categorias não mudou. Foram mantidos espaçamentos adaptativos, quebra de nomes completos, alternativa empilhada e aviso explícito para variantes muito pequenas. As fontes da grade não foram reduzidas. A quantidade de eventos exibidos continua dependente do espaço; as regras de ocorrência e contagem não mudaram.

## Data do evento

PersonalEventDateField usa OutlinedCard como um único campo acionável por toque e teclado, com rótulo, valor brasileiro e ícone de calendário decorativo. DatePickerDialog, DatePicker e rememberDatePickerState vêm da dependência Material 3 existente, sem novo pacote. A escolha de ano do componente permite alcançar anos distantes; o intervalo 1–9999 preserva datas estritas de quatro dígitos, sem bloquear o passado. A digitação não é necessária e o alternador de entrada textual não é exibido.

Cada abertura inicializa a seleção com a data do formulário. O estado provisório pertence ao diálogo; somente Confirmar chama onSelected. Cancelar e fechar o diálogo não alteram o formulário. O fluxo de salvar, validação estrita, UUID, recorrência anual, repositório e JSON existentes foram preservados.

selectedDateMillis representa meia-noite UTC da data civil. As duas conversões em PersonalDateSelection.kt usam ZoneOffset.UTC, nunca o fuso local. O domínio e a persistência continuam LocalDate e a mesma string ISO.

Referência do componente: [documentação oficial Android — Date pickers](https://developer.android.com/develop/ui/compose/components/datepickers).

## Verificações

- Widget: três testes instrumentados aprovados no Samsung Android 16; 925 renderizações sintéticas. Incluem outubro/2026, novembro/2026, março/2027, quatro/cinco/seis semanas, quatro escalas de fonte, navegação e armazenamento isolados, limites de texto/contêineres, sobreposição, legendas e ausência de truncamentos do Glance.
- Samsung 401,33 × 453,87 dp e variante 400 × 450 dp: em fontes 1,0 e 1,15, outubro e março exigem outras datas completas na direita e ambos os eventos pessoais abaixo. Novembro preserva os três nacionais. Capturas sintéticas desses três meses foram inspecionadas em app/build/qa/ (ignorado pelo Git).
- JVM: 48 testes aprovados, sem falhas ou ignorados. A suíte acrescenta conversão UTC em quatro fusos, datas históricas (inclusive 1800), futura, limites 1/9999, 29/02 e recorrência anual; março/2027 preserva Sexta-feira da Paixão como religioso local e Páscoa como comemorativa, separados dos nacionais.
- Lint: 0 erros, 15 avisos preexistentes. Build de debug e APK de instrumentação concluídos; git diff --check aprovado.
- O teste instrumentado PersonalDateDeviceChecks compila o fluxo do formulário real com repositório exclusivo no cache: seleção, cancelamento, edição, datas históricas/bissextas, salvar e reabrir. A execução desse fluxo no aparelho foi dispensada explicitamente pelo usuário. Não se declara aprovação funcional/visual desse seletor no Samsung.
- A fixture PersonalDatePreviewActivity existe somente no APK debug, não é exportada, não grava no repositório do usuário e não atualiza os widgets do launcher. Nenhum evento real foi criado, alterado ou removido.

Comandos locais: gradlew.bat --offline --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest. Instrumentação de widget: adb shell am instrument -w -r -e section widget com.folhinhautil.app.test/com.folhinhautil.app.widget.WidgetLayoutInstrumentation. O runner aceita section date para a futura conferência do seletor em aparelho desbloqueado.

## Limites e pendências

A aprovação manual do seletor (campo/ícone, lista de anos, confirmar/cancelar, salvar/reabrir e fontes ampliadas) permanece pendente por decisão do usuário. Fontes muito grandes e dimensões pequenas podem limitar itens visíveis ou usar o fallback; as seleções e os dados persistidos não são reduzidos. O catálogo segue limitado a 2099 e não foi extrapolado. A pendência lunar anterior descrita em VALIDACAO.md não foi alterada nesta tarefa. O pedido inicial previa entrega local; após os resultados, o usuário autorizou explicitamente commit e push para main.
