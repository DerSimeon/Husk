#!/usr/bin/env bash
# Generates PacketMapping.ofProtocol(...) tables for Husk's registered packets from
# PrismarineJS/minecraft-data. Clones the repo (shallow) if missing, else updates it.
#
# Usage:
#   tools/generate-packet-ids.sh [output-file]     # default: stdout
#   MCDATA_DIR=/some/path tools/generate-packet-ids.sh
#   EXTRA_PROTOCOLS="776=tools/p776.json" tools/generate-packet-ids.sh  # protocols not yet in minecraft-data
#   EXTRA_REPORTS="777=tools/packets-777.json" ...  # vanilla data-generator report (java -cp <client jar +
#     libs> net.minecraft.data.Main --reports) for versions minecraft-data lacks entirely
#
# Output: one block per packet, e.g.
#   // play/toClient boss_bar
#   PacketMapping.ofProtocol(107 to 0x0c, 393 to 0x0c, 764 to 0x0a, ...)
# Paste each into the matching registration in protocol/packet/*.kt (replacing todo()).
set -euo pipefail
OUT="${1:-/dev/stdout}" # captured now: the target loops below reuse the positional params

REPO_DIR="${MCDATA_DIR:-build/minecraft-data}"
REPO_URL="https://github.com/PrismarineJS/minecraft-data"
MIN_PROTOCOL=47 # 1.8

command -v jq >/dev/null || { echo "jq required" >&2; exit 1; }
command -v git >/dev/null || { echo "git required" >&2; exit 1; }

if [ ! -d "$REPO_DIR/.git" ]; then
  echo "Cloning minecraft-data into $REPO_DIR ..." >&2
  git clone --depth 1 "$REPO_URL" "$REPO_DIR" >&2
else
  echo "Updating minecraft-data in $REPO_DIR ..." >&2
  git -C "$REPO_DIR" pull --ff-only >&2 || echo "  (pull failed; using existing checkout)" >&2
fi

DATA="$REPO_DIR/data"
PATHS="$DATA/dataPaths.json"
PVERS="$DATA/pc/common/protocolVersions.json"

# Registered Husk packets: "state direction minecraft-data-name vanilla-name"
# (vanilla-name is the resource name in a vanilla packets.json report, for EXTRA_REPORTS)
TARGETS=(
  "handshaking toServer set_protocol intention"
  "status toServer ping_start status_request"
  "status toServer ping ping_request"
  "status toClient server_info status_response"
  "status toClient ping pong_response"
  "login toServer login_start hello"
  "login toServer login_acknowledged login_acknowledged"
  "login toClient disconnect login_disconnect"
  "login toClient success login_finished"
  "login toClient compress login_compression"
  "configuration toClient finish_configuration finish_configuration"
  "configuration toClient disconnect disconnect"
  "configuration toClient keep_alive keep_alive"
  "configuration toClient custom_payload custom_payload"
  "configuration toClient registry_data registry_data"
  "configuration toClient tags update_tags"
  "configuration toClient select_known_packs select_known_packs"
  "configuration toServer finish_configuration finish_configuration"
  "configuration toServer keep_alive keep_alive"
  "configuration toServer select_known_packs select_known_packs"
  "play toClient login login"
  "play toClient keep_alive keep_alive"
  "play toClient system_chat system_chat"
  "play toClient kick_disconnect disconnect"
  "play toClient set_title_text set_title_text"
  "play toClient set_title_subtitle set_subtitle_text"
  "play toClient set_title_time set_titles_animation"
  "play toClient boss_bar boss_event"
  "play toClient position player_position"
  "play toClient custom_payload custom_payload"
  "play toClient game_state_change game_event"
  "play toClient abilities player_abilities"
  "play toClient player_info player_info_update"
  "play toClient update_view_position set_chunk_cache_center"
  "play toClient map_chunk level_chunk_with_light"
  "play toServer keep_alive keep_alive"
)

# Ascending, protocol-deduped release list: "protocol<TAB>minecraftVersion"
mapfile -t VERSIONS < <(
  jq -r --argjson min "$MIN_PROTOCOL" '
    map(select(
      .usesNetty==true and .version>=$min and .version<1073741824
      and (.minecraftVersion|test("^[0-9.]+$"))
    ))
    | sort_by(.version)[]
    | "\(.version)\t\(.minecraftVersion)"
  ' "$PVERS" | tr -d '\r'
)

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

declare -A PROTO_TSV # protocol -> tsv file "state<TAB>dir<TAB>name<TAB>id"
ORDER=()             # ascending unique protocols

for line in "${VERSIONS[@]}"; do
  proto="${line%%$'\t'*}"
  mcver="${line#*$'\t'}"
  [ -n "${PROTO_TSV[$proto]:-}" ] && continue
  rel="$(jq -r --arg v "$mcver" '.pc[$v].protocol // empty' "$PATHS")"
  # Fallback: some released versions aren't indexed in dataPaths yet but have a data folder.
  [ -z "$rel" ] && [ -f "$DATA/pc/$mcver/protocol.json" ] && rel="pc/$mcver"
  [ -z "$rel" ] && continue
  pj="$DATA/$rel/protocol.json"
  [ -f "$pj" ] || continue
  out="$TMP/$proto.tsv"
  {
    for state in handshaking status login configuration play; do
      for dir in toClient toServer; do
        jq -r --arg s "$state" --arg d "$dir" '
          (.[$s][$d].types.packet[1][0].type[1].mappings // {})
          | to_entries[] | "\($s)\t\($d)\t\(.value)\t\(.key)"
        ' "$pj" 2>/dev/null || true
      done
    done
  } | tr -d '\r' >"$out"
  PROTO_TSV[$proto]="$out"
  ORDER+=("$proto")
done

# Extra "protocol=path/to/protocol.json" entries, appended after the minecraft-data releases.
for extra in ${EXTRA_PROTOCOLS:-}; do
  proto="${extra%%=*}"
  pj="${extra#*=}"
  [ -n "${PROTO_TSV[$proto]:-}" ] && continue
  out="$TMP/$proto.tsv"
  for state in handshaking status login configuration play; do
    for dir in toClient toServer; do
      jq -r --arg s "$state" --arg d "$dir" '
        (.[$s][$d].types.packet[1][0].type[1].mappings // {})
        | to_entries[] | "\($s)\t\($d)\t\(.value)\t\(.key)"
      ' "$pj" 2>/dev/null || true
    done
  done | tr -d '\r' >"$out"
  PROTO_TSV[$proto]="$out"
  ORDER+=("$proto")
done

# Extra "protocol=path/to/packets.json" vanilla reports; ids are keyed by vanilla name, so they are
# translated back to minecraft-data names through the TARGETS table.
for extra in ${EXTRA_REPORTS:-}; do
  proto="${extra%%=*}"
  rj="${extra#*=}"
  [ -n "${PROTO_TSV[$proto]:-}" ] && continue
  out="$TMP/$proto.tsv"
  for t in "${TARGETS[@]}"; do
    # shellcheck disable=SC2086
    set -- $t
    case "$1" in handshaking) vs=handshake ;; *) vs="$1" ;; esac
    case "$2" in toClient) vd=clientbound ;; *) vd=serverbound ;; esac
    id="$(jq -r --arg s "$vs" --arg d "$vd" --arg n "minecraft:$4" '.[$s][$d][$n].protocol_id // empty' "$rj" | tr -d '\r')"
    [ -n "$id" ] && printf '%s\t%s\t%s\t0x%02x\n' "$1" "$2" "$3" "$id"
  done >"$out"
  PROTO_TSV[$proto]="$out"
  ORDER+=("$proto")
done

emit() {
  local state="$1" dir="$2" name="$3"
  local prev="" entries=""
  for proto in "${ORDER[@]}"; do
    local id
    id="$(awk -F'\t' -v s="$state" -v d="$dir" -v n="$name" \
      '$1==s&&$2==d&&$3==n{print $4; exit}' "${PROTO_TSV[$proto]}")"
    if [ -n "$id" ]; then
      if [ "$id" != "$prev" ]; then
        entries="${entries:+$entries, }$proto to $id"
        prev="$id"
      fi
    else
      prev="" # absent here; re-emit if it reappears later
    fi
  done
  echo "// $state/$dir $name"
  if [ -z "$entries" ]; then
    echo "PacketMapping.todo() // not found in range"
  else
    echo "PacketMapping.ofProtocol($entries)"
  fi
  echo
}

{
  echo "// Generated from minecraft-data by tools/generate-packet-ids.sh"
  echo "// Protocol range: $MIN_PROTOCOL (1.8) .. latest release. IDs are hex."
  echo "// Note: assumes each packet keeps a single lineage (no remove-then-readd); true for the Limbo set."
  echo
  for t in "${TARGETS[@]}"; do
    # shellcheck disable=SC2086
    set -- $t
    emit "$1" "$2" "$3"
  done
} >"$OUT"

echo "Done. Covered ${#ORDER[@]} protocol versions." >&2
