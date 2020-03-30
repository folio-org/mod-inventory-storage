UPDATE ${myuniversity}_${mymodule}.instance_log
SET	jsonb = JSONB_SET(instance_log.jsonb, '{discoverySuppress}', TO_JSONB(false))
WHERE jsonb->>'discoverySuppress' IS NULL;
