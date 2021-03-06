package org.thirteen.authorization.interceptor;

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import org.thirteen.authorization.common.utils.JwtUtil;
import org.thirteen.authorization.common.utils.StringUtil;
import org.thirteen.authorization.common.utils.WebUtil;
import org.thirteen.authorization.exceptions.ForbiddenException;
import org.thirteen.authorization.exceptions.UnauthorizedException;
import org.thirteen.authorization.model.vo.SysPermissionVO;
import org.thirteen.authorization.redis.service.RedisTokenService;
import org.thirteen.authorization.redis.token.RedisToken;
import org.thirteen.authorization.service.AuthorityService;
import org.thirteen.authorization.service.SysPermissionService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.thirteen.authorization.constant.GlobalConstants.*;

/**
 * @author Aaron.Sun
 * @description JWT token拦截器，拦截请求并校验token
 * @date Created in 15:38 2020/2/23
 * @modified By
 */
public class JwtInterceptor extends HandlerInterceptorAdapter {

    private final SysPermissionService sysPermissionService;
    private final AuthorityService authorityService;
    private final RedisTokenService redisTokenService;

    /**
     * 所有登陆后可访问的地址
     */
    private List<String> loginUrlList;
    /**
     * 所有认证后可访问的地址
     */
    private List<String> authUrlList;
    /**
     * 所有授权后可访问的地址
     */
    private List<String> permsUrlList;

    public JwtInterceptor(SysPermissionService sysPermissionService, AuthorityService authorityService,
                          RedisTokenService redisTokenService) {
        this.sysPermissionService = sysPermissionService;
        this.authorityService = authorityService;
        this.redisTokenService = redisTokenService;
        this.initFilterChains();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        LocalDateTime now = LocalDateTime.now();
        boolean flag;
        // token验证，如果token有效，设置account到threadLocal
        this.validate(now, request);
        // 地址过滤
        String uri = request.getRequestURI();
        // TODO 需登录判断与需认证判断暂用同一逻辑，待后续拆分
        if (this.loginUrlList.contains(uri) || this.authUrlList.contains(uri) || this.permsUrlList.contains(uri)) {
            if (StringUtil.isEmpty(JwtUtil.getAccount())) {
                throw new UnauthorizedException();
            }
            flag = true;
            // 校验权限
            if (this.permsUrlList.contains(uri)) {
                flag = this.authorityService.validate(uri);
                if (!flag) {
                    throw new ForbiddenException();
                }
            }
        } else {
            flag = true;
        }
        return flag;
    }

    /**
     * token验证，如果token有效，设置account到threadLocal
     *
     * @param now                请求时间
     * @param httpServletRequest 请求对象
     */
    private void validate(LocalDateTime now, HttpServletRequest httpServletRequest) {
        try {
            // 获取token
            String token = JwtUtil.getTokenFromRequest(httpServletRequest);
            // 由token获取redisToken
            RedisToken redisToken = this.redisTokenService.get(token);
            // 判断redisToken是否为空
            if (redisToken != null) {
                // 校验当前redisToken是否过期
                try {
                    JwtUtil.verify(redisToken.getToken());
                    // 更新最后一次访问时间
                    redisToken.setLastAccessTime(now);
                    this.redisTokenService.put(token, redisToken);
                    // 如果未过期，设置当前用户账号到threadLocal
                    JwtUtil.setAccount(redisToken.getAccount());
                } catch (Exception e) {
                    // 如果过期了，判断是否是否在续签token的时间范围内
                    if (this.redisTokenService.isNotExpired(now, redisToken.getLastAccessTime())) {
                        redisToken.setToken(JwtUtil.sign(redisToken.getAccount()));
                        redisToken.setIp(WebUtil.getIpAddr(httpServletRequest));
                        redisToken.setReSignTime(JwtUtil.getIssuedAtDateFromToken(redisToken.getToken()));
                        redisToken.setLastAccessTime(redisToken.getReSignTime());
                        // 续签
                        this.redisTokenService.put(token, redisToken);
                        // 设置当前用户账号到threadLocal
                        JwtUtil.setAccount(redisToken.getAccount());
                    } else {
                        this.redisTokenService.delete(token);
                        JwtUtil.removeAccount();
                    }
                }
            } else {
                JwtUtil.removeAccount();
            }
        } catch (Exception e) {
            // 如果token失效或非法，则删除threadLocal中的用户账号
            JwtUtil.removeAccount();
        }
    }

    /**
     * 初始化过滤链
     */
    public void initFilterChains() {
        // 初始化地址集合
        this.loginUrlList = Arrays.asList("/getCurrentUser", "/validate");
        this.authUrlList = new ArrayList<>();
        this.permsUrlList = new ArrayList<>();
        // 所有未删除的权限集合
        List<SysPermissionVO> allPermissionList = this.sysPermissionService.findAll().getList();
        for (SysPermissionVO permission : allPermissionList) {
            // 判断权限是否启用
            if (ACTIVE_ON.equals(permission.getActive())) {
                // 根据不同的权限类型，设置不同的拦截链
                switch (permission.getType()) {
                    case PERMISSION_LOGIN:
                        this.loginUrlList.add(permission.getUrl());
                        break;
                    case PERMISSION_AUTHOR:
                        this.authUrlList.add(permission.getUrl());
                        break;
                    case PERMISSION_PERMS:
                        this.permsUrlList.add(permission.getUrl());
                        break;
                    default:
                }
            }
        }
    }

}
