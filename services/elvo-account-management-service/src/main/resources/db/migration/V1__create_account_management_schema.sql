create table accounts (
    account_id uuid primary key,
    user_id uuid not null unique,
    ean varchar(64) not null unique,
    account_type varchar(32) not null,
    account_status varchar(32) not null,
    kyc_status varchar(32) not null,
    parent_account_id uuid null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    version bigint not null default 0
);

create index idx_accounts_user_id on accounts (user_id);
create index idx_accounts_ean on accounts (ean);
create index idx_accounts_status on accounts (account_status);

create table account_permissions (
    permission_id uuid primary key,
    account_id uuid not null unique references accounts(account_id) on delete cascade,
    can_receive_money boolean not null default true,
    can_send_money boolean not null default true,
    can_withdraw boolean not null default true,
    can_deposit boolean not null default true,
    can_use_delegated_access boolean not null default false,
    can_use_agent_withdrawal boolean not null default false,
    can_perform_bill_payment boolean not null default true,
    can_create_sub_accounts boolean not null default false,
    effective_from timestamp with time zone not null default now(),
    effective_to timestamp with time zone null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create table account_limits (
    limit_id uuid primary key,
    account_id uuid not null unique references accounts(account_id) on delete cascade,
    daily_transfer_limit numeric(19,2) null,
    monthly_transfer_limit numeric(19,2) null,
    withdrawal_limit numeric(19,2) null,
    deposit_limit numeric(19,2) null,
    bill_payment_limit numeric(19,2) null,
    max_single_transaction numeric(19,2) null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    version bigint not null default 0
);

create table account_relationships (
    relationship_id uuid primary key,
    parent_account_id uuid not null references accounts(account_id) on delete cascade,
    child_account_id uuid not null references accounts(account_id) on delete cascade,
    relationship_type varchar(32) not null,
    status varchar(32) not null,
    start_date timestamp with time zone not null default now(),
    end_date timestamp with time zone null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create index idx_account_relationships_parent on account_relationships (parent_account_id);
create index idx_account_relationships_child on account_relationships (child_account_id);

create table account_restrictions (
    restriction_id uuid primary key,
    account_id uuid not null references accounts(account_id) on delete cascade,
    restriction_type varchar(32) not null,
    reason varchar(255) null,
    start_date timestamp with time zone not null default now(),
    end_date timestamp with time zone null,
    created_by varchar(128) null,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);

create index idx_account_restrictions_account_id on account_restrictions (account_id);

create table account_audit_log (
    audit_log_id uuid primary key,
    account_id uuid null references accounts(account_id) on delete set null,
    action_type varchar(64) not null,
    description varchar(512) null,
    request_id varchar(64) null,
    correlation_id varchar(64) null,
    source_service varchar(128) null,
    source_ip varchar(64) null,
    source_user_agent varchar(255) null,
    created_by varchar(128) null,
    created_at timestamp with time zone not null default now()
);

create index idx_account_audit_account_id on account_audit_log (account_id);
create index idx_account_audit_request_id on account_audit_log (request_id);

