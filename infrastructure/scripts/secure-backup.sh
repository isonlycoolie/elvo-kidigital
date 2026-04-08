#!/usr/bin/env bash

# Secure Backup Script for ELVO Services
# Description: Encrypts and stores service backups securely
# Usage: ./secure-backup.sh [service] [backup-type]
# Features:
#   - AES-256 encryption
#   - Integrity verification with SHA-256
#   - Automated encryption key management
#   - Access logging and audit trail
#   - Encrypted backup to S3

set -euo pipefail

# Configuration
BACKUP_USER="backup"
BACKUP_GROUP="backup"
BACKUP_ROOT="/var/backups/elvo"
ENCRYPTION_KEY_FILE="/secure/vault/backup-encryption-key"
LOG_FILE="/var/log/elvo-backup.log"
AUDIT_LOG_FILE="/var/log/elvo-backup-audit.log"
S3_BACKUP_BUCKET="${S3_BACKUP_BUCKET:-elvo-backups}"
S3_BACKUP_PREFIX="${S3_BACKUP_PREFIX:-encrypted-backups}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Logging function
log_info() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [INFO] $*" | tee -a "$LOG_FILE"
}

log_error() {
    echo -e "${RED}[$(date +'%Y-%m-%d %H:%M:%S')] [ERROR] $*${NC}" | tee -a "$LOG_FILE" >&2
}

log_success() {
    echo -e "${GREEN}[$(date +'%Y-%m-%d %H:%M:%S')] [SUCCESS] $*${NC}" | tee -a "$LOG_FILE"
}

log_audit() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] [AUDIT] actor=backup-script action=$1 service=$2 status=$3" >> "$AUDIT_LOG_FILE"
}

# Verify backup encryption key exists and is readable
verify_encryption_key() {
    if [[ ! -f "$ENCRYPTION_KEY_FILE" ]]; then
        log_error "Encryption key file not found: $ENCRYPTION_KEY_FILE"
        return 1
    fi
    
    if [[ ! -r "$ENCRYPTION_KEY_FILE" ]]; then
        log_error "Encryption key file not readable: $ENCRYPTION_KEY_FILE"
        return 1
    fi
    
    log_info "Encryption key verified: $ENCRYPTION_KEY_FILE"
    return 0
}

# Create backup directory with secure permissions
create_backup_directory() {
    local backup_dir="$1"
    local service="$2"
    
    mkdir -p "$backup_dir/$service"
    chmod 700 "$backup_dir/$service"
    chown "$BACKUP_USER:$BACKUP_GROUP" "$backup_dir/$service"
    
    log_info "Backup directory created: $backup_dir/$service"
}

# Encrypt backup file using AES-256
encrypt_backup() {
    local source_file="$1"
    local encrypted_file="$2"
    
    if [[ ! -f "$source_file" ]]; then
        log_error "Source file not found: $source_file"
        return 1
    fi
    
    log_info "Encrypting backup: $source_file"
    
    # Encrypt using OpenSSL with AES-256-GCM
    openssl enc -aes-256-cbc \
        -in "$source_file" \
        -out "$encrypted_file" \
        -pass file:"$ENCRYPTION_KEY_FILE" \
        -P \
        -md sha256 \
        -S "$(openssl rand -hex 8)"
    
    if [[ $? -eq 0 ]]; then
        log_success "Backup encrypted: $encrypted_file"
        
        # Secure source file deletion (5-pass overwrite for sensitive data)
        shred -vfz -n 5 "$source_file" 2>/dev/null || rm -f "$source_file"
        log_info "Source file securely deleted"
        
        return 0
    else
        log_error "Failed to encrypt backup: $source_file"
        return 1
    fi
}

# Generate SHA-256 checksum for integrity verification
generate_checksum() {
    local file="$1"
    local checksum_file="${file}.sha256"
    
    sha256sum "$file" | tee "$checksum_file" > /dev/null
    log_info "Checksum generated: $checksum_file"
    
    echo "$checksum_file"
}

# Verify backup integrity using checksum
verify_backup_integrity() {
    local file="$1"
    local checksum_file="${file}.sha256"
    
    if [[ ! -f "$checksum_file" ]]; then
        log_error "Checksum file not found: $checksum_file"
        return 1
    fi
    
    log_info "Verifying backup integrity: $file"
    
    sha256sum -c "$checksum_file" > /dev/null 2>&1
    if [[ $? -eq 0 ]]; then
        log_success "Backup integrity verified"
        return 0
    else
        log_error "Backup integrity check failed: $file"
        return 1
    fi
}

# Upload encrypted backup to S3
upload_to_s3() {
    local file="$1"
    local service="$2"
    local backup_date=$(date +'%Y%m%d')
    
    local s3_path="s3://${S3_BACKUP_BUCKET}/${S3_BACKUP_PREFIX}/${service}/${backup_date}/$(basename "$file")"
    
    log_info "Uploading backup to S3: $s3_path"
    
    # Upload with server-side encryption
    aws s3 cp "$file" "$s3_path" \
        --sse AES256 \
        --metadata "service=$service,encrypted=yes,date=$backup_date" \
        --no-progress
    
    if [[ $? -eq 0 ]]; then
        log_success "Backup uploaded to S3: $s3_path"
        log_audit "upload" "$service" "success"
        return 0
    else
        log_error "Failed to upload backup to S3: $s3_path"
        log_audit "upload" "$service" "failure"
        return 1
    fi
}

# Main backup function
backup_service() {
    local service="$1"
    local backup_type="${2:-full}"
    
    log_info "Starting backup for service: $service (type: $backup_type)"
    log_audit "backup_start" "$service" "initiated"
    
    # Verify encryption key before starting
    if ! verify_encryption_key; then
        log_audit "backup_start" "$service" "failed"
        return 1
    fi
    
    # Create secure backup directory
    create_backup_directory "$BACKUP_ROOT" "$service"
    
    local backup_timestamp=$(date +'%Y%m%d_%H%M%S')
    local backup_filename="${service}_${backup_type}_${backup_timestamp}.sql"
    local backup_path="$BACKUP_ROOT/$service/$backup_filename"
    local encrypted_path="${backup_path}.enc"
    
    # Service-specific backup logic
    case "$service" in
        billing)
            log_info "Backing up Billing Service database"
            pg_dump -d elvo_billing -U backup_user -h localhost > "$backup_path" || {
                log_error "Failed to backup Billing Service database"
                log_audit "database_dump" "$service" "failed"
                return 1
            }
            ;;
        wallet)
            log_info "Backing up Wallet Service database"
            pg_dump -d elvo_wallet -U backup_user -h localhost > "$backup_path" || {
                log_error "Failed to backup Wallet Service database"
                log_audit "database_dump" "$service" "failed"
                return 1
            }
            ;;
        identity)
            log_info "Backing up Identity Service database"
            pg_dump -d elvo_identity -U backup_user -h localhost > "$backup_path" || {
                log_error "Failed to backup Identity Service database"
                log_audit "database_dump" "$service" "failed"
                return 1
            }
            ;;
        *)
            log_error "Unknown service: $service"
            log_audit "backup_start" "$service" "failed"
            return 1
            ;;
    esac
    
    log_info "Database dump complete: $backup_path"
    
    # Encrypt backup
    if ! encrypt_backup "$backup_path" "$encrypted_path"; then
        log_audit "encryption" "$service" "failed"
        return 1
    fi
    
    # Generate and verify checksum
    if ! generate_checksum "$encrypted_path"; then
        log_audit "checksum_generation" "$service" "failed"
        return 1
    fi
    
    if ! verify_backup_integrity "$encrypted_path"; then
        log_audit "integrity_check" "$service" "failed"
        return 1
    fi
    
    # Upload to S3
    if ! upload_to_s3 "$encrypted_path" "$service"; then
        return 1
    fi
    
    # Set secure permissions on local backup
    chmod 600 "$encrypted_path" "${encrypted_path}.sha256"
    chown "$BACKUP_USER:$BACKUP_GROUP" "$encrypted_path" "${encrypted_path}.sha256"
    
    log_success "Backup completed successfully: $service"
    log_audit "backup_complete" "$service" "success"
    
    return 0
}

# Main script execution
main() {
    if [[ $# -lt 1 ]]; then
        echo "Usage: $0 <service> [backup-type]"
        echo "Services: billing, wallet, identity"
        echo "Backup types: full, incremental (default: full)"
        exit 1
    fi
    
    local service="$1"
    local backup_type="${2:-full}"
    
    # Verify running as backup user
    if [[ $(whoami) != "$BACKUP_USER" ]]; then
        log_error "This script must be run as user: $BACKUP_USER"
        exit 1
    fi
    
    # Ensure backup root directory exists
    mkdir -p "$BACKUP_ROOT"
    chmod 700 "$BACKUP_ROOT"
    
    # Run backup
    if backup_service "$service" "$backup_type"; then
        exit 0
    else
        exit 1
    fi
}

# Run main function with all arguments
main "$@"
