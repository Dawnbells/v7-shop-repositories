package cn.v7soft.admin.service.impl;

import cn.v7soft.admin.service.ISystemSettingsService;
import cn.v7soft.dao.entities.primary.SystemSettings;
import cn.v7soft.dao.repositories.primary.SystemSettingsRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
public class SystemSettingsService implements ISystemSettingsService {
    private final SystemSettingsRepository repository;
    private static final Long KEY_SSL_SERVER = 1000L;

    @Override
    public String getSslServer() {
        Optional<SystemSettings> systemSettings = repository.findById(KEY_SSL_SERVER);
        return getOr(systemSettings, "");
    }


    private String getOr(Optional<SystemSettings> optional, String defaultValue) {
        if (optional.isEmpty()) {
            return defaultValue;
        }
        return optional.get().getValue();
    }
}
