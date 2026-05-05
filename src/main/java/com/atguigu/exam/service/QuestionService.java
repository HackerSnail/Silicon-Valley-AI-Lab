package com.atguigu.exam.service;

import com.atguigu.exam.entity.Question;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.atguigu.exam.vo.QuestionImportVo;
/**
 * 题目业务服务接口 - 定义题目相关的业务逻辑
 * 
 * Spring Boot三层架构教学要点：
 * 1. Service层：业务逻辑层，位于Controller和Mapper之间
 * 2. 接口设计：定义业务方法规范，便于不同实现类的切换
 * 3. 继承IService：使用MyBatis Plus提供的通用服务接口，减少重复代码
 * 4. 事务管理：Service层是事务的边界，复杂业务操作应该加@Transactional
 * 5. 业务封装：将复杂的数据操作封装成有业务意义的方法
 * 
 * MyBatis Plus教学：
 * - IService<T>：提供基础的CRUD方法（save、update、remove、list等）
 * - 自定义方法：在接口中定义特定业务需求的方法
 * - 实现类：继承ServiceImpl<Mapper, Entity>并实现自定义业务方法
 * 
 * 设计原则：
 * - 单一职责：专门处理题目相关的业务逻辑
 * - 开闭原则：通过接口定义，便于扩展新的实现
 * - 依赖倒置：Controller依赖接口而不是具体实现
 * 
 * @author 智能学习平台开发团队
 * @version 1.0
 */
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface QuestionService extends IService<Question> {
    void customPageService(Page<Question> page, QuestionQueryVo questionQueryVo);

    public Question customDetailQuestion(Long id);

    public List<Question> customDetailQuestionList(Long categoryId);

    public List<Question> customGetQuestionsByDifficulty(String difficulty);

    void customSaveQuestion(Question question);

    void customUpdateQuestion(Question question);

    void customRemoveQuestionById(Long id);

    List<Question> customFindPopularQuestions(Integer size);

    List<Question> customGetRandomQuestions(Integer count, Long categoryId, String difficulty);

        /**
     * 解析并预览Excel文件中的题目内容，不会导入到数据库
     *   文件和格式校验
     *   文件解析处理
     * @param file
     * @return
     */
    List<QuestionImportVo> preViewExcel(MultipartFile file) throws IOException;

     /**
     * 【方法重载】
     * 批量题目导入 [execl和ai生成批量导入]
     *   批量数据库添加
     * @param questionImportVoList
     * @return Excel导入完成！成功导入 %d [工程导入] / %d [题目总数] 道题目
     */
    int importBatchQuestions(List<QuestionImportVo> questionImportVoList) throws IOException;
    /**
     * 进行题目批量导入
     * @param file
     * @return
     */
    String importExeclBatchQuestions(MultipartFile file) throws IOException;
} 