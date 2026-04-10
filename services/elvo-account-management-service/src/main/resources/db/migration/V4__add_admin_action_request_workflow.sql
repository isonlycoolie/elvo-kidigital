create table account_admin_action_requests (
    admin_action_request_id uuid primary key,
    account_id uuid not null,
    action_type varchar(64) not null,
    restriction_type varchar(64) null,
    status varchar(32) not null,
    reason varchar(512),
    requested_by varchar(128),
    approved_by varchar(128),
    approval_note varchar(512),
    requested_at timestamptz not null default now(),
    approved_at timestamptz null
);

create index idx_admin_action_requests_account_id on account_admin_action_requests(account_id);
create index idx_admin_action_requests_status on account_admin_action_requests(status);
