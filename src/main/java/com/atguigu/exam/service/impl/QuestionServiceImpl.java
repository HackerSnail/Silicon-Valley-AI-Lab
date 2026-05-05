package com.atguigu.exam.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.atguigu.exam.common.CacheConstants;
import com.atguigu.exam.entity.PaperQuestion;
import com.atguigu.exam.entity.Question;
import com.atguigu.exam.entity.QuestionAnswer;
import com.atguigu.exam.entity.QuestionChoice;
import com.atguigu.exam.mapper.PaperQuestionMapper;
import com.atguigu.exam.mapper.QuestionAnswerMapper;
import com.atguigu.exam.mapper.QuestionChoiceMapper;
import com.atguigu.exam.mapper.QuestionMapper;
import com.atguigu.exam.service.QuestionService;
import com.atguigu.exam.utils.ExcelUtil;
import com.atguigu.exam.utils.RedisUtils;
import com.atguigu.exam.utils.ThreadPoolUtils;
import com.atguigu.exam.vo.QuestionImportVo;
import com.atguigu.exam.vo.QuestionQueryVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.extern.slf4j.Slf4j;

/**
 * 题目Service实现类
 * 实现题目相关的业务逻辑
 */
@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {
    private final QuestionMapper questionMapper;
    private final RedisUtils redisUtils;
    private final ThreadPoolUtils threadPoolUtils;
    private final QuestionChoiceMapper questionChoiceMapper;
    private final QuestionAnswerMapper questionAnswerMapper;
    private final PaperQuestionMapper paperQuestionMapper;

    public QuestionServiceImpl(QuestionMapper questionMapper, RedisUtils redisUtils, ThreadPoolUtils threadPoolUtils,
            QuestionChoiceMapper questionChoiceMapper, QuestionAnswerMapper questionAnswerMapper,
            PaperQuestionMapper paperQuestionMapper) {
        this.questionMapper = questionMapper;
        this.redisUtils = redisUtils;
        this.threadPoolUtils = threadPoolUtils;
        this.questionChoiceMapper = questionChoiceMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.paperQuestionMapper = paperQuestionMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void customPageService(Page<Question> pageBean, QuestionQueryVo questionQueryVo) {
        questionMapper.customPage(pageBean, questionQueryVo);
    }

    @Override
    public Question customDetailQuestion(Long id) {
        Question question = questionMapper.customGetById(id);
        if (question == null) {
            throw new RuntimeException("题目查询详情失败！原因可能提前被删除！题目id为：" + id);
        }
        // 2.进行热点题目缓存
        // new Thread(() -> {
        // incrementQuestion(id);
        // }).start();
        // new Thread(new Runnable() {
        // @Override
        // public void run() {
        // incrementQuestion(id);
        // }
        // }).start();
        threadPoolUtils.execute(() -> {
            incrementQuestion(id);
        });
        return question;
    }

    // 定义进行题目访问次数增长的方法
    // 异步方法
    // @Async("taskExecutor") （@Async 需要 public）
    // public void incrementQuestion(Long questionId){
    private void incrementQuestion(Long questionId) {
        Double zIncrementScore = redisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY, questionId, 1);
        log.info("完成{}题目分数累计，累计后分数为：{}", questionId, zIncrementScore);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void customSaveQuestion(Question question) {
        // 1.一定插入题目信息 （回显题目id）
        // 同一个类型不能题目title相同
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                .eq(Question::getType, question.getType());
        queryWrapper
                .eq(Question::getTitle, question.getTitle());
        // 自己的业务或者自己的mapper: getBaseMapper() baseMapper
        boolean exists = baseMapper.exists(queryWrapper);
        if (exists) {
            // 同一类型，title相同
            throw new RuntimeException("在%s下，存在%s 名称的题目已经存在！保存失败！".formatted(question.getType(), question.getTitle()));
        }

        boolean saved = save(question);
        if (!saved) {
            // 同一类型，title相同
            throw new RuntimeException("在%s下，存在%s 名称的题目！保存失败！".formatted(question.getType(), question.getTitle()));
        }
        // 2.获取答案对象，并先配置题目id
        QuestionAnswer answer = question.getAnswer();
        answer.setQuestionId(question.getId());
        // 3.判断是不是选择题
        if ("CHOICE".equals(question.getType())) {
            // 是 -》 循环 -》 选项 + 题目id -> 保存 -》 判断是不是正确 进行 AD
            List<QuestionChoice> choices = question.getChoices();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < choices.size(); i++) {
                // 给每个选项匹配questionId
                // [0 [1] 2 [3] ]
                QuestionChoice choice = choices.get(i);
                // 确保，正确顺序！ 否则默认是0 随机了
                choice.setSort(i);
                choice.setQuestionId(question.getId());
                questionChoiceMapper.insert(choice);
                if (choice.getIsCorrect()) {
                    // true 本次是正确答案
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    // B,D
                    sb.append((char) ('A' + i));
                }
            }
            // 进行答案赋值
            answer.setAnswer(sb.toString());
        }
        // 4.保存答案对象
        questionAnswerMapper.insert(answer);
        // 5.保证方法的一致性！ 需要添加事务
    }

    /**
     * 更新题目及其完整信息（包含选项和答案）
     * <p>
     * 业务复杂性：
     * - 需要处理选项的增删改：删除旧选项，添加新选项
     * - 答案更新：覆盖原有答案或新增答案
     * - 数据完整性：确保更新过程中数据一致
     * <p>
     * 实现策略：
     * 1. 更新题目主表信息
     * 2. 删除原有选项，重新插入新选项（简化逻辑）
     * 3. 更新或插入答案信息
     *
     * @param question 包含更新信息的题目对象
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void customUpdateQuestion(Question question) {
        // 1. 题目的校验 （不同id不运行title重复）
        LambdaQueryWrapper<Question> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Question::getTitle, question.getTitle());
        queryWrapper.ne(Question::getId, question.getId());
        boolean exists = baseMapper.exists(queryWrapper);
        if (exists) {
            throw new RuntimeException(
                    "修改：%s题目的新标题：%s和其他的题目重复了！修改失败！".formatted(question.getId(), question.getTitle()));
        }
        // 2. 修改题目
        boolean updated = updateById(question);
        if (!updated) {
            throw new RuntimeException("修改：%s题目失败！！".formatted(question.getId()));
        }
        // 3. 获取答案对象
        QuestionAnswer answer = question.getAnswer();
        // 4. 判断是选择题
        if ("CHOICE".equals(question.getType())) {
            List<QuestionChoice> choiceList = question.getChoices();
            // 删除题目对应的所有选项（原） [根据题目id删除]
            LambdaQueryWrapper<QuestionChoice> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(QuestionChoice::getQuestionId, question.getId());
            questionChoiceMapper.delete(lambdaQueryWrapper);
            // 循环新增选项（选项上id == null）
            // 拼接正确的档案 a,b
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < choiceList.size(); i++) {
                QuestionChoice choice = choiceList.get(i);
                choice.setId(null);
                // 确保，正确顺序！ 否则默认是0 随机了
                choice.setSort(i);
                choice.setCreateTime(null);
                choice.setUpdateTime(null);
                // 新增选项需要！！
                choice.setQuestionId(question.getId());
                questionChoiceMapper.insert(choice);
                if (choice.getIsCorrect()) {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    sb.append((char) ('A' + i));
                }
            }
            // 答案对象赋值选择题答案
            answer.setAnswer(sb.toString());
        }
        // 5. 进行答案的修改
        questionAnswerMapper.updateById(answer);
        // 6. 保证一致性，添加事务
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void customRemoveQuestionById(Long id) {
        // 1. 判断试卷题目表，存在删除失败！
        LambdaQueryWrapper<PaperQuestion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PaperQuestion::getQuestionId, id);
        Long count = paperQuestionMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new RuntimeException("该题目：%s 被试卷表中引用%s次，删除失败！".formatted(id, count));
        }
        // 2. 删除主表 题目表
        boolean removed = removeById(id);
        if (!removed) {
            throw new RuntimeException("该题目：%s 信息删除失败！！");
        }
        // 3. 删除子表 答案和选项表
        questionAnswerMapper.delete(new LambdaQueryWrapper<QuestionAnswer>().eq(QuestionAnswer::getQuestionId, id));
        questionChoiceMapper.delete(new LambdaQueryWrapper<QuestionChoice>().eq(QuestionChoice::getQuestionId, id));
    }

    /**
     * 查询指定数量的热门题目
     * 
     * @param size 默认：6
     * @return
     */

    @Override
    @Transactional
    public List<Question> customFindPopularQuestions(Integer size) {
        // 1. 定义热门题目集合（总集合）
        List<Question> popularQuestions = new ArrayList<>();

        // 2. 去zset中获取热门题目，并且添加到总集合中
        // 获取题目排行，需要获取id和分数！ 分数用于后续的排序处理！
        Set<ZSetOperations.TypedTuple<Object>> tupleSet = redisUtils.zReverseRangeWithScores(CacheConstants.POPULAR_QUESTIONS_KEY, 0, size - 1);
        // 定义接收id的集合
        List<Long> idsSet = new ArrayList<>();
        if (tupleSet != null && tupleSet.size() > 0) {
            // 根据排行榜的积分，倒序进行Id查询！
            List<Long> idsList = tupleSet.stream()
                    .sorted((o1, o2) -> Integer.compare(o2.getScore().intValue(), o1.getScore().intValue()))
                    .map(o -> Long.valueOf(o.getValue().toString())).collect(Collectors.toList());
            // 复制，用于后面补充！！
            idsSet.addAll(idsList);
            log.debug("从redis获取热门题目的id集合，且保证顺序：{}", idsList);

            for (Long id : idsList) {
                Question question = getById(id);
                if (question != null) {
                    // 防止redis有缓存，但是数据库中没有！ 后续优化，删除题目，应该删除热题榜单中对应的value
                    popularQuestions.add(question);
                }
            }
            log.debug("去redis查询的热门题目，题目数：{},题目内容为：{}", popularQuestions.size(), popularQuestions);
        }

        // 3. 检查是否已经满足size
        int diff = size - popularQuestions.size();
        if (diff > 0) {
            // 4. 不满足，题目表中 非热门题目 时间倒序 limit 差数量
            LambdaQueryWrapper<Question> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.orderByDesc(Question::getCreateTime);
                // 只有当 idsSet 不为空时才添加 NOT IN 条件
            if (idsSet != null && !idsSet.isEmpty()) {
                lambdaQueryWrapper.notIn(Question::getId, idsSet);
            }
            // limit diff;

            lambdaQueryWrapper.last("limit " + diff);
            List<Question> questionDiffList = list(lambdaQueryWrapper);
            log.debug("去question表中补充热门题目，题目数：{},题目内容为：{}", questionDiffList.size(), questionDiffList);
            if (questionDiffList != null && questionDiffList.size() > 0) {
                // 5. 补充也添加到总集合中
                popularQuestions.addAll(questionDiffList);
            }
        }
        // 6. 总集合一起进行答案和选项填充
        fillQuestionChoiceAndAnswer(popularQuestions);
        // 7. 返回即可
        return popularQuestions;
    }

    private void fillQuestionChoiceAndAnswer(List<Question> popularQuestions) {
        // // 1. 遍历总集合，填充答案和选项
        // for (Question question : popularQuestions) {
        // // 2. 去答案表中查询答案
        // QuestionAnswer answer = questionAnswerMapper.selectOne(new
        // LambdaQueryWrapper<QuestionAnswer>()
        // .eq(QuestionAnswer::getQuestionId, question.getId()));
        // // 3. 去选项表中查询选项
        // List<QuestionChoice> choices = questionChoiceMapper.selectList(new
        // LambdaQueryWrapper<QuestionChoice>()
        // .eq(QuestionChoice::getQuestionId, question.getId()));
        // // 4. 填充到题目对象中
        // question.setAnswer(answer);
        // question.setChoices(choices);
        // }
        // 1. 提取所有题目ID
        List<Long> questionIds = popularQuestions.stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        // 2. 批量查询答案
        Map<Long, QuestionAnswer> answerMap = questionAnswerMapper
                .selectList(new LambdaQueryWrapper<QuestionAnswer>()
                        .in(QuestionAnswer::getQuestionId, questionIds))
                .stream()
                .collect(Collectors.toMap(QuestionAnswer::getQuestionId, a -> a));

        // 3. 批量查询选项
        Map<Long, List<QuestionChoice>> choiceMap = questionChoiceMapper
                .selectList(new LambdaQueryWrapper<QuestionChoice>()
                        .in(QuestionChoice::getQuestionId, questionIds))
                .stream()
                .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));

        // 4. 填充数据
        for (Question question : popularQuestions) {
            question.setAnswer(answerMap.get(question.getId()));
            question.setChoices(choiceMap.get(question.getId()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<Question> customDetailQuestionList(Long categoryId) {
         // 1. 查询该分类下的所有题目（包含答案和选项）
        List<Question> questions = questionMapper.selectListByCategory(categoryId);
        
        // 2. 如果题目不多，直接返回；如果多，用批量查询优化
        if (questions.isEmpty()) {
            return questions;
        }
        
        // 3. 批量查询答案和选项（避免N+1问题）
        fillQuestionChoiceAndAnswer(questions);
        
        return questions;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Question> customGetQuestionsByDifficulty(String difficulty) {
        List<Question> questions = questionMapper.selectListByDifficulty(difficulty);
        fillQuestionChoiceAndAnswer(questions);
        return questions;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Question> customGetRandomQuestions(Integer count, Long categoryId, String difficulty) {
        if (count == null || count <= 0) {
            count = 10;
        }
        if (count > 100) {
            count = 100; // 限制最大抽取数量
        }
        
        List<Question> questions = questionMapper.selectRandomQuestions(count, categoryId, difficulty);
        
        // 填充答案和选项（如果SQL已经JOIN了，这步可以省略）
        fillQuestionChoiceAndAnswer(questions);
        //只统计用户真正感兴趣、查看详情的题目
        // // 异步增加热度
        // for (Question question : questions) {
        //     threadPoolUtils.execute(() -> {
        //         incrementQuestion(question.getId());
        //     });
        // }
        
        return questions;
    }
    /**
     * 解析并预览Excel文件中的题目内容，不会导入到数据库
     * 文件和格式校验
     * 文件解析处理
     * @param file
     * @return
    */
    @Override
    public List<QuestionImportVo> preViewExcel(MultipartFile file) throws IOException {
        //数据校验
        if (file == null || file.isEmpty()){
            throw new RuntimeException("预览数据的文件为空！");
        }
        String fileName = file.getOriginalFilename();
        //xls xlsx
        if (fileName == null || fileName.isEmpty() ||
    (!fileName.toLowerCase().endsWith(".xls") && !fileName.toLowerCase().endsWith(".xlsx"))){
            throw new RuntimeException("预览数据的文件格式错误，必须是 .xls或者.xlsx！");
        }
        //解析数据
        List<QuestionImportVo> questionImportVoList = ExcelUtil.parseExcel(file);
        //返回结果
        return questionImportVoList;
    }
    /**
  * 批量题目导入 [execl和ai生成批量导入]
  * 批量数据库添加
  * @param questionImportVoList
  * @return Excel导入完成！成功导入 %d [工程导入] / %d [题目总数] 道题目
  */
    @Override
    public int importBatchQuestions(List<QuestionImportVo> questions) {
        //1. 进行数据校验
        if (questions == null || questions.isEmpty()){
            throw new RuntimeException("导入的题目集合为空！");
        }

        //3. 循环 + try 调用保存的方法 [部分成功]
        int successCount = 0;
        for (int i = 0; i < questions.size(); i++) {
            try {
                //2. 进行vo - question [提取一个方法]
                Question question =  convertQuestionImportVoToQuestion(questions.get(i));
                //数据单体保存
                customSaveQuestion(question);
                //正确技术统计
                successCount++;
            }catch (Exception e){
                //导入失败的提示
                log.debug("{}题目导入失败！",questions.get(i).getTitle());
            }
        }
        return successCount;
    }
    private Question convertQuestionImportVoToQuestion(QuestionImportVo questionImportVo) {
        //1. 给question本体属性赋值
        Question question = new Question();
        //question.setTitle(questionImportVo.getTitle());
        /**
             * 作用：给对象的属性进行赋值！根据另一个对象的相同属性值！
             * 参数1：source 源对象 【提供值】
             * 参数2：target 目标对象 【接收值】
             */
        BeanUtils.copyProperties(questionImportVo,question);

        //2. 判断是选择，给选项集合进行赋值
        if ("CHOICE".equals(questionImportVo.getType())){
            if (questionImportVo.getChoices().size() > 0) {
                List<QuestionChoice> questionChoices = new ArrayList<>(questionImportVo.getChoices().size());
                for (QuestionImportVo.ChoiceImportDto importVoChoice : questionImportVo.getChoices()) {
                    QuestionChoice questionChoice = new QuestionChoice();
                    questionChoice.setContent(importVoChoice.getContent());
                    questionChoice.setIsCorrect(importVoChoice.getIsCorrect());
                    questionChoice.setSort(importVoChoice.getSort());
                    questionChoices.add(questionChoice);
                }
                question.setChoices(questionChoices);
            }
        }
        //3. 不管是不是选择题创建答案对象并赋值 【保存的时候，获取答案对象，选择题可以没有答案值，保存会判断答案值】
        QuestionAnswer questionAnswer = new QuestionAnswer();
        //判断题，需要将true和false转成大写！ 否则无法识别！！
        if ("JUDGE".equals(questionImportVo.getType())){
            questionAnswer.setAnswer(questionImportVo.getAnswer().toUpperCase());
        }else{
            questionAnswer.setAnswer(questionImportVo.getAnswer());
        }
        questionAnswer.setKeywords(questionImportVo.getKeywords());
        question.setAnswer(questionAnswer);

        return question;
    }
    /**
     * 进行题目批量导入
     * @param file
     * @return
     */
    @Override
    public String importExeclBatchQuestions(MultipartFile file) throws IOException {
        //1.校验
        if (file == null || file.isEmpty()){
            throw new RuntimeException("批量导入的文件为空！");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isEmpty() ||
    (!filename.toLowerCase().endsWith(".xls") && !filename.toLowerCase().endsWith(".xlsx"))){
            throw new RuntimeException("批量导入的文件格式错误，必须是 .xls或者.xlsx！");
        }
        //2.解析文件
        List<QuestionImportVo> questionImportVoList = ExcelUtil.parseExcel(file);
        //3.调用批量导入业务
        int successCount = importBatchQuestions(questionImportVoList);
        String result = "批量导入题目接口调用成功！ 一共：%s 题目需要导入，成功导入了：%s 道题！".formatted(questionImportVoList.size(),successCount);
        return result;
    }
}
