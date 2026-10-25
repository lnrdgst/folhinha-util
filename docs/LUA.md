# Fases da Lua — cálculo e convenções

## Domínio e intervalo

`domain/MoonPhases.kt` calcula offline os instantes das quatro fases principais usando Jean Meeus, *Astronomical Algorithms*, 2ª edição, capítulo 49. A época e o mês sinódico médio servem apenas de ponto inicial: são aplicados os termos periódicos de anomalia solar/lunar, argumento de latitude, nodo e 14 correções planetárias. Não é uma repetição de 29 ou 30 dias.

Suporte público: 2003–2099, igual ao calendário. Eventos vizinhos ao intervalo são calculados internamente para classificar corretamente os primeiros e últimos dias. O componente não depende de HolidayProvider, Android, API de rede ou armazenamento.

Referência matemática em código: [Sonia Keys, meeus/moonphase (MIT)](https://github.com/soniakeys/meeus/blob/master/v3/moonphase/moonphase.go). Licença em [licenses/meeus-MIT.txt](licenses/meeus-MIT.txt). A implementação Kotlin usa a mesma série de Meeus, com argumentos planetários calculados em graus antes da conversão para radianos.

## Escala de tempo e data local

Os resultados de Meeus são em TT/JDE. A conversão para UT usa os [polinômios ΔT de Espenak/Meeus publicados pela NASA](https://eclipse.gsfc.nasa.gov/SEcat5/deltatpoly.html), incluindo as expressões de 1986–2005, 2005–2050 e 2050–2150. UT1 é tratado como aproximação de UTC ao construir Instant; a diferença subsegundo atual não é representada.

A atribuição ao dia usa `date.atStartOfDay(zone)` até o início do dia seguinte, com `ZoneId` real do aparelho, não deslocamento fixo nem divisões de 24 horas. O fuso é uma entrada explícita do domínio e o mapa mensal é recalculado quando o mês ou o fuso muda.

Exemplo de referência: a Lua cheia de 02/04/2026 às 02:12 UT pertence a 01/04 em São Paulo. A cheia de 29/06/2026 às 23:56 UT pertence a 30/06 no fuso UTC+14. Ambos são testados.

## Oito nomes no calendário

[O USNO distingue quatro fases principais instantâneas e quatro intervalos intermediários](https://aa.usno.navy.mil/faq/moon_phases).

- Se um evento principal pertence ao dia civil selecionado, esse dia recebe Lua nova, Quarto crescente, Lua cheia ou Quarto minguante, e os detalhes mostram seu horário local aproximado.
- Nos outros dias, a fase é o intervalo entre eventos principais reais: crescente, gibosa crescente, gibosa minguante ou minguante. A consulta usa o meio-dia local, sem estimar eventos por uma fração constante da lunação.
- O indicador representa a **fase do dia**, conforme a convenção de calendário; não afirma que a fase principal exata dura 24 horas ou que já ocorreu no horário atual.
- Não é exibido percentual de iluminação: o algoritmo implementado fornece instantes de fases, não posição e distância completas para esse cálculo.

## Precisão e limitações

O teste usa 347 instantes oficiais, com resolução de um minuto, nos anos 2003, 2024, 2026, 2027, 2028, 2050 e 2099. Maior diferença absoluta observada: **45,082 segundos**, com tolerância explícita de **120 segundos** por instante. Isso mede as amostras verificadas, não é uma garantia de erro máximo em todos os instantes ou anos futuros.

As referências são independentes da implementação e ficam em `app/src/test/resources/astronomy/moon-phase-references.csv`, com as URLs de origem. São dados estáticos de teste, não tabelas usadas no aplicativo.

- [API do USNO: fases de 2024](https://aa.usno.navy.mil/api/moon/phases/year?year=2024), [2026](https://aa.usno.navy.mil/api/moon/phases/year?year=2026), [2027](https://aa.usno.navy.mil/api/moon/phases/year?year=2027).
- [NASA: fases 2001–2025](https://eclipse.gsfc.nasa.gov/phase/phase2001gmt.html) para 2003; [NASA: fases 2001–2100](https://eclipse.gsfc.nasa.gov/phase/phases2001.html) para 2028, 2050 e 2099.
- [Documentação da API do USNO](https://aa.usno.navy.mil/data/api).

ΔT futuro é uma previsão e depende da rotação irregular da Terra. Datas locais de eventos muito próximos da meia-noite podem diferir de efemérides de maior precisão; os horários apresentados são aproximados. O recurso não calcula nascer/pôr da Lua, eclipses, marés ou visibilidade do crescente.

## Interface e hemisfério sul

Os ícones são vetores desenhados com Compose Canvas, sem emojis, bitmaps ou biblioteca adicional. Na convenção adotada, crescente/quarto crescente têm iluminação à esquerda e minguante/quarto minguante à direita. São esquemas de fase do hemisfério sul, não uma simulação da orientação celeste de um observador: a inclinação aparente varia com latitude, horário e posição no céu.

O número, as cores do calendário e o ponto de feriado continuam independentes. A descrição acessível de cada dia inclui sua fase, e selecionar uma data sempre revela o detalhe lunar, mesmo sem agenda ou feriado. O widget não recebe informação lunar.
