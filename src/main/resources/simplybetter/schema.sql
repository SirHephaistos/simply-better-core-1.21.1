-- Positions in worlds (shared struct for any location)
CREATE TABLE IF NOT EXISTS sb_positions (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  dimension_id          TEXT    NOT NULL,                    -- e.g. minecraft:overworld
  x                     REAL    NOT NULL,
  y                     REAL    NOT NULL,
  z                     REAL    NOT NULL,
  orientation_yaw       REAL    NOT NULL,
  orientation_pitch     REAL    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sb_positions_dimension ON sb_positions(dimension_id);

-- Players registry
CREATE TABLE IF NOT EXISTS sb_players (
  uuid                  TEXT    PRIMARY KEY,                 -- player UUID as string
  name                  TEXT    NOT NULL,
  first_seen            TEXT    NOT NULL DEFAULT (datetime('now')),
  last_seen             TEXT    NOT NULL DEFAULT (datetime('now')),
  playtime_seconds      INTEGER NOT NULL DEFAULT 0,
  can_be_ignored        INTEGER NOT NULL DEFAULT 1,          -- boolean: 1=true, 0=false
  nickname              TEXT,
  last_seen_position_id INTEGER,
  FOREIGN KEY(last_seen_position_id) REFERENCES sb_positions(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_sb_players_name ON sb_players(name);

-- Per-player toggles/settings
CREATE TABLE IF NOT EXISTS sb_players_settings (
  player_uuid           TEXT    PRIMARY KEY,
  tpa_blocked           INTEGER NOT NULL DEFAULT 0,
  msg_blocked           INTEGER NOT NULL DEFAULT 0,
  auto_tp_accept        INTEGER NOT NULL DEFAULT 0,
  pay_blocked           INTEGER NOT NULL DEFAULT 0,
  pay_confirm           INTEGER NOT NULL DEFAULT 1,
  clearinv_confirm      INTEGER NOT NULL DEFAULT 1,
  vanished              INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY(player_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE
);

-- Worlds known to the system
CREATE TABLE IF NOT EXISTS sb_worlds (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  dimension_id          TEXT    NOT NULL UNIQUE,
  center_position_id    INTEGER,
  FOREIGN KEY(center_position_id) REFERENCES sb_positions(id) ON DELETE SET NULL
);

-- Random Teleport settings per world
CREATE TABLE IF NOT EXISTS sb_rtp_settings (
  world_id              INTEGER PRIMARY KEY,
  min_range             INTEGER NOT NULL,
  max_range             INTEGER NOT NULL,
  cooldown_seconds      INTEGER NOT NULL,
  FOREIGN KEY(world_id) REFERENCES sb_worlds(id) ON DELETE CASCADE
);

-- Warps
CREATE TABLE IF NOT EXISTS sb_warps (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  name                  TEXT    NOT NULL UNIQUE,
  created_at            TEXT    NOT NULL DEFAULT (datetime('now')),
  created_by_uuid       TEXT,
  position_id           INTEGER NOT NULL,
  FOREIGN KEY(created_by_uuid) REFERENCES sb_players(uuid) ON DELETE SET NULL,
  FOREIGN KEY(position_id)     REFERENCES sb_positions(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sb_warps_creator ON sb_warps(created_by_uuid);

-- Homes
CREATE TABLE IF NOT EXISTS sb_homes (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  name                  TEXT    NOT NULL,
  created_at            TEXT    NOT NULL DEFAULT (datetime('now')),
  owner_uuid            TEXT    NOT NULL,
  position_id           INTEGER NOT NULL,
  FOREIGN KEY(owner_uuid)  REFERENCES sb_players(uuid)   ON DELETE CASCADE,
  FOREIGN KEY(position_id) REFERENCES sb_positions(id)   ON DELETE CASCADE,
  UNIQUE(owner_uuid, name),
  UNIQUE(owner_uuid, position_id)
);
CREATE INDEX IF NOT EXISTS idx_sb_homes_owner ON sb_homes(owner_uuid);

-- /back locations per player
CREATE TABLE IF NOT EXISTS sb_back_locations (
  player_uuid                 TEXT    PRIMARY KEY,
  updated_at                  TEXT    NOT NULL DEFAULT (datetime('now')),
  previous_position_id        INTEGER NOT NULL,
  current_position_id         INTEGER NOT NULL,
  FOREIGN KEY(player_uuid)          REFERENCES sb_players(uuid)   ON DELETE CASCADE,
  FOREIGN KEY(previous_position_id) REFERENCES sb_positions(id)   ON DELETE CASCADE,
  FOREIGN KEY(current_position_id)  REFERENCES sb_positions(id)   ON DELETE CASCADE
);

-- Ignores (player-to-player)
CREATE TABLE IF NOT EXISTS sb_ignores (
  owner_uuid             TEXT NOT NULL,
  target_uuid            TEXT NOT NULL,
  created_at             TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY(owner_uuid, target_uuid),
  FOREIGN KEY(owner_uuid)  REFERENCES sb_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY(target_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sb_ignores_owner ON sb_ignores(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_sb_ignores_target ON sb_ignores(target_uuid);

-- AFK state
CREATE TABLE IF NOT EXISTS sb_afks (
  player_uuid            TEXT    PRIMARY KEY,
  since_seconds          INTEGER NOT NULL DEFAULT 0,
  message                TEXT,
  FOREIGN KEY(player_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE
);

-- Bans (player bans)
CREATE TABLE IF NOT EXISTS sb_bans (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at             TEXT    NOT NULL DEFAULT (datetime('now')),
  expires_at             TEXT,                               -- NULL for permanent
  reason                 TEXT    NOT NULL,
  player_uuid            TEXT    NOT NULL,
  banned_by_uuid         TEXT,
  FOREIGN KEY(player_uuid)    REFERENCES sb_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY(banned_by_uuid) REFERENCES sb_players(uuid) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_sb_bans_player ON sb_bans(player_uuid);

-- IP bans (store the IP string; optionally also the target player)
CREATE TABLE IF NOT EXISTS sb_bans_ip (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at             TEXT    NOT NULL DEFAULT (datetime('now')),
  expires_at             TEXT,
  reason                 TEXT    NOT NULL,
  ip_address             TEXT    NOT NULL,                   -- store plain IP string
  player_uuid            TEXT,
  banned_by_uuid         TEXT,
  FOREIGN KEY(player_uuid)    REFERENCES sb_players(uuid) ON DELETE SET NULL,
  FOREIGN KEY(banned_by_uuid) REFERENCES sb_players(uuid) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_sb_bans_ip_ip ON sb_bans_ip(ip_address);

-- Mutes
CREATE TABLE IF NOT EXISTS sb_mutes (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at             TEXT    NOT NULL DEFAULT (datetime('now')),
  expires_at             TEXT,
  reason                 TEXT    NOT NULL,
  player_uuid            TEXT    NOT NULL,
  muted_by_uuid          TEXT,
  FOREIGN KEY(player_uuid)   REFERENCES sb_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY(muted_by_uuid) REFERENCES sb_players(uuid) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_sb_mutes_player ON sb_mutes(player_uuid);

-- Jails
CREATE TABLE IF NOT EXISTS sb_jails (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  can_be_visited         INTEGER NOT NULL DEFAULT 0,
  center_position_id     INTEGER NOT NULL,
  visit_entry_position_id INTEGER NOT NULL,
  FOREIGN KEY(center_position_id)      REFERENCES sb_positions(id) ON DELETE CASCADE,
  FOREIGN KEY(visit_entry_position_id) REFERENCES sb_positions(id) ON DELETE CASCADE
);

-- Jail sanctions
CREATE TABLE IF NOT EXISTS sb_jails_sanctions (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  created_at             TEXT    NOT NULL DEFAULT (datetime('now')),
  expires_at             TEXT,
  reason                 TEXT    NOT NULL,
  player_uuid            TEXT    NOT NULL,
  banned_by_uuid         TEXT,
  jail_id                INTEGER NOT NULL,
  FOREIGN KEY(player_uuid)    REFERENCES sb_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY(banned_by_uuid) REFERENCES sb_players(uuid) ON DELETE SET NULL,
  FOREIGN KEY(jail_id)        REFERENCES sb_jails(id)     ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sb_jails_sanctions_player ON sb_jails_sanctions(player_uuid);

-- Kits
CREATE TABLE IF NOT EXISTS sb_kits (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  name                   TEXT    NOT NULL UNIQUE,
  description            TEXT,
  updated_at             TEXT    NOT NULL DEFAULT (datetime('now')),
  cooldown_seconds       INTEGER NOT NULL DEFAULT 0
);

-- Items belonging to kits
CREATE TABLE IF NOT EXISTS sb_kits_items (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  namespace              TEXT    NOT NULL,                   -- e.g. minecraft
  item_name              TEXT    NOT NULL,                   -- e.g. diamond_sword
  custom_name            TEXT,
  custom_lore            TEXT,                               -- JSON or plain text
  custom_enchants        TEXT,                               -- JSON string
  quantity               INTEGER NOT NULL DEFAULT 1,
  kit_id                 INTEGER NOT NULL,
  FOREIGN KEY(kit_id) REFERENCES sb_kits(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sb_kits_items_kit ON sb_kits_items(kit_id);

-- Kit cooldowns per player
CREATE TABLE IF NOT EXISTS sb_kits_cooldowns (
  kit_id                 INTEGER NOT NULL,
  player_uuid            TEXT    NOT NULL,
  started_at             TEXT    NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY(kit_id, player_uuid),
  FOREIGN KEY(kit_id)      REFERENCES sb_kits(id)     ON DELETE CASCADE,
  FOREIGN KEY(player_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sb_kits_cooldowns_player ON sb_kits_cooldowns(player_uuid);

-- Accounts (economy): one account per player
CREATE TABLE IF NOT EXISTS sb_accounts (
  player_uuid            TEXT    PRIMARY KEY,
  balance                INTEGER NOT NULL DEFAULT 0,
  updated_at             TEXT    NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY(player_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE
);

-- Transactions (economy)
CREATE TABLE IF NOT EXISTS sb_transactions (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  amount                 INTEGER NOT NULL,                   -- store in smallest unit (e.g. cents)
  date                   TEXT    NOT NULL DEFAULT (datetime('now')),
  account_player_uuid    TEXT    NOT NULL,
  interact_player_uuid   TEXT,
  FOREIGN KEY(account_player_uuid)  REFERENCES sb_accounts(player_uuid) ON DELETE CASCADE,
  FOREIGN KEY(interact_player_uuid) REFERENCES sb_accounts(player_uuid) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_sb_tx_account ON sb_transactions(account_player_uuid);
CREATE INDEX IF NOT EXISTS idx_sb_tx_date ON sb_transactions(date);

-- Audit logs (generic)
CREATE TABLE IF NOT EXISTS sb_audit_logs (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  table_name             TEXT    NOT NULL,
  initiator              TEXT,                               -- player UUID or "system"
  context_json           TEXT,                               -- JSON payload
  at                     TEXT    NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_sb_audit_table ON sb_audit_logs(table_name);

-- User activity logs
CREATE TABLE IF NOT EXISTS sb_user_logs (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  at                     TEXT    NOT NULL DEFAULT (datetime('now')),
  description            TEXT    NOT NULL,
  player_uuid            TEXT    NOT NULL,
  player_position_id     INTEGER,
  interact_position_id   INTEGER,
  FOREIGN KEY(player_uuid)          REFERENCES sb_players(uuid)   ON DELETE CASCADE,
  FOREIGN KEY(player_position_id)   REFERENCES sb_positions(id)   ON DELETE SET NULL,
  FOREIGN KEY(interact_position_id) REFERENCES sb_positions(id)   ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_sb_user_logs_player ON sb_user_logs(player_uuid);
CREATE INDEX IF NOT EXISTS idx_sb_user_logs_at ON sb_user_logs(at);

-- Public chat logs
CREATE TABLE IF NOT EXISTS sb_chat_logs (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  at                     TEXT    NOT NULL DEFAULT (datetime('now')),
  content                TEXT    NOT NULL,
  sender_player_uuid     TEXT    NOT NULL,
  FOREIGN KEY(sender_player_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sb_chat_sender ON sb_chat_logs(sender_player_uuid);
CREATE INDEX IF NOT EXISTS idx_sb_chat_at ON sb_chat_logs(at);

-- SocialSpy (who is spying private messages)
CREATE TABLE IF NOT EXISTS sb_socialspy (
  spy_player_uuid        TEXT PRIMARY KEY,
  created_at             TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY(spy_player_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE
);

-- Private chat logs (direct messages)
CREATE TABLE IF NOT EXISTS sb_private_chat_logs (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  at                     TEXT    NOT NULL DEFAULT (datetime('now')),
  content                TEXT    NOT NULL,
  sender_player_uuid     TEXT    NOT NULL,
  receiver_player_uuid   TEXT    NOT NULL,
  FOREIGN KEY(sender_player_uuid)   REFERENCES sb_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY(receiver_player_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sb_privates_sender ON sb_private_chat_logs(sender_player_uuid);
CREATE INDEX IF NOT EXISTS idx_sb_privates_receiver ON sb_private_chat_logs(receiver_player_uuid);
CREATE INDEX IF NOT EXISTS idx_sb_privates_at ON sb_private_chat_logs(at);

-- Mail system (single consolidated table)
CREATE TABLE IF NOT EXISTS sb_mails (
  id                     INTEGER PRIMARY KEY AUTOINCREMENT,
  is_read                INTEGER NOT NULL DEFAULT 0,         -- boolean
  subject                TEXT    NOT NULL,                   -- previously 128 chars
  content                TEXT    NOT NULL,                   -- previously 2048 chars
  read_at                TEXT,
  sent_at                TEXT    NOT NULL DEFAULT (datetime('now')),
  expires_at             TEXT,                               -- NULL = no expiry
  sender_player_uuid     TEXT    NOT NULL,
  target_player_uuid     TEXT    NOT NULL,
  FOREIGN KEY(sender_player_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE,
  FOREIGN KEY(target_player_uuid) REFERENCES sb_players(uuid) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_sb_mails_target ON sb_mails(target_player_uuid, is_read);
CREATE INDEX IF NOT EXISTS idx_sb_mails_sender ON sb_mails(sender_player_uuid);