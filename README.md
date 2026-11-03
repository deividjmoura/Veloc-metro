# GPS Speedometer

Aplicativo Android nativo em Kotlin + Jetpack Compose, com painel esportivo horizontal, velocímetro GPS e mapa integrado.

## Requisitos
- Android Studio Ladybug ou mais recente
- JDK 17
- Android SDK 35
- Projeto Google Cloud com faturamento configurado quando exigido pelas APIs

## Configurar Google Maps e rotas
1. Crie um projeto no Google Cloud Console.
2. Ative Maps SDK for Android para exibir o mapa.
3. Para desenhar a rota dentro do painel, ative Directions API e configure o faturamento conforme as regras do Google.
4. Crie o arquivo local.properties na raiz (não faça commit):

```properties
MAPS_API_KEY=SUA_CHAVE_MAPS_ANDROID
DIRECTIONS_API_KEY=SUA_CHAVE_DIRECTIONS
```

**Segurança:** a chamada Directions desta versão é feita diretamente pelo aplicativo e a chave pode ser extraída do APK. Use apenas em desenvolvimento, com chave separada, restrições, cotas e monitoramento. Antes de publicar, mova essa chamada para um backend protegido. Restrinja a chave Maps SDK ao pacote com.gpsspeedometer e ao SHA-1 da assinatura. Não distribua uma chave de web service irrestrita dentro do aplicativo.

Sem MAPS_API_KEY, o mapa pode ficar em branco. Sem DIRECTIONS_API_KEY, a rota interna fica indisponível, mas a navegação externa continua funcionando.

## Abrir e executar
1. Abra a pasta do projeto no Android Studio.
2. Configure as chaves em local.properties.
3. Aguarde a sincronização do Gradle.
4. Execute em um aparelho Android com GPS e conceda a permissão de localização.

## Funcionalidades 0.3.0
- Interface esportiva em orientação horizontal.
- Velocímetro GPS com alternância km/h e mph.
- Velocidade máxima da sessão e média real da viagem desde o início do contador (inclui períodos parado).
- Distância da viagem, reset e odômetro acumulado persistido.
- Google Maps integrado no painel direito, marcador GPS e centralização.
- Campo de destino e cálculo de rota de carro via Directions API.
- Desenho da polyline da rota, marcador do destino e enquadramento automático.
- Exibição da distância e duração estimada retornadas pelo serviço.
- Alternativa para abrir a navegação no Google Maps externo.

## Testes automatizados

Execute os testes unitários com `gradle testDebugUnitTest`. O GitHub Actions executa essa tarefa antes de gerar o APK debug.

## Pendências conhecidas
- Histórico de viagens com Room.
- Rastreamento em segundo plano com foreground service e notificação persistente.
- Backend protegido para chamadas de rotas antes de publicar em produção.
- Tratamento de destinos ambíguos com seleção de resultados/Places.
- Compilação automatizada pelo GitHub Actions; testes funcionais em aparelho físico ainda precisam ser realizados.

> O odômetro começa em zero nesta instalação e acumula distância GPS válida. Não representa o hodômetro físico do veículo.
