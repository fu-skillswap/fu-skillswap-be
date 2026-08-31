-- Expand verification documents with immutable file metadata.
-- This migration is intentionally forward-only and keeps the columns nullable:
-- historical rows may reference a file that has already been removed. The
-- application must therefore tolerate a missing metadata snapshot.

alter table mentor_verification_documents
    add column if not exists original_filename varchar(255),
    add column if not exists content_type varchar(100),
    add column if not exists size_bytes bigint,
    add column if not exists file_url text;

-- Backfill metadata from the storage record when it is still available.
update mentor_verification_documents d
set original_filename = coalesce(d.original_filename, f.original_name),
    content_type = coalesce(d.content_type, f.mime_type),
    size_bytes = coalesce(d.size_bytes, f.size_bytes),
    file_url = coalesce(d.file_url, f.public_url)
from files f
where d.stored_file_id = f.id
  and (d.original_filename is null
       or d.content_type is null
       or d.size_bytes is null
       or d.file_url is null);

-- Keep lookups local to the owner aggregate; null metadata is valid for
-- historical rows whose storage record cannot be recovered.
create index if not exists idx_mentor_verification_documents_stored_file_id
    on mentor_verification_documents (stored_file_id);
