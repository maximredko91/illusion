# Room, Compose, Media3, and kotlinx.serialization ship their own consumer-rules.pro bundled in
# their AARs - nothing needed here for those. Rules below were added because R8 actually broke on
# them, not guessed preemptively.

# smbj's transitive deps (net.engio.mbassy's optional event-filter expression-language support,
# and Kerberos/SPNEGO auth via org.ietf.jgss) reference JVM-only classes that don't exist on
# Android and are never actually reached at runtime - this app only ever uses NTLM auth (see
# SmbClient.kt), never Kerberos, and never touches mbassy's EL-expression event filtering. R8's
# own generated missing_rules.txt for this exact build.
-dontwarn javax.el.BeanELResolver
-dontwarn javax.el.ELContext
-dontwarn javax.el.ELResolver
-dontwarn javax.el.ExpressionFactory
-dontwarn javax.el.FunctionMapper
-dontwarn javax.el.ValueExpression
-dontwarn javax.el.VariableMapper
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# ML Kit's on-device translate library (com.google.mlkit:translate, used for offline tag
# translation - see TagTranslationRepository) is built on Google's internal GMS/Dagger-wired
# machinery, and R8 stripping/renaming a field its generated *_Factory constructors depend on
# crashes with a NullPointerException the moment any code path lazily touches it - not confined to
# one package. First observed opening Settings (com.google.android.datatransport, MLKit's own
# usage-telemetry batching), then again scrolling DownloadsScreen's LazyColumn
# (com.google.mlkit.nl.translate.internal + com.google.android.gms.internal.mlkit_translate/
# mlkit_common) - same root cause, different lazy-init trigger site each time, so keeping one
# narrow package at a time is whack-a-mole. Keeping every package this dependency actually ships
# is the real fix, matches Google's own R8 guidance for GMS/Firebase-adjacent SDKs, and costs
# little size next to the ML Kit model/runtime .so files themselves.
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.android.datatransport.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_translate.** { *; }
-dontwarn com.google.android.gms.internal.mlkit_translate.**
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
-dontwarn com.google.android.gms.internal.mlkit_common.**
