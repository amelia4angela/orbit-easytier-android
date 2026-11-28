#!/usr/bin/env bash
# 生成结果的回归自测：把 app 里真正的 TomlUtils.kt / ConfigState.kt 编到 JVM 上跑，
# 用 tomlj 解析输出的 TOML，逐项确认字段落在官方 EasyTier 2.6.4 的正确段里。
#
# 用法：bash tools/toml-selftest/run.sh
# 依赖：JDK（JAVA_HOME 或 PATH 里的 java）+ 网络（首次下载 3 个 jar 到本目录 lib/）。
set -euo pipefail
cd "$(dirname "$0")"
REPO="$(cd ../.. && pwd)"

LIB=lib
mkdir -p "$LIB"
[ -f "$LIB/json.jar" ] || curl -sSfL -o "$LIB/json.jar" \
  https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar
[ -f "$LIB/tomlj-1.1.1.jar" ] || curl -sSfL -o "$LIB/tomlj-1.1.1.jar" \
  https://repo1.maven.org/maven2/org/tomlj/tomlj/1.1.1/tomlj-1.1.1.jar
[ -f "$LIB/antlr4-runtime-4.11.1.jar" ] || curl -sSfL -o "$LIB/antlr4-runtime-4.11.1.jar" \
  https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.11.1/antlr4-runtime-4.11.1.jar

# 用 Gradle 缓存里的 kotlin 编译器，避免额外下载（只要 modules-2 里的原版 jar，
# jars-9 下是 Gradle 插桩过的副本，脱离 Gradle 进程跑不起来）。
# 按版本号取最高的那一份：工程升级 Kotlin 后缓存里会同时存在新旧编译器，
# 自测必须用工程实际使用的新编译器（K2）跑，否则测的是旧行为。
newest_jar() {
  find "$HOME/.gradle/caches" -path "*/modules-2/*" -name "$1" -printf '%f\t%p\n' 2>/dev/null \
    | sort -V -t$'\t' -k1,1 | tail -1 | cut -f2-
}
KC=$(newest_jar "kotlin-compiler-embeddable-*.jar")
STD=$(newest_jar "kotlin-stdlib-[0-9]*.jar")
TROVE=$(find "$HOME/.gradle/caches" -path "*/modules-2/*" -name "trove4j-*.jar" | head -1)
DAEMON=$(find "$HOME/.gradle/caches" -path "*/modules-2/*" -name "kotlin-daemon-embeddable-*.jar" | head -1)
ANN=$(find "$HOME/.gradle/caches" -path "*/modules-2/*" -name "annotations-2[0-9]*.jar" | head -1)
if [ -z "$KC" ] || [ -z "$STD" ]; then
  echo "找不到 kotlin 编译器，请先在仓库里跑一次 ./gradlew tasks 预热 Gradle 缓存" >&2
  exit 1
fi

# Kotlin 2.x 的 compiler-embeddable 在启动时就要 kotlin-reflect（1.x 里是内嵌的）。
# 优先用与编译器同版本的 reflect，没有就下到 lib/ 里。
KVER=$(basename "$KC" .jar); KVER=${KVER#kotlin-compiler-embeddable-}
REFLECT=$(newest_jar "kotlin-reflect-$KVER.jar")
if [ -z "$REFLECT" ]; then
  curl -sSfL -o "$LIB/kotlin-reflect-$KVER.jar" \
    "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-reflect/$KVER/kotlin-reflect-$KVER.jar"
  REFLECT="$LIB/kotlin-reflect-$KVER.jar"
fi
COROUTINES=$(find "$HOME/.gradle/caches" -path "*/modules-2/*" -name "kotlinx-coroutines-core-jvm-*.jar" | head -1)

DEPS="$LIB/json.jar:$LIB/tomlj-1.1.1.jar:$LIB/antlr4-runtime-4.11.1.jar"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT

echo "编译自测源码：Kotlin ${KVER}（$(basename "$KC")）"
java -cp "$KC:$STD:$REFLECT:$TROVE:$DAEMON:$COROUTINES:$ANN" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -classpath "$DEPS:$STD" -d "$OUT" -no-reflect -nowarn \
  "$REPO/orbit/src/main/java/com/easytier/TomlUtils.kt" \
  "$REPO/orbit/src/main/java/com/easytier/ui/ConfigState.kt" \
  SelfTest.kt

java -cp "$OUT:$DEPS:$STD" SelfTestKt
