CREATE TABLE IF NOT EXISTS plugins (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    base_url TEXT NOT NULL,
    version TEXT NOT NULL,
    author TEXT,
    channel TEXT NOT NULL,
    likes INTEGER DEFAULT 0,
    dislikes INTEGER DEFAULT 0,
    is_active INTEGER DEFAULT 1,
    code_file_path TEXT,
    updated_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_plugins_channel ON plugins(channel);
CREATE INDEX IF NOT EXISTS idx_plugins_active ON plugins(is_active);
