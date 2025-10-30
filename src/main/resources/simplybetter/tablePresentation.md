# Database Schema Overview (Alphabetical)

## sb_accounts
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| player_uuid | NOT NULL | TEXT | PK, FK → sb_players(uuid), ON DELETE CASCADE |
| balance | NOT NULL | INTEGER | DEFAULT 0 |
| updated_at | NOT NULL | TEXT | DEFAULT datetime('now') |

## sb_afks
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| player_uuid | NOT NULL | TEXT | PK, FK → sb_players(uuid), ON DELETE CASCADE |
| since | NOT NULL | TEXT | DEFAULT datetime('now') |
| message | NULL | TEXT |  |

## sb_audit_logs
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| id | NOT NULL | INTEGER | PK AUTOINCREMENT |
| table_name | NOT NULL | TEXT |  |
| initiator | NULL | TEXT | UUID or "system" |
| context_json | NULL | TEXT | JSON |
| at | NOT NULL | TEXT | DEFAULT datetime('now') |

## sb_back_locations
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| player_uuid | NOT NULL | TEXT | PK, FK → sb_players(uuid), ON DELETE CASCADE |
| updated_at | NOT NULL | TEXT | DEFAULT datetime('now') |
| previous_position_id | NOT NULL | INTEGER | FK → sb_positions(id), ON DELETE CASCADE |
| current_position_id | NOT NULL | INTEGER | FK → sb_positions(id), ON DELETE CASCADE |

## sb_bans
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| id | NOT NULL | INTEGER | PK AUTOINCREMENT |
| created_at | NOT NULL | TEXT | DEFAULT datetime('now') |
| expires_at | NULL | TEXT | NULL = permanent |
| reason | NOT NULL | TEXT |  |
| player_uuid | NOT NULL | TEXT | FK → sb_players(uuid), ON DELETE CASCADE |
| banned_by_uuid | NULL | TEXT | FK → sb_players(uuid), ON DELETE SET NULL |

## sb_bans_ip
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| id | NOT NULL | INTEGER | PK AUTOINCREMENT |
| created_at | NOT NULL | TEXT | DEFAULT datetime('now') |
| expires_at | NULL | TEXT |  |
| reason | NOT NULL | TEXT |  |
| ip_address | NOT NULL | TEXT | plain IP string |
| player_uuid | NULL | TEXT | FK → sb_players(uuid), ON DELETE SET NULL |
| banned_by_uuid | NULL | TEXT | FK → sb_players(uuid), ON DELETE SET NULL |

## sb_chat_logs
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| id | NOT NULL | INTEGER | PK AUTOINCREMENT |
| at | NOT NULL | TEXT | DEFAULT datetime('now') |
| content | NOT NULL | TEXT |  |
| sender_player_uuid | NOT NULL | TEXT | FK → sb_players(uuid), ON DELETE CASCADE |

## sb_homes
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| id | NOT NULL | INTEGER | PK AUTOINCREMENT |
| name | NOT NULL | TEXT | UNIQUE (owner_uuid, name) |
| created_at | NOT NULL | TEXT | DEFAULT datetime('now') |
| owner_uuid | NOT NULL | TEXT | FK → sb_players(uuid), ON DELETE CASCADE |
| position_id | NOT NULL | INTEGER | FK → sb_positions(id), ON DELETE CASCADE |

## sb_ignores
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| owner_uuid | NOT NULL | TEXT | PK (composite), FK → sb_players(uuid), ON DELETE CASCADE |
| target_uuid | NOT NULL | TEXT | PK (composite), FK → sb_players(uuid), ON DELETE CASCADE |
| created_at | NOT NULL | TEXT | DEFAULT datetime('now') |

## sb_jails
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| id | NOT NULL | INTEGER | PK AUTOINCREMENT |
| can_be_visited | NOT NULL | INTEGER | DEFAULT 0 |
| center_position_id | NOT NULL | INTEGER | FK → sb_positions(id), ON DELETE CASCADE |
| visit_entry_position_id | NOT NULL | INTEGER | FK → sb_positions(id), ON DELETE CASCADE |

## sb_jails_sanctions
| Column | NULL | Type | Info / Default |
|--------|------|------|----------------|
| id | NOT NULL | INTEGER | PK AUTOINCREMENT |
| created_at | NOT NULL | TEXT | DEFAULT datetime('now') |
| expires_at | NULL | TEXT |  |
| reason | NOT NULL | TEXT |  |
| player_uuid | NOT NULL | TEXT | FK → sb_players(uuid), ON DELETE CASCADE |
| banned_by_uuid | NULL | TEXT | FK → sb_players(uuid), ON DELETE SET NULL |
| jail_id | NOT NULL | INTEGER | FK → sb_jails(id), ON DELETE CA
