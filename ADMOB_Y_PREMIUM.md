# Estado actual (v0.5.19): AdMob real conectado

El SDK de AdMob (`play-services-ads 23.6.0`, incluye el consentimiento UMP) ya está integrado. **No hay que tocar código**: todo se
controla en `gradle.properties` (claves `admob.*`).

- `admob.real=false` (por defecto) → IDs de **prueba de Google** (anuncios con la etiqueta "Test Ad").
- `admob.real=true` + `admob.appId` y los IDs de bloque de TU cuenta → anuncios reales. Si falta `admob.appId` se siguen usando los de prueba.
- `admob.testDevices` → ID(s) de tu teléfono (Logcat, filtro `Ads`/`GoogleAds`, o AdMob → Ajustes → Dispositivos de prueba).
  Con IDs reales **solo se debe probar en un dispositivo registrado como de prueba**, para que AdMob no cuente tus vistas/clics.
- `admob.testVersion`: versión registrada en AdMob → Test apps. Si la versión compilada es otra, se usan los IDs de demostración (seguro). Al publicar, déjalo vacío.
- El ID de la app (`~`) va en el manifiesto vía `manifestPlaceholders` y el SDK se inicia tras el consentimiento (`Ads.startSdk`).
- Si un anuncio no carga o se cierra antes de tiempo no se da la recompensa (no hay anuncio simulado con el SDK real).
- Rewarded: `AdMobRewardedProvider.kt`. Banners: `RealBanner` en `MonetizationUi.kt`. El modo simulado se vuelve a activar con `STUB_MODE = true`.

---

# Conectar AdMob y Google Play Billing a Flux

Todo lo de monetización vive en `app/src/main/java/app/flux/downloader/monetization/`. Hoy funciona en **modo de preparación**:
los anuncios con recompensa son una ventana simulada con cuenta regresiva, los banners son un espacio reservado y la compra
Premium avisa que "estará disponible pronto". Para activar lo real hay que seguir estos pasos.

## 0) Datos que necesito y cómo conseguirlos (paso a paso)

### A. Datos de AdMob (los que me tienes que pasar)
| # | Dato | Dónde se saca | Formato |
|---|---|---|---|
| 1 | **ID de la aplicación** | AdMob → Aplicaciones → Flux → Configuración de la app | `ca-app-pub-XXXXXXXXXXXXXXXX~NNNNNNNNNN` |
| 2 | **Bloque recompensado "descarga"** | Aplicaciones → Flux → Bloques de anuncios → Añadir → Recompensado | `ca-app-pub-XXXXXXXXXXXXXXXX/NNNNNNNNNN` |
| 3 | **Bloque recompensado "carpeta privada"** (desbloquear) | igual que el anterior | igual |
| 4 | **Bloque recompensado "casilla"** (+1 casilla) | igual que el anterior | igual |
| 5 | **Bloque banner "Descargas"** | Añadir bloque → Banner | igual |
| 6 | **Bloque banner "Progreso"** | Añadir bloque → Banner | igual |
| 7 | (Opcional) ID de tu dispositivo de prueba | AdMob → Ajustes → Dispositivos de prueba | texto |
Se pueden usar 1 solo bloque recompensado y 1 solo banner, pero conviene tener 3 + 2 para medir cada ubicación por separado.

### B. Paso a paso en AdMob
1. Entra a https://admob.google.com con tu cuenta de Google → **Comenzar**. Acepta las condiciones y elige país, zona horaria y **moneda de la cuenta (USD recomendado; no se puede cambiar después)**.
2. **Pagos**: Pagos → Información de pago: completa nombre y dirección, forma de cobro y la **información fiscal** (para quien no vive en EE. UU. suele ser el formulario W-8BEN). Se cobra al superar el umbral de pago; al llegar a unos US$10 Google manda un **PIN por correo postal** para verificar la dirección.
3. **Añadir la app**: Aplicaciones → **Añadir aplicación** → Android → si todavía no está en Google Play responde **No** y escribe el nombre "Flux". Al publicarla en Play se vincula con el paquete `app.flux.downloader`. Al terminar te muestra el **ID de la aplicación** (dato 1).
4. **Bloques recompensados**: dentro de la app → Bloques de anuncios → Añadir bloque → **Recompensado**. Nombre, p. ej., `flux_descarga`. AdMob pide una recompensa (elemento y cantidad): pon `descarga` y `1` (no se usa, la app da la recompensa). Repite para `flux_privada` y `flux_casilla`. Cada uno te da su ID (datos 2, 3 y 4).
5. **Banners**: Añadir bloque → **Banner** → `flux_banner_descargas` y `flux_banner_progreso` (datos 5 y 6).
6. **Consentimiento (obligatorio en UE/Reino Unido)**: Privacidad y mensajería → **Consentimiento europeo** → crear mensaje → asignarlo a la app Flux. Opcional: normas de privacidad de EE. UU.
7. **app-ads.txt** (recomendado para cobrar mejor): necesitas una página web propia; en Google Play pones esa web como sitio del desarrollador y subes `app-ads.txt` a su raíz con `google.com, pub-XXXXXXXXXXXXXXXX, DIRECT, f08c47fec0942fa0` (tu `pub-…` es la parte del ID de la app antes de `~`). Se puede hacer después.
8. **Dispositivo de prueba**: Ajustes → Dispositivos de prueba → Añadir con el ID que aparece en el registro (Logcat) al correr la app con el SDK. Mientras desarrollas se usan siempre los IDs de prueba; con los reales solo en la versión publicada (si no, AdMob puede suspender la cuenta por clics propios).
9. AdMob **revisa la app** cuando está publicada; hasta que la aprueban puede mostrar pocos anuncios.

### C. Datos de Google Play (para Premium)
| # | Dato | Dónde |
|---|---|---|
| 8 | Suscripción mensual con ID exacto `flux_premium_monthly` (US$2.99/mes, renovación automática) | Play Console → Monetizar → Productos → Suscripciones |
| 9 | Suscripción anual con ID exacto `flux_premium_annual` (US$7.99/año, renovación automática) | igual |
| 10 | URL de la **política de privacidad** y correo de soporte | la piden Play y el mensaje de consentimiento |
| 11 | ¿Habrá prueba gratis (Aloha da 7 días)? Sí/No y cuántos días | decisión de negocio (se configura como oferta en la suscripción) |
Pasos: crear la cuenta de desarrollador de Google Play (pago único de US$25) y el **perfil de pagos de comerciante**; crear la app con el paquete `app.flux.downloader`; en "Contenido de la app" declarar **Anuncios: sí** y completar **Seguridad de los datos** (ID de publicidad y compras); crear las dos suscripciones (plan base mensual y anual) y activarlas; en Configuración → Pruebas de licencia añadir los correos de tus probadores.

### D. Decisiones que necesito de ti
1. Si un anuncio no carga (sin internet o sin relleno): ¿se permite la descarga igualmente o se pide reintentar?
2. ¿Prueba gratis en Premium? (sí/no, cuántos días)
3. Banner fijo cerca de la barra de pestañas: la política de AdMob prohíbe ubicarlos donde se pueda tocar por error; si lo prefieres lo separo más de la barra.

## Qué se necesita de la cuenta
**AdMob** (https://admob.google.com):
- ID de la aplicación (`ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`).
- ID de bloque **Recompensado** (1 para descargas, 1 para desbloquear la carpeta privada y 1 para ganar casillas; pueden ser el mismo).
- ID de bloque **Banner** (1 para la pestaña Descargas y 1 para Progreso; pueden ser el mismo).
- Mensaje de consentimiento (UMP) configurado en AdMob → Privacidad y mensajes (obligatorio en la UE/Reino Unido).

**Google Play Console**:
- La app creada y subida (al menos a pruebas internas).
- Dos suscripciones con estos IDs de producto: `flux_premium_monthly` (US$2.99/mes) y `flux_premium_annual` (US$7.99/año).

## 1) AdMob
1. `app/build.gradle.kts` → dependencias: `implementation("com.google.android.gms:play-services-ads:23.6.0")` (usar la versión vigente).
2. `AndroidManifest.xml`, dentro de `<application>`:
   ```xml
   <meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY"/>
   ```
3. `Ads.kt` → `AdConfig`: cambiar `ADMOB_APP_ID`, `REWARDED_*` y `BANNER_*` por los reales y poner `STUB_MODE = false`.
4. `FluxApp.onCreate()`: `MobileAds.initialize(this)` y `Ads.provider = AdMobRewardedProvider(this)`.
5. Crear `monetization/AdMobRewardedProvider.kt`:
   ```kotlin
   package app.flux.downloader.monetization

   import android.app.Activity
   import android.content.Context
   import com.google.android.gms.ads.AdError
   import com.google.android.gms.ads.AdRequest
   import com.google.android.gms.ads.FullScreenContentCallback
   import com.google.android.gms.ads.LoadAdError
   import com.google.android.gms.ads.rewarded.RewardedAd
   import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
   import kotlinx.coroutines.suspendCancellableCoroutine
   import kotlin.coroutines.resume

   class AdMobRewardedProvider(private val ctx: Context) : RewardedAdProvider {
       override suspend fun show(placement: AdPlacement, activity: Activity?): Boolean {
           if (activity == null) return false
           val ad: RewardedAd = runCatching {
               suspendCancellableCoroutine<RewardedAd> { cont ->
                   RewardedAd.load(ctx, placement.adUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                       override fun onAdLoaded(ad: RewardedAd) { if (cont.isActive) cont.resume(ad) }
                       override fun onAdFailedToLoad(e: LoadAdError) {
                           if (cont.isActive) cont.resumeWith(Result.failure(IllegalStateException(e.message)))
                       }
                   })
               }
           }.getOrNull() ?: return false // si no carga, no se da la recompensa (decidir con el dueño si se permite igual)
           return suspendCancellableCoroutine { cont ->
               var rewarded = false
               ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                   override fun onAdDismissedFullScreenContent() { if (cont.isActive) cont.resume(rewarded) }
                   override fun onAdFailedToShowFullScreenContent(e: AdError) { if (cont.isActive) cont.resume(false) }
               }
               ad.show(activity) { rewarded = true }
           }
       }
   }
   ```
   (Si el anuncio no carga, conviene permitir la acción igualmente o mostrar un aviso: decidirlo con el dueño. Políticas de AdMob: el anuncio
   con recompensa debe ser opcional y explicar la recompensa antes de mostrarse; en Flux ya se explica en cada pantalla.)
6. Banners: en `MonetizationUi.kt`, `AdBanner(...)`, dejar de retornar cuando `STUB_MODE = false` y dibujar el `AdView`:
   ```kotlin
   AndroidView(factory = { c ->
       AdView(c).apply {
           setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(c, 360))
           adUnitId = if (placement == "progreso") AdConfig.BANNER_PROGRESS else AdConfig.BANNER_HOME
           loadAd(AdRequest.Builder().build())
       }
   }, modifier = modifier.fillMaxWidth())
   ```
7. Consentimiento (UMP): `com.google.android.ump:user-messaging-platform` y solicitar el formulario antes de `MobileAds.initialize`.
8. Probar SIEMPRE con los IDs de prueba (los que ya vienen en `AdConfig`) y los IDs reales solo en la versión publicada, para no arriesgar la cuenta.

## 2) Google Play Billing (Premium)
1. Dependencia: `implementation("com.android.billingclient:billing-ktx:7.1.1")` (versión vigente).
2. Implementar `BillingProvider` (en `Premium.kt` está la interfaz y `Billing.provider`):
   - consultar los productos `PremiumPlans.PRODUCT_MONTHLY` / `PRODUCT_ANNUAL` con `queryProductDetails` para mostrar el precio local en `PremiumScreen`;
   - `launchBillingFlow(activity, ...)` en `purchase(...)`;
   - al confirmar (`PurchaseState.PURCHASED`): `acknowledgePurchase` y `PremiumState.setPremium(true)`;
   - al iniciar la app: `queryPurchasesAsync` para restaurar / revocar Premium (si ya no hay suscripción activa → `setPremium(false)`).
3. `FluxApp.onCreate()`: `Billing.provider = PlayBillingProvider(this)`.
4. La pantalla Premium ya tiene los dos planes (anual con "Ahorra 78%"), el botón Comprar y el texto de renovación automática.

## 3) Qué hace Premium hoy (ya conectado a `PremiumState.isPremium`)
- Descargas sin anuncio en todas las calidades (`DownloadGate`).
- Sin banners (`AdBanner`) ni anuncios para ganar casillas.
- Casillas ilimitadas en la carpeta privada (`VaultSlots`).
- Ícono blanco de Flux (`AppIcon`, alias del manifiesto).
En versiones de desarrollo (debuggable) la pantalla Premium trae un botón para activar/desactivar Premium de prueba.

## 4) Reglas de negocio (por si hay que ajustarlas)
- 240p es gratis y sin anuncios; el resto de calidades y el audio piden un anuncio con recompensa por video (`DownloadGate.FREE_MAX_HEIGHT`).
- "Descargar todos": un anuncio por video, uno tras otro (`Ads` usa un `Mutex`).
- Carpeta privada: se desbloquea una vez con un anuncio (3 casillas); luego 1 casilla por anuncio, máximo 5 al día (`VaultSlots.DAILY_AD_LIMIT`).
- El gestor de archivos y las carpetas normales no llevan publicidad.
