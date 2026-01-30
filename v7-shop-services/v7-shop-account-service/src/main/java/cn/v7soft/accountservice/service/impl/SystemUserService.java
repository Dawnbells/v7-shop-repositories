package cn.v7soft.accountservice.service.impl;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.v7soft.accountservice.controller.req.LoginRequest;
import cn.v7soft.accountservice.controller.req.SystemUserAddRequest;
import cn.v7soft.accountservice.service.ISystemUserService;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.dao.dto.SystemUserDto;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import cn.v7soft.dao.tenant.TenantContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 系统用户服务
 */
@Service
public class SystemUserService extends BaseService<SystemUser, SystemUserRepository> implements ISystemUserService {
    private final StringRedisTemplate redisTemplate;

    public SystemUserService(SystemUserRepository systemUserRepository, StringRedisTemplate redisTemplate) {
        super(systemUserRepository);
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public String login(LoginRequest request) {
        try {
            // 登录的时候禁用Tenant过滤
            TenantContext.silent();
            // 调用Repository的方法查询用户
            SystemUser user = repository.findByTelephoneAndTenantId(request.getTelephone(), TenantContext.getCurrentTenant());
            // 如果查询结果不为空，则说明用户存在且密码正确，返回true
            ClientResponseEnum.LOGIN_FAILED.notNull(user);
            ClientResponseEnum.LOGIN_FAILED.assertTrue(user.getStatus() == StatusEnum.VALID, "账号或密码错误");
            ClientResponseEnum.LOGIN_FAILED.assertTrue(BCrypt.checkpw(request.getPassword(), user.getPassword()));
            StpUtil.login(user.getId());
            StpUtil.getSession().set(SaSession.USER, SystemUserDto.convert(user));
        } finally {
            // 恢复
            TenantContext.restore();
        }
        return StpUtil.getTokenValue();
    }

    @Override
    public SystemUser addUser(SystemUserAddRequest request) {
        SystemUser systemUser = repository.findByTelephone(request.getTelephone());
        if (systemUser != null) {
            // 电话号码重复
            ClientResponseEnum.REGISTER_DUPLICATE_TELEPHONE.isNull(systemUser);
        }
        SystemUser user = SystemUser.builder().build();
        BeanUtil.copyProperties(request, user);
        user.setPlainPassword(request.getPassword());
        user.setPassword(BCrypt.hashpw(request.getPassword()));
        return repository.save(user);
    }

    @Override
    public String getTicket() {
        final String ticket = IdUtil.nanoId();
        this.redisTemplate.opsForValue().set(ticket, StpUtil.getTokenValue(), 30, TimeUnit.SECONDS);
        return ticket;
    }

    @Override
    public String loginByTicket(String ticket) {
        String tokenValue = this.redisTemplate.opsForValue().get(ticket);
        if (!StringUtils.hasLength(tokenValue)) {
            ClientResponseEnum.PARAMETER_ILLEGAL.throwException("请检查Ticket是否有效");
        }
        return tokenValue;
    }
}
