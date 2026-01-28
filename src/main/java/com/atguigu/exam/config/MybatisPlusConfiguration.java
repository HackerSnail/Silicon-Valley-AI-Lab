package com.atguigu.exam.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.atguigu.exam.mapper")
public class MybatisPlusConfiguration {
}
