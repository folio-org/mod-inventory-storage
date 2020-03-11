UPDATE ${myuniversity}_${mymodule}.instance
SET	jsonb = JSONB_SET(instance.jsonb, '{staffSuppress}', TO_JSONB(false))
WHERE jsonb->>'staffSuppress' IS NULL;
