#!/bin/bash
# 修复 SQLite 数据库损坏问题

echo "=== LinLi News 数据库修复脚本 ==="
echo ""

# 1. 备份原数据库
BACKUP_FILE="linli_news.db.backup.$(date +%Y%m%d_%H%M%S)"
echo "1. 备份原数据库到 $BACKUP_FILE"
cp linli_news.db "$BACKUP_FILE"
echo "   ✓ 备份完成"
echo ""

# 2. 尝试从损坏的数据库中导出数据
echo "2. 尝试导出可恢复的数据..."
sqlite3 linli_news.db ".recover" > recovered_data.sql 2>/dev/null
if [ $? -eq 0 ] && [ -s recovered_data.sql ]; then
    echo "   ✓ 数据导出成功"
else
    echo "   ⚠ 无法恢复数据，将创建全新数据库"
    rm -f recovered_data.sql
fi
echo ""

# 3. 删除损坏的数据库文件
echo "3. 删除损坏的数据库文件..."
rm -f linli_news.db linli_news.db-shm linli_news.db-wal
echo "   ✓ 已删除"
echo ""

# 4. 重建数据库
echo "4. 重建数据库（Flyway 迁移将在应用启动时自动执行）"
echo "   请启动应用程序，Flyway 会自动创建新的数据库结构"
echo ""

# 5. 如果有恢复的数据，提供恢复指令
if [ -f recovered_data.sql ]; then
    echo "5. 恢复数据（可选）"
    echo "   如果需要恢复数据，请在应用启动后执行："
    echo "   sqlite3 linli_news.db < recovered_data.sql"
fi

echo ""
echo "=== 修复完成 ==="
echo "下一步："
echo "1. 启动应用程序（Flyway 会自动创建数据库结构）"
echo "2. 如果有 recovered_data.sql，可以尝试导入恢复的数据"
