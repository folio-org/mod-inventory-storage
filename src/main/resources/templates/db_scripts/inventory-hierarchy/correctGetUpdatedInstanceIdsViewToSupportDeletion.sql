drop function if exists ${myuniversity}_${mymodule}.get_updated_instance_ids_view;
-- Correct joining(JOIN -> LEFT JOIN line 28) operation for holdings and items tables to get instances ids also when they have holdings being updated only
CREATE OR REPLACE FUNCTION ${myuniversity}_${mymodule}.get_updated_instance_ids_view(startDate                          timestamptz,
                                                                                     endDate                            timestamptz,
                                                                                     deletedRecordSupport               bool DEFAULT TRUE,
                                                                                     skipSuppressedFromDiscoveryRecords bool DEFAULT TRUE,
                                                                                     onlyInstanceUpdateDate             bool DEFAULT TRUE)
    RETURNS TABLE
            (
                "instanceId"            uuid,
                "source"                varchar,
                "updatedDate"           timestamptz,
                "suppressFromDiscovery" boolean,
                "deleted"               boolean
            )
AS
$BODY$
WITH instanceIdsInRange AS ( SELECT inst.id AS instanceId,
                                    (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) AS maxDate
                             FROM ${myuniversity}_${mymodule}.instance inst
                             WHERE (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)

                             UNION ALL
                             SELECT instanceid, MAX(maxdate) as maxdate
                               FROM (
                                     SELECT instanceid,(strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) as maxdate
                                       FROM ${myuniversity}_${mymodule}.holdings_record hr
                                      WHERE ((strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)
                                        AND NOT EXISTS (SELECT NULL WHERE $5))
                                     UNION
                                     SELECT instanceid, (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) AS maxDate
                                       FROM ${myuniversity}_${mymodule}.holdings_record hr
                                              INNER JOIN ${myuniversity}_${mymodule}.item item ON item.holdingsrecordid = hr.id
                                      WHERE (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)
                                        AND NOT EXISTS (SELECT NULL WHERE $5)
                                    ) AS related_hr_items
                                    GROUP BY instanceid
                             UNION ALL
                             SELECT (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                    greatest((strToTimestamp(audit_item.jsonb ->> 'createdDate')),
                                             (strToTimestamp(audit_holdings_record.jsonb ->> 'createdDate'))) AS maxDate
                             FROM ${myuniversity}_${mymodule}.audit_holdings_record audit_holdings_record
                                      JOIN ${myuniversity}_${mymodule}.audit_item audit_item
                                           ON (audit_item.jsonb ->> '{record,holdingsRecordId}')::uuid =
                                              (audit_holdings_record.jsonb ->> '{record,id}')::uuid
                             WHERE ((strToTimestamp(audit_holdings_record.jsonb ->> 'createdDate')) BETWEEN dateOrMin($1) AND dateOrMax($2) OR
                                    (strToTimestamp(audit_item.jsonb ->> 'createdDate')) BETWEEN dateOrMin($1) AND dateOrMax($2))
                                    AND NOT EXISTS (SELECT NULL WHERE $5)
                             UNION ALL -- case when only item was deleted
            						     SELECT hold_rec.instanceId,
                                    greatest((strToTimestamp(audit_item.jsonb ->> 'createdDate')),
                                             (strToTimestamp(hold_rec.jsonb -> 'metadata' ->> 'updatedDate'))) AS maxDate
                             FROM ${myuniversity}_${mymodule}.holdings_record hold_rec
                                      JOIN ${myuniversity}_${mymodule}.audit_item audit_item
                                          ON (audit_item.jsonb -> 'record' ->> 'holdingsRecordId')::uuid = hold_rec.id
                             WHERE ((strToTimestamp(hold_rec.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2) OR
                                    (strToTimestamp(audit_item.jsonb ->> 'createdDate')) BETWEEN dateOrMin($1) AND dateOrMax($2))
                                    AND NOT EXISTS (SELECT NULL WHERE $5)
            						     UNION ALL -- case when only holding was deleted
            						     SELECT (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                     strToTimestamp(audit_holdings_record.jsonb ->> 'createdDate') AS maxDate
                             FROM ${myuniversity}_${mymodule}.audit_holdings_record audit_holdings_record
                             WHERE ((strToTimestamp(audit_holdings_record.jsonb ->> 'createdDate')) BETWEEN dateOrMin($1) AND dateOrMax($2))
                                     AND NOT EXISTS (SELECT NULL WHERE $5) )
SELECT instanceId,
       instance.jsonb ->> 'source' AS source,
       MAX(instanceIdsInRange.maxDate) AS maxDate,
       (instance.jsonb ->> 'discoverySuppress')::bool AS suppressFromDiscovery,
       false AS deleted
FROM instanceIdsInRange,
    ${myuniversity}_${mymodule}.instance
WHERE instanceIdsInRange.maxDate BETWEEN dateOrMin($1) AND dateOrMax($2)
      AND instance.id = instanceIdsInRange.instanceId
      AND NOT ($4 AND COALESCE((instance.jsonb ->> 'discoverySuppress')::bool, false))
GROUP BY 1, 2, 4

UNION ALL
SELECT (jsonb #>> '{record,id}')::uuid              AS instanceId,
        jsonb #>> '{record,source}'                 AS source,
        strToTimestamp(jsonb ->> 'createdDate')     AS maxDate,
        false                                       AS suppressFromDiscovery,
        true                                        AS deleted
FROM ${myuniversity}_${mymodule}.audit_instance
WHERE $3
      AND strToTimestamp(jsonb ->> 'createdDate') BETWEEN dateOrMin($1) AND dateOrMax($2)

$BODY$ LANGUAGE sql;
