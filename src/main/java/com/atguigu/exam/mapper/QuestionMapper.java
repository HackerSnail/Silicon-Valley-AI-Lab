package com.atguigu.exam.mapper;


import com.atguigu.exam.entity.Category;
import com.atguigu.exam.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 题目Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 获取每个分类的题目数量
     * @return 包含分类ID和题目数量的结果列表
     */
    @Select("SELECT category_id AS categoryId, COUNT(*) AS questionCount FROM questions WHERE  is_deleted = 0 GROUP BY category_id")
    List<Map<String, Object>> getCategoryQuestionCount();

    /*
     * 递归查询全量分类树形结构（注解版）
     * 注意：
     * 1. SQL 中的换行用 \n 或直接换行（注解支持多行字符串）；
     * 2. 单引号无需转义，双引号若有则需转义为 \"；
     * 3. 仅支持 MySQL 8.0+、PostgreSQL 等支持 WITH RECURSIVE 的数据库。
     */
//    @Select({
//            "WITH RECURSIVE category_tree AS (",
//            "    SELECT c.id, c.name, c.parent_id AS parentId, CAST(c.id AS CHAR) AS path, 0 AS level ",
//            "    FROM categories c WHERE c.parent_id = 0",
//            "    UNION ALL",
//            "    SELECT c.id, c.name, c.parent_id AS parentId, CONCAT(ct.path, ',', c.id) AS path, ct.level + 1 AS level ",
//            "    FROM categories c JOIN category_tree ct ON c.parent_id = ct.id",
//            "),",
//            "category_question_count AS (",
//            "    SELECT q.category_id, COUNT(*) AS direct_count ",
//            "    FROM questions q GROUP BY q.category_id",
//            ")",
//            "SELECT ct.id, ct.name, ct.parentId, ct.level, ",
//            "COALESCE(cqc.direct_count, 0) + SUM(COALESCE(ccqc.direct_count, 0)) OVER (PARTITION BY ct.id) AS total_count ",
//            "FROM category_tree ct ",
//            "LEFT JOIN category_question_count cqc ON ct.id = cqc.category_id ",
//            "LEFT JOIN category_question_count ccqc ON FIND_IN_SET(ccqc.category_id, ct.path) > 0 ",
//            "ORDER BY ct.level, ct.id"
//    })
//    List<Category> selectCategoryTreeWithCount();
}

