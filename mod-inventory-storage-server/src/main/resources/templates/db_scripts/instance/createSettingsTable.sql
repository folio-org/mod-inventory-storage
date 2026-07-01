CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.settings (
    id UUID PRIMARY KEY NOT NULL DEFAULT gen_random_uuid(),
    "key" VARCHAR(255) NOT NULL,
    "value" TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    central_managed BOOLEAN NOT NULL,
    description TEXT,
    created_date TIMESTAMP,
    created_by_user_id UUID,
    updated_date TIMESTAMP,
    updated_by_user_id UUID,
    CONSTRAINT unique_settings_key UNIQUE ("key")
);

INSERT INTO ${myuniversity}_${mymodule}.settings (
    "key",
    "value",
    type,
    central_managed,
    description,
    created_date,
    created_by_user_id,
    updated_date,
    updated_by_user_id
)
VALUES (
    'inventory.optimize-updates.enabled',
    'false',
    'BOOLEAN',
    true,
    'Enables optimization to prevent redundant updates in Inventory (Instance, Holding, and Item).',
    NOW(),
    '00000000-0000-0000-0000-000000000000',
    NOW(),
    '00000000-0000-0000-0000-000000000000'
)
ON CONFLICT ("key") DO NOTHING;
