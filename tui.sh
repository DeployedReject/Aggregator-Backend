#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

# Ensure classpath cache exists and compile updates if files changed
if [ ! -f "target/tui-cp.txt" ] || [ ! -d "target/classes" ] || \
   [ -n "$(find src/main/java/com/github/deployedreject/Aggregator_backend/tui -newer target/classes/com/github/deployedreject/Aggregator_backend/tui/StandaloneTui.class 2>/dev/null)" ]; then
    echo "Preparing TUI client..."
    ./mvnw compile -DskipTests -q
    ./mvnw dependency:build-classpath -Dmdep.outputFile=target/tui-cp.txt -q
fi

# Launch the standalone Lanterna TUI client (boots in ~80ms)
exec java -cp "target/classes:$(cat target/tui-cp.txt)" \
    com.github.deployedreject.Aggregator_backend.tui.StandaloneTui "$@"
