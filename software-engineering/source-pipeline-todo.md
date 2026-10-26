# Deferred

- **JMA (`JarModAgent` / ClassTransform)** — runtime jar-mod/coremod agent
  format (unimined/JarModAgent, Lenni0451's ClassTransform): `.transform`
  resource files + `@CTransformer`/`@CInject`-style annotation targets need
  remapping. Fits the existing `AnnotationTableExtension`/`AsmExtension` seam
  (same shape as `MixinExtension`). Deferred — implement when actually needed.
