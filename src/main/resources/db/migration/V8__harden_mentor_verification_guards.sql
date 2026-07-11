create unique index if not exists uq_mentor_verification_active_request
    on mentor_verification_requests (mentor_user_id)
    where status in ('DRAFT', 'PENDING_REVIEW', 'NEEDS_REVISION');

create unique index if not exists uq_mentor_verification_affiliation_active
    on mentor_verification_documents (request_id, document_type)
    where is_active = true and document_type = 'FPTU_AFFILIATION_PROOF';
