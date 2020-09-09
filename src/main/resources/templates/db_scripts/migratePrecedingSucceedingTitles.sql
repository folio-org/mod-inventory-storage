INSERT INTO ${myuniversity}_${mymodule}.preceding_succeeding_title (id, jsonb)
  SELECT id, jsonb_build_object(
    'id', jsonb->'id',
    'metadata', jsonb->'metadata',
    'precedingInstanceId', jsonb->'superInstanceId',
    'succeedingInstanceId', jsonb->'subInstanceId')
  FROM ${myuniversity}_${mymodule}.instance_relationship as ir
  WHERE ir.instanceRelationshipTypeId ='cde80cc2-0c8b-4672-82d4-721e51dcb990'
  ON CONFLICT DO NOTHING;
