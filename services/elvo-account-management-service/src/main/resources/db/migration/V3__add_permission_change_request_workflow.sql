create table account_permission_change_requests (
    permission_change_request_id uuid primary key,
    account_id uuid not null,
    permission_flag varchar(64) not null,
    previous_enabled boolean not null,
    requested_enabled boolean not null,
    status varchar(32) not null,
    reason varchar(512),
    requested_by varchar(128),
    approved_by varchar(128),
    approval_note varchar(512),
    requested_at timestamp with time zone not null default now(),
    approved_at timestamp with time zone null
);

create index idx_permission_change_requests_account_id on account_permission_change_requests(account_id);
create index idx_permission_change_requests_status on account_permission_change_requests(status);

