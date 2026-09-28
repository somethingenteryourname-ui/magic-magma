#!/usr/bin/env bash
# Boots a real Paper 1.21.11 server with the plugin, fires a few nukes from the
# console and checks they launch, detonate and carve craters without errors.
#
#   tools/smoke-test.sh path/to/MagicNuke.jar
set -euo pipefail

JAR="$(realpath "$1")"
MC_VERSION="${MC_VERSION:-1.21.11}"
WORK="${WORK:-$(pwd)/smoke-server}"
UA="MagicNuke-smoke-test (github actions)"

rm -rf "$WORK"
mkdir -p "$WORK/plugins"
cd "$WORK"

echo "::group::Download Paper $MC_VERSION"
URL=$(curl -fsSL -H "User-Agent: $UA" "https://fill.papermc.io/v3/projects/paper/versions/$MC_VERSION/builds/latest" \
  | jq -r '.downloads."server:default".url')
echo "Paper: $URL"
curl -fsSL -H "User-Agent: $UA" -o paper.jar "$URL"
echo "::endgroup::"

cp "$JAR" plugins/
echo "eula=true" > eula.txt
cat > server.properties <<'EOF'
online-mode=false
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
view-distance=4
simulation-distance=4
server-port=25565
EOF

mkfifo console
# keep the fifo open for writing so the server doesn't see EOF
exec 3<>console
java -Xmx2G -jar paper.jar --nogui < console > server.log 2>&1 &
PID=$!

send() { echo ">>> $*"; echo "$*" >&3; }
wait_for() {
  local pattern="$1" timeout="$2"
  for _ in $(seq "$timeout"); do
    if grep -q "$pattern" server.log; then return 0; fi
    if ! kill -0 "$PID" 2>/dev/null; then echo "Server died"; tail -50 server.log; exit 1; fi
    sleep 1
  done
  echo "Timed out waiting for: $pattern"; tail -80 server.log; kill "$PID" || true; exit 1
}

echo "::group::Start server"
wait_for 'Done (' 600
echo "::endgroup::"

# the built-in pack host serves the same zip that's inside the jar
unzip -p "$JAR" magicnuke-pack.zip > expected.zip
curl -fsS -o served.zip "http://127.0.0.1:8163/magicnuke-test.zip"
cmp expected.zip served.zip
echo "Resource pack host OK ($(stat -c%s served.zip) bytes)"
unzip -tq served.zip

send "forceload add -96 -96 96 96"
sleep 3
send "nuke sizes"
send "nuke launch mini 0 ~ 0 world"
send "nuke strike small 48 ~ 0 world"
send "nuke launch 30 -48 ~ 8 world"
send "nuke list"

wait_for 'Crater complete at world -4' 150
sleep 5
# let the mushroom clouds finish and clean up
sleep 25
send "nuke list"
sleep 2
send "stop"
wait "$PID" || true

echo "::group::Server log"
cat server.log
echo "::endgroup::"

fail=0
detonations=$(grep -c 'detonated at world' server.log || true)
craters=$(grep -c 'Crater complete at world' server.log || true)
echo "Detonations: $detonations, craters: $craters"
[ "$detonations" -ge 3 ] || { echo "Expected 3 detonations"; fail=1; }
[ "$craters" -ge 3 ] || { echo "Expected 3 craters"; fail=1; }
grep -q 'MagicNuke ready' server.log || { echo "Plugin did not enable"; fail=1; }
if grep -nE 'dev\.magicnuke|Could not pass event|Error occurred while enabling MagicNuke|Task #[0-9]+ for MagicNuke' server.log | grep -iE 'exception|error|at dev\.magicnuke|could not pass|task #'; then
  echo "Errors from MagicNuke found in the log"
  fail=1
fi
exit $fail
