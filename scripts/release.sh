#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
用法: scripts/release.sh <版本号> [--push] [--dry-run]

  <版本号>     语义化版本，例如 1.1.0（不要带 v）
  --push       提交并打 tag 后，推送当前分支和 tag
  --dry-run    只打印将要执行的操作，不修改文件

示例:
  scripts/release.sh 1.1.0
  scripts/release.sh 1.1.0 --push
  scripts/release.sh 1.1.0 --dry-run
EOF
}

VERSION=""
PUSH=0
DRY_RUN=0

for arg in "$@"; do
  case "$arg" in
    --push) PUSH=1 ;;
    --dry-run) DRY_RUN=1 ;;
    -h|--help) usage; exit 0 ;;
    -*)
      echo "未知参数: $arg" >&2
      usage >&2
      exit 1
      ;;
    *)
      if [ -n "$VERSION" ]; then
        echo "只能指定一个版本号。" >&2
        exit 1
      fi
      VERSION="$arg"
      ;;
  esac
done

if [ -z "$VERSION" ]; then
  usage >&2
  exit 1
fi

if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "版本号必须形如 1.2.3。" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TAG="v$VERSION"

if [ ! -f version.properties ]; then
  echo "找不到 version.properties。" >&2
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "工作区不干净，请先提交或暂存改动。" >&2
  git status --short >&2
  exit 1
fi

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "tag $TAG 已存在。" >&2
  exit 1
fi

CURRENT_CODE="$(grep -E '^VERSION_CODE=' version.properties | head -n1 | cut -d= -f2 | tr -d '[:space:]')"
if ! [[ "$CURRENT_CODE" =~ ^[0-9]+$ ]]; then
  echo "version.properties 里的 VERSION_CODE 无效。" >&2
  exit 1
fi
NEXT_CODE=$((CURRENT_CODE + 1))

echo "版本:    $TAG"
echo "versionCode: $CURRENT_CODE -> $NEXT_CODE"

if [ "$DRY_RUN" -eq 1 ]; then
  echo "[dry-run] 将写入 version.properties，并执行 git commit / git tag。"
  exit 0
fi

printf 'VERSION_CODE=%d\nVERSION_NAME=%s\n' "$NEXT_CODE" "$VERSION" > version.properties

git add version.properties
git commit -m "release: $TAG"
git tag -a "$TAG" -m "ShotNow $TAG"

BRANCH="$(git rev-parse --abbrev-ref HEAD)"

if [ "$PUSH" -eq 1 ]; then
  git push origin "$BRANCH"
  git push origin "$TAG"
  echo "已推送 $BRANCH 和 $TAG，GitHub Actions 将开始构建 Release。"
else
  echo "已创建本地提交和 tag。"
  echo "推送以触发自动发版："
  echo "  git push origin $BRANCH && git push origin $TAG"
fi
