UPDATE ${myuniversity}_${mymodule}.authority_source_file
SET jsonb = jsonb ||
  '{
     "codes": [
       "n",
       "nb",
       "nr",
       "no",
       "ns"
     ]
   }'
WHERE id = 'af045f2f-e851-4613-984c-4bc13430454a';
