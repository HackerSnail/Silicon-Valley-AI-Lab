package com.atguigu.exam.controller;


import com.atguigu.exam.common.Result;
import com.atguigu.exam.entity.Banner;
import com.atguigu.exam.service.BannerService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.minio.errors.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * 轮播图控制器 - 处理轮播图管理相关的HTTP请求
 * 包括图片上传、轮播图的CRUD操作、状态切换等功能
 */
@Slf4j
@RestController  // REST控制器，返回JSON数据
@RequestMapping("/api/banners")  // 轮播图API路径前缀
@CrossOrigin  // 允许跨域访问
@Tag(name = "轮播图管理", description = "轮播图相关操作，包括图片上传、轮播图增删改查、状态管理等功能")  // Swagger API分组
public class BannerController {

    private final BannerService bannerService;

    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    /**
     * 上传轮播图图片
     *
     * @param file 图片文件
     * @return 图片访问URL
     */
    @PostMapping("/upload-image")  // 处理POST请求
    @Operation(summary = "上传轮播图图片", description = "将图片文件上传到MinIO服务器，返回可访问的图片URL")  // API描述
    public Result<String> uploadBannerImage(
            @Parameter(description = "要上传的图片文件，支持jpg、png、gif等格式，大小限制5MB")
            @RequestParam("file") MultipartFile file) throws ServerException, InsufficientDataException,
            ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidResponseException, XmlParserException, InternalException {
        String imageUrl = bannerService.uploadImage(file);
        return Result.success(imageUrl, "图片上传成功");

    }

    /**
     * 获取启用的轮播图（前台首页使用）
     *
     * @return 轮播图列表
     */
    @GetMapping("/active")  // 处理GET请求
    @Operation(summary = "获取启用的轮播图", description = "获取状态为启用的轮播图列表，供前台首页展示使用")  // API描述
    @Transactional(readOnly = true)  // 添加只读事务，启用事务同步
    public Result<List<Banner>> getActiveBanners() {
        LambdaQueryWrapper<Banner> bannerLambdaQueryWrapper = new LambdaQueryWrapper<>();
        bannerLambdaQueryWrapper.eq(Banner::getIsActive, true).orderByDesc(Banner::getSortOrder);
        List<Banner> list = bannerService.list(bannerLambdaQueryWrapper);
        log.info("查询所有前台需要的轮播信息业务执行成功！结果为：{}，共获取{}条记录", list, list.size());
        return Result.success(list, "查询所有前台需要的轮播banner信息成功！");
    }

    /**
     * 获取所有轮播图（管理后台使用）
     *
     * @return 轮播图列表
     */
    @GetMapping("/list")  // 处理GET请求
    @Operation(summary = "获取所有轮播图", description = "获取所有轮播图列表，包括启用和禁用的，供管理后台使用")  // API描述
    @Transactional(readOnly = true)  // 添加只读事务，启用事务同步
    public Result<List<Banner>> getAllBanners() {
        LambdaQueryWrapper<Banner> bannerLambdaQueryWrapper = new LambdaQueryWrapper<>();
        bannerLambdaQueryWrapper.orderByAsc(Banner::getSortOrder);
        List<Banner> list = bannerService.list(bannerLambdaQueryWrapper);
        log.info("查询所有后台需要的轮播信息业务执行成功！结果为：{}", list);
        return Result.success(list, "查询所有banner信息成功！");
    }

    /**
     * 根据ID获取轮播图
     *
     * @param id 轮播图ID
     * @return 轮播图详情
     */
    @GetMapping("/{id}")  // 处理GET请求
    @Operation(summary = "根据ID获取轮播图", description = "根据轮播图ID获取单个轮播图的详细信息")  // API描述
    @Transactional(readOnly = true)  // 添加只读事务，启用事务同步
    public Result<Banner> getBannerById(@Parameter(description = "轮播图ID") @PathVariable Long id) {
        Banner banner = bannerService.getById(id);
        if (banner == null) {
            log.warn("查询轮播图失败！轮播图ID：{}不存在", id);
            return Result.error("轮播图不存在");
        }
        if (!banner.getIsActive()) {
            log.warn("查询轮播图成功！但轮播图ID：{}已被禁用", id);
            return Result.success(banner, "轮播图已被禁用");
        }
        return Result.success(banner, "查询轮播图成功！");
    }

    /**
     * 添加轮播图
     *
     * @param banner 轮播图对象
     * @return 操作结果
     */
    @PostMapping("/add")  // 处理POST请求
    @Operation(summary = "添加轮播图", description = "创建新的轮播图，需要提供图片URL、标题、跳转链接等信息")  // API描述
    public Result<String> addBanner(@RequestBody Banner banner) {
        // 1. 记录请求日志
        log.info("开始添加轮播图，请求参数：{}", banner);

        // 2. 调用Service层处理业务逻辑
        bannerService.addBanner(banner);

        // 3. 记录响应日志
        log.info("轮播图添加成功，ID：{}", banner.getId());
        return Result.success("添加轮播图成功！");
    }

    /**
     * 更新轮播图
     *
     * @param banner 轮播图对象
     * @return 操作结果
     */
    @PutMapping("/update")  // 处理PUT请求
    @Operation(summary = "更新轮播图", description = "更新轮播图的信息，包括图片、标题、跳转链接、排序等")  // API描述
    public Result<String> updateBanner(@RequestBody Banner banner) {
        bannerService.updateBanner(banner);
        return Result.success("更新轮播图成功！");
    }

    /**
     * 删除轮播图
     *
     * @param id 轮播图ID
     * @return 操作结果
     */
    @DeleteMapping("/delete/{id}")  // 处理DELETE请求
    @Operation(summary = "删除轮播图", description = "根据ID删除指定的轮播图")  // API描述
    @Transactional
    public Result<String> deleteBanner(@Parameter(description = "轮播图ID") @PathVariable Long id) {
        boolean success = bannerService.removeById(id);
        // 2. 验证删除结果
        if (!success) {
            log.warn("删除轮播图失败！轮播图ID：{}不存在或已被删除", id);
            throw new RuntimeException("轮播图不存在或已被删除");
        }
        // 3. 记录成功日志
        log.info("轮播图ID：{}删除成功", id);
        return Result.success("", "轮播图删除成功");
    }


    /**
     * 启用/禁用轮播图
     *
     * @param id       轮播图ID
     * @param isActive 是否启用
     * @return 操作结果
     */
    @PutMapping("/toggle/{id}")  // 处理PUT请求
    @Operation(summary = "切换轮播图状态", description = "启用或禁用指定的轮播图，禁用后不会在前台显示")  // API描述
    @Transactional(rollbackFor = Exception.class)
    public Result<String> toggleBannerStatus(
            @Parameter(description = "轮播图ID") @PathVariable Long id,
            @Parameter(description = "是否启用，true为启用，false为禁用") @RequestParam Boolean isActive) {
        // 1. 执行条件更新
        LambdaUpdateWrapper<Banner> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Banner::getId, id)
                .ne(Banner::getIsActive, isActive)  // 状态不同才更新
                .set(Banner::getIsActive, isActive);
        boolean updated = bannerService.update(wrapper);
        // 2. 验证更新结果
        if (!updated) {
            // 检查记录是否存在
            if (bannerService.getById(id) == null) {
                log.warn("切换轮播图状态失败！轮播图ID：{}不存在", id);
                throw new RuntimeException("轮播图不存在");
            }
            // 记录状态未改变
            log.info("轮播图状态无需改变！轮播图ID：{}，当前状态已为{}", id, isActive ? "启用" : "禁用");
            return Result.success("", "轮播图当前已为" + (isActive ? "启用" : "禁用") + "状态");
        }
        // 3. 记录成功日志
        log.info("轮播图状态切换成功！轮播图ID：{}，新状态：{}", id, isActive ? "启用" : "禁用");
        return Result.success("", "轮播图" + (isActive ? "启用" : "禁用") + "成功");

    }
} 