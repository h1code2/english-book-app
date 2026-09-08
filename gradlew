#!/bin/sh

##############################################################################
# Incremental Gradle wrapper bootstrap: uses an already-unpacked local
# distribution if present, otherwise falls back to the standard wrapper jar.
##############################################################################

APP_BASE_NAME=$(basename "$0")
DIRNAME=$(cd "$(dirname "$0")" && pwd)

die() { echo "$*" >&2; exit 1; }

# Resolve project version from gradle-wrapper.properties
GRADLE_VERSION=$(sed -n 's/^distributionUrl=.*gradle-\(.*\)-bin.zip.*/\1/p' "$DIRNAME/gradle/wrapper/gradle-wrapper.properties" | head -1)
[ -n "$GRADLE_VERSION" ] || die "ERROR: cannot parse gradle version from gradle/wrapper/gradle-wrapper.properties"

GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
export GRADLE_USER_HOME

DIST_HOME="$GRADLE_USER_HOME/wrapper/dists/gradle-$GRADLE_VERSION-bin"
LOCAL_GRADLE=""
if [ -d "$DIST_HOME" ]; then
  for d in "$DIST_HOME"/*/gradle-$GRADLE_VERSION; do
    [ -x "$d/bin/gradle" ] && LOCAL_GRADLE="$d/bin/gradle" && break
  done
fi

if [ -n "$LOCAL_GRADLE" ]; then
  exec "$LOCAL_GRADLE" "$@"
fi

# Fallback: standard wrapper jar bootstrap
CLASSPATH="$DIRNAME/gradle/wrapper/gradle-wrapper.jar:$DIRNAME/gradle/wrapper/gradle-wrapper-shared.jar"
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
