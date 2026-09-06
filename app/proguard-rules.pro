# V1 默认关闭 minify 以保证构建确定性。
# 如后续开启混淆，必须保留以下 libxposed 官方规则：
-dontwarn io.github.libxposed.annotation.**
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
