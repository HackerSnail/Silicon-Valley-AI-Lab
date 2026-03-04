package com.atguigu.exam.service.impl;


import com.atguigu.exam.entity.Category;
import com.atguigu.exam.mapper.CategoryMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.CategoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final QuestionMapper questionMapper;
    public CategoryServiceImpl(CategoryMapper categoryMapper, QuestionMapper questionMapper) {
        this.categoryMapper = categoryMapper;
        this.questionMapper = questionMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getCategories() {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByAsc(Category::getSort);

        // 1. 查询所有分类
        List<Category> categories = categoryMapper.selectList(queryWrapper);
        // 2. 为分类列表填充题目数量【进行子分类和count数量填充】
        fillQuestionCount(categories);
        return categories;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> getCategoryTree() {
        LambdaQueryWrapper<Category> categoryLambdaQueryWrapper = new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSort);
        // 1. 查询所有分类
        List<Category> categories = categoryMapper.selectList(categoryLambdaQueryWrapper);
        // 2. 构建分类树结构并返回顶级分类列表
        return buildTree(categories);
    }

    @Override
    @Transactional
    public void saveCategory(Category category) {
        LambdaQueryWrapper<Category> queryWrapper = new LambdaQueryWrapper<Category>()
                .eq(Category::getParentId, category.getParentId())
                .eq(Category::getName, category.getName());
        // 3. 检查分类是否已存在
        Category existingCategory = categoryMapper.selectOne(queryWrapper);
        if (existingCategory != null) {
            //不能添加，同一个父类下名称重复了
            if (category.getParentId() == 0) {
                // 顶级分类不能重复
                throw new RuntimeException("顶级分类下不能重复添加分类！本次添加失败！");

            }
            Category parentCategory = this.getById(existingCategory.getParentId());
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次添加失败！".
                    formatted(parentCategory.getName(),category.getName()));
        }
        // 4. 保存分类
        categoryMapper.insert(category);
        log.info("添加分类：{}", category.getName());
    }

    private void fillQuestionCount(List<Category> categories) {
        // 1. 遍历所有题目
        Map<Long, Long> questionCountMap = questionMapper.getCategoryQuestionCount().stream().collect(
                Collectors.toMap(
                        map -> (long) map.get("categoryId"),
                        map -> (long) map.get("questionCount")
                )
        );
        // 2. 填充每个分类的题目数量
        categories.forEach(category -> category.setCount(
                questionCountMap.getOrDefault(category.getId(), 0L)
        ));
    }
    /**
     * 构建分类树形结构
     * @param categories 所有分类列表
     * @return 顶级分类列表（树形结构）
     */
    private List<Category> buildTree(List<Category> categories) {
        // 1. 填充每个分类的题目数量
        fillQuestionCount(categories);

        // 2. 使用Stream API按parentId进行分组，得到 Map<parentId, List<children>>
        Map<Long, List<Category>> childrenMap = categories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

    /*
        stream()：把 List<Category> 转成 Stream 流，开启流式操作
        Collectors.groupingBy：按指定规则分组，这里用方法引用 Category::getParentId
        提取分类的 parentId 作为分组 key，value 是对应 parentId 的分类列表
        快速构建 父 ID - 子分类列表 映射
     */

        
        // 3. 遍历所有分类，为它们设置children属性，并累加子分类的题目数量
        categories.forEach(category -> {
            // 从Map中找到当前分类的所有子分类，无对应值时给默认空列表，避免空指针
            List<Category> children = childrenMap.getOrDefault(category.getId(), new ArrayList<>());
            category.setChildren(children);

            // 汇总子分类的题目数量到父分类
            long childrenQuestionCount = children.stream()
                    .mapToLong(c -> c.getCount() != null ? c.getCount() : 0L)
                    .sum();

        /*
            forEach：遍历每个分类，对单个分类做处理，类似增强 for 循环，但结合 Stream 更灵活
            getOrDefault：从分组好的 childrenMap 取当前分类的子分类，无对应值时给默认空列表
            嵌套 stream().mapToLong().sum()：先转成 LongStream，通过 mapToLong 处理 count（空值转 0）
            再用 sum 汇总子分类题目数，结合自身题目数，设置到当前分类，完成递归汇总逻辑
         */

            // 获取当前分类自身的题目数量，避免空指针
            long selfQuestionCount = category.getCount() != null ? category.getCount() : 0L;
            // 父分类的总数 = 自身的题目数 + 所有子分类的题目数总和
            category.setCount(selfQuestionCount + childrenQuestionCount);
        });

        // 4. 最后，筛选出所有顶级分类（parentId为0），它们是树的根节点
    /*
        filter：按条件（parentId == 0）过滤分类，只保留顶级分类
        collect(Collectors.toList())：把过滤后的 Stream 流转为 List，作为分类树的根节点集合返回
     */
        return categories.stream()
                .filter(category -> category.getParentId() != null && category.getParentId() == 0)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateCategory(Category category) {
        //1.先校验  同一父分类下！ 可以跟自己的name重复，不能跟其他的子分类name重复！
        LambdaQueryWrapper<Category> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(Category::getParentId, category.getParentId())
        .ne(Category::getId, category.getId())
        .eq(Category::getName, category.getName());
        CategoryMapper categoryMapper = getBaseMapper();
        boolean exists = categoryMapper.exists(lambdaQueryWrapper);
        if (exists) {
            Category parent = getById(category.getParentId());
            //不能添加，同一个父类下名称重复了
            throw new RuntimeException("在%s父分类下，已经存在名为：%s的子分类，本次更新失败！".formatted(parent.getName(),category.getName()));
        }
        //2.再更新
        updateById(category);
        log.info("更新分类：{}", category.getName());
    }

}