INSERT INTO ${myuniversity}_${mymodule}.authority_source_file (id, JSONB)
VALUES ('cb58492d-018e-442d-9ce3-35aabfc524aa', '{
                    "id": "cb58492d-018e-442d-9ce3-35aabfc524aa",
                    "name": "Art & architecture thesaurus (AAT)",
                    "codes": [
                      "aat",
                      "aatg"
                    ],
                    "type": "Subjects",
                    "baseUrl": "vocab.getty.edu/aat/",
                    "source": "folio"
         }'),
       ('191874a0-707a-4634-928e-374ee9103225', '{
                    "id": "191874a0-707a-4634-928e-374ee9103225",
                    "name": "Faceted Application of Subject Terminology (FAST)",
                    "codes": [
                      "fst"
                    ],
                    "type": "Subjects",
                    "baseUrl": "id.worldcat.org/fast/",
                    "source": "folio"
         }'),
       ('b224845c-5026-4594-8b55-61d39ecf0541', '{
                    "id": "b224845c-5026-4594-8b55-61d39ecf0541",
                    "name": "GSAFD Genre Terms (GSAFD)",
                    "codes": [
                      "gsafd"
                    ],
                    "type": "Subjects",
                    "baseUrl": "vocabularyserver.com/gsafd/",
                    "source": "folio"
         }'),
       ('ccebe5d8-5bfe-46f5-bfa2-79f257c249c9', '{
                    "id": "ccebe5d8-5bfe-46f5-bfa2-79f257c249c9",
                    "name": "LC Children''s Subject Headings",
                    "codes": [
                      "sj"
                    ],
                    "type": "Subjects",
                    "baseUrl": "id.loc.gov/authorities/childrensSubjects/",
                    "source": "folio"
         }'),
       ('4b531a84-d4fe-44e5-b75f-542ec71b2f62', '{
                    "id": "4b531a84-d4fe-44e5-b75f-542ec71b2f62",
                    "name": "LC Demographic Group Terms (LCFGT)",
                    "codes": [
                      "dg"
                    ],
                    "type": "Subjects",
                    "baseUrl": "id.loc.gov/authorities/demographicTerms/",
                    "source": "folio"
         }'),
       ('67d1ec4b-a19a-4324-9f19-473b49e370ac', '{
                    "id": "67d1ec4b-a19a-4324-9f19-473b49e370ac",
                    "name": "LC Genre/Form Terms (LCGFT)",
                    "codes": [
                      "gf"
                    ],
                    "type": "Subjects",
                    "baseUrl": "id.loc.gov/authorities/genreForms/",
                    "source": "folio"
         }'),
       ('2c0e41b5-8ffb-4856-aa64-76648a6f6b18', '{
                    "id": "2c0e41b5-8ffb-4856-aa64-76648a6f6b18",
                    "name": "LC Medium of Performance Thesaurus for Music (LCMPT)",
                    "codes": [
                      "mp"
                    ],
                    "type": "Subjects",
                    "baseUrl": "id.loc.gov/authorities/performanceMediums/",
                    "source": "folio"
         }'),
       ('af045f2f-e851-4613-984c-4bc13430454a', '{
                       "id": "af045f2f-e851-4613-984c-4bc13430454a",
                       "name": "LC Name Authority file (LCNAF)",
                       "codes": [
                         "n",
                         "nb",
                         "nr",
                         "no"
                       ],
                       "type": "Names",
                       "baseUrl": "id.loc.gov/authorities/names/",
                       "source": "folio"
         }'),
       ('837e2c7b-037b-4113-9dfd-b1b8aeeb1fb8', '{
                       "id": "837e2c7b-037b-4113-9dfd-b1b8aeeb1fb8",
                       "name": "LC Subject Headings (LCSH)",
                       "codes": [
                         "sh"
                       ],
                       "type": "Subjects",
                       "baseUrl": "id.loc.gov/authorities/subjects/",
                       "source": "folio"
         }'),
       ('6ddf21a6-bc2f-4cb0-ad96-473e1f82da23', '{
                       "id": "6ddf21a6-bc2f-4cb0-ad96-473e1f82da23",
                       "name": "Medical Subject Headings (MeSH)",
                       "codes": [
                         "D"
                       ],
                       "type": "Subjects",
                       "baseUrl": "id.nlm.nih.gov/mesh/",
                       "source": "folio"
         }'),
       ('b0f38dbe-5bc0-477d-b1ee-6d9878a607f7', '{
                       "id": "b0f38dbe-5bc0-477d-b1ee-6d9878a607f7",
                       "name": "Rare Books and Manuscripts Section (RBMS)",
                       "codes": [
                         "rbmscv"
                       ],
                       "type": "Subjects",
                       "source": "folio"
         }'),
       ('70ff583b-b8c9-483e-ac21-cb4a9217898b', '{
                       "id": "70ff583b-b8c9-483e-ac21-cb4a9217898b",
                       "name": "Thesaurus for Graphic Materials (TGM)",
                       "codes": [
                         "lcgtm",
                         "tgm"
                       ],
                       "type": "Subjects",
                       "baseUrl": "id.loc.gov/vocabulary/graphicMaterials/",
                       "source": "folio"
         }')
ON CONFLICT DO NOTHING;
