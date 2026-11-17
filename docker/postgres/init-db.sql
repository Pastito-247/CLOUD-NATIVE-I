DO $$
DECLARE
    db text;
BEGIN
    FOREACH db IN ARRAY ARRAY[
        'tuki_products',
        'tuki_categories',
        'tuki_users',
        'tuki_orders',
        'tuki_payments',
        'tuki_audit',
        'tuki_reporting'
    ]
    LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = db) THEN
            EXECUTE format('CREATE DATABASE %I', db);
        END IF;
    END LOOP;
END
$$;
