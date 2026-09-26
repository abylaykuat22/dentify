#!/bin/sh
# Разовая инициализация MinIO (сервис minio-init в compose.dev.yml). Идемпотентна:
#   1) приватный бакет $S3_BUCKET — без анонимного доступа;
#   2) пользователь приложения $S3_ACCESS_KEY с политикой dentify-app — доступ только к этому бакету.
# Root-учётка MinIO приложению не выдаётся, как и в проде (там — сервисный аккаунт с ключом на бакет).
set -eu

: "${MINIO_ROOT_USER:?}" "${MINIO_ROOT_PASSWORD:?}" "${S3_BUCKET:?}" "${S3_ACCESS_KEY:?}" "${S3_SECRET_KEY:?}"

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null

mc mb --ignore-existing "local/$S3_BUCKET"
mc anonymous set private "local/$S3_BUCKET"

policy_file=$(mktemp)
cat >"$policy_file" <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation", "s3:ListBucket", "s3:ListBucketMultipartUploads"],
      "Resource": ["arn:aws:s3:::${S3_BUCKET}"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject",
                 "s3:AbortMultipartUpload", "s3:ListMultipartUploadParts"],
      "Resource": ["arn:aws:s3:::${S3_BUCKET}/*"]
    }
  ]
}
EOF

mc admin user add local "$S3_ACCESS_KEY" "$S3_SECRET_KEY" >/dev/null
mc admin policy create local dentify-app "$policy_file" >/dev/null
rm -f "$policy_file"

# В части версий mc attach падает, если политика уже привязана, — поэтому сначала смотрим
# поле policyName (не просто вхождение строки: accessKey тоже может называться dentify-app).
case "$(mc admin user info local "$S3_ACCESS_KEY" --json)" in
  *'"policyName":"'*dentify-app*) ;;
  *) mc admin policy attach local dentify-app --user "$S3_ACCESS_KEY" >/dev/null ;;
esac

echo "minio: bucket '$S3_BUCKET' is private, user '$S3_ACCESS_KEY' has policy dentify-app"
