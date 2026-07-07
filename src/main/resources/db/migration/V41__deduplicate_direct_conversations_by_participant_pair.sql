with direct_pairs as (
    select c.id as conversation_id,
           case when cp1.user_id::text < cp2.user_id::text then cp1.user_id else cp2.user_id end as user_a_id,
           case when cp1.user_id::text < cp2.user_id::text then cp2.user_id else cp1.user_id end as user_b_id,
           c.created_at,
           c.last_message_at
    from conversations c
    join conversation_participants cp1 on cp1.conversation_id = c.id
    join conversation_participants cp2 on cp2.conversation_id = c.id
        and cp1.user_id::text < cp2.user_id::text
    where c.type = 'DIRECT'
      and c.status = 'ACTIVE'
),
ranked_pairs as (
    select conversation_id,
           first_value(conversation_id) over (
               partition by user_a_id, user_b_id
               order by coalesce(last_message_at, created_at) desc, created_at desc, conversation_id
           ) as canonical_conversation_id,
           row_number() over (
               partition by user_a_id, user_b_id
               order by coalesce(last_message_at, created_at) desc, created_at desc, conversation_id
           ) as rn
    from direct_pairs
),
duplicate_pairs as (
    select conversation_id, canonical_conversation_id
    from ranked_pairs
    where rn > 1
)
update messages m
set conversation_id = d.canonical_conversation_id
from duplicate_pairs d
where m.conversation_id = d.conversation_id;

with direct_pairs as (
    select c.id as conversation_id,
           case when cp1.user_id::text < cp2.user_id::text then cp1.user_id else cp2.user_id end as user_a_id,
           case when cp1.user_id::text < cp2.user_id::text then cp2.user_id else cp1.user_id end as user_b_id,
           c.created_at,
           c.last_message_at
    from conversations c
    join conversation_participants cp1 on cp1.conversation_id = c.id
    join conversation_participants cp2 on cp2.conversation_id = c.id
        and cp1.user_id::text < cp2.user_id::text
    where c.type = 'DIRECT'
      and c.status = 'ACTIVE'
),
ranked_pairs as (
    select conversation_id,
           row_number() over (
               partition by user_a_id, user_b_id
               order by coalesce(last_message_at, created_at) desc, created_at desc, conversation_id
           ) as rn
    from direct_pairs
),
duplicate_pairs as (
    select conversation_id
    from ranked_pairs
    where rn > 1
)
delete from conversation_participants cp
using duplicate_pairs d
where cp.conversation_id = d.conversation_id;

with direct_pairs as (
    select c.id as conversation_id,
           case when cp1.user_id::text < cp2.user_id::text then cp1.user_id else cp2.user_id end as user_a_id,
           case when cp1.user_id::text < cp2.user_id::text then cp2.user_id else cp1.user_id end as user_b_id,
           c.created_at,
           c.last_message_at
    from conversations c
    join conversation_participants cp1 on cp1.conversation_id = c.id
    join conversation_participants cp2 on cp2.conversation_id = c.id
        and cp1.user_id::text < cp2.user_id::text
    where c.type = 'DIRECT'
      and c.status = 'ACTIVE'
),
ranked_pairs as (
    select conversation_id,
           row_number() over (
               partition by user_a_id, user_b_id
               order by coalesce(last_message_at, created_at) desc, created_at desc, conversation_id
           ) as rn
    from direct_pairs
),
duplicate_pairs as (
    select conversation_id
    from ranked_pairs
    where rn > 1
)
delete from conversations c
using duplicate_pairs d
where c.id = d.conversation_id;

with latest_message as (
    select distinct on (conversation_id)
           conversation_id,
           content,
           created_at
    from messages
    order by conversation_id, created_at desc, id desc
)
update conversations c
set last_message_content = latest_message.content,
    last_message_at = latest_message.created_at
from latest_message
where c.id = latest_message.conversation_id;
