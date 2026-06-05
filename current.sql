create sequence hrid_instances_seq
    maxvalue 99999999999;

alter sequence hrid_instances_seq owner to test_tenant_mod_inventory_storage;

create sequence hrid_holdings_seq
    maxvalue 99999999999;

alter sequence hrid_holdings_seq owner to test_tenant_mod_inventory_storage;

create sequence hrid_items_seq
    maxvalue 99999999999;

alter sequence hrid_items_seq owner to test_tenant_mod_inventory_storage;

create table rmb_internal
(
    id    serial
        primary key,
    jsonb jsonb not null
);

alter table rmb_internal
    owner to folio_admin;

grant select, update, usage on sequence rmb_internal_id_seq to test_tenant_mod_inventory_storage;

create table rmb_job
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table rmb_job
    owner to folio_admin;

create table rmb_internal_index
(
    name   text    not null
        primary key,
    def    text    not null,
    remove boolean not null
);

alter table rmb_internal_index
    owner to folio_admin;

create table rmb_internal_analyze
(
    tablename text
);

alter table rmb_internal_analyze
    owner to folio_admin;

create table loan_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table loan_type
    owner to folio_admin;

create unique index loan_type_name_idx_unique
    on loan_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table material_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table material_type
    owner to folio_admin;

create unique index material_type_name_idx_unique
    on material_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table locinstitution
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table locinstitution
    owner to folio_admin;

create unique index locinstitution_name_idx_unique
    on locinstitution (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table loccampus
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text,
    institutionid uuid
        constraint institutionid_locinstitution_fkey
            references locinstitution
);

alter table loccampus
    owner to folio_admin;

create unique index loccampus_name_idx_unique
    on loccampus (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create index loccampus_institutionid_idx
    on loccampus (institutionid);

create table loclibrary
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text,
    campusid      uuid
        constraint campusid_loccampus_fkey
            references loccampus
);

alter table loclibrary
    owner to folio_admin;

create unique index loclibrary_name_idx_unique
    on loclibrary (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create index loclibrary_campusid_idx
    on loclibrary (campusid);

create table location
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text,
    institutionid uuid
        constraint institutionid_locinstitution_fkey
            references locinstitution,
    campusid      uuid
        constraint campusid_loccampus_fkey
            references loccampus,
    libraryid     uuid
        constraint libraryid_loclibrary_fkey
            references loclibrary
);

alter table location
    owner to folio_admin;

create index location_primaryservicepoint_idx
    on location ("left"(lower(jsonb ->> 'primaryServicePoint'::text), 600));

create unique index location_name_idx_unique
    on location (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create unique index location_code_idx_unique
    on location (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'code'::text)));

create index location_institutionid_idx
    on location (institutionid);

create index location_campusid_idx
    on location (campusid);

create index location_libraryid_idx
    on location (libraryid);

create table service_point
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table service_point
    owner to folio_admin;

create index service_point_pickuplocation_idx
    on service_point ("left"(lower(jsonb ->> 'pickupLocation'::text), 600));

create unique index service_point_name_idx_unique
    on service_point (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create unique index service_point_code_idx_unique
    on service_point (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'code'::text)));

create table service_point_user
(
    id                    uuid  not null
        primary key,
    jsonb                 jsonb not null,
    creation_date         timestamp,
    created_by            text,
    defaultservicepointid uuid
        constraint defaultservicepointid_service_point_fkey
            references service_point
);

alter table service_point_user
    owner to folio_admin;

create unique index service_point_user_userid_idx_unique
    on service_point_user (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'userId'::text)));

create index service_point_user_defaultservicepointid_idx
    on service_point_user (defaultservicepointid);

create table identifier_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table identifier_type
    owner to folio_admin;

create unique index identifier_type_name_idx_unique
    on identifier_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table instance_relationship_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table instance_relationship_type
    owner to folio_admin;

create unique index instance_relationship_type_name_idx_unique
    on instance_relationship_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table contributor_type
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table contributor_type
    owner to folio_admin;

create unique index contributor_type_name_idx_unique
    on contributor_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create unique index contributor_type_code_idx_unique
    on contributor_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'code'::text)));

create table contributor_name_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table contributor_name_type
    owner to folio_admin;

create unique index contributor_name_type_name_idx_unique
    on contributor_name_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table instance_type
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table instance_type
    owner to folio_admin;

create unique index instance_type_name_idx_unique
    on instance_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create unique index instance_type_code_idx_unique
    on instance_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'code'::text)));

create table instance_format
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table instance_format
    owner to folio_admin;

create unique index instance_format_name_idx_unique
    on instance_format (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create unique index instance_format_code_idx_unique
    on instance_format (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'code'::text)));

create table nature_of_content_term
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table nature_of_content_term
    owner to folio_admin;

create unique index nature_of_content_term_name_idx_unique
    on nature_of_content_term (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table classification_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table classification_type
    owner to folio_admin;

create unique index classification_type_name_idx_unique
    on classification_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table electronic_access_relationship
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table electronic_access_relationship
    owner to folio_admin;

create unique index electronic_access_relationship_name_idx_unique
    on electronic_access_relationship (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table statistical_code_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table statistical_code_type
    owner to folio_admin;

create unique index statistical_code_type_code_idx_unique
    on statistical_code_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'code'::text)));

create table statistical_code
(
    id                    uuid  not null
        primary key,
    jsonb                 jsonb not null,
    creation_date         timestamp,
    created_by            text,
    statisticalcodetypeid uuid
        constraint statisticalcodetypeid_statistical_code_type_fkey
            references statistical_code_type
);

alter table statistical_code
    owner to folio_admin;

create unique index statistical_code_code_statisticalcodetypeid_idx_unique
    on statistical_code (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'code'::text)),
                         lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'statisticalCodeTypeId'::text)));

create unique index statistical_code_name_idx_unique
    on statistical_code (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create index statistical_code_statisticalcodetypeid_idx
    on statistical_code (statisticalcodetypeid);

create table instance_status
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table instance_status
    owner to folio_admin;

create unique index instance_status_name_idx_unique
    on instance_status (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create unique index instance_status_code_idx_unique
    on instance_status (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'code'::text)));

create table mode_of_issuance
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table mode_of_issuance
    owner to folio_admin;

create unique index mode_of_issuance_name_idx_unique
    on mode_of_issuance (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table alternative_title_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table alternative_title_type
    owner to folio_admin;

create unique index alternative_title_type_name_idx_unique
    on alternative_title_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table instance_date_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table instance_date_type
    owner to folio_admin;

create table instance
(
    id                    uuid  not null
        primary key,
    jsonb                 jsonb not null,
    creation_date         timestamp,
    created_by            text,
    instancestatusid      uuid
        constraint instancestatusid_instance_status_fkey
            references instance_status,
    modeofissuanceid      uuid
        constraint modeofissuanceid_mode_of_issuance_fkey
            references mode_of_issuance,
    dates_datetypeid      uuid
        constraint dates_datetypeid_instance_date_type_fkey
            references instance_date_type,
    instancetypeid        uuid
        constraint instancetypeid_instance_type_fkey
            references instance_type,
    complete_updated_date timestamp with time zone
);

alter table instance
    owner to folio_admin;

create index instance_source_idx
    on instance ("left"(lower(jsonb ->> 'source'::text), 600));

create index instance_indextitle_idx
    on instance ("left"(lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'indexTitle'::text)), 600));

create index instance_title_idx
    on instance ("left"(lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'title'::text)), 600));

create index instance_sourceuri_idx
    on instance ("left"(lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'sourceUri'::text)), 600));

create index instance_statisticalcodeids_idx
    on instance ("left"(lower(jsonb ->> 'statisticalCodeIds'::text), 600));

create index instance_staffsuppress_idx
    on instance ("left"(lower(jsonb ->> 'staffSuppress'::text), 600));

create index instance_discoverysuppress_idx
    on instance ("left"(lower(jsonb ->> 'discoverySuppress'::text), 600));

create index instance_metadata_updateddate_idx
    on instance ("left"(lower((jsonb -> 'metadata'::text) ->> 'updatedDate'::text), 600));

create unique index instance_hrid_idx_unique
    on instance (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'hrid'::text)));

create unique index instance_matchkey_idx_unique
    on instance (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'matchKey'::text)));

create index instance_identifiers_idx_ft
    on instance using gin (test_tenant_mod_inventory_storage.get_tsvector(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'identifiers'::text)));

create index instance_invalidisbn_idx_ft
    on instance using gin (test_tenant_mod_inventory_storage.get_tsvector(test_tenant_mod_inventory_storage.normalize_invalid_isbns(jsonb -> 'identifiers'::text)));

create index instance_isbn_idx_ft
    on instance using gin (test_tenant_mod_inventory_storage.get_tsvector(test_tenant_mod_inventory_storage.normalize_isbns(jsonb -> 'identifiers'::text)));

create index instance_instancestatusid_idx
    on instance (instancestatusid);

create index instance_modeofissuanceid_idx
    on instance (modeofissuanceid);

create index instance_dates_datetypeid_idx
    on instance (dates_datetypeid);

create index instance_instancetypeid_idx
    on instance (instancetypeid);

create index instance_pmh_metadata_updateddate_idx
    on instance (test_tenant_mod_inventory_storage.strtotimestamp((jsonb -> 'metadata'::text) ->> 'updatedDate'::text));

create index idx_instance_complete_updated_date
    on instance (complete_updated_date);

create index instance_deleted_idx
    on instance ((jsonb ->> 'deleted'::text));

create index instance_discoverysuppress_source_idx
    on instance ((jsonb ->> 'discoverySuppress'::text), (jsonb ->> 'source'::text));

create table audit_instance
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table audit_instance
    owner to folio_admin;

create index audit_instance_pmh_createddate_idx
    on audit_instance (test_tenant_mod_inventory_storage.strtotimestamp(jsonb ->> 'createdDate'::text));

create table instance_relationship
(
    id                         uuid  not null
        primary key,
    jsonb                      jsonb not null,
    creation_date              timestamp,
    created_by                 text,
    superinstanceid            uuid
        constraint superinstanceid_instance_fkey
            references instance,
    subinstanceid              uuid
        constraint subinstanceid_instance_fkey
            references instance,
    instancerelationshiptypeid uuid
        constraint instancerelationshiptypeid_instance_relationship_type_fkey
            references instance_relationship_type
);

alter table instance_relationship
    owner to folio_admin;

create index instance_relationship_superinstanceid_idx
    on instance_relationship (superinstanceid);

create index instance_relationship_subinstanceid_idx
    on instance_relationship (subinstanceid);

create index instance_relationship_instancerelationshiptypeid_idx
    on instance_relationship (instancerelationshiptypeid);

create table instance_source_marc
(
    id            uuid  not null
        primary key
        references instance
            on delete cascade,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table instance_source_marc
    owner to folio_admin;

create table ill_policy
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table ill_policy
    owner to folio_admin;

create unique index ill_policy_name_idx_unique
    on ill_policy (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table call_number_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table call_number_type
    owner to folio_admin;

create unique index call_number_type_name_idx_unique
    on call_number_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table holdings_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table holdings_type
    owner to folio_admin;

create unique index holdings_type_name_idx_unique
    on holdings_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table instance_note_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table instance_note_type
    owner to folio_admin;

create unique index instance_note_type_name_idx_unique
    on instance_note_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table holdings_note_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table holdings_note_type
    owner to folio_admin;

create unique index holdings_note_type_name_idx_unique
    on holdings_note_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table item_note_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table item_note_type
    owner to folio_admin;

create unique index item_note_type_name_idx_unique
    on item_note_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table item_damaged_status
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table item_damaged_status
    owner to folio_admin;

create unique index item_damaged_status_name_idx_unique
    on item_damaged_status (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table holdings_records_source
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table holdings_records_source
    owner to folio_admin;

create unique index holdings_records_source_name_idx_unique
    on holdings_records_source (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table holdings_record
(
    id                  uuid  not null
        primary key,
    jsonb               jsonb not null,
    creation_date       timestamp,
    created_by          text,
    instanceid          uuid
        constraint instanceid_instance_fkey
            references instance,
    permanentlocationid uuid
        constraint permanentlocationid_location_fkey
            references location,
    temporarylocationid uuid
        constraint temporarylocationid_location_fkey
            references location,
    effectivelocationid uuid
        constraint effectivelocationid_location_fkey
            references location,
    holdingstypeid      uuid
        constraint holdingstypeid_holdings_type_fkey
            references holdings_type,
    callnumbertypeid    uuid
        constraint callnumbertypeid_call_number_type_fkey
            references call_number_type,
    illpolicyid         uuid
        constraint illpolicyid_ill_policy_fkey
            references ill_policy,
    sourceid            uuid
        constraint sourceid_holdings_records_source_fkey
            references holdings_records_source
);

alter table holdings_record
    owner to folio_admin;

create index holdings_record_callnumber_idx
    on holdings_record ("left"(lower(jsonb ->> 'callNumber'::text), 600));

create index holdings_record_callnumberandsuffix_idx
    on holdings_record ("left"(lower(test_tenant_mod_inventory_storage.concat_space_sql(VARIADIC
                                                                                        ARRAY [jsonb ->> 'callNumber'::text, jsonb ->> 'callNumberSuffix'::text])),
                               600));

create index holdings_record_fullcallnumber_idx
    on holdings_record ("left"(lower(test_tenant_mod_inventory_storage.concat_space_sql(VARIADIC
                                                                                        ARRAY [jsonb ->> 'callNumberPrefix'::text, jsonb ->> 'callNumber'::text, jsonb ->> 'callNumberSuffix'::text])),
                               600));

create index holdings_record_discoverysuppress_idx
    on holdings_record ("left"(lower(jsonb ->> 'discoverySuppress'::text), 600));

create unique index holdings_record_hrid_idx_unique
    on holdings_record (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'hrid'::text)));

create index holdings_record_instanceid_idx
    on holdings_record (instanceid);

create index holdings_record_permanentlocationid_idx
    on holdings_record (permanentlocationid);

create index holdings_record_temporarylocationid_idx
    on holdings_record (temporarylocationid);

create index holdings_record_effectivelocationid_idx
    on holdings_record (effectivelocationid);

create index holdings_record_holdingstypeid_idx
    on holdings_record (holdingstypeid);

create index holdings_record_callnumbertypeid_idx
    on holdings_record (callnumbertypeid);

create index holdings_record_illpolicyid_idx
    on holdings_record (illpolicyid);

create index holdings_record_sourceid_idx
    on holdings_record (sourceid);

create index holdings_record_pmh_metadata_updateddate_idx
    on holdings_record (test_tenant_mod_inventory_storage.strtotimestamp((jsonb -> 'metadata'::text) ->> 'updatedDate'::text));

create table audit_holdings_record
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table audit_holdings_record
    owner to folio_admin;

create index audit_holdings_record_pmh_createddate_idx
    on audit_holdings_record (test_tenant_mod_inventory_storage.strtotimestamp((jsonb -> 'record'::text) ->> 'updatedDate'::text));

create table item
(
    id                                   uuid  not null
        primary key,
    jsonb                                jsonb not null,
    creation_date                        timestamp,
    created_by                           text,
    holdingsrecordid                     uuid
        constraint holdingsrecordid_holdings_record_fkey
            references holdings_record,
    permanentloantypeid                  uuid
        constraint permanentloantypeid_loan_type_fkey
            references loan_type,
    temporaryloantypeid                  uuid
        constraint temporaryloantypeid_loan_type_fkey
            references loan_type,
    materialtypeid                       uuid
        constraint materialtypeid_material_type_fkey
            references material_type,
    permanentlocationid                  uuid
        constraint permanentlocationid_location_fkey
            references location,
    temporarylocationid                  uuid
        constraint temporarylocationid_location_fkey
            references location,
    effectivelocationid                  uuid
        constraint effectivelocationid_location_fkey
            references location,
    itemlevelcallnumbertypeid            uuid
        constraint itemlevelcallnumbertypeid_call_number_type_fkey
            references call_number_type,
    effectivecallnumbercomponents_typeid uuid
        constraint effectivecallnumbercomponents_typeid_call_number_type_fkey
            references call_number_type
);

alter table item
    owner to folio_admin;

create index item_accessionnumber_idx
    on item ("left"(lower(jsonb ->> 'accessionNumber'::text), 600));

create index item_status_name_idx
    on item ("left"(lower(test_tenant_mod_inventory_storage.f_unaccent((jsonb -> 'status'::text) ->> 'name'::text)),
                    600));

create index item_callnumberandsuffix_idx
    on item ("left"(lower(test_tenant_mod_inventory_storage.concat_space_sql(VARIADIC
                                                                             ARRAY [(jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text, (jsonb -> 'effectiveCallNumberComponents'::text) ->> 'suffix'::text])),
                    600));

create index item_fullcallnumber_idx
    on item ("left"(lower(test_tenant_mod_inventory_storage.concat_space_sql(VARIADIC
                                                                             ARRAY [(jsonb -> 'effectiveCallNumberComponents'::text) ->> 'prefix'::text, (jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text, (jsonb -> 'effectiveCallNumberComponents'::text) ->> 'suffix'::text])),
                    600));

create index item_discoverysuppress_idx
    on item ("left"(lower(jsonb ->> 'discoverySuppress'::text), 600));

create index item_purchaseorderlineidentifier_idx
    on item ("left"(lower(jsonb ->> 'purchaseOrderLineIdentifier'::text), 600));

create index item_effectivecallnumbercomponents_callnumber_idx
    on item ("left"(lower((jsonb -> 'effectiveCallNumberComponents'::text) ->> 'callNumber'::text), 600));

create index item_order_idx
    on item ((jsonb -> 'order'::text));

create unique index item_barcode_idx_unique
    on item (lower(jsonb ->> 'barcode'::text));

create unique index item_hrid_idx_unique
    on item (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'hrid'::text)));

create index item_holdingsrecordid_idx
    on item (holdingsrecordid);

create index item_permanentloantypeid_idx
    on item (permanentloantypeid);

create index item_temporaryloantypeid_idx
    on item (temporaryloantypeid);

create index item_materialtypeid_idx
    on item (materialtypeid);

create index item_permanentlocationid_idx
    on item (permanentlocationid);

create index item_temporarylocationid_idx
    on item (temporarylocationid);

create index item_effectivelocationid_idx
    on item (effectivelocationid);

create index item_itemlevelcallnumbertypeid_idx
    on item (itemlevelcallnumbertypeid);

create index item_effectivecallnumbercomponents_typeid_idx
    on item (effectivecallnumbercomponents_typeid);

create index item_pmh_metadata_updateddate_idx
    on item (test_tenant_mod_inventory_storage.strtotimestamp((jsonb -> 'metadata'::text) ->> 'updatedDate'::text));

create index item_barcode_idx_pattern
    on item (lower(jsonb ->> 'barcode'::text) text_pattern_ops);

create table audit_item
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table audit_item
    owner to folio_admin;

create index audit_item_pmh_createddate_idx
    on audit_item (test_tenant_mod_inventory_storage.strtotimestamp((jsonb -> 'record'::text) ->> 'updatedDate'::text));

create table hrid_settings
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text,
    lock          boolean default true
        unique
        constraint hrid_settings_lock_check
            check (lock = true)
);

alter table hrid_settings
    owner to test_tenant_mod_inventory_storage;

alter sequence hrid_items_seq owned by hrid_settings.jsonb;

create table preceding_succeeding_title
(
    id                   uuid  not null
        primary key,
    jsonb                jsonb not null
        constraint preceding_or_succeeding_id_is_set
            check (((jsonb -> 'precedingInstanceId'::text) IS NOT NULL) OR
                   ((jsonb -> 'succeedingInstanceId'::text) IS NOT NULL)),
    creation_date        timestamp,
    created_by           text,
    precedinginstanceid  uuid
        constraint precedinginstanceid_instance_fkey
            references instance,
    succeedinginstanceid uuid
        constraint succeedinginstanceid_instance_fkey
            references instance
);

alter table preceding_succeeding_title
    owner to folio_admin;

create index preceding_succeeding_title_precedinginstanceid_idx
    on preceding_succeeding_title (precedinginstanceid);

create index preceding_succeeding_title_succeedinginstanceid_idx
    on preceding_succeeding_title (succeedinginstanceid);

create table reindex_job
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table reindex_job
    owner to folio_admin;

create table bound_with_part
(
    id               uuid  not null
        primary key,
    jsonb            jsonb not null,
    creation_date    timestamp,
    created_by       text,
    itemid           uuid
        constraint itemid_item_fkey
            references item,
    holdingsrecordid uuid
        constraint holdingsrecordid_holdings_record_fkey
            references holdings_record
);

alter table bound_with_part
    owner to folio_admin;

create unique index bound_with_part_itemid_holdingsrecordid_idx_unique
    on bound_with_part (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'itemId'::text)),
                        lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'holdingsRecordId'::text)));

create index bound_with_part_itemid_idx
    on bound_with_part (itemid);

create index bound_with_part_holdingsrecordid_idx
    on bound_with_part (holdingsrecordid);

create table notification_sending_error
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table notification_sending_error
    owner to folio_admin;

create table iteration_job
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table iteration_job
    owner to folio_admin;

create table related_instance_type
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table related_instance_type
    owner to folio_admin;

create table async_migration_job
(
    id    uuid  not null
        primary key,
    jsonb jsonb not null
);

alter table async_migration_job
    owner to folio_admin;

create table subject_type
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table subject_type
    owner to folio_admin;

create unique index subject_type_name_idx_unique
    on subject_type (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create table subject_source
(
    id            uuid  not null
        primary key,
    jsonb         jsonb not null,
    creation_date timestamp,
    created_by    text
);

alter table subject_source
    owner to folio_admin;

create unique index subject_source_name_idx_unique
    on subject_source (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'name'::text)));

create unique index subject_source_code_idx_unique
    on subject_source (lower(test_tenant_mod_inventory_storage.f_unaccent(jsonb ->> 'code'::text)));

create table instance_subject_source
(
    instance_id uuid
        constraint fk_instance_id
            references instance
            on delete cascade,
    source_id   uuid
        constraint fk_source_id
            references subject_source,
    constraint unique_instance_source
        unique (instance_id, source_id)
);

alter table instance_subject_source
    owner to folio_admin;

create table instance_subject_type
(
    instance_id uuid
        constraint fk_instance_id
            references instance
            on delete cascade,
    type_id     uuid
        constraint fk_type_id
            references subject_type,
    constraint unique_instance_type
        unique (instance_id, type_id)
);

alter table instance_subject_type
    owner to folio_admin;

create table instance_statistical_code
(
    instance_id         uuid
        constraint fk_instance_id
            references instance
            on delete cascade,
    statistical_code_id uuid
        constraint fk_statistical_code_id
            references statistical_code,
    constraint unq_instance_statistical_code
        unique (instance_id, statistical_code_id)
);

alter table instance_statistical_code
    owner to folio_admin;

create index idx_instance_statistical_code_instance_id
    on instance_statistical_code (instance_id);

create index idx_instance_statistical_code_statistical_code_id
    on instance_statistical_code (statistical_code_id);

create table holdings_record_statistical_code
(
    holdings_record_id  uuid
        constraint fk_holdings_record_id
            references holdings_record
            on delete cascade,
    statistical_code_id uuid
        constraint fk_statistical_code_id
            references statistical_code,
    constraint unq_holdings_record_statistical_code
        unique (holdings_record_id, statistical_code_id)
);

alter table holdings_record_statistical_code
    owner to folio_admin;

create index idx_holdings_record_statistical_code_holdings_record_id
    on holdings_record_statistical_code (holdings_record_id);

create index idx_holdings_record_statistical_code_statistical_code_id
    on holdings_record_statistical_code (statistical_code_id);

create table item_statistical_code
(
    item_id             uuid
        constraint fk_holdings_record_id
            references item
            on delete cascade,
    statistical_code_id uuid
        constraint fk_statistical_code_id
            references statistical_code,
    constraint unq_item_statistical_code
        unique (item_id, statistical_code_id)
);

alter table item_statistical_code
    owner to folio_admin;

create index idx_item_statistical_code_item_id
    on item_statistical_code (item_id);

create index idx_item_statistical_code_statistical_code_id
    on item_statistical_code (statistical_code_id);

create table item_order_tracker
(
    holdings_id uuid              not null
        primary key,
    max_order   integer default 1 not null
);

alter table item_order_tracker
    owner to folio_admin;

create view instance_holdings_item_view(id, jsonb) as
-- missing source code
;

alter table instance_holdings_item_view
    owner to folio_admin;

create view instance_set
            (id, holdings_records, items, preceding_titles, succeeding_titles, super_instance_relationships,
             sub_instance_relationships)
as
-- missing source code
;

alter table instance_set
    owner to folio_admin;

create view hrid_settings_view(jsonb) as
-- missing source code
;

alter table hrid_settings_view
    owner to folio_admin;

create function uuid_smaller(uuid, uuid) returns uuid
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function uuid_smaller(uuid, uuid) owner to folio_admin;

create function uuid_larger(uuid, uuid) returns uuid
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function uuid_larger(uuid, uuid) owner to folio_admin;

create function next_uuid(uuid) returns uuid
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function next_uuid(uuid) owner to folio_admin;

create function count_estimate_smart2(rows bigint, lim bigint, query text) returns bigint
    strict
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function count_estimate_smart2(bigint, bigint, text) owner to folio_admin;

create function count_estimate_default(query text) returns bigint
    immutable
    strict
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function count_estimate_default(text) owner to folio_admin;

create function count_estimate(query text) returns bigint
    stable
    strict
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function count_estimate(text) owner to folio_admin;

create function upsert(text, uuid, anyelement) returns uuid
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function upsert(text, uuid, anyelement) owner to folio_admin;

create function f_unaccent(text) returns text
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function f_unaccent(text) owner to folio_admin;

create function get_tsvector(text) returns tsvector
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function get_tsvector(text) owner to folio_admin;

create function tsquery_and(text) returns tsquery
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function tsquery_and(text) owner to folio_admin;

create function tsquery_or(text) returns tsquery
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function tsquery_or(text) owner to folio_admin;

create function tsquery_phrase(text) returns tsquery
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function tsquery_phrase(text) owner to folio_admin;

create function normalize_digits(text) returns text
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function normalize_digits(text) owner to folio_admin;

create function set_id_in_jsonb() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_id_in_jsonb() owner to folio_admin;

create function concat_space_sql(text[]) returns text
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function concat_space_sql(text[]) owner to folio_admin;

create function concat_array_object_values(jsonb_array jsonb, field text) returns text
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function concat_array_object_values(jsonb, text) owner to folio_admin;

create function concat_array_object_values(jsonb_array jsonb, field text, filterkey text, filtervalue text) returns text
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function concat_array_object_values(jsonb, text, text, text) owner to folio_admin;

create function first_array_object_value(jsonb_array jsonb, field text, filterkey text, filtervalue text) returns text
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function first_array_object_value(jsonb, text, text, text) owner to folio_admin;

create function concat_array_object(jsonb_array jsonb) returns text
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function concat_array_object(jsonb) owner to folio_admin;

create function rmb_internal_index(atable text, aname text, tops text, newdef text) returns void
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function rmb_internal_index(text, text, text, text) owner to folio_admin;

create function normalize_isbns(jsonb_array jsonb) returns text
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function normalize_isbns(jsonb) owner to folio_admin;

create function normalize_invalid_isbns(jsonb_array jsonb) returns text
    immutable
    strict
    parallel safe
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function normalize_invalid_isbns(jsonb) owner to folio_admin;

create function loan_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function loan_type_set_md() owner to folio_admin;

create function set_loan_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_loan_type_md_json() owner to folio_admin;

create function material_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function material_type_set_md() owner to folio_admin;

create function set_material_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_material_type_md_json() owner to folio_admin;

create function locinstitution_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function locinstitution_set_md() owner to folio_admin;

create function set_locinstitution_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_locinstitution_md_json() owner to folio_admin;

create function update_loccampus_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_loccampus_references() owner to folio_admin;

create function loccampus_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function loccampus_set_md() owner to folio_admin;

create function set_loccampus_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_loccampus_md_json() owner to folio_admin;

create function update_loclibrary_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_loclibrary_references() owner to folio_admin;

create function loclibrary_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function loclibrary_set_md() owner to folio_admin;

create function set_loclibrary_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_loclibrary_md_json() owner to folio_admin;

create function update_location_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_location_references() owner to folio_admin;

create function location_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function location_set_md() owner to folio_admin;

create function set_location_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_location_md_json() owner to folio_admin;

create function service_point_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function service_point_set_md() owner to folio_admin;

create function set_service_point_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_service_point_md_json() owner to folio_admin;

create function update_service_point_user_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_service_point_user_references() owner to folio_admin;

create function service_point_user_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function service_point_user_set_md() owner to folio_admin;

create function set_service_point_user_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_service_point_user_md_json() owner to folio_admin;

create function identifier_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function identifier_type_set_md() owner to folio_admin;

create function set_identifier_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_identifier_type_md_json() owner to folio_admin;

create function instance_relationship_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function instance_relationship_type_set_md() owner to folio_admin;

create function set_instance_relationship_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_instance_relationship_type_md_json() owner to folio_admin;

create function contributor_name_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function contributor_name_type_set_md() owner to folio_admin;

create function set_contributor_name_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_contributor_name_type_md_json() owner to folio_admin;

create function nature_of_content_term_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function nature_of_content_term_set_md() owner to folio_admin;

create function set_nature_of_content_term_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_nature_of_content_term_md_json() owner to folio_admin;

create function classification_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function classification_type_set_md() owner to folio_admin;

create function set_classification_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_classification_type_md_json() owner to folio_admin;

create function electronic_access_relationship_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function electronic_access_relationship_set_md() owner to folio_admin;

create function set_electronic_access_relationship_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_electronic_access_relationship_md_json() owner to folio_admin;

create function statistical_code_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function statistical_code_type_set_md() owner to folio_admin;

create function set_statistical_code_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_statistical_code_type_md_json() owner to folio_admin;

create function update_statistical_code_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_statistical_code_references() owner to folio_admin;

create function statistical_code_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function statistical_code_set_md() owner to folio_admin;

create function set_statistical_code_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_statistical_code_md_json() owner to folio_admin;

create function instance_status_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function instance_status_set_md() owner to folio_admin;

create function set_instance_status_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_instance_status_md_json() owner to folio_admin;

create function mode_of_issuance_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function mode_of_issuance_set_md() owner to folio_admin;

create function set_mode_of_issuance_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_mode_of_issuance_md_json() owner to folio_admin;

create function alternative_title_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function alternative_title_type_set_md() owner to folio_admin;

create function set_alternative_title_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_alternative_title_type_md_json() owner to folio_admin;

create function instance_date_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function instance_date_type_set_md() owner to folio_admin;

create function set_instance_date_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_instance_date_type_md_json() owner to folio_admin;

create function update_instance_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_instance_references() owner to folio_admin;

create function instance_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function instance_set_md() owner to folio_admin;

create function set_instance_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_instance_md_json() owner to folio_admin;

create function instance_set_ol_version() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function instance_set_ol_version() owner to folio_admin;

create function audit_instance_changes() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function audit_instance_changes() owner to folio_admin;

create function update_instance_relationship_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_instance_relationship_references() owner to folio_admin;

create function instance_relationship_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function instance_relationship_set_md() owner to folio_admin;

create function set_instance_relationship_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_instance_relationship_md_json() owner to folio_admin;

create function instance_source_marc_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function instance_source_marc_set_md() owner to folio_admin;

create function set_instance_source_marc_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_instance_source_marc_md_json() owner to folio_admin;

create function set_instance_sourcerecordformat() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_instance_sourcerecordformat() owner to folio_admin;

create function update_instance_source_marc() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_instance_source_marc() owner to folio_admin;

create function ill_policy_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function ill_policy_set_md() owner to folio_admin;

create function set_ill_policy_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_ill_policy_md_json() owner to folio_admin;

create function call_number_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function call_number_type_set_md() owner to folio_admin;

create function set_call_number_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_call_number_type_md_json() owner to folio_admin;

create function holdings_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function holdings_type_set_md() owner to folio_admin;

create function set_holdings_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_holdings_type_md_json() owner to folio_admin;

create function instance_note_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function instance_note_type_set_md() owner to folio_admin;

create function set_instance_note_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_instance_note_type_md_json() owner to folio_admin;

create function holdings_note_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function holdings_note_type_set_md() owner to folio_admin;

create function set_holdings_note_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_holdings_note_type_md_json() owner to folio_admin;

create function item_note_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function item_note_type_set_md() owner to folio_admin;

create function set_item_note_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_item_note_type_md_json() owner to folio_admin;

create function item_damaged_status_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function item_damaged_status_set_md() owner to folio_admin;

create function set_item_damaged_status_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_item_damaged_status_md_json() owner to folio_admin;

create function holdings_records_source_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function holdings_records_source_set_md() owner to folio_admin;

create function set_holdings_records_source_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_holdings_records_source_md_json() owner to folio_admin;

create function update_holdings_record_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_holdings_record_references() owner to folio_admin;

create function holdings_record_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function holdings_record_set_md() owner to folio_admin;

create function set_holdings_record_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_holdings_record_md_json() owner to folio_admin;

create function holdings_record_set_ol_version() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function holdings_record_set_ol_version() owner to folio_admin;

create function audit_holdings_record_changes() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function audit_holdings_record_changes() owner to folio_admin;

create function update_item_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_item_references() owner to folio_admin;

create function item_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function item_set_md() owner to folio_admin;

create function set_item_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_item_md_json() owner to folio_admin;

create function item_set_ol_version() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function item_set_ol_version() owner to folio_admin;

create function audit_item_changes() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function audit_item_changes() owner to folio_admin;

create function hrid_settings_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function hrid_settings_set_md() owner to folio_admin;

create function set_hrid_settings_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_hrid_settings_md_json() owner to folio_admin;

create function update_preceding_succeeding_title_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_preceding_succeeding_title_references() owner to folio_admin;

create function preceding_succeeding_title_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function preceding_succeeding_title_set_md() owner to folio_admin;

create function set_preceding_succeeding_title_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_preceding_succeeding_title_md_json() owner to folio_admin;

create function update_bound_with_part_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_bound_with_part_references() owner to folio_admin;

create function bound_with_part_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function bound_with_part_set_md() owner to folio_admin;

create function set_bound_with_part_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_bound_with_part_md_json() owner to folio_admin;

create function subject_type_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function subject_type_set_md() owner to folio_admin;

create function set_subject_type_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_subject_type_md_json() owner to folio_admin;

create function subject_source_set_md() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function subject_source_set_md() owner to folio_admin;

create function set_subject_source_md_json() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_subject_source_md_json() owner to folio_admin;

create function set_instance_status_updated_date() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_instance_status_updated_date() owner to folio_admin;

create function update_item_status_date() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_item_status_date() owner to folio_admin;

create function strtotimestamp(text) returns timestamp with time zone
    immutable
    strict
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function strtotimestamp(text) owner to folio_admin;

create function dateormin(timestamp with time zone) returns timestamp with time zone
    immutable
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function dateormin(timestamp with time zone) owner to folio_admin;

create function dateormax(timestamp with time zone) returns timestamp with time zone
    immutable
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function dateormax(timestamp with time zone) owner to folio_admin;

create function getelectronicaccessname(val jsonb) returns jsonb
    strict
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function getelectronicaccessname(jsonb) owner to folio_admin;

create function getitemnotetypename(val jsonb) returns jsonb
    strict
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function getitemnotetypename(jsonb) owner to folio_admin;

create function pmh_view_function(startdate timestamp with time zone, enddate timestamp with time zone, deletedrecordsupport boolean default true, skipsuppressedfromdiscoveryrecords boolean default true)
    returns table("instanceid" uuid, "updateddate" timestamp with time zone, "deleted" boolean, "itemsandholdingsfields" jsonb)
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function pmh_view_function(timestamp with time zone, timestamp with time zone, boolean, boolean) owner to folio_admin;

create function pmh_get_updated_instances_ids(startdate timestamp with time zone, enddate timestamp with time zone, deletedrecordsupport boolean default true, skipsuppressedfromdiscoveryrecords boolean default true)
    returns table("instanceid" uuid, "updateddate" timestamp with time zone, "suppressfromdiscovery" boolean, "deleted" boolean)
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function pmh_get_updated_instances_ids(timestamp with time zone, timestamp with time zone, boolean, boolean) owner to folio_admin;

create function pmh_instance_view_function(instanceids uuid[], skipsuppressedfromdiscoveryrecords boolean default true)
    returns table("instanceid" uuid, "itemsandholdingsfields" jsonb)
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function pmh_instance_view_function(uuid[], boolean) owner to folio_admin;

create function getholdingnotetypename(val jsonb) returns jsonb
    strict
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function getholdingnotetypename(jsonb) owner to folio_admin;

create function getnatureofcontentname(val jsonb) returns jsonb
    strict
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function getnatureofcontentname(jsonb) owner to folio_admin;

create function get_items_and_holdings_view(instanceids uuid[], skipsuppressedfromdiscoveryrecords boolean default true)
    returns table("instanceId" uuid, "source" varchar, "modeOfIssuance" varchar, "natureOfContent" jsonb, "holdings" jsonb, "items" jsonb)
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function get_items_and_holdings_view(uuid[], boolean) owner to folio_admin;

create function getstatisticalcodes(val jsonb) returns jsonb
    strict
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function getstatisticalcodes(jsonb) owner to folio_admin;

create function migrate_series_and_subjects(jsonb) returns jsonb
    immutable
    parallel safe
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function migrate_series_and_subjects(jsonb) owner to folio_admin;

create function get_updated_instance_ids_view(startdate timestamp with time zone, enddate timestamp with time zone, deletedrecordsupport boolean default true, skipsuppressedfromdiscoveryrecords boolean default true, onlyinstanceupdatedate boolean default true, source varchar default NULL)
    returns table("instanceId" uuid, "source" varchar, "updatedDate" timestamp with time zone, "suppressFromDiscovery" boolean, "deleted" boolean)
    language sql
as
$$
    begin
-- missing source code
end;
$$;

alter function get_updated_instance_ids_view(timestamp with time zone, timestamp with time zone, boolean, boolean, boolean, varchar) owner to folio_admin;

create function completeupdateddate_for_instance() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function completeupdateddate_for_instance() owner to folio_admin;

create function completeupdateddate_for_item_delete() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function completeupdateddate_for_item_delete() owner to folio_admin;

create function completeupdateddate_for_holdings_insert_update() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function completeupdateddate_for_holdings_insert_update() owner to folio_admin;

create function completeupdateddate_for_holdings_delete() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function completeupdateddate_for_holdings_delete() owner to folio_admin;

create function completeupdateddate_for_item_insert_update() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function completeupdateddate_for_item_insert_update() owner to folio_admin;

create function check_subject_references() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function check_subject_references() owner to folio_admin;

create function update_instance_statistical_code() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_instance_statistical_code() owner to folio_admin;

create function update_item_statistical_code() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_item_statistical_code() owner to folio_admin;

create function update_holdings_record_statistical_code() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function update_holdings_record_statistical_code() owner to folio_admin;

create function set_order() returns trigger
    language plpgsql
as
$$
begin
    -- missing source code
end;
$$;

alter function set_order() owner to folio_admin;

create aggregate max(uuid) (
    sfunc = uuid_larger,
    stype = uuid,
    combinefunc = uuid_larger,
    parallel = safe,
    sortop = operator (>)
    );

alter aggregate max(uuid) owner to folio_admin;

create aggregate min(uuid) (
    sfunc = uuid_smaller,
    stype = uuid,
    combinefunc = uuid_smaller,
    parallel = safe,
    sortop = operator (<)
    );

alter aggregate min(uuid) owner to folio_admin;

