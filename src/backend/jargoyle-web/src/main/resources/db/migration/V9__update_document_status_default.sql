-- The upload workflow now creates documents in PENDING_UPLOAD status.
-- Update the column default to match.
alter table documents alter column status set default 'PENDING_UPLOAD';
