START TRANSACTION;

DELETE FROM ${myuniversity}_${mymodule}.instance_relationship
WHERE instanceRelationshipTypeId ='cde80cc2-0c8b-4672-82d4-721e51dcb990';

DELETE FROM ${myuniversity}_${mymodule}.instance_relationship_type
WHERE id ='cde80cc2-0c8b-4672-82d4-721e51dcb990';

END TRANSACTION;
