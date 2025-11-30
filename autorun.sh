#!/bin/bash
echo "Starting changeme..."

# Try JAVA_HOME first
if [[ -x "$JAVA_HOME/bin/java" ]]; then
    JAVA="$JAVA_HOME/bin/java"
elif [[ -x "/usr/bin/java" ]]; then
    JAVA="/usr/bin/java"
else
    echo "Java not found. Please install Java or set JAVA_HOME."
    exit 1
fi

"$JAVA" --enable-native-access=ALL-UNNAMED -jar changeme.jar
