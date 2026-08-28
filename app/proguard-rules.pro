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
