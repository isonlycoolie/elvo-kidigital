create table account_limit_change_requests (
    limit_change_request_id uuid primary key,
    account_id uuid not null,
    limit_scope varchar(64) not null,
    previous_amount numeric(19,2) not null,
    requested_amount numeric(19,2) not null,
    reason varchar(512),
    requested_by varchar(128),
    requested_at timestamp with time zone not null default now(),
    activation_at timestamp with time zone not null,
    status varchar(32) not null,
    activated_at timestamp with time zone null
);

create index idx_limit_change_requests_account_id on account_limit_change_requests(account_id);
create index idx_limit_change_requests_status on account_limit_change_requests(status);
create index idx_limit_change_requests_activation_at on account_limit_change_requests(activation_at);

