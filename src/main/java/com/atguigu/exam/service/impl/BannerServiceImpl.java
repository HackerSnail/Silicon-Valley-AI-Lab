package com.atguigu.exam.service.impl;

import com.atguigu.exam.entity.Banner;
import com.atguigu.exam.mapper.BannerMapper;
import com.atguigu.exam.service.BannerService;

import com.atguigu.exam.service.FileUploadService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.lang.model.util.Types;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 轮播图服务实现类
 */
@Slf4j
@Service
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {
    // 从配置文件读取参数
    @Value("${banner.upload.max-size:5242880}")  // 默认5MB
    private long maxFileSize;

    @Value("${banner.upload.allowed-types:image/jpeg,image/png,image/gif,image/webp}")
    private Set<String> allowedImageTypes;

    @Value("${banner.upload.dangerous-types:image/svg+xml,image/x-icon}")
    private Set<String> dangerousImageTypes;
    private final FileUploadService fileUploadService;

    public BannerServiceImpl(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadImage(MultipartFile file)throws ServerException, InsufficientDataException,
            ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException,
            InvalidResponseException, XmlParserException, InternalException {
//        // 1. 验证文件是否为空
//        if (file.isEmpty()) {
//            throw new RuntimeException("上传文件不能为空");
//        }
//        //  大小验证
//        if (file.getSize() > 5 * 1024 * 1024) {
//            throw new RuntimeException("图片大小不能超过5MB");
//        }
//        // 2. 快速初步验证文件类型
//        String contentType = file.getContentType();
//        if (contentType == null || !contentType.startsWith("image/")) {
//            throw new RuntimeException("只允许上传图片文件"); // 修正错误信息
//        }
        // 2. 真实类型验证（核心安全保障）
//        String realContentType = getRealContentType(file);
//        // 过滤危险的图片格式
//        if (realContentType == null || DANGEROUS_IMAGE_TYPES.contains(realContentType)) {
//            throw new RuntimeException("不支持该图片格式");
//        }
//        if (!ALLOWED_IMAGE_TYPES.contains(realContentType)) {
//            throw new RuntimeException("只允许上传JPG、PNG、GIF、WEBP格式的图片");
//        }
        try {
            // 1. 基础验证
            validateFileBasic(file);

            // 2. 真实类型验证
            String realContentType = getRealContentType(file);
            validateImageType(realContentType);

            // 3. 执行上传（修正参数顺序）
            String uploadUrl = fileUploadService.uploadFile("banners", file);
            log.info("图片上传成功，URL: {}", uploadUrl);

            return uploadUrl;
        } catch (IOException e) {
            log.error("图片上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("图片上传失败，请稍后重试", e);
        } catch (RuntimeException e) {
            log.warn("图片验证失败: {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addBanner(Banner banner) {
        //1.确认banner createTime和updateTime有时间
        //方式1：数据库设置时间  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
        //方案2：代码时间赋值   set new Date();
        //方案3：使用mybatis-plus自动填充功能 [知识点中会说明]
        //2.判断下启动状态
        if (banner.getIsActive() == null) {
            banner.setIsActive(true);
        }
        //3.判断优先级
        if (banner.getSortOrder() == null) {
            banner.setSortOrder(0);
        }
        //4.进行保存
        boolean isSuccess = save(banner);

        if (!isSuccess) {
            throw new RuntimeException("轮播图保存失败！");
        }

        log.info("轮播图保存成功！！");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateBanner(Banner banner) {
        boolean success = this.updateById(banner);
        if (!success) {
            throw new RuntimeException("轮播图更新失败");
        }
    }

    // 基础文件验证
    private void validateFileBasic(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }
        if (file.getSize() > maxFileSize) {
            throw new RuntimeException("图片大小不能超过5MB");
        }
    }

    // 图片类型验证
    private void validateImageType(String contentType) {
        if (contentType == null) {
            throw new RuntimeException("无法识别文件类型");
        }
        if (dangerousImageTypes.contains(contentType)) {
            throw new RuntimeException("不支持该图片格式");
        }
        if (!allowedImageTypes.contains(contentType)) {
            throw new RuntimeException("只允许上传JPG、PNG、GIF、WEBP格式的图片");
        }
    }

    // 获取真实文件类型
    // 常见文件的魔数与MIME类型映射（可根据业务扩展）
    private static final Map<String, String> MAGIC_NUMBER_MAP = new HashMap<>();

    static {
        // 图片类
        MAGIC_NUMBER_MAP.put("FFD8FF", "image/jpeg");
        MAGIC_NUMBER_MAP.put("89504E47", "image/png");
        MAGIC_NUMBER_MAP.put("47494638", "image/gif");
        MAGIC_NUMBER_MAP.put("52494646", "image/webp");
        // 文档类
        MAGIC_NUMBER_MAP.put("504B0304", "application/zip"); // zip/jar/xlsx/docx等
        MAGIC_NUMBER_MAP.put("25504446", "application/pdf");
        // 视频/音频类
        MAGIC_NUMBER_MAP.put("0000001866747970", "video/mp4");
        MAGIC_NUMBER_MAP.put("4944330300000000", "audio/mp3");
    }

    // 读取文件头的字节数（覆盖大部分文件的魔数长度）
    private static final int BYTE_READ_LENGTH = 8;

    /**
     * 获取MultipartFile的真实MIME类型
     * @param file 上传的文件
     * @return 真实MIME类型，未知类型返回application/octet-stream
     * @throws IOException 流读取异常（统一抛检查型异常，让调用方明确处理）
     */
    private String getRealContentType(MultipartFile file) throws IOException {

        try (InputStream inputStream = file.getInputStream()) {
            // 读取文件头字节（仅读取前8字节，无需加载整个文件）
            byte[] headerBytes = new byte[BYTE_READ_LENGTH];
            int readLen = inputStream.read(headerBytes);
            if (readLen <= 0) {
                return "application/octet-stream";
            }

            // 将字节数组转为十六进制字符串（忽略末尾未读取的0）
            String hexHeader = bytesToHex(headerBytes, readLen).toUpperCase();

            // 匹配魔数获取MIME类型
            for (Map.Entry<String, String> entry : MAGIC_NUMBER_MAP.entrySet()) {
                String magicHex = entry.getKey();
                if (hexHeader.startsWith(magicHex)) {
                    return entry.getValue();
                }
            }

            // 未匹配到已知类型，返回默认二进制类型
            return "application/octet-stream";
        }
    }

    /**
     * 字节数组转十六进制字符串（仅处理前readLen个字节）
     */
    private String bytesToHex(byte[] bytes, int readLen) {
        StringBuilder hexBuilder = new StringBuilder();
        for (int i = 0; i < readLen; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if (hex.length() == 1) {
                hexBuilder.append('0');
            }
            hexBuilder.append(hex);
        }
        return hexBuilder.toString();
    }

}