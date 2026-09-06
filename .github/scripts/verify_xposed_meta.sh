#!/usr/bin/env bash
# Xposed 元数据与入口类发布门禁（开发书 10.4）。
# 用法: verify_xposed_meta.sh <apk路径>
set -euo pipefail

APK="${1:?usage: verify_xposed_meta.sh <apk>}"
ENTRY_CLASS="com.ouhuan.oplusassistant.xposed.MainModule"

fail() {
    echo "::error::$1"
    exit 1
}

[ -f "$APK" ] || fail "APK not found: $APK"

INIT_LIST="$(unzip -p "$APK" META-INF/xposed/java_init.list 2>/dev/null)" \
    || fail "META-INF/xposed/java_init.list missing in APK"
INIT_TRIMMED="$(printf '%s' "$INIT_LIST" | tr -d '[:space:]')"
[ "$INIT_TRIMMED" = "$ENTRY_CLASS" ] \
    || fail "java_init.list invalid. expected '$ENTRY_CLASS', got: '$INIT_TRIMMED'"

MODULE_PROP="$(unzip -p "$APK" META-INF/xposed/module.prop 2>/dev/null)" \
    || fail "META-INF/xposed/module.prop missing in APK"
grep -qx 'minApiVersion=102' <<<"$MODULE_PROP" || fail "module.prop minApiVersion must be 102"
grep -qx 'targetApiVersion=102' <<<"$MODULE_PROP" || fail "module.prop targetApiVersion must be 102"
grep -qx 'staticScope=true' <<<"$MODULE_PROP" || fail "module.prop staticScope must be true"
grep -qx 'exceptionMode=protective' <<<"$MODULE_PROP" || fail "module.prop exceptionMode must be protective"
grep -qx 'autoHotReload=false' <<<"$MODULE_PROP" || fail "module.prop autoHotReload must be false"

SCOPE="$(unzip -p "$APK" META-INF/xposed/scope.list 2>/dev/null | tr -d '[:space:]')" \
    || fail "META-INF/xposed/scope.list missing in APK"
[ "$SCOPE" = "system" ] || fail "scope.list must contain exactly 'system' (got: '$SCOPE')"

unzip -p "$APK" 'classes*.dex' | grep -aq "L${ENTRY_CLASS//./\/};" \
    || fail "entry class $ENTRY_CLASS not found in dex (java_init.list entry is invalid)"

echo "Xposed metadata OK: $APK"
