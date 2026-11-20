# LiveImage

Aplicativo Android experimental para posicionar uma imagem flutuante sobre outros aplicativos durante uma LIVE.

## Recursos previstos
- Selecionar uma imagem da galeria.
- Ajustar tamanho e posição na prévia.
- Ativar ou remover a sobreposição.
- Remoção de fundo experimental para imagens de pessoas usando ML Kit Selfie Segmentation.

## Compilar
Abra o projeto no Android Studio ou execute `gradle assembleDebug` com Android SDK 35 e JDK 17.

O APK de depuração será gerado em `app/build/outputs/apk/debug/app-debug.apk`.

**Aviso:** a permissão de sobreposição apenas exibe a imagem no aparelho. O TikTok pode não incluir essa camada na transmissão. Isso depende do modo de LIVE/captura utilizado e precisa ser testado no próprio TikTok. A remoção de fundo é voltada a pessoas, não a objetos ou logotipos. Esta é uma primeira versão experimental, ainda não validada em aparelho físico.