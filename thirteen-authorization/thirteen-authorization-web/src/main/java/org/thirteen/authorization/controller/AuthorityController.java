package org.thirteen.authorization.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thirteen.authorization.web.ResponseResult;

/**
 * @author Aaron.Sun
 * @description 权限校验接口（认证中心）
 * @date Created in 21:35 2018/5/30
 * @modified by
 */
@Api(tags = "权限校验接口")
@RestController
public class AuthorityController {

    @ApiOperation(value = "权限验证", notes = "验证对应请求路径是否允许访问", response = ResponseResult.class)
    @GetMapping(value = "/validate")
    public ResponseResult validate(@ApiParam(required = true, value = "需验证的请求路径") String url) {
        return ResponseResult.ok();
    }

    @ApiOperation(value = "重新加载过滤链", notes = "重新加载shiro过滤链", response = ResponseResult.class)
    @GetMapping(value = "/reloadFilterChains")
    public ResponseResult reloadFilterChains() {
        return ResponseResult.ok();
    }

}