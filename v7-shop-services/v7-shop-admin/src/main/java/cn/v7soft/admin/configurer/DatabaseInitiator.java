package cn.v7soft.admin.configurer;

import cn.dev33.satoken.secure.BCrypt;
import cn.v7soft.dao.entities.primary.SystemRouter;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.Gender;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import lombok.AllArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 初始化域名证书申请
 */
@Component
@AllArgsConstructor
public class DatabaseInitiator implements ApplicationRunner {
    private final SystemUserRepository systemUserRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Optional<SystemUser> admin = systemUserRepository.findById(1L);
        if (admin.isPresent()) {
            return;
        }
        initAdminUser();
        initSystemRouter();
    }

    private void initAdminUser() {
        SystemUser systemUser = SystemUser.builder()
                .id(1L)
                .companyId(1L)
                .name("管理员")
                .gender(Gender.MALE)
                .telephone("15880411714")
                .userType(SystemUserType.ADMIN)
                .plainPassword("")
                .password(BCrypt.hashpw("Wq2024"))
                .build();
        systemUser.setId(1L);
        systemUserRepository.saveAndFlush(systemUser);
    }

    private void initSystemRouter() {
        SystemRouter.builder().build();
    }
}
