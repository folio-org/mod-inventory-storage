UPDATE ${myuniversity}_${mymodule}.call_number_type
SET jsonb = jsonb ||
  '{
     "source": "system"
   }'
WHERE id IN ('03dd64d0-5626-4ecd-8ece-4531e0069f35',
             '95467209-6d7b-468b-94df-0f5d7ad2747d',
             'fc388041-6cd0-4806-8a74-ebe3b9ab4c6e',
             '054d460d-d6b9-4469-9e37-7a78a2266655',
             '6caca63e-5651-4db6-9247-3205156e9699');

