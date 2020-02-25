UPDATE ${myuniversity}_${mymodule}.instance
SET	jsonb = JSONB_SET(instance.jsonb, '{discoverySuppress}', TO_JSONB(false))
WHERE jsonb->>'discoverySuppress' IS NULL;
