#!/usr/bin/env bash
set -euo pipefail

# ELVO Wallet Service RabbitMQ least-privilege user setup
# Required env vars:
#   WALLET_RABBIT_USER
#   WALLET_RABBIT_PASSWORD
# Optional env vars:
#   WALLET_RABBIT_VHOST (default: /)
#   WALLET_EXCHANGE_PREFIX (default: elvo.wallet)

: "${WALLET_RABBIT_USER:?WALLET_RABBIT_USER is required}"
: "${WALLET_RABBIT_PASSWORD:?WALLET_RABBIT_PASSWORD is required}"

WALLET_RABBIT_VHOST="${WALLET_RABBIT_VHOST:-/}"
WALLET_EXCHANGE_PREFIX="${WALLET_EXCHANGE_PREFIX:-elvo.wallet}"

rabbitmqctl add_user "${WALLET_RABBIT_USER}" "${WALLET_RABBIT_PASSWORD}" 2>/dev/null || true
rabbitmqctl change_password "${WALLET_RABBIT_USER}" "${WALLET_RABBIT_PASSWORD}"

# Restrict to wallet exchange/queue namespace only.
CONFIGURE_REGEX="^${WALLET_EXCHANGE_PREFIX}(\\..*)?$"
WRITE_REGEX="^${WALLET_EXCHANGE_PREFIX}(\\..*)?$"
READ_REGEX="^${WALLET_EXCHANGE_PREFIX}(\\..*)?$"

rabbitmqctl set_permissions -p "${WALLET_RABBIT_VHOST}" \
  "${WALLET_RABBIT_USER}" \
  "${CONFIGURE_REGEX}" \
  "${WRITE_REGEX}" \
  "${READ_REGEX}"

rabbitmqctl set_user_tags "${WALLET_RABBIT_USER}"

echo "Wallet RabbitMQ user configured with least privilege."
