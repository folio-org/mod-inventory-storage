CREATE OR REPLACE FUNCTION get_updated_instance_ids_view(
	startdate timestamp with time zone,
	enddate timestamp with time zone,
	deletedrecordsupport boolean DEFAULT true,
	skipsuppressedfromdiscoveryrecords boolean DEFAULT true,
	onlyinstanceupdatedate boolean DEFAULT true,
	source character varying DEFAULT NULL::character varying)
    RETURNS TABLE("instanceId" uuid, source character varying, "updatedDate" timestamp with time zone, "suppressFromDiscovery" boolean, deleted boolean) 
    LANGUAGE 'sql'
    COST 100
    VOLATILE PARALLEL UNSAFE
    ROWS 1000

AS $BODY$
WITH instanceIdsInRange AS ( SELECT inst.id AS instanceId,
                                    (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) AS maxDate
                             FROM instance inst
                             WHERE ($6 IS NULL OR inst.jsonb ->> 'source' = $6)
                             AND (strToTimestamp(inst.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)

                             UNION ALL
                             SELECT instanceid, MAX(maxdate) as maxdate
                               FROM (
                                     SELECT instanceid,(strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) as maxdate
                                       FROM holdings_record hr
                                      WHERE ((strToTimestamp(hr.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)
                                        AND NOT EXISTS (SELECT NULL WHERE $5))
                                     UNION
                                     SELECT instanceid, (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) AS maxDate
                                       FROM holdings_record hr
                                              INNER JOIN item item ON item.holdingsrecordid = hr.id
                                      WHERE (strToTimestamp(item.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2)
                                        AND NOT EXISTS (SELECT NULL WHERE $5)
                                    ) AS related_hr_items
                                    GROUP BY instanceid
                             UNION ALL
                             SELECT (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                    greatest((strToTimestamp(audit_item.jsonb ->> 'createdDate')),
                                             (strToTimestamp(audit_holdings_record.jsonb ->> 'createdDate'))) AS maxDate
                             FROM audit_holdings_record audit_holdings_record
                                      JOIN audit_item audit_item
                                           ON (audit_item.jsonb ->> '{record,holdingsRecordId}')::uuid =
                                              (audit_holdings_record.jsonb ->> '{record,id}')::uuid
                             WHERE ((strToTimestamp(audit_holdings_record.jsonb ->> 'createdDate')) BETWEEN dateOrMin($1) AND dateOrMax($2) OR
                                    (strToTimestamp(audit_item.jsonb ->> 'createdDate')) BETWEEN dateOrMin($1) AND dateOrMax($2))
                                    AND NOT EXISTS (SELECT NULL WHERE $5)
                             UNION ALL -- case when only item was deleted
            						     SELECT hold_rec.instanceId,
                                    greatest((strToTimestamp(audit_item.jsonb ->> 'createdDate')),
                                             (strToTimestamp(hold_rec.jsonb -> 'metadata' ->> 'updatedDate'))) AS maxDate
                             FROM holdings_record hold_rec
                                      JOIN audit_item audit_item
                                          ON (audit_item.jsonb -> 'record' ->> 'holdingsRecordId')::uuid = hold_rec.id
                             WHERE ((strToTimestamp(hold_rec.jsonb -> 'metadata' ->> 'updatedDate')) BETWEEN dateOrMin($1) AND dateOrMax($2) OR
                                    (strToTimestamp(audit_item.jsonb ->> 'createdDate')) BETWEEN dateOrMin($1) AND dateOrMax($2))
                                    AND NOT EXISTS (SELECT NULL WHERE $5)
            						     UNION ALL -- case when only holding was deleted
            						     SELECT (audit_holdings_record.jsonb #>> '{record,instanceId}')::uuid,
                                     strToTimestamp(audit_holdings_record.jsonb ->> 'createdDate') AS maxDate
                             FROM audit_holdings_record audit_holdings_record
                             WHERE ((strToTimestamp(audit_holdings_record.jsonb ->> 'createdDate')) BETWEEN dateOrMin($1) AND dateOrMax($2))
                                     AND NOT EXISTS (SELECT NULL WHERE $5) )
SELECT instanceId,
       instance.jsonb ->> 'source' AS source,
       MAX(instanceIdsInRange.maxDate) AS maxDate,
       (instance.jsonb ->> 'discoverySuppress')::bool AS suppressFromDiscovery,
       false AS deleted
FROM instanceIdsInRange,
    instance
WHERE instanceIdsInRange.maxDate BETWEEN dateOrMin($1) AND dateOrMax($2)
      AND instance.id = instanceIdsInRange.instanceId
      AND NOT ($4 AND COALESCE((instance.jsonb ->> 'discoverySuppress')::bool, false))
      AND ($6 IS NULL OR instance.jsonb ->> 'source' = $6)
GROUP BY 1, 2, 4

UNION ALL
SELECT (jsonb #>> '{record,id}')::uuid              AS instanceId,
        jsonb #>> '{record,source}'                 AS source,
        strToTimestamp(jsonb ->> 'createdDate')     AS maxDate,
        false                                       AS suppressFromDiscovery,
        true                                        AS deleted
FROM audit_instance
WHERE $3
      AND strToTimestamp(jsonb ->> 'createdDate') BETWEEN dateOrMin($1) AND dateOrMax($2)
      AND ($6 IS NULL OR jsonb #>> '{record,source}' = $6)

$BODY$;