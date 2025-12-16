CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.instance_statistical_code
(
    instance_id         uuid,
    statistical_code_id uuid,
    CONSTRAINT fk_instance_id FOREIGN KEY (instance_id) REFERENCES ${myuniversity}_${mymodule}.instance (id) ON DELETE CASCADE,
    CONSTRAINT fk_statistical_code_id FOREIGN KEY (statistical_code_id) REFERENCES ${myuniversity}_${mymodule}.statistical_code (id),
    CONSTRAINT unq_instance_statistical_code UNIQUE (instance_id, statistical_code_id)
);

CREATE INDEX IF NOT EXISTS idx_instance_statistical_code_instance_id ON ${myuniversity}_${mymodule}.instance_statistical_code (instance_id);
CREATE INDEX IF NOT EXISTS idx_instance_statistical_code_statistical_code_id ON ${myuniversity}_${mymodule}.instance_statistical_code (statistical_code_id);

CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.holdings_record_statistical_code
(
    holdings_record_id  uuid,
    statistical_code_id uuid,
    CONSTRAINT fk_holdings_record_id FOREIGN KEY (holdings_record_id) REFERENCES ${myuniversity}_${mymodule}.holdings_record (id) ON DELETE CASCADE,
    CONSTRAINT fk_statistical_code_id FOREIGN KEY (statistical_code_id) REFERENCES ${myuniversity}_${mymodule}.statistical_code (id),
    CONSTRAINT unq_holdings_record_statistical_code UNIQUE (holdings_record_id, statistical_code_id)
);

CREATE INDEX IF NOT EXISTS idx_holdings_record_statistical_code_holdings_record_id ON ${myuniversity}_${mymodule}.holdings_record_statistical_code (holdings_record_id);
CREATE INDEX IF NOT EXISTS idx_holdings_record_statistical_code_statistical_code_id ON ${myuniversity}_${mymodule}.holdings_record_statistical_code (statistical_code_id);


CREATE TABLE IF NOT EXISTS ${myuniversity}_${mymodule}.item_statistical_code
(
    item_id             uuid,
    statistical_code_id uuid,
    CONSTRAINT fk_holdings_record_id FOREIGN KEY (item_id) REFERENCES ${myuniversity}_${mymodule}.item (id) ON DELETE CASCADE,
    CONSTRAINT fk_statistical_code_id FOREIGN KEY (statistical_code_id) REFERENCES ${myuniversity}_${mymodule}.statistical_code (id),
    CONSTRAINT unq_item_statistical_code UNIQUE (item_id, statistical_code_id)
);

CREATE INDEX IF NOT EXISTS idx_item_statistical_code_item_id ON ${myuniversity}_${mymodule}.item_statistical_code (item_id);
CREATE INDEX IF NOT EXISTS idx_item_statistical_code_statistical_code_id ON ${myuniversity}_${mymodule}.item_statistical_code (statistical_code_id);

