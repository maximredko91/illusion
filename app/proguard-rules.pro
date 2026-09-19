# Room, Compose, Media3, and kotlinx.serialization ship their own consumer-rules.pro bundled in
# their AARs - nothing needed here for those. Rules below were added because R8 actually broke on
# them, not guessed preemptively.

# Instantiated by name from AndroidManifest metadata by the Google Cast framework.
-keep class com.illusion.app.data.cast.GoogleCastOptionsProvider { public <init>(); }

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

# FFmpeg video decoder JNI (src/main/cpp/ffmpeg_video_jni.cc) reaches these by name via
# GetMethodID/GetFieldID - invisible to R8, which removed initForYuvFrame/initForPrivateFrame in the
# first release build and crashed playback of every DivX file. Media3's own decoder extensions ship
# the same rule as a consumer rule; this app doesn't depend on those artifacts, so it needs its own.
-keep class androidx.media3.decoder.VideoDecoderOutputBuffer {
    boolean initForYuvFrame(int, int, int, int, int);
    void initForPrivateFrame(int, int);
    java.nio.ByteBuffer data;
    long decoderPrivate;
}
