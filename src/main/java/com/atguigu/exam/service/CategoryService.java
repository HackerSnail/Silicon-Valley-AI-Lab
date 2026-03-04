package com.atguigu.exam.service;

import com.atguigu.exam.entity.Category;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface CategoryService extends IService<Category> {


    /**
     * 获取所有分类列表
     * @return 分类列表
     * 分类列表包含子分类和题目数量
     */
    List<Category> getCategories();

    /**
     * 获取分类树结构
     * @return 分类树列表
     */
    List<Category> getCategoryTree();

    /**
     * 保存分类信息
     *
     @param category
     */
    void saveCategory(Category category);

     /**
     * 更新分类信息
     * @param category 分类信息
     */
    void updateCategory(Category category);
} 