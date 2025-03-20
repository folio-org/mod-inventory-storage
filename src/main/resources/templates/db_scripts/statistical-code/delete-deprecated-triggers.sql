DROP TRIGGER IF EXISTS check_statistical_code_references_on_insert ON item CASCADE;
DROP TRIGGER IF EXISTS check_statistical_code_references_on_update ON item CASCADE;
DROP TRIGGER IF EXISTS instance_check_statistical_code_references_on_insert ON instance CASCADE;
DROP TRIGGER IF EXISTS instance_check_statistical_code_references_on_update ON instance CASCADE;

DROP FUNCTION IF EXISTS check_statistical_code_references();

DROP TRIGGER IF EXISTS check_item_statistical_code_reference_on_delete ON statistical_code CASCADE;
DROP FUNCTION IF EXISTS process_statistical_code_delete();